"""Fail-closed protected CI candidate builder; never publishes a release.

All approval pins are reviewed data in release-policy.json. No SDK is fetched,
copied, cached, or uploaded. Error output deliberately excludes SDK paths.
"""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "ci/release-policy.json"
EXPECTED_REPO = "seancummins/vcf-content-factory-sdk-rdm-topology"
KIND = "vcfcf_rdm_topology_poc"
SHA = re.compile(r"[0-9a-f]{64}")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with Path(path).open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def check_digest(path, expected):
    require(isinstance(expected, str) and SHA.fullmatch(expected), "unapproved checksum")
    require(digest(path) == expected, "checksum mismatch")


def tree_digest(root):
    """Stable digest of installed PyYAML source/native files, excluding bytecode."""
    h = hashlib.sha256()
    for path in sorted(Path(root).rglob("*")):
        if path.is_file() and "__pycache__" not in path.parts:
            h.update(path.relative_to(root).as_posix().encode() + b"\0")
            h.update(bytes.fromhex(digest(path)))
    return h.hexdigest()


def check_context(policy, env, head):
    require(policy["release_enabled"] is True, "release policy disabled")
    require(env.get("GITHUB_ACTIONS") == "true", "CI required")
    require(env.get("GITHUB_REPOSITORY") == EXPECTED_REPO == policy["repository"], "wrong repository")
    require(env.get("GITHUB_EVENT_NAME") == "push" and env.get("GITHUB_REF_TYPE") == "tag", "protected tag push required")
    require(env.get("GITHUB_REF_PROTECTED") == "true", "protected ref required")
    require(env.get("RDM_RUNNER_APPROVED") == "true", "runner approval missing")
    require(re.fullmatch(r"[0-9a-f]{40}", head) is not None, "invalid source revision")
    require(head == env.get("GITHUB_SHA") == env.get("RDM_APPROVED_SOURCE_SHA"), "source approval mismatch")


def check_version(metadata, tag):
    version, build = metadata.get("version"), metadata.get("build_number")
    require(isinstance(version, str) and re.fullmatch(r"[1-9][0-9]*\.[0-9]+\.[0-9]+", version), "approved 1.x+ version required")
    require(type(build) is int and build > 0, "positive integer build required")
    require(tag == f"v{version}.{build}", "tag/version/build mismatch")
    require(metadata.get("adapter_kind") == KIND, "unexpected adapter kind")
    return f"vcfcf_sdk_rdm_topology_poc.{version}.{build}.pak"


def has_git_ancestor(path):
    return any((parent / ".git").exists() for parent in path.parents)


def check_sdk(path, blocked_roots, expected):
    require(path.is_absolute() and path.is_file(), "SDK provisioning unavailable")
    resolved = path.resolve(strict=True)
    require(not any(resolved == p.resolve() or resolved.is_relative_to(p.resolve()) for p in blocked_roots), "SDK must be outside all build/upload/checkouts/temp roots")
    require(not has_git_ancestor(resolved), "SDK must be outside Git checkouts")
    check_digest(resolved, expected)
    return resolved


def unpack_kit(archive, target):
    """Extract only regular files/directories in the documented kit root."""
    with tarfile.open(archive) as tf:
        names = set()
        members = tf.getmembers()
        for member in members:
            p = PurePosixPath(member.name)
            require(not p.is_absolute() and ".." not in p.parts and p.parts[0] == "sdk_buildkit", "unsafe kit path")
            require(member.isdir() or member.isfile(), "kit links/devices forbidden")
            require(member.name not in names, "duplicate kit member")
            names.add(member.name)
        tf.extractall(target, members=members, filter="data")


def check_zip(z):
    names = z.namelist()
    require(len(names) == len(set(names)) and z.testzip() is None, "invalid archive integrity")
    for name in names:
        p = PurePosixPath(name)
        require(not p.is_absolute() and ".." not in p.parts and "\\" not in name, "unsafe archive path")
        require(not any(x in name.lower() for x in (".env", "token", "password", "credential.json", "lab-probe", "rdm-lab", "inventory.json")), "unexpected sensitive artifact")


