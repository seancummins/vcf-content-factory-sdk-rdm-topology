# Fixture-only deployment status — VCF Content Factory RDM Topology POC

This POC is deliberately not an install procedure. It provides deterministic
fixture data to compile and inspect Java Integration SDK relationship mechanics.

## No endpoint, credentials, or network activity

- It has no vCenter, VCF Operations Suite API, or other endpoint setting.
- It requests no credentials and opens no network connection.
- It has no connection-validation operation; its Test Connection path performs
  no remote validation.
- It does not collect live inventory, create `CanonicalLun`, or emit metrics.

## Deployment prohibition

Do not upload, install, configure, or enable this POC on VCF Operations without
fresh explicit approval for a controlled deployment experiment. Compilation and
local PAK assembly do not demonstrate foreign relationship persistence or UI
presentation. That evidence requires a separately authorized test deployment.

## Local-only verification

Use the commands in the repository `README.md`. They require a locally supplied
Integration SDK JAR and do not contact VMware or VCF Operations systems.
