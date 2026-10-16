"""Pure ZIP metadata tests. Synthetic entries are never passed to a JVM loader."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


MODULE = Path(__file__).resolve().parents[1] / "scripts/check-descriptor-dependencies.py"
SPEC = importlib.util.spec_from_file_location("descriptor_dependencies", MODULE)
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)
SDK_CLASS = "com/integrien/alive/common/adapter3/describe/AdapterDescribe.class"


class DependencyInventoryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="rdm-dependency-test-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.sdk = self.make_jar("sdk.jar", {SDK_CLASS: b"metadata-only-not-bytecode"})

    def make_jar(self, filename, entries, manifest=None):
        path = self.root / filename
        with zipfile.ZipFile(path, "w") as jar:
            if manifest is not None:
                jar.writestr("META-INF/MANIFEST.MF", manifest)
            for name, value in entries.items():
                jar.writestr(name, value)
        return str(path)

    def rejects(self, code, sdk=None, extra=""):
        with self.assertRaises(CHECK.InvalidClasspath) as raised:
            CHECK.inspect(self.sdk if sdk is None else sdk, extra)
        self.assertEqual(code, raised.exception.code)

    def test_missing_classes_are_inventory_not_loader_success(self):
        reports = CHECK.inspect(self.sdk, "")
        self.assertTrue(any("LogManager status=MISSING" in line for line in reports))
        self.assertTrue(any("INVENTORY_ONLY" in line for line in reports))
        self.assertFalse(any("code=PASS" in line for line in reports))

    def test_observed_lang2_dependency_is_not_satisfied_by_lang3(self):
        # Metadata entries only: these bytes are never passed to Java.
        lang3 = self.make_jar("lang3.jar", {"org/apache/commons/lang3/exception/ExceptionUtils.class": b"metadata-only"})
        reports = CHECK.inspect(self.sdk, lang3)
        expected = ("DESCRIPTOR_DEPENDENCY scope=observed_transitive_loader "
                    "class=org.apache.commons.lang.exception.NestableException status=MISSING")
        self.assertIn(expected, reports)
        lang2 = self.make_jar("lang2.jar", {"org/apache/commons/lang/exception/NestableException.class": b"metadata-only"})
        reports = CHECK.inspect(self.sdk, lang2)
        self.assertIn(expected.replace("status=MISSING", "status=PRESENT jar_index=1"), reports)

    def test_checksum_and_sanitized_version(self):
        dep = self.make_jar("dep.jar", {"example/Metadata.class": b"not-bytecode"},
                            "Manifest-Version: 1.0\r\nImplementation-Version: 2.17.2\r\n\r\n")
        reports = CHECK.inspect(self.sdk, dep)
        self.assertIn("implementation_version=2.17.2", reports[1])
        self.assertRegex(reports[1], r"sha256=[0-9a-f]{64}")
        self.assertFalse(any(self.directory.name in line for line in reports))

    def test_reject_directory(self):
        self.rejects("EXPLICIT_JARS_REQUIRED", extra=str(self.root))

    def test_reject_wildcard(self):
        self.rejects("EXPLICIT_JARS_REQUIRED", extra=str(self.root / "*.jar"))

    def test_reject_empty_classpath_element(self):
        self.rejects("EXPLICIT_JARS_REQUIRED", extra=self.make_jar("empty-element.jar", {}) + ":")

    def test_reject_duplicate_path(self):
        self.rejects("DUPLICATE_JAR", extra=self.sdk)

    def test_reject_duplicate_classes(self):
        dep = self.make_jar("dup.jar", {SDK_CLASS: b"not-bytecode"})
        self.rejects("DUPLICATE_CLASS", extra=dep)

    def test_reject_manifest_hidden_classpath(self):
        dep = self.make_jar("hidden.jar", {}, "Manifest-Version: 1.0\nClass-Path: ../tests/\n\n")
        self.rejects("IMPLICIT_MANIFEST_CLASSPATH", extra=dep)

    def test_reject_folded_manifest_hidden_classpath(self):
        dep = self.make_jar("folded.jar", {}, "Manifest-Version: 1.0\nClass-Path: ../\n tests/\n\n")
        self.rejects("IMPLICIT_MANIFEST_CLASSPATH", extra=dep)

    def test_wrong_sdk_is_not_accepted(self):
        self.rejects("SDK_CLASS_UNAVAILABLE", sdk=self.make_jar("wrong.jar", {}))

    def test_version_content_is_redacted(self):
        dep = self.make_jar("private.jar", {}, "Manifest-Version: 1.0\nImplementation-Version: private value token\n\n")
        reports = CHECK.inspect(self.sdk, dep)
        self.assertTrue(all("private value token" not in line for line in reports))
        self.assertIn("implementation_version=unrecorded", reports[1])

    def test_malformed_manifest(self):
        dep = self.make_jar("malformed.jar", {}, " malformed\n")
        self.rejects("INVALID_MANIFEST", extra=dep)

    def test_cli_corrupt_jar_is_sanitized(self):
        invalid = self.root / "private-sensitive-name.jar"
        invalid.write_bytes(b"not a jar")
        result = subprocess.run([sys.executable, str(MODULE)], capture_output=True, text=True,
                                env={**os.environ, "VCFCF_SDK_JAR": str(invalid),
                                     "VCFCF_DESCRIPTOR_CLASSPATH": ""}, check=False)
        self.assertEqual(2, result.returncode)
        self.assertEqual("DESCRIPTOR_DEPENDENCY code=UNREADABLE_OR_INVALID_JAR\n", result.stdout)
        self.assertEqual("", result.stderr)

    def test_inherited_jvm_injection_is_rejected_before_java(self):
        shell = MODULE.with_name("check-sdk-descriptor-load.sh")
        descriptor = MODULE.parent.parent / "describe.xml"
        for key in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH"):
            with self.subTest(key=key):
                environment = {**os.environ, "VCFCF_SDK_JAR": self.sdk,
                               "VCFCF_DESCRIPTOR_CLASSPATH": ""}
                for option in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH"):
                    environment.pop(option, None)
                environment[key] = "private-injected-option"
                result = subprocess.run(["bash", str(shell), str(descriptor), "vcfcf_rdm_topology_poc"],
                                        capture_output=True, text=True, env=environment, check=False)
                self.assertEqual(2, result.returncode)
                self.assertEqual("DESCRIPTOR_LOAD code=BLOCKED_INHERITED_JAVA_CONFIGURATION\n", result.stdout)
                self.assertEqual("", result.stderr)


if __name__ == "__main__":
    unittest.main()
