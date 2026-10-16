#!/usr/bin/env python3
"""Inspect explicitly supplied local JARs only; not a platform compatibility verdict.

No extraction, downloads, class loading, or test logging shims. The actual SDK
loader remains authoritative for its dynamically reached dependency closure.
"""
import hashlib
import os
from pathlib import Path
import re
import sys
import zipfile


PROBES = (
    ("observed_loader", "org.apache.logging.log4j.LogManager"),
    ("descriptor_bytecode", "org.apache.logging.log4j.Logger"),
    ("prior_partial_loader", "org.apache.commons.configuration.ConfigurationException"),
    ("descriptor_bytecode", "org.apache.commons.configuration.PropertiesConfiguration"),
    ("observed_transitive_loader", "org.apache.commons.lang.exception.NestableException"),
    ("sdk_wide_candidate", "org.apache.commons.lang.NotImplementedException"),
    ("sdk_wide_candidate", "org.apache.commons.lang3.builder.EqualsBuilder"),
    ("transitive_candidate", "org.apache.commons.logging.LogFactory"),
    ("transitive_candidate", "org.apache.commons.collections.CollectionUtils"),
    ("sdk_wide_candidate", "org.apache.logging.log4j.core.LoggerContext"),
)


class InvalidClasspath(Exception):
    def __init__(self, code):
        self.code = code


def manifest_fields(raw):
    lines = raw.decode("utf-8", errors="strict").replace("\r\n", "\n").split("\n")
    unfolded = []
    for line in lines:
        if not line:
            break  # Only the main manifest section is relevant.
        if line.startswith(" "):
            if not unfolded:
                raise InvalidClasspath("INVALID_MANIFEST")
            unfolded[-1] += line[1:]
        else:
            unfolded.append(line)
    fields = {}
    for line in unfolded:
        key, sep, value = line.partition(": ")
        if not sep or key.lower() in fields:
            raise InvalidClasspath("INVALID_MANIFEST")
        fields[key.lower()] = value
    return fields


def safe_version(value):
    return value if value and re.fullmatch(r"[A-Za-z0-9_.+\-]{1,100}", value) else "unrecorded"


def inspect(sdk, extra):
    if not sdk:
        raise InvalidClasspath("SDK_UNAVAILABLE")
    paths = [sdk] + (extra.split(os.pathsep) if extra else [])
    if len(paths) > 64:
        raise InvalidClasspath("CLASSPATH_LIMIT")
    seen_paths, classes, reports = set(), {}, []
    for index, entry in enumerate(paths):
        if not entry or any(c in entry for c in "*?[]\r\n\0"):
            raise InvalidClasspath("EXPLICIT_JARS_REQUIRED")
        path = Path(entry)
        if not path.is_file() or path.suffix.lower() != ".jar":
            raise InvalidClasspath("EXPLICIT_JARS_REQUIRED")
        resolved = path.resolve()
        if resolved in seen_paths:
            raise InvalidClasspath("DUPLICATE_JAR")
        seen_paths.add(resolved)
        if path.stat().st_size > 268435456:
            raise InvalidClasspath("JAR_SIZE_LIMIT")
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(1048576), b""):
                digest.update(block)
        with zipfile.ZipFile(path) as jar:
            infos = jar.infolist()
            if len(infos) > 100000 or sum(i.file_size for i in infos) > 536870912:
                raise InvalidClasspath("JAR_SIZE_LIMIT")
            if len({i.filename for i in infos}) != len(infos):
                raise InvalidClasspath("DUPLICATE_ZIP_ENTRY")
            if jar.testzip() is not None:
                raise InvalidClasspath("JAR_INTEGRITY")
            fields = {}
            if "META-INF/MANIFEST.MF" in jar.namelist():
                info = jar.getinfo("META-INF/MANIFEST.MF")
                if info.file_size > 1048576:
                    raise InvalidClasspath("INVALID_MANIFEST")
                fields = manifest_fields(jar.read(info))
            if fields.get("class-path", "").strip():
                raise InvalidClasspath("IMPLICIT_MANIFEST_CLASSPATH")
            for info in infos:
                name = info.filename
                if name.endswith(".class") and not name.startswith("META-INF/") and name != "module-info.class":
                    if name in classes:
                        raise InvalidClasspath("DUPLICATE_CLASS")
                    classes[name] = index
            reports.append("DESCRIPTOR_DEPENDENCY jar_index=" + str(index)
                           + " sha256=" + digest.hexdigest()
                           + " implementation_version=" + safe_version(fields.get("implementation-version"))
                           + " specification_version=" + safe_version(fields.get("specification-version")))
    if classes.get("com/integrien/alive/common/adapter3/describe/AdapterDescribe.class") != 0:
        raise InvalidClasspath("SDK_CLASS_UNAVAILABLE")
    for scope, name in PROBES:
        owner = classes.get(name.replace(".", "/") + ".class")
        reports.append("DESCRIPTOR_DEPENDENCY scope=" + scope + " class=" + name
                       + (" status=MISSING" if owner is None else " status=PRESENT jar_index=" + str(owner)))
    reports.append("DESCRIPTOR_DEPENDENCY code=INVENTORY_ONLY platform_compatibility=UNVERIFIED")
    return reports


def main():
    try:
        if len(sys.argv) != 1:
            raise InvalidClasspath("INVALID_ARGUMENTS")
        for report in inspect(os.environ.get("VCFCF_SDK_JAR"), os.environ.get("VCFCF_DESCRIPTOR_CLASSPATH", "")):
            print(report)
        return 0
    except InvalidClasspath as failure:
        print("DESCRIPTOR_DEPENDENCY code=" + failure.code)
    except Exception:
        # ZIP decompression/codec/permission failures must never dump paths or
        # untrusted archive metadata. Process interrupts are not swallowed.
        print("DESCRIPTOR_DEPENDENCY code=UNREADABLE_OR_INVALID_JAR")
    return 2


if __name__ == "__main__":
    sys.exit(main())
