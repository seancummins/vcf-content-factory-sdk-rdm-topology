#!/usr/bin/env bash
set -euo pipefail
adapter_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_build="$(mktemp -d "${TMPDIR:-/tmp}/rdm-discovery-test.XXXXXX")"
trap 'rm -rf "${test_build}"' EXIT
mapfile -t source_files < <(find "${adapter_dir}/src/com/vcfcf/adapters/rdmtopologypoc/discovery" "${adapter_dir}/tests/com/vcfcf/adapters/rdmtopologypoc/discovery" -name '*.java' -type f | sort)
javac --release 11 -proc:none -d "${test_build}" "${source_files[@]}"
if [[ "${1:-}" == "--no-network-tests" ]]; then
  java -cp "${test_build}" com.vcfcf.adapters.rdmtopologypoc.discovery.DiscoveryFoundationTest
  exit 0
fi
# Test-only ephemeral enterprise CA and CA-signed localhost certificate. Never runtime dependencies.
keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -dname CN=Synthetic-Test-CA -ext bc:c -validity 2 -storetype PKCS12 -keystore "${test_build}/ca.p12" -storepass synthetic-password -keypass synthetic-password >/dev/null 2>&1
keytool -exportcert -alias ca -keystore "${test_build}/ca.p12" -storepass synthetic-password -file "${test_build}/ca.der" >/dev/null 2>&1
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -dname CN=localhost -storetype PKCS12 -keystore "${test_build}/server.p12" -storepass synthetic-password -keypass synthetic-password >/dev/null 2>&1
keytool -certreq -alias server -keystore "${test_build}/server.p12" -storepass synthetic-password -file "${test_build}/server.csr" >/dev/null 2>&1
keytool -gencert -alias ca -keystore "${test_build}/ca.p12" -storepass synthetic-password -infile "${test_build}/server.csr" -outfile "${test_build}/server.der" -validity 2 -ext SAN=dns:localhost -ext KU=digitalSignature,keyEncipherment -ext EKU=serverAuth >/dev/null 2>&1
keytool -importcert -noprompt -alias ca -keystore "${test_build}/server.p12" -storepass synthetic-password -file "${test_build}/ca.der" >/dev/null 2>&1
keytool -importcert -noprompt -alias server -keystore "${test_build}/server.p12" -storepass synthetic-password -file "${test_build}/server.der" >/dev/null 2>&1
java -cp "${test_build}" com.vcfcf.adapters.rdmtopologypoc.discovery.DiscoveryFoundationTest "${test_build}/server.p12" "${test_build}/ca.der"
jdeps --multi-release 11 --recursive -s "${test_build}/com/vcfcf/adapters/rdmtopologypoc/discovery/"*.class
