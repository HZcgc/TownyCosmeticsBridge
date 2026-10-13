#!/usr/bin/env python3
"""Check preserved release integrity and run the same core checks on its source rebuild."""
from pathlib import Path
import argparse,zipfile,subprocess,hashlib,shutil
import yaml
ROOT=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
p.add_argument('--jdk',type=Path,required=True);p.add_argument('--deps',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
for line in (ROOT/'releases/SHA256SUMS.txt').read_text().splitlines():
 digest,name=line.split('  ',1);assert hashlib.sha256((ROOT/'releases'/name).read_bytes()).hexdigest()==digest
print('Preserved release and install-bundle checksums match.')
deps=sorted(f for f in a.deps.rglob('*.jar') if zipfile.is_zipfile(f));cp=':'.join(map(str,deps))
classes=a.output/'test-classes';classes.mkdir(exist_ok=True)
subprocess.run([str(a.jdk/'javac'),'--release','21','-cp',str(a.jar)+':'+cp,'-d',str(classes),str(ROOT/'src/test/java/net/townyreborn/journey/RecoveryChecks.java')],check=True)
outputs=[]
for name,jar in [('original',ROOT/'releases/TownyRebornProgression-2.0.0-beta.1.jar'),('rebuilt',a.jar)]:
 folder=a.output/name
 if folder.exists():shutil.rmtree(folder)
 with zipfile.ZipFile(jar) as z:
  meta=yaml.safe_load(z.read('plugin.yml'));assert meta['permissions']['townyreborn.progression.access']['default'] is False
  assert 'Quests' not in meta['depend'] and 'Quests' not in meta['softdepend']
  assert not any(b'com/leonardobishop/quests' in z.read(n) for n in z.namelist() if n.endswith('.class'))
 r=subprocess.run([str(a.jdk/'java'),'-Xverify:all','-cp',str(classes)+':'+str(jar)+':'+cp,'net.townyreborn.journey.RecoveryChecks',str(folder),str(ROOT/'src/main/resources/config.yml')],capture_output=True,text=True)
 (a.output/(name+'.log')).write_text(r.stdout+r.stderr)
 if r.returncode:print(r.stdout+r.stderr);raise SystemExit(r.returncode)
 print(name, r.stdout.splitlines()[-1]);outputs.append(r.stdout)
assert outputs[0]==outputs[1], 'Release and source rebuild disagree'
print('Original and recovered source pass the identical 24 core scenarios.')
