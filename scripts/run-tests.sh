#!/usr/bin/env bash
set -euo pipefail

adapter_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo_root="$(cd "${adapter_dir}/../../.." && pwd)"
sdk_jar="${VCFCF_SDK_JAR:?VCFCF_SDK_JAR must point to vrops-adapters-sdk-2.2.jar}"
framework_jar="${VCFCF_FRAMEWORK_JAR:-${repo_root}/src/vcfcf_managementpacks/adapter_runtime/vcfcf-adapter-base.jar}"
test_build="$(mktemp -d "${TMPDIR:-/tmp}/rdm-topology-poc-test.XXXXXX")"

if [[ ! -f "${sdk_jar}" || ! -f "${framework_jar}" ]]; then
  echo "SDK or framework JAR is unavailable; run validate-sdk first." >&2
  exit 1
fi

trap 'rm -rf "${test_build}"' EXIT

mapfile -t source_files < <(find "${adapter_dir}/src" "${adapter_dir}/tests" -name '*.java' -type f | sort)
javac -proc:none -cp "${sdk_jar}:${framework_jar}" -d "${test_build}" "${source_files[@]}"
java -cp "${sdk_jar}:${framework_jar}:${test_build}" com.vcfcf.adapters.rdmtopologypoc.RdmTopologyPocTest
