#!/usr/bin/env python3
"""Pinned JDK 25 build, regression tests and complete plugin overlay packaging."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / "build"
DEPS = BUILD / "deps"
DIST = ROOT / "dist"
JAVA = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else Path(shutil.which("javac")).parent


def run(command):
    subprocess.run([str(arg) for arg in command], cwd=ROOT, check=True)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    DEPS.mkdir(parents=True, exist_ok=True)
    inputs = json.loads((ROOT / "tools/build-inputs.json").read_text())
    for name, entry in inputs.items():
        path = DEPS / name
        if not path.exists() or digest(path) != entry["sha256"]:
            temporary = path.with_suffix(".download")
            run(["curl", "--fail", "--location", "--silent", "--show-error", "--retry", "3",
                 "--connect-timeout", "15", "--max-time", "180", entry["url"], "-o", temporary])
            if digest(temporary) != entry["sha256"]:
                raise RuntimeError(f"Checksum mismatch: {name}")
            temporary.replace(path)
        with zipfile.ZipFile(path) as jar:
            if jar.testzip(): raise RuntimeError(f"Invalid JAR: {name}")
    libraries = DEPS / "paper-libraries"
    libraries.mkdir(exist_ok=True)
    with zipfile.ZipFile(DEPS / "paper-bundle.jar") as jar:
        for name in jar.namelist():
            if name.startswith("META-INF/libraries/") and name.endswith(".jar"):
                (libraries / Path(name).name).write_bytes(jar.read(name))
    cp = os.pathsep.join(str(p) for p in [DEPS / "paper-api.jar", DEPS / "slf4j-api.jar",
         DEPS / "log4j-api.jar", libraries / "*", DEPS / "*"])
    projects = [ROOT, *sorted((ROOT / "integrations").glob("Towny*")),
                ROOT / "quickshop-patch", ROOT / "excellentcrates-patch"]
    outputs = []
    DIST.mkdir(exist_ok=True)
    for project in projects:
        target = project / "target/classes"
        if target.exists(): shutil.rmtree(target)
        target.mkdir(parents=True)
        sources = sorted((project / "src/main/java").rglob("*.java"))
        run([JAVA / "javac", "--release", "25", "-encoding", "UTF-8", "-cp", cp, "-d", target, *sources])
        outputs.append(target)
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        pom = ET.parse(project / "pom.xml").getroot()
        artifact = pom.findtext("m:artifactId", namespaces=ns)
        version = pom.findtext("m:version", namespaces=ns)
        if project.name in {"quickshop-patch", "excellentcrates-patch"}: continue
        jar_path = DIST / f"{artifact}-{version}.jar"
        for previous in DIST.glob(f"{artifact}-*.jar"):
            if previous != jar_path: previous.unlink()
        with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as jar:
            for folder in [target, project / "src/main/resources"]:
                for path in sorted(folder.rglob("*")):
                    if path.is_file():
                        data = path.read_bytes()
                        if folder.name == "resources": data = data.replace(b"${project.version}", version.encode())
                        jar.writestr(str(path.relative_to(folder)), data)
    tests = BUILD / "test-classes"
    if tests.exists(): shutil.rmtree(tests)
    tests.mkdir()
    # Replacement classes must precede the original plugin classes on the test classpath.
    test_cp = os.pathsep.join(str(p) for p in [*reversed(outputs), cp])
    run([JAVA / "javac", "--release", "25", "-encoding", "UTF-8", "-cp", test_cp,
         "-d", tests, *sorted((ROOT / "tests/java").rglob("*.java"))])
    patched = DIST / "QuickShop-Hikari-6.3.0.3-townybarrels.2.jar"
    replacement = ROOT / "quickshop-patch/target/classes"
    with zipfile.ZipFile(DEPS / "QuickShop.jar") as old, zipfile.ZipFile(patched, "w", zipfile.ZIP_DEFLATED) as new:
        for entry in old.infolist():
            if (replacement / entry.filename).is_file(): continue
            if entry.filename.startswith("META-INF/") and entry.filename.endswith((".SF", ".RSA", ".DSA")): continue
            data = old.read(entry.filename)
            if entry.filename == "plugin.yml": data = data.replace(b"6.3.0.3", b"6.3.0.3-townybarrels.2")
            new.writestr(entry, data)
        for path in replacement.rglob("*.class"): new.write(path, path.relative_to(replacement))
        new.write(ROOT / "quickshop-patch/LICENSE", "META-INF/TOWNY-BARREL-PATCH-LICENSE")
        for path in (ROOT / "quickshop-patch/src/main/java").rglob("*.java"):
            new.write(path, "META-INF/towny-patch-source/" + str(path.relative_to(ROOT / "quickshop-patch/src/main/java")))

    ec_patch = ROOT / "excellentcrates-patch"
    ec_classes = ec_patch / "target/classes"
    ec_version = ET.parse(ec_patch / "pom.xml").getroot().findtext("{http://maven.apache.org/POM/4.0.0}version")
    ec_patched = DIST / f"ExcellentCrates-{ec_version}.jar"
    for previous in DIST.glob("ExcellentCrates-*.jar"):
        if previous != ec_patched: previous.unlink()
    with zipfile.ZipFile(DEPS / "ExcellentCrates.jar") as old, zipfile.ZipFile(ec_patched, "w", zipfile.ZIP_DEFLATED) as new:
        descriptors = {"plugin.yml", "paper-plugin.yml"}
        if not descriptors.issubset(old.namelist()): raise RuntimeError("EC7 plugin descriptors are missing")
        for replacement in ec_classes.rglob("*.class"):
            relative = str(replacement.relative_to(ec_classes))
            # javac may emit synthetic nested helpers for an overlaid class.
            outer = relative.split("$", 1)[0] + ".class" if "$" in relative else relative
            if outer not in old.namelist():
                raise RuntimeError(f"Unexpected EC7 replacement: {replacement.name}")
        for entry in old.infolist():
            if (ec_classes / entry.filename).is_file(): continue
            if entry.filename.startswith("META-INF/") and entry.filename.endswith((".SF", ".RSA", ".DSA")): continue
            data = old.read(entry.filename)
            if entry.filename in descriptors:
                original_version = b"version: '7.1.0'"
                if data.count(original_version) != 1: raise RuntimeError(f"Unexpected EC7 version in {entry.filename}")
                data = data.replace(original_version, f"version: '{ec_version}'".encode())
            new.writestr(entry, data)
        for path in ec_classes.rglob("*.class"): new.write(path, path.relative_to(ec_classes))
        new.write(ec_patch / "LICENSE", "META-INF/TOWNY-KEY-PATCH-LICENSE")
        for path in (ec_patch / "src/main/java").rglob("*.java"):
            new.write(path, "META-INF/towny-key-patch-source/" + str(path.relative_to(ec_patch / "src/main/java")))
    # Exercise the startup gate using the actual Paper descriptor in the final JAR.
    run([JAVA / "java", "--enable-native-access=ALL-UNNAMED", f"-javaagent:{DEPS / 'byte-buddy-agent.jar'}",
         f"-Dtownyreborn.crates-jar={ec_patched}", "-cp", str(tests) + os.pathsep + test_cp,
         "org.junit.platform.console.ConsoleLauncher", "execute", f"--scan-classpath={tests}", "--disable-banner"])
    jars = sorted(DIST.glob("*.jar"))
    (DIST / "SHA256SUMS").write_text("".join(f"{digest(p)}  {p.name}\n" for p in jars))
    with zipfile.ZipFile(DIST / "TownyReborn-Plugin-Update-2026-10-10.zip", "w", zipfile.ZIP_DEFLATED) as bundle:
        for path in [*jars, DIST / "SHA256SUMS", ROOT / "INSTALLATION.md"]:
            bundle.write(path, path.name)
        bundle.write(ec_patch / "README.md", "excellentcrates-patch/README.md")
    key_bundle = DIST / "TownyReborn-Crate-Key-Fix.zip"
    hunt_version = ET.parse(ROOT / "integrations/TownyRebornHunt/pom.xml").getroot().findtext("{http://maven.apache.org/POM/4.0.0}version")
    with zipfile.ZipFile(key_bundle, "w", zipfile.ZIP_DEFLATED) as bundle:
        bundle.write(ec_patched, f"plugins/{ec_patched.name}")
        hunt = DIST / f"TownyRebornHunt-{hunt_version}.jar"
        bundle.write(hunt, f"plugins/{hunt.name}")
        bundle.write(ec_patch / "README.md", "INSTALLATION.md")
        bundle.writestr("SHA256SUMS", "".join(f"{digest(p)}  plugins/{p.name}\n" for p in [ec_patched, hunt]))
    print("Build and tests passed. Files:", DIST)
    pet_project = ROOT / "integrations/TownyRebornPetShops"
    pet_version = ET.parse(pet_project / "pom.xml").getroot().findtext("{http://maven.apache.org/POM/4.0.0}version")
    pet_bundle = DIST / f"TownyReborn-Petshops-{pet_version}.zip"
    for previous in DIST.glob("TownyReborn-Petshops-*.zip"):
        if previous != pet_bundle: previous.unlink()
    with zipfile.ZipFile(pet_bundle, "w", zipfile.ZIP_DEFLATED) as bundle:
        bundle.write(DIST / f"TownyRebornPetShops-{pet_version}.jar", f"plugins/TownyRebornPetShops-{pet_version}.jar")
        for name in ["config.yml", "pets.yml"]:
            bundle.write(pet_project / "src/main/resources" / name, "plugins/TownyRebornPetShops/" + name)
        bundle.write(pet_project / "README.md", "INSTALLATION.md")


if __name__ == "__main__": main()