def inspect_pak(path, version, sdk_hash):
    """Bound the fixture-only package surface; do not whitelist SDK bytecode."""
    with zipfile.ZipFile(path) as outer:
        check_zip(outer)
        require(json.loads(outer.read("manifest.txt"))["version"] == version, "PAK version mismatch")
        require(set(outer.namelist()) == {"manifest.txt", "eula.txt", "default.svg", "resources/", "resources/resources.properties", "overview.packed", "adapters.zip"}, "unexpected outer PAK files")
        with zipfile.ZipFile(io.BytesIO(outer.read("adapters.zip"))) as inner:
            check_zip(inner)
            allowed = {"manifest.txt", "eula.txt", "default.svg", "resources/resources.properties", f"{KIND}.jar", f"{KIND}/conf/describe.xml", f"{KIND}/conf/version.txt", f"{KIND}/conf/resources/resources.properties", f"{KIND}/lib/vcfcf-adapter-base.jar", f"{KIND}/conf/images/AdapterKind/{KIND}.svg", f"{KIND}/conf/images/ResourceKind/{KIND}.svg", f"{KIND}/conf/images/ResourceKind/RdmMapping.svg", f"{KIND}/conf/images/TraversalSpec/default.svg"}
            require({n for n in inner.namelist() if not n.endswith("/")} == allowed, "unexpected inner PAK files")
            require(json.loads(inner.read("manifest.txt"))["version"] == version, "inner version mismatch")
            jars = {n for n in inner.namelist() if n.endswith(".jar")}
            require(jars == {f"{KIND}.jar", f"{KIND}/lib/vcfcf-adapter-base.jar"}, "unexpected runtime dependency")
            for name in jars:
                blob = inner.read(name)
                require(hashlib.sha256(blob).hexdigest() != sdk_hash, "proprietary SDK bundled")
                with zipfile.ZipFile(io.BytesIO(blob)) as jar:
                    check_zip(jar)
                    for entry in jar.namelist():
                        require(entry.startswith(("META-INF/", "com/vcfcf/")) or entry in ("com/", "adapter.properties"), "unexpected JAR payload or vendor bytecode")
            xml = ET.fromstring(inner.read(f"{KIND}/conf/describe.xml"))
            for element in xml.iter():
                element.tag = element.tag.rsplit("}", 1)[-1]
            require(xml.find(".//CredentialKinds") is None and not any("credentialKind" in e.attrib for e in xml.iter()), "credential contract changed")
            instance = xml.find(f".//ResourceKind[@key='{KIND}']")
            require(instance is not None and instance.get("isCredentialRequired") == "false", "explicit no-auth contract required")


def run(argv, **kwargs):
    # Capture subprocess errors: compiler/tool diagnostics can include SDK paths.
    return subprocess.run(argv, check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, **kwargs).stdout


def sanitize_diagnostic(diagnostic, env):
    replacements = {str(ROOT): "<source>"}
    for key in ("VCFCF_SDK_JAR", "GITHUB_WORKSPACE", "RUNNER_TEMP"):
        value = env.get(key)
        if value:
            replacements[value] = f"<{key.lower()}>"
            # Compiler arguments use realpath after provisioning checks, even
            # when protected runner configuration supplies a symlink.
            replacements[str(Path(value).resolve())] = f"<{key.lower()}>"
    for value in sorted(replacements, key=len, reverse=True):
        diagnostic = diagnostic.replace(value, replacements[value])
    return diagnostic


def check_toolchain(policy):
    import yaml
    require(platform.python_version() == policy["python_version"], "Python version mismatch")
    check_digest(sys.executable, policy["python_sha256"])
    require(yaml.__version__ == policy["pyyaml_version"], "PyYAML version mismatch")
    require(tree_digest(Path(yaml.__file__).parent) == policy["pyyaml_tree_sha256"], "PyYAML integrity mismatch")
    result = {"python": platform.python_version(), "pyyaml": yaml.__version__}
    for name in ("java", "javac"):
        executable = shutil.which(name)
        require(executable is not None, "JDK unavailable")
        check_digest(executable, policy[f"{name}_sha256"])
        reported = run([executable, "-version"]).strip()
        require(reported == policy[f"{name}_version"], "JDK version mismatch")
        result[name] = reported
    return result


def check_registry_shape(path):
    """Reject empty/error downloads before invoking the unchanged vendored gate."""
    text = path.read_text(encoding="utf-8")
    require(text.startswith("# Defect registry\n"), "unrecognized defect registry header")
    require(re.search(r"^#{1,4}\s+DEF-[0-9]+\s*$", text, re.MULTILINE) is not None, "defect registry history missing")
    require(re.search(r"<(?:!doctype\s+html|html|body)(?:\s|>)", text, re.IGNORECASE) is None, "HTML defect registry response")


