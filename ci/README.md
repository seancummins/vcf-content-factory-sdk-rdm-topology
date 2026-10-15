# Protected first-release candidate preparation

F1 prepares source only. Release creation and deployment are disabled. Current
`adapter.yaml` stays at prototype `0.1.0`, build 4. Proposed first CI candidate
metadata is `version: "1.0.0"`, `build_number: 5`; the corresponding tag and
artifact would be `v1.0.0.5` and
`vcfcf_sdk_rdm_topology_poc.1.0.0.5.pak`. A release owner must approve and update
metadata and CHANGELOG in a reviewed change before a tag can pass. Never tag or
publish a `0.x` artifact. Fixture collection and additive relationship semantics
are unchanged. Compilation and CI acceptance cannot prove persisted topology.

## Source and dependency provenance

Approved F2 destination: `seancummins/vcf-content-factory-sdk-rdm-topology`,
initially **private**, default branch `main`. Publication requires separate
explicit approval. This supersedes the F1 proposed adapter destination; the
Sentania factory/buildkit and Synology reference URLs below remain upstream
dependency provenance, not this adapter's ownership or remote. No restricted
binaries or customer inventory belong in this repository, even while private.
Keep its independent Git metadata and history. The factory registry entry is
added only after the remote is separately approved and created. F2 prepares
these local changes only: the current F1/F2 worktree must be reviewed and
explicitly committed by the operator before bootstrap; do not silently commit,
push, or create a remote. Confirm actual checkpoints rather than assuming F1
files are already in history.

`ci/defect_gate.py` is a byte-for-byte vendored copy from the factory-managed
UniFi adapter CI convention. SHA-256:
`b58e6cce671669c1de366638ae337abe3ee92fdea68fd8c481403511f9619501`.
It runs reviewed local code against inert registry DATA fetched from the
factory's `main` at build time. The registry digest is recorded; a later blocking
defect must prevent a future release. Parser updates require review and a new pin.
No remotely fetched code executes before its approved archive SHA-256 verifies.

The release wrapper additionally requires the fetched registry's exact
`# Defect registry` header and at least one `DEF-NNN` section, and rejects HTML
response bodies before invoking the unchanged vendored parser. This deliberately
fails closed for empty/error downloads; the current registry preserves closed
defect history. A future legitimately empty registry or changed header/format
requires a reviewed wrapper-policy update rather than silently passing release.

The checkout and Python setup action SHAs come from existing factory-managed
UniFi workflows. The artifact upload SHA comes from the managed cluster-perf-risk
workflow. All are full commit pins; their upstream provenance must also be
approved in repository security setup. No floating action refs are accepted.

`release-policy.json` deliberately has release disabled and null approval pins.
A published full-version sdk-buildkit containing the F0.5 comparator and exact
approved Synology PAK is required. Its archive version/tag/SHA-256 must be reviewed
before configuration. Never create a replacement baseline from this candidate.
Approved Synology `v1.0.0.30` reference checksum:
`beaa8555ab3d8648fcb7dda6c26e2e4a295f0f2f854db467468060dd048480a1`.
The helper verifies the reference inside `sdk_buildkit/reference_paks/` and uses
that exact file/digest in both the automatic build gate and direct comparison.

The administrator supplies a licensed SDK through `RDM_SDK_JAR_PATH`, outside
all runner checkout, temporary, kit and upload roots (for example an approved
read-only administrator-managed software location). Symlinks are resolved before
scope checks. The SDK digest must be independently approved in the policy; a
locally computed digest alone is not approval. The helper never fetches, copies,
caches, uploads or publishes the SDK. Its sanitized provenance records digest
only. Strict PAK member/JAR class allowlists reject extra dependencies, vendor
classes and unexpected inventory/config payloads. These packaging checks cannot
substitute for reviewing source for embedded sensitive strings.

The isolated runner image must already contain Python (3.12+), PyYAML, JDK,
Git, curl and GitHub CLI. No pip/apt install occurs in the workflow. Approve the
exact Python version/executable SHA, complete `java -version` and
`javac -version` output/executable SHAs, and PyYAML version/package tree SHA in
the policy. `tree_digest()` defines the deterministic installed PyYAML fingerprint
excluding bytecode. Record image identity and package acquisition provenance in
administrator approval. The buildkit archive SHA locks bundled framework source
and dependencies. Dependency absence or mismatch fails closed. Byte-for-byte
identical artifacts across different images/timestamps are not asserted; the
official candidate must be traceable to source, pinned kit, SDK and toolchain.

## External controls: REQUIRED, UNVERIFIED

These controls require separate external approval and cannot be established by
workflow YAML alone:

