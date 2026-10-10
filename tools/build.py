#!/usr/bin/env python3
"""Pinned JDK 25 build, regression tests and complete QuickShop overlay packaging."""
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
    projects = [ROOT, *sorted((ROOT / "integrations").glob("Towny*")), ROOT / "quickshop-patch"]
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
        if project.name == "quickshop-patch": continue
        jar_path = DIST / f"{artifact}-{version}.jar"
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
    # The replacement wrapper must precede the original QuickShop class on the test classpath.
    test_cp = os.pathsep.join(str(p) for p in [*reversed(outputs), cp])
    run([JAVA / "javac", "--release", "25", "-encoding", "UTF-8", "-cp", test_cp,
         "-d", tests, *sorted((ROOT / "tests/java").rglob("*.java"))])
    run([JAVA / "java", "--enable-native-access=ALL-UNNAMED", f"-javaagent:{DEPS / 'byte-buddy-agent.jar'}",
         "-cp", str(tests) + os.pathsep + test_cp, "org.junit.platform.console.ConsoleLauncher", "execute",
         f"--scan-classpath={tests}", "--disable-banner"])
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
    jars = sorted(DIST.glob("*.jar"))
    (DIST / "SHA256SUMS").write_text("".join(f"{digest(p)}  {p.name}\n" for p in jars))
    with zipfile.ZipFile(DIST / "TownyReborn-Plugin-Update-2026-10-10.zip", "w", zipfile.ZIP_DEFLATED) as bundle:
        for path in [*jars, DIST / "SHA256SUMS", ROOT / "INSTALLATION.md"]:
            bundle.write(path, path.name)
    print("Build and tests passed. Files:", DIST)


if __name__ == "__main__": main()
