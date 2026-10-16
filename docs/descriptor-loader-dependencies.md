# Offline SDK descriptor-loader dependencies

This utility is local validation only. It neither packages platform JARs nor
changes the collector runtime. The full loader never includes `tests/org` logging
shims. Existing partial unit tests retain their explicitly limited logging
linkage; they are not substituted for actual descriptor acceptance.

## Supply legitimate external libraries

An administrator must supply authorized, matching development-appliance
libraries outside Git. Record appliance version/build, original library names,
manifest/Maven versions and SHA-256 before use; class presence and a version
string do not establish legitimate provenance or compatibility. No automatic
download, arbitrary Maven replacement, appliance connection or JAR extraction
is performed by these scripts. The supplied SDK's filename suffix `2.2` is not
its embedded Maven artifact version (which is `1.0`). Its `AdapterDescribe`
class has class-file major 61, requiring Java 17+ to execute this particular
SDK, despite the probe's `--release 11` source compilation target.

Configure only explicit local JAR paths, in deliberate order:

```bash
export VCFCF_SDK_JAR=/absolute/authorized/sdk.jar
export VCFCF_DESCRIPTOR_CLASSPATH=/absolute/authorized/log4j-api.jar:/absolute/authorized/commons-configuration.jar
python3 scripts/check-descriptor-dependencies.py
bash scripts/check-sdk-descriptor-load.sh describe.xml vcfcf_rdm_topology_poc
bash scripts/check-sdk-descriptor-load.sh docs/diagnostic/diagnostic-describe.xml vcfcf_rdm_discovery_diagnostic
```

The two example dependency paths illustrate the syntax, **not a complete or
approved dependency set**. Add other matching libraries only when supported by
appliance metadata/dependency evidence. Keep files immutable during a run;
hashes identify inspected bytes, not an atomic snapshot of concurrently modified
external files. Never copy these dependencies into the adapter's `lib/`, tests,
repository, PAK or publication outputs.

The checker validates ZIP integrity and emits indexed SHA-256/version metadata
and class-presence evidence. It rejects directories (including logging-shim
directories), wildcard/empty entries, duplicate paths/classes, oversized JARs,
duplicate ZIP entries and implicit manifest `Class-Path`. Class presence is
**INVENTORY_ONLY**, never actual-loader success, platform approval or proof of
complete dependency closure. Versions absent from a JAR manifest are reported
as `unrecorded`, not guessed from filenames. Authenticate provenance separately.

The launcher rejects inherited `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`,
`_JAVA_OPTIONS` and `CLASSPATH` rather than silently admitting agents or hidden
shim paths. Run in a clean, administrator-controlled local Java environment;
the utility is not a sandbox for malicious JARs or security providers.

## Dependency evidence levels

| Scope | Evidence / limitation |
|---|---|
| `observed_loader` | Clean actual loader first fails on Log4j `LogManager`. |
| `descriptor_bytecode` | Descriptor/localization classes reference Log4j `Logger` and Commons Configuration 1.x `PropertiesConfiguration`. |
| `observed_transitive_loader` | With the six operator-supplied external libraries, both actual descriptor attempts reach missing Commons Lang 2 `NestableException`. Commons Configuration's `ConfigurationException` extends that class. |
| `prior_partial_loader` | Earlier partial logging-linked tests reached missing Commons Configuration `ConfigurationException`; not clean full-loader acceptance. |
| `sdk_wide_candidate` | Other SDK classes reference Commons Lang 2, Commons Lang 3 and Log4j core. Not proof that loading either descriptor requires all of these. |
| `transitive_candidate` | Commons Logging/Collections may be dependencies of externally supplied libraries. Exact versions/necessity require their own legitimate metadata or actual loader evidence. |

The SDK embedded POM directly declares Commons Configuration **1.10** and Commons
Lang 3 **3.14.0**. Its inherited properties mention Log4j **2.17.2**, Commons
Collections **3.2.2**, and Commons Logging **1.1**; these are build metadata, not
proof of the appliance's installed versions. Commons Lang 2 is a distinct package
from Lang 3. The operator-supplied Commons Configuration 1.10 JAR's embedded POM
directly declares `commons-lang:commons-lang:2.6`; its `ConfigurationException`
bytecode extends `org.apache.commons.lang.exception.NestableException`. This
establishes an additional genuine loader dependency and declared version, not
proof that a matching Lang 2 JAR has been supplied or approved. Do not replace it
with Lang 3 or fabricate that class. Likewise the SDK POM's actual
Commons Codec dependency is **1.15**, despite a separate inherited **1.4**
property. Do not use stale properties or an SDK-wide `jdeps` report as a complete
minimum descriptor classpath. Other SDK security/trust/JAXB dependencies may be
unrelated to this loader path.

Only `DESCRIPTOR_LOAD code=PASS` / exit 0 means actual SDK model loading and
expected adapter-key/count inspection succeeded. Exit 2 is blocked dependency,
security or environment setup, never a descriptor pass; exit 1 is invalid input
or descriptor. Both descriptors must be run separately. No shim-free pass can be
claimed until the genuine dependency closure is supplied and both runs succeed.

Run the metadata/guard regression tests with:

```bash
python3 tests/test_descriptor_dependencies.py
```

Synthetic ZIP entries in those tests are metadata fixtures only; no JVM ever
loads them. They cannot qualify the SDK loader or replace missing real libraries.
