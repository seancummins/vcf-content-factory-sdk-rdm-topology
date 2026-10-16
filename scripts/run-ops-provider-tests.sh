#!/usr/bin/env bash
set -euo pipefail
adapter_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${VCFCF_SDK_JAR:?Supply the authorized local Operations SDK}"
test_build="$(mktemp -d "${TMPDIR:-/tmp}/rdm-ops-test.XXXXXX")"
trap 'rm -rf "${test_build}"' EXIT
mapfile -t source_files < <(find "${adapter_dir}/src/com/vcfcf/adapters/rdmtopologypoc/discovery" "${adapter_dir}/src/com/vcfcf/adapters/rdmtopologypoc/ops" "${adapter_dir}/tests/com/vcfcf/adapters/rdmtopologypoc/ops" -name '*.java' -type f | sort)
javac --release 11 -proc:none -cp "${VCFCF_SDK_JAR}" -d "${test_build}" "${source_files[@]}"
if [[ "${1:-}" == "--no-network-tests" ]]; then
  java -cp "${test_build}:${VCFCF_SDK_JAR}" com.vcfcf.adapters.rdmtopologypoc.ops.OperationsProviderTest
  exit 0
fi
# Only ephemeral synthetic CA/server keys; no production SDK binaries or credentials copied.
keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -dname CN=Synthetic-Ops-Test-CA -ext bc:c -validity 2 -storetype PKCS12 -keystore "${test_build}/ca.p12" -storepass synthetic-password -keypass synthetic-password >/dev/null 2>&1
keytool -exportcert -rfc -alias ca -keystore "${test_build}/ca.p12" -storepass synthetic-password -file "${test_build}/ca.pem" >/dev/null 2>&1
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -dname CN=localhost -storetype PKCS12 -keystore "${test_build}/server.p12" -storepass synthetic-password -keypass synthetic-password >/dev/null 2>&1
keytool -certreq -alias server -keystore "${test_build}/server.p12" -storepass synthetic-password -file "${test_build}/server.csr" >/dev/null 2>&1
keytool -gencert -alias ca -keystore "${test_build}/ca.p12" -storepass synthetic-password -infile "${test_build}/server.csr" -outfile "${test_build}/server.der" -validity 2 -ext SAN=dns:localhost -ext KU=digitalSignature,keyEncipherment -ext EKU=serverAuth >/dev/null 2>&1
keytool -importcert -noprompt -alias ca -keystore "${test_build}/server.p12" -storepass synthetic-password -file "${test_build}/ca.pem" >/dev/null 2>&1
keytool -importcert -noprompt -alias server -keystore "${test_build}/server.p12" -storepass synthetic-password -file "${test_build}/server.der" >/dev/null 2>&1
java -cp "${test_build}:${VCFCF_SDK_JAR}" com.vcfcf.adapters.rdmtopologypoc.ops.OperationsProviderTest "${test_build}/server.p12" "${test_build}/ca.pem"
mapfile -t production_classes < <(find "${test_build}/com/vcfcf/adapters/rdmtopologypoc/ops" -name '*.class' ! -name 'OperationsProviderTest*' -type f | sort)
jdeps --multi-release 11 -cp "${test_build}:${VCFCF_SDK_JAR}" -s "${production_classes[@]}"
