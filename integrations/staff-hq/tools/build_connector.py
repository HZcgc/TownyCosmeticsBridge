#!/usr/bin/env python3
"""Build the connector, verify native boundaries and optionally test real Journey audit receipts."""
from pathlib import Path
import argparse, json, re, subprocess, tempfile, zipfile

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--jdk',type=Path,required=True,help='JDK 25 bin directory')
p.add_argument('--deps',type=Path,required=True)
p.add_argument('--junit',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--journey-jar',type=Path)
a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
source=ROOT/'source/connector'
dependencies=sorted(f for f in a.deps.rglob('*.jar') if zipfile.is_zipfile(f))
cp=':'.join(map(str,dependencies));classes=Path(tempfile.mkdtemp(prefix='classes-',dir=a.output));tests=Path(tempfile.mkdtemp(prefix='tests-',dir=a.output))
subprocess.run([str(a.jdk/'javac'),'--release','17','-cp',cp,'-d',str(classes),*map(str,sorted((source/'src/main/java').rglob('*.java'))),*map(str,sorted((source/'stubs').rglob('*.java')))],check=True)
jar=a.output/'TownyRebornStaffConnector-1.5.1.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as archive:
 for folder in [classes,source/'src/main/resources']:
  for f in sorted(folder.rglob('*')):
   if not f.is_file() or f.relative_to(folder).parts[0]=='litebans':continue
   info=zipfile.ZipInfo(f.relative_to(folder).as_posix());info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,f.read_bytes())
testcp=':'.join(map(str,[tests,jar,a.junit]))+':'+cp
subprocess.run([str(a.jdk/'javac'),'--release','25','-cp',testcp,'-d',str(tests),*map(str,sorted((source/'src/test/java').rglob('*.java')))],check=True)
r=subprocess.run([str(a.jdk/'java'),'-Xverify:all','-jar',str(a.junit),'execute','--class-path',testcp,'--select-package','com.townyreborn.staffconnector','--disable-banner','--disable-ansi-colors','--details','summary'],capture_output=True,text=True)
(a.output/'junit.log').write_text(r.stdout+r.stderr);print(r.stdout[-3500:])
if r.returncode:raise SystemExit(r.returncode)
successful=re.search(r'\[\s*(\d+) tests successful',r.stdout)
skipped=re.search(r'\[\s*(\d+) tests skipped',r.stdout)
aborted=re.search(r'\[\s*(\d+) tests aborted',r.stdout)
(a.output/'verification.json').write_text(json.dumps({'tests_successful':int(successful[1]),'tests_skipped':int(skipped[1]),'tests_aborted':int(aborted[1])}))
if a.journey_jar:
 with zipfile.ZipFile(a.journey_jar) as archive:(a.output/'journey-config.yml').write_bytes(archive.read('config.yml'))
 bridgecp=testcp+':'+str(a.journey_jar)
 subprocess.run([str(a.jdk/'javac'),'--release','25','-cp',bridgecp,'-d',str(tests),str(ROOT/'tests/java/JourneyBridgeFixture.java')],check=True)
 subprocess.run([str(a.jdk/'java'),'-Xverify:all','-cp',bridgecp,'com.townyreborn.staffconnector.JourneyBridgeFixture',tempfile.mkdtemp(prefix='bridge-game-',dir=a.output),str(a.output/'journey-config.yml'),str(a.output/'journey-fixture.json')],check=True)
print(jar)