1. Create the approved repository; import reviewed independent history without
   artifacts/secrets. Protect `main`, require pull requests and independent
   reviews, CODEOWNERS approval for `ci/`, `.github/`, metadata and Java changes,
   and required `check-source / static`; prevent force push/deletion and restrict
   administrator bypass. Confirm actual required-check name from GitHub.
2. Protect `v*` tag creation/update/deletion with a ruleset restricted to release
   owners. Release tags must point to a reviewed commit on protected `main`.
   Configure the `rdm-release` environment for protected tags only, named required
   reviewers, prevention of self-review, and no administrator bypass where
   supported by the chosen GitHub plan. Verify private/public plan capabilities.
3. Provision a dedicated ephemeral self-hosted runner group
   `rdm-protected-release` and labels `linux`, `x64`, `rdm-release-ephemeral`.
   Scope it to the exact repository and release workflow where supported. It
   must never run untrusted pull-request code, even if a PR edits a workflow to
   request its labels. Enforce this outside repository YAML, with no other
   workflows/repositories admitted. If the GitHub plan cannot enforce workflow
   restrictions, do not enable this runner until an equivalent external admission
   control is independently approved. Use one-job disposable runner/image,
   read-only SDK mount, no VMware network routes, no cloud/lab credentials, and
   egress limited to approved GitHub artifact/data downloads. Destroy its workspace
   after the job; the SDK mount stays outside all cleanup paths.
   This is a deferred protected-CI design, **not an F2 setup instruction**.
   The destination is now a personal-account private repository. Before F3,
   independently verify the account/plan supports the required runner-group,
   workflow-admission, ruleset, and environment-review controls; an
   organization-scoped runner group cannot be assumed available. If equivalent
   isolation cannot be enforced, leave the protected workflow disabled and the
   runner unconfigured rather than weakening its constraints.
4. Approve SDK licensing/provisioning, runner image/toolchain and F0.5-capable
   published kit pins. Fill every null policy value through review, then enable
   policy. No new SDK runtime Git repository or upload is authorized.
5. Set protected-environment `RDM_SDK_JAR_PATH`, `RDM_RUNNER_APPROVED=true`, and
   `RDM_APPROVED_SOURCE_SHA` equal to the exact approved 40-character tag target.
   Environment reviewers must verify this target, main ancestry, workflow/policy
   diff and image identity. Enable repository variable `RDM_RELEASE_ENABLED=true`
   only after all controls pass. The helper additionally checks protected-ref
   context, repository allowlist, approval SHA, clean checkout, exact 1.x+ tag
   and metadata before dependency download or compilation.

## Execution and operator verification

No-secret static checks run on GitHub-hosted PR/main runners only:

```bash
python3 -m unittest discover -s ci/tests -v
bash -n scripts/run-tests.sh
```

These checks use Python's standard library and deterministic synthetic fixtures;
they neither compile Java nor invoke the release entry point. Do not provide an
SDK, repository/environment secrets, caches, artifacts, or release credentials.
The hosted workflow has read-only contents permission and does not persist its
checkout token. Verify its actual GitHub check name and successful `main`/PR
runs after separately authorized bootstrap; local success is not hosted-CI
evidence. Keep `release_enabled` false, `RDM_RELEASE_ENABLED` unset/false,
approval pins unset, and all protected runners and environments unconfigured
throughout F2. `.github/CODEOWNERS` names the confirmed repository owner; that
alone does not establish an independent review or an enforced GitHub rule.

After separate authorization, review/update version/build/changelog and approve
the source commit, all pins and protections. A release owner creates the matching
protected tag. The gated candidate job runs the defect gate, validate-sdk,
focused Java tests, release build with structural automatic gate, direct
structural comparison and package inspection. Any failure prevents the upload
step. The helper removes its temporary kit/build directory on exit and refuses
existing output. Ephemeral runner teardown is also required after cancellation
or host failure; it must never remove the external SDK.

Successful CI uploads only PAK, `<pak>.sha256`, `comparison.txt` and
`provenance.json` as a seven-day Actions candidate artifact. The job has
`contents: read`; it has no publication step and creates no GitHub Release.
Reviewers must inspect every WARNING/INFO in the report, source/tag/build/kit/
reference/SDK/toolchain provenance and the expected artifact count. A zero-
BLOCKING CI comparison satisfies only that comparison sub-check, not installation
or relationship-persistence approval.

After separately authorized download, verify in a clean directory:

```bash
sha256sum -c vcfcf_sdk_rdm_topology_poc.1.0.0.5.pak.sha256
```

Compare artifact SHA with `provenance.json`, CI source commit and matching tag.
Publication is a further explicitly approved operator action using exact PAK,
sidecar and sanitized evidence filenames. Do not use glob uploads, overwrite an
existing release, promote local preview PAKs, or publish the SDK/build workspace.
Deployment remains a separately authorized experiment after release review.
