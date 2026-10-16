#!/usr/bin/env bash
set -euo pipefail
adapter_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${VCFCF_SDK_JAR:?Supply the authorized local Operations SDK}"
test_build="$(mktemp -d "${TMPDIR:-/tmp}/rdm-runtime-test.XXXXXX")"
trap 'rm -rf "${test_build}"' EXIT
mapfile -t source_files < <(find "${adapter_dir}/src/com/vcfcf/adapters/rdmtopologypoc/discovery" "${adapter_dir}/src/com/vcfcf/adapters/rdmtopologypoc/runtime" "${adapter_dir}/tests/com/vcfcf/adapters/rdmtopologypoc/runtime" -name '*.java' -type f | sort)
javac --release 11 -proc:none -cp "${VCFCF_SDK_JAR}" -d "${test_build}" "${source_files[@]}"
# Synthetic CA only: no VMware connection or production trust store changes.
keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -dname CN=Synthetic-Runtime-Test-CA -ext bc:c -validity 2 -storetype PKCS12 -keystore "${test_build}/ca.p12" -storepass synthetic-password -keypass synthetic-password >/dev/null 2>&1
keytool -exportcert -rfc -alias ca -keystore "${test_build}/ca.p12" -storepass synthetic-password -file "${test_build}/ca.pem" >/dev/null 2>&1
java -cp "${test_build}:${VCFCF_SDK_JAR}" com.vcfcf.adapters.rdmtopologypoc.runtime.RuntimeBindingTest "${test_build}/ca.pem"
mapfile -t production_classes < <(find "${test_build}/com/vcfcf/adapters/rdmtopologypoc/runtime" -name '*.class' ! -name 'RuntimeBindingTest*' -type f | sort)
jdeps --multi-release 11 -cp "${test_build}:${VCFCF_SDK_JAR}" -s "${production_classes[@]}"
