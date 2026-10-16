# VCF Content Factory RDM Topology POC — Reference

Generated from `describe.xml` and `resources.properties` for build 0.0.0.13.

## Adapter

| Field | Value |
|---|---|
| Adapter Kind | `vcfcf_rdm_topology_poc` |
| Tier | 2 (Java SDK) |
| Monitoring Interval | 5 minutes |
| License Required | No |

### Credentials

| Field | Key | Type |
|---|---|---|
| Username | `username` | string |
| Password | `password` | string (masked) |

### Connection Settings

| Field | Key | Default | Required |
|---|---|---|---|
| Runtime Fixture JSON (lab only) | `fixture_json` | — | No |
| Execution Mode (fixture or read_only_diagnostic; omit for existing fixture accounts) | `execution_mode` | — | No |
| vCenter HTTPS SDK Endpoint | `vcenter_endpoint` | — | No |
| vCenter Trust Mode (jvm or enterprise_ca) | `trust_mode` | — | No |
| Discovery Scope (single_vm_current_host) | `discovery_scope` | — | No |
| Operations HTTPS Suite API Endpoint | `ops_endpoint` | — | No |
| Operations Trust Mode (jvm or enterprise_ca) | `ops_trust_mode` | — | No |
| Target vCenter Instance UUID | `target_vcenter_uuid` | — | No |
| Target VM MOID | `target_vm_moid` | — | No |
| Target Virtual Disk Key | `target_disk_key` | — | No |
| Diagnostic Enabled (true or false) | `diagnostic_enabled` | — | No |
| Authorize One Attempt (true or false) | `authorize_once` | — | No |

---

## Object Types

### RDM Mapping

**Identifier**: `vcenter_instance_uuid` (vCenter Instance UUID)

---

### Canonical LUN

**Identifier**: `identity_token` (Qualified Logical Unit Identity Token)

#### Logical Unit Identity

| Key | Label | Type | Unit | Monitored |
|---|---|---|---|---|
| `format` | Identity Format | property | — | — |
| `canonical_name` | Canonical Device Name | property | — | — |
| `provenance` | Evidence Provenance | property | — | — |
| `assessment` | Identity Assessment | property | — | — |

#### Storage Description

| Key | Label | Type | Unit | Monitored |
|---|---|---|---|---|
| `vendor` | Storage Vendor | property | — | — |
| `model` | Storage Model | property | — | — |
| `revision` | Storage Revision | property | — | — |
| `serial_number` | Storage Serial Number | property | — | — |
| `lun_type` | SCSI LUN Type | property | — | — |
| `device_type` | Device Type | property | — | — |
| `capacity_bytes` | Capacity Bytes | property | — | — |

---