def main():
    policy = json.loads(POLICY.read_text())
    head = run(["git", "rev-parse", "HEAD"], cwd=ROOT).strip()
    check_context(policy, os.environ, head)
    run(["git", "merge-base", "--is-ancestor", "HEAD", "refs/remotes/origin/main"], cwd=ROOT)
    require(not run(["git", "status", "--porcelain"], cwd=ROOT).strip(), "clean source checkout required")
    check_digest(ROOT / "ci/defect_gate.py", policy["defect_gate_sha256"])
    toolchain = check_toolchain(policy)
    import yaml
    metadata = yaml.safe_load((ROOT / "adapter.yaml").read_text())
    filename = check_version(metadata, os.environ.get("GITHUB_REF_NAME"))
    require(re.fullmatch(r"sdk-buildkit-v[0-9]+\.[0-9]+\.[0-9]+", policy.get("buildkit_tag") or ""), "full published kit tag required")
    require(policy["buildkit_tag"] == "sdk-buildkit-v" + str(policy.get("buildkit_version")), "kit version/tag mismatch")
    require(policy["buildkit_repository"] == "sentania-labs/vcf-content-factory", "unexpected buildkit source")
    output = ROOT / "release-output"
    require(not output.exists(), "refuse existing output")
    workspace = Path(os.environ["GITHUB_WORKSPACE"]).resolve()
    runner_temp = Path(os.environ["RUNNER_TEMP"]).resolve()
    sdk = check_sdk(Path(os.environ.get("VCFCF_SDK_JAR", "")), [ROOT, workspace, workspace.parent, runner_temp, Path(tempfile.gettempdir()), output], policy["sdk_sha256"])
    with tempfile.TemporaryDirectory(prefix="rdm-release-", dir=runner_temp) as scratch:
        work = Path(scratch)
        archive = work / "kit.tgz"
        asset = f"sdk-buildkit-{policy['buildkit_version']}.tgz"
        run(["gh", "release", "download", policy["buildkit_tag"], "--repo", policy["buildkit_repository"], "--pattern", asset, "--output", str(archive)])
        check_digest(archive, policy["buildkit_sha256"])
        unpack_kit(archive, work)
        kit = work / "sdk_buildkit"
        require((kit / "VERSION").read_text().strip() == policy["buildkit_version"], "embedded kit version mismatch")
        reference = kit / "reference_paks" / policy["reference_filename"]
        check_digest(reference, policy["reference_sha256"])
        registry = work / "defects.md"
        run(["curl", "--fail", "--silent", "--show-error", "--location", "https://raw.githubusercontent.com/sentania-labs/vcf-content-factory/main/knowledge/context/defects.md", "--output", str(registry)])
        check_registry_shape(registry)
        run([sys.executable, str(ROOT / "ci/defect_gate.py"), "--pak", "rdm-topology-poc", "--registry", str(registry)])
        env = {**os.environ, "PYTHONPATH": str(work), "VCFCF_SDK_JAR": str(sdk)}
        prefix = [sys.executable, "-m", "sdk_buildkit"]
        run(prefix + ["validate-sdk", str(ROOT), "--sdk-jar", str(sdk)], env=env)
        test_env = {**env, "VCFCF_FRAMEWORK_JAR": str(kit / "adapter_runtime/vcfcf-adapter-base.jar")}
        test_output = run(["bash", str(ROOT / "scripts/run-tests.sh")], env=test_env)
        build_output = run(prefix + ["build-sdk", str(ROOT), "--release", "--sdk-jar", str(sdk), "--output", str(work / "dist"), "--pak-compare-policy", "structural", "--pak-compare-reference", str(reference), "--pak-compare-reference-sha256", policy["reference_sha256"]], env=env)
        pak = work / "dist" / filename
        require(set(p.name for p in pak.parent.glob("*.pak")) == {filename}, "unexpected artifact name/count")
        report = run(prefix + ["pak-compare", str(pak), str(reference), "--policy", "structural", "--reference-sha256", policy["reference_sha256"]], env=env)
        inspect_pak(pak, f"{metadata['version']}.{metadata['build_number']}", policy["sdk_sha256"])
        checksum = digest(pak)
        # Only the small, explicitly listed products reach the upload directory.
        output.mkdir(mode=0o700)
        shutil.copyfile(pak, output / filename)
        (output / (filename + ".sha256")).write_text(f"{checksum}  {filename}\n")
        report = sanitize_diagnostic(report.replace(str(sdk), "<external-sdk>").replace(str(work), "<build>"), os.environ)
        (output / "comparison.txt").write_text(report)
        provenance = {"source_commit": head, "tag": os.environ["GITHUB_REF_NAME"], "repository": EXPECTED_REPO, "artifact": filename, "sha256": checksum, "policy": "structural", "reference_release": "v1.0.0.30", "reference_sha256": policy["reference_sha256"], "buildkit_tag": policy["buildkit_tag"], "buildkit_sha256": policy["buildkit_sha256"], "sdk_sha256": policy["sdk_sha256"], "toolchain": toolchain, "defect_registry_sha256": digest(registry), "test_output_sha256": hashlib.sha256(test_output.encode()).hexdigest(), "build_output_sha256": hashlib.sha256(build_output.encode()).hexdigest()}
        (output / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
        print(f"Verified candidate {filename}: {checksum}")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Preserve all comparison findings; redact local SDK/build/source paths.
        if isinstance(error, subprocess.CalledProcessError):
            print(sanitize_diagnostic(error.output or "", os.environ), file=sys.stderr)
        print(f"Release candidate failed ({type(error).__name__}); no publication occurred.", file=sys.stderr)
        sys.exit(1)
