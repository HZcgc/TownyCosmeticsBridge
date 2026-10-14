#!/usr/bin/env python3
"""Check preserved release integrity and run the same core checks on its source rebuild."""
from pathlib import Path
import argparse,zipfile,subprocess,hashlib,shutil,tempfile
import yaml
ROOT=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
p.add_argument('--jdk',type=Path,required=True);p.add_argument('--deps',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
p.add_argument('--permissions-fixture',type=Path)
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
  folder.mkdir(parents=True);(folder/'config.yml').write_bytes(z.read('config.yml'))
 data=Path(tempfile.mkdtemp(prefix='core-data-',dir=folder))
 r=subprocess.run([str(a.jdk/'java'),'-Xverify:all','-cp',str(classes)+':'+str(jar)+':'+cp,'net.townyreborn.journey.RecoveryChecks',str(data),str(folder/'config.yml')],capture_output=True,text=True)
 (a.output/(name+'.log')).write_text(r.stdout+r.stderr)
 if r.returncode:print(r.stdout+r.stderr);raise SystemExit(r.returncode)
 print(name, r.stdout.splitlines()[-1]);outputs.append(r.stdout)
assert outputs[0]==outputs[1], 'Release and source rebuild disagree'
print('Original beta and updated source pass the same 24 core recovery scenarios.')
subprocess.run([str(a.jdk/'javac'),'--release','21','-cp',str(a.jar)+':'+cp,'-d',str(classes),str(ROOT/'src/test/java/net/townyreborn/journey/JourneyUpdateChecks.java')],check=True)
jar_arg='-javaagent:'+str((a.deps/'mockito-core.jar').resolve())
update_data=Path(tempfile.mkdtemp(prefix='update-data-',dir=a.output))
r=subprocess.run([str(a.jdk/'java'),'-Xverify:all',jar_arg,'-Dnet.bytebuddy.experimental=true','-cp',str(classes)+':'+str(a.jar)+':'+cp,'net.townyreborn.journey.JourneyUpdateChecks',str(update_data),str(a.jar),str(ROOT/'releases/TownyRebornProgression-2.0.0-beta.1.jar'),str(ROOT/'ui/TAB-groups.yml'),str(ROOT)],capture_output=True,text=True)
(a.output/'update.log').write_text(r.stdout+r.stderr)
print(r.stdout)
if r.returncode:print(r.stderr);raise SystemExit(r.returncode)
subprocess.run([str(a.jdk/'javac'),'--release','21','-cp',str(classes)+':'+str(a.jar)+':'+cp,'-d',str(classes),str(ROOT/'src/test/java/net/townyreborn/journey/LongJourneyChecks.java')],check=True)
long_data=Path(tempfile.mkdtemp(prefix='long-data-',dir=a.output))
r=subprocess.run([str(a.jdk/'java'),'-Xverify:all',jar_arg,'-Dnet.bytebuddy.experimental=true','-cp',str(classes)+':'+str(a.jar)+':'+cp,'net.townyreborn.journey.LongJourneyChecks',str(long_data),str(a.jar),str(ROOT)],capture_output=True,text=True)
(a.output/'long.log').write_text(r.stdout+r.stderr)
print(r.stdout)
if r.returncode:print(r.stderr);raise SystemExit(r.returncode)
subprocess.run([str(a.jdk/'javac'),'--release','21','-cp',str(classes)+':'+str(a.jar)+':'+cp,'-d',str(classes),str(ROOT/'src/test/java/net/townyreborn/journey/PermissionImportChecks.java')],check=True)
command=[str(a.jdk/'java'),'-Xverify:all',jar_arg,'-Dnet.bytebuddy.experimental=true','-cp',str(classes)+':'+str(a.jar)+':'+cp,'net.townyreborn.journey.PermissionImportChecks',str(a.jar)]
if a.permissions_fixture:command.append(str(a.permissions_fixture))
r=subprocess.run(command,capture_output=True,text=True);(a.output/'permissions.log').write_text(r.stdout+r.stderr);print(r.stdout)
if r.returncode:print(r.stderr);raise SystemExit(r.returncode)
