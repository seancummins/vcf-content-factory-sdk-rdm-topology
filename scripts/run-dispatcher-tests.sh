#!/usr/bin/env bash
set -euo pipefail
adapter_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo_root="$(cd "${adapter_dir}/../../.." && pwd)"
: "${VCFCF_SDK_JAR:?Supply the authorized local Operations SDK}"
: "${VCFCF_DESCRIPTOR_CLASSPATH:?Supply the verified platform JAR paths explicitly}"
test_build="$(mktemp -d "${TMPDIR:-/tmp}/rdm-dispatcher-test.XXXXXX")"
trap 'rm -rf "${test_build}"' EXIT
framework_jar="${VCFCF_FRAMEWORK_JAR:-${repo_root}/src/vcfcf_managementpacks/adapter_runtime/vcfcf-adapter-base.jar}"
classpath="${VCFCF_SDK_JAR}:${framework_jar}:${VCFCF_DESCRIPTOR_CLASSPATH}"
mapfile -t source_files < <(find "${adapter_dir}/src" "${adapter_dir}/tests/com" -name '*.java' ! -name CanonicalLunTest.java ! -name RdmTopologyPocTest.java -type f | sort)
javac --release 11 -proc:none -cp "${classpath}" -d "${test_build}" "${source_files[@]}"
java -DADAPTER_HOME="${test_build}" -DALIVE_BASE="${test_build}" -cp "${test_build}:${classpath}" com.vcfcf.adapters.rdmtopologypoc.DispatcherTest "${adapter_dir}/describe.xml"
if [[ "${1:-}" == "--fixture-lifecycle" ]]; then
  java -DADAPTER_HOME="${test_build}" -DALIVE_BASE="${test_build}" -cp "${test_build}:${classpath}" com.vcfcf.adapters.rdmtopologypoc.FixtureLifecycleProbe "${adapter_dir}/describe.xml"
fi
