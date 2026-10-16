#!/usr/bin/env bash
# Offline only: supplied legitimate SDK/platform classpath; no downloads or shims.
set -euo pipefail
if [[ $# -ne 2 || ! -f "$1" || ! "$2" =~ ^[A-Za-z][A-Za-z0-9_]{0,127}$ ]]; then
  printf '%s\n' 'DESCRIPTOR_LOAD code=INVALID_ARGUMENTS'
  exit 1
fi
if [[ -z "${VCFCF_SDK_JAR:-}" || ! -f "${VCFCF_SDK_JAR}" ]]; then
  printf '%s\n' 'DESCRIPTOR_LOAD code=BLOCKED_SDK_UNAVAILABLE'
  exit 2
fi
if [[ -n "${JAVA_TOOL_OPTIONS:-}${JDK_JAVA_OPTIONS:-}${_JAVA_OPTIONS:-}${CLASSPATH:-}" ]]; then
  printf '%s\n' 'DESCRIPTOR_LOAD code=BLOCKED_INHERITED_JAVA_CONFIGURATION'
  exit 2
fi
adapter_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Reject implicit classpaths before Java may follow manifest Class-Path entries.
# Presence/metadata inventory is not a loader pass or proof of platform provenance.
if ! python3 "${adapter_dir}/scripts/check-descriptor-dependencies.py"; then
  printf '%s\n' 'DESCRIPTOR_LOAD code=BLOCKED_DEPENDENCY_CONFIGURATION'
  exit 2
fi
test_build="$(mktemp -d /tmp/rdm-descriptor-load.XXXXXX)"
trap 'rm -rf "${test_build}"' EXIT
chmod 700 "${test_build}"
source_file="${adapter_dir}/tests/com/vcfcf/adapters/rdmtopologypoc/diagnostic/DescriptorLoadingProbe.java"
if ! javac --release 11 -proc:none -cp "${VCFCF_SDK_JAR}" -d "${test_build}" "${source_file}" >"${test_build}/compile.log" 2>&1; then
  printf '%s\n' 'DESCRIPTOR_LOAD code=BLOCKED_COMPILER_OR_SDK'
  exit 2
fi
# Additional platform dependencies must already be legitimately available locally.
# Deliberately excludes tests/org logging shims and all automatic dependency discovery.
test_classpath="${test_build}:${VCFCF_SDK_JAR}"
if [[ -n "${VCFCF_DESCRIPTOR_CLASSPATH:-}" ]]; then
  test_classpath="${test_classpath}:${VCFCF_DESCRIPTOR_CLASSPATH}"
fi
java -cp "${test_classpath}" com.vcfcf.adapters.rdmtopologypoc.diagnostic.DescriptorLoadingProbe "$1" "$2"
