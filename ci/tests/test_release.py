"""Offline checks: no SDK, network, PAK build or third-party test dependencies."""
import copy
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest import mock
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("rdm_release", ROOT / "ci/release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.policy = json.loads((ROOT / "ci/release-policy.json").read_text())
        self.approved = {**self.policy, "release_enabled": True}
        self.sha = "a" * 40
        self.env = {"GITHUB_ACTIONS": "true", "GITHUB_REPOSITORY": release.EXPECTED_REPO, "GITHUB_EVENT_NAME": "push", "GITHUB_REF_TYPE": "tag", "GITHUB_REF_PROTECTED": "true", "RDM_RUNNER_APPROVED": "true", "GITHUB_SHA": self.sha, "RDM_APPROVED_SOURCE_SHA": self.sha}

    def test_disabled_policy_fails(self):
        with self.assertRaisesRegex(ValueError, "disabled"):
            release.check_context(self.policy, self.env, self.sha)

    def test_approved_context(self):
        release.check_context(self.approved, self.env, self.sha)

    def test_missing_or_wrong_approvals_rejected(self):
        for key in self.env:
            with self.subTest(key=key):
                env = {**self.env, key: "wrong"}
                with self.assertRaises(ValueError):
                    release.check_context(self.approved, env, self.sha)

    def test_wrong_repository_policy_rejected(self):
        with self.assertRaises(ValueError):
            release.check_context({**self.approved, "repository": "other/repo"}, self.env, self.sha)

    def test_f2_private_repository_contract_and_old_owner_rejected(self):
        destination = "seancummins/vcf-content-factory-sdk-rdm-topology"
        self.assertEqual(release.EXPECTED_REPO, destination)
        self.assertEqual(self.policy["repository"], destination)
        self.assertEqual(self.policy["bootstrap_visibility"], "private")
        self.assertEqual(self.policy["default_branch"], "main")
        self.assertIs(self.policy["release_enabled"], False)
        for key in ("buildkit_tag", "buildkit_version", "buildkit_sha256", "sdk_sha256", "python_version", "python_sha256", "java_version", "java_sha256", "javac_version", "javac_sha256", "pyyaml_version", "pyyaml_tree_sha256"):
            self.assertIsNone(self.policy[key], key)
        workflow = (ROOT / ".github/workflows/build-pak-on-tag.yml").read_text()
        self.assertIn(f"github.repository == '{destination}'", workflow)
        old_destination = "sentania-labs/vcf-content-factory-sdk-rdm-topology-poc"
        self.assertNotIn(old_destination, workflow)
        old_env = {**self.env, "GITHUB_REPOSITORY": old_destination}
        with self.assertRaisesRegex(ValueError, "repository"):
            release.check_context({**self.approved, "repository": old_destination}, old_env, self.sha)
        owners = (ROOT / ".github/CODEOWNERS").read_text()
        self.assertIn("* @seancummins", owners)

    def test_version_and_artifact_consistency(self):
        self.assertEqual(release.check_version({"version": "1.0.0", "build_number": 5, "adapter_kind": release.KIND}, "v1.0.0.5"), "vcfcf_sdk_rdm_topology_poc.1.0.0.5.pak")

    def test_prototype_or_invalid_versions_and_tags_rejected(self):
        valid = {"version": "1.0.0", "build_number": 5, "adapter_kind": release.KIND}
        cases = [({**valid, "version": "0.1.0"}, "v0.1.0.5"), ({**valid, "build_number": True}, "v1.0.0.True"), ({**valid, "build_number": 0}, "v1.0.0.0"), ({**valid, "adapter_kind": "other"}, "v1.0.0.5"), (valid, "v1.0.0.4"), (valid, "v1.0.0.5-rc1"), ({**valid, "version": "1.0"}, "v1.0.5")]
        for metadata, tag in cases:
            with self.subTest(metadata=metadata, tag=tag), self.assertRaises(ValueError):
                release.check_version(metadata, tag)

    def test_checksum_requires_approval_and_exact_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "fake"
            path.write_bytes(b"fixture")
            release.check_digest(path, hashlib.sha256(b"fixture").hexdigest())
            for expected in (None, "a" * 64, "", "v1"):
                with self.subTest(expected=expected), self.assertRaises(ValueError):
                    release.check_digest(path, expected)

    def test_sdk_path_and_symlink_scope(self):
        # Scope tests isolate checkout detection (separately exercised below);
        # the developer's /tmp may itself contain unrelated .git metadata.
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(release, "has_git_ancestor", return_value=False):
            root = Path(tmp)
            forbidden = root / "workspace"
            forbidden.mkdir()
            external = root / "admin-sdk.jar"
            external.write_bytes(b"fake SDK fixture")
            h = release.digest(external)
            self.assertEqual(release.check_sdk(external, [forbidden], h), external)
            (forbidden / "bad.jar").write_bytes(b"fake SDK fixture")
            outside_link = root / "link.jar"
            outside_link.symlink_to(forbidden / "bad.jar")
            for path in (forbidden / "bad.jar", outside_link, Path("relative.jar")):
                with self.subTest(path=path), self.assertRaises(ValueError):
                    release.check_sdk(path, [forbidden], h)

    def test_sdk_inside_independent_checkout_or_worktree_rejected(self):
        for git_kind in ("directory", "file"):
            with self.subTest(git_kind=git_kind), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                marker = root / ".git"
                marker.mkdir() if git_kind == "directory" else marker.write_text("gitdir: /fixture/git")
                sdk = root / "sdk.jar"
                sdk.write_bytes(b"fixture")
                with self.assertRaisesRegex(ValueError, "Git checkouts"):
                    release.check_sdk(sdk, [], release.digest(sdk))

    def test_failed_subprocess_diagnostics_redact_raw_and_resolved_sdk(self):
        with tempfile.TemporaryDirectory() as tmp:
            sdk = Path(tmp) / "licensed.jar"
            sdk.write_bytes(b"fixture")
            link = Path(tmp) / "configured.jar"
            link.symlink_to(sdk)
            diagnostic = f"B1 BLOCKING: fixture\n{link}\n{sdk}\n{ROOT}\n3 WARNING 22 INFO"
            scrubbed = release.sanitize_diagnostic(diagnostic, {"VCFCF_SDK_JAR": str(link)})
            for raw in (str(link), str(sdk), str(ROOT)):
                self.assertNotIn(raw, scrubbed)
            self.assertIn("B1 BLOCKING: fixture", scrubbed)
            self.assertIn("3 WARNING 22 INFO", scrubbed)

    def test_kit_rejects_traversal_links_duplicates_and_wrong_root(self):
        for name, kind in [("../escape", "file"), ("sdk_buildkit/link", "link"), ("other/file", "file"), ("sdk_buildkit/same", "duplicate")]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as tmp:
                archive = Path(tmp) / "fixture.tar"
                with tarfile.open(archive, "w") as tf:
                    member = tarfile.TarInfo(name)
                    if kind == "link":
                        member.type = tarfile.SYMTYPE
                        member.linkname = "/etc/passwd"
                    tf.addfile(member)
                    if kind == "duplicate":
                        tf.addfile(member)
                with self.assertRaises(ValueError):
                    release.unpack_kit(archive, Path(tmp) / "out")

    def test_vendored_defect_gate_provenance(self):
        release.check_digest(ROOT / "ci/defect_gate.py", self.policy["defect_gate_sha256"])

    def test_registry_shape_rejects_empty_unrecognized_and_html(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "registry.md"
            for text in ("", "fixture registry", "<html><body>error</body></html>", "# Defect registry\nNo sections", "# Defect registry\n### DEF-001\n<html>error</html>"):
                with self.subTest(text=text):
                    path.write_text(text)
                    with self.assertRaises(ValueError):
                        release.check_registry_shape(path)
            path.write_text("# Defect registry\n\n### DEF-001\n")
            release.check_registry_shape(path)

    def pak_fixture(self, path, *, descriptor=None, extra=None, vendor_class=False):
        kind = release.KIND
        jar_bytes = io.BytesIO()
        with zipfile.ZipFile(jar_bytes, "w") as jar:
            jar.writestr("com/vendor/Restricted.class" if vendor_class else "com/vcfcf/Fixture.class", b"fixture")
        inner_bytes = io.BytesIO()
        data = {"manifest.txt": json.dumps({"version": "1.0.0.5"}), "eula.txt": "", "default.svg": "", "resources/resources.properties": "", f"{kind}.jar": jar_bytes.getvalue(), f"{kind}/conf/describe.xml": descriptor or f'<AdapterKind xmlns="http://schemas.vmware.com/vcops/schema"><ResourceKinds><ResourceKind key="{kind}" isCredentialRequired="false"/></ResourceKinds></AdapterKind>', f"{kind}/conf/version.txt": "1.0.0.5", f"{kind}/conf/resources/resources.properties": "", f"{kind}/lib/vcfcf-adapter-base.jar": jar_bytes.getvalue(), f"{kind}/conf/images/AdapterKind/{kind}.svg": "", f"{kind}/conf/images/ResourceKind/{kind}.svg": "", f"{kind}/conf/images/ResourceKind/RdmMapping.svg": "", f"{kind}/conf/images/TraversalSpec/default.svg": ""}
        if extra:
            data.update(extra)
        with zipfile.ZipFile(inner_bytes, "w") as inner:
            for name, payload in data.items():
                inner.writestr(name, payload)
        with zipfile.ZipFile(path, "w") as outer:
            for name in ("eula.txt", "default.svg", "resources/", "resources/resources.properties", "overview.packed"):
                outer.writestr(name, "")
            outer.writestr("manifest.txt", json.dumps({"version": "1.0.0.5"}))
            outer.writestr("adapters.zip", inner_bytes.getvalue())

    def test_namespaced_descriptor_and_package_integrity(self):
        with tempfile.TemporaryDirectory() as tmp:
            pak = Path(tmp) / "fixture.pak"
            self.pak_fixture(pak)
            release.inspect_pak(pak, "1.0.0.5", "0" * 64)
            with self.assertRaisesRegex(ValueError, "version"):
                release.inspect_pak(pak, "1.0.0.6", "0" * 64)

    def test_unexpected_inner_payload_or_vendor_classes_rejected(self):
        for kw in ({"extra": {"vcfcf_rdm_topology_poc/work/raw-response.json": "{}"}}, {"extra": {"renamed-sdk.bin": "fixture"}}, {"vendor_class": True}):
            with self.subTest(kw=kw), tempfile.TemporaryDirectory() as tmp:
                pak = Path(tmp) / "fixture.pak"
                self.pak_fixture(pak, **kw)
                with self.assertRaises(ValueError):
                    release.inspect_pak(pak, "1.0.0.5", "0" * 64)

    def test_namespaced_credential_or_missing_noauth_contract_rejected(self):
        for content in ('<CredentialKinds/>', f'<ResourceKind key="{release.KIND}"/>', f'<ResourceKind key="{release.KIND}" isCredentialRequired="false" credentialKind="dummy"/>'):
            with self.subTest(content=content), tempfile.TemporaryDirectory() as tmp:
                pak = Path(tmp) / "fixture.pak"
                self.pak_fixture(pak, descriptor=f'<AdapterKind xmlns="http://schemas.vmware.com/vcops/schema">{content}</AdapterKind>')
                with self.assertRaises(ValueError):
                    release.inspect_pak(pak, "1.0.0.5", "0" * 64)

    def test_enabled_policy_requires_complete_approval_pins(self):
        for key in ("sdk_sha256", "buildkit_tag", "buildkit_version", "buildkit_sha256", "python_version", "python_sha256", "java_version", "java_sha256", "javac_version", "javac_sha256", "pyyaml_version", "pyyaml_tree_sha256"):
            if self.policy["release_enabled"]:
                self.assertTrue(self.policy[key], key)
        self.assertEqual(self.policy["reference_sha256"], "beaa8555ab3d8648fcb7dda6c26e2e4a295f0f2f854db467468060dd048480a1")

    def test_pr_workflow_is_hosted_without_release_secrets(self):
        workflow = (ROOT / ".github/workflows/check-source.yml").read_text()
        self.assertIn("runs-on: ubuntu-latest", workflow)
        for required in ("pull_request:", "branches: [main]", "contents: read", "persist-credentials: false", "python3 -m unittest discover -s ci/tests -v", "bash -n scripts/run-tests.sh"):
            self.assertIn(required, workflow)
        for forbidden in ("self-hosted", "secrets.", "pull_request_target", "workflow_run", "SDK_JAR", "contents: write", "ci/release.py", "upload-artifact", "actions/cache", "gh release", "vars."):
            self.assertNotIn(forbidden, workflow)

    def test_release_workflow_guards_and_upload_whitelist(self):
        workflow = (ROOT / ".github/workflows/build-pak-on-tag.yml").read_text()
        for required in ("github.ref_protected == true", "vars.RDM_RELEASE_ENABLED == 'true'", "github.event_name == 'push'", "github.ref_type == 'tag'", "environment: rdm-release", "group: rdm-protected-release", "rdm-release-ephemeral", "RDM_APPROVED_SOURCE_SHA", "merge-base --is-ancestor", "persist-credentials: false", "contents: read"):
            self.assertIn(required, workflow)
        for forbidden in ("pull_request:", "pull_request_target", "workflow_dispatch", "contents: write", "continue-on-error", "--clobber", "SDK_RUNTIME_SSH_KEY", "upload-pages", "gh release create"):
            self.assertNotIn(forbidden, workflow)
        upload = workflow.split("          path: |\n")[1]
        self.assertEqual([x.strip() for x in upload.splitlines()], ["release-output/vcfcf_sdk_rdm_topology_poc.*.pak", "release-output/vcfcf_sdk_rdm_topology_poc.*.pak.sha256", "release-output/provenance.json", "release-output/comparison.txt"])

    def test_all_actions_use_locally_sourced_full_commit_pins(self):
        for file in (ROOT / ".github/workflows").glob("*.yml"):
            for line in file.read_text().splitlines():
                if "uses:" in line:
                    self.assertRegex(line, r"uses: actions/[a-z-]+@[0-9a-f]{40}$")

    def test_both_comparison_entry_points_are_structural_and_exact(self):
        source = (ROOT / "ci/release.py").read_text()
        for required in ('"--pak-compare-policy", "structural"', '"--pak-compare-reference", str(reference)', '"--pak-compare-reference-sha256", policy["reference_sha256"]', '"--policy", "structural", "--reference-sha256", policy["reference_sha256"]'):
            self.assertIn(required, source)
        self.assertNotIn("--pak-compare-warn-only", source)

    def test_tracked_files_exclude_sdk_generated_and_local_inputs(self):
        files = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
        for name in files:
            self.assertFalse(name.endswith((".jar", ".pak", ".pyc", ".log")), name)
            self.assertFalse(any(part in ("tmp", "scratch", "reports", "dist", "build", ".test-build", "release-output", ".env") for part in Path(name).parts), name)

    def test_main_gate_order_failure_and_success_outputs(self):
        """Execute real orchestration with deterministic local fake dependencies."""
        # main imports PyYAML; fake only its parser, retaining the real pipeline.
        import types
        metadata = {"version": "1.0.0", "build_number": 5, "adapter_kind": release.KIND}
        yaml_stub = types.SimpleNamespace(safe_load=lambda text: metadata)
        for failure in ("sdk", "reference", "registry", "defect", "validate", "tests", "automatic", "direct", None):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp) / "source"
                root.mkdir()
                (root / "adapter.yaml").write_text("fixture metadata")
                runner_tmp = Path(tmp) / "temp"
                runner_tmp.mkdir()
                sdk = Path(tmp) / "external-sdk.jar"
                sdk.write_bytes(b"synthetic fixture")
                policy = {**self.approved, "buildkit_tag": "sdk-buildkit-v1.2.3", "buildkit_version": "1.2.3", "buildkit_sha256": "1" * 64, "sdk_sha256": "2" * 64}
                policy_path = Path(tmp) / "policy.json"
                policy_path.write_text(json.dumps(policy))
                events = []

                def fake_check_sdk(*args):
                    events.append("sdk")
                    if failure == "sdk":
                        raise ValueError("SDK digest mismatch")
                    return sdk

                def fake_digest(path, expected):
                    if Path(path).name == policy["reference_filename"]:
                        events.append("reference")
                        if failure == "reference":
                            raise ValueError("reference digest mismatch")

                def fake_run(argv, **kwargs):
                    if argv[:3] == ["git", "rev-parse", "HEAD"]:
                        return self.sha
                    if argv[0] == "git":
                        return ""
                    if argv[0] == "gh":
                        archive = Path(argv[argv.index("--output") + 1])
                        with tarfile.open(archive, "w") as tf:
                            for name, payload in {"sdk_buildkit/VERSION": b"1.2.3", f"sdk_buildkit/reference_paks/{policy['reference_filename']}": b"fake reference"}.items():
                                info = tarfile.TarInfo(name)
                                info.size = len(payload)
                                tf.addfile(info, io.BytesIO(payload))
                        return ""
                    if argv[0] == "curl":
                        Path(argv[-1]).write_text("<html>error</html>" if failure == "registry" else "# Defect registry\n\n### DEF-999\n")
                        return ""
                    if "validate-sdk" in argv:
                        events.append("validate")
                        if failure == "validate":
                            raise subprocess.CalledProcessError(1, argv, output="INVALID synthetic")
                        return "validated"
                    if argv[0] == "bash":
                        events.append("tests")
                        if failure == "tests":
                            raise subprocess.CalledProcessError(1, argv, output="synthetic assertion failure")
                        return "7 tests passed"
                    if "build-sdk" in argv:
                        events.append("automatic")
                        self.assertEqual(argv[argv.index("--pak-compare-policy") + 1], "structural")
                        self.assertEqual(argv[argv.index("--pak-compare-reference-sha256") + 1], policy["reference_sha256"])
                        self.assertTrue(argv[argv.index("--pak-compare-reference") + 1].endswith(policy["reference_filename"]))
                        if failure == "automatic":
                            raise subprocess.CalledProcessError(1, argv, output="BLOCKING synthetic")
                        dist = Path(argv[argv.index("--output") + 1])
                        dist.mkdir()
                        self.pak_fixture(dist / "vcfcf_sdk_rdm_topology_poc.1.0.0.5.pak")
                        return "built with automatic structural comparison"
                    if "pak-compare" in argv:
                        events.append("direct")
                        self.assertEqual(argv[argv.index("--policy") + 1], "structural")
                        self.assertEqual(argv[argv.index("--reference-sha256") + 1], policy["reference_sha256"])
                        if failure == "direct":
                            raise subprocess.CalledProcessError(1, argv, output="BLOCKING synthetic")
                        return f"0 BLOCKING 3 WARNING 22 INFO {sdk} {root} {runner_tmp}"
                    events.append("defect")
                    if failure == "defect":
                        raise subprocess.CalledProcessError(2, argv, output="DEF-999 blocking synthetic")
                    return "defect gate passed"

                env = {**self.env, "GITHUB_WORKSPACE": str(root), "RUNNER_TEMP": str(runner_tmp), "GITHUB_REF_NAME": "v1.0.0.5", "VCFCF_SDK_JAR": str(sdk)}
                with mock.patch.object(release, "ROOT", root), mock.patch.object(release, "POLICY", policy_path), mock.patch.dict(os.environ, env, clear=True), mock.patch.dict(sys.modules, {"yaml": yaml_stub}), mock.patch.object(release, "check_toolchain", return_value={"python": "fixture"}), mock.patch.object(release, "check_sdk", side_effect=fake_check_sdk), mock.patch.object(release, "check_digest", side_effect=fake_digest), mock.patch.object(release, "run", side_effect=fake_run):
                    if failure:
                        with self.assertRaises((ValueError, subprocess.CalledProcessError)):
                            release.main()
                        self.assertFalse((root / "release-output").exists())
                    else:
                        release.main()
                        out = root / "release-output"
                        filename = "vcfcf_sdk_rdm_topology_poc.1.0.0.5.pak"
                        self.assertEqual({p.name for p in out.iterdir()}, {filename, filename + ".sha256", "provenance.json", "comparison.txt"})
                        self.assertEqual((out / (filename + ".sha256")).read_text(), f"{release.digest(out / filename)}  {filename}\n")
                        evidence = json.loads((out / "provenance.json").read_text())
                        self.assertEqual(evidence["source_commit"], self.sha)
                        self.assertEqual(evidence["policy"], "structural")
                        self.assertNotIn(str(sdk), (out / "comparison.txt").read_text())
                        self.assertNotIn(str(root), (out / "provenance.json").read_text())
                if "validate" in events:
                    self.assertLess(events.index("sdk"), events.index("validate"))
                    self.assertLess(events.index("reference"), events.index("validate"))
                if failure in ("sdk", "reference", "registry", "defect"):
                    self.assertNotIn("validate", events)
                if failure == "registry":
                    self.assertNotIn("defect", events)
                if failure in ("validate", "tests"):
                    self.assertNotIn("automatic", events)
                if failure == "automatic":
                    self.assertNotIn("direct", events)


if __name__ == "__main__":
    unittest.main()
