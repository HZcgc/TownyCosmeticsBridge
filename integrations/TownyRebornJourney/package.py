#!/usr/bin/env python3
"""Package the verified JAR and explicit folder overlay, without any player data."""
from pathlib import Path
import argparse, hashlib, re, shutil, zipfile
import yaml

ROOT = Path(__file__).resolve().parent
p = argparse.ArgumentParser()
p.add_argument('--jar', type=Path, required=True)
p.add_argument('--verification', type=Path, required=True)
a = p.parse_args()
with zipfile.ZipFile(a.jar) as jar:
    meta = yaml.safe_load(jar.read('plugin.yml'))
    version = meta['version']
    assert jar.read('config.yml') == (ROOT/'src/main/resources/config.yml').read_bytes(), 'JAR/config mismatch'

logs = {name: (a.verification/(name+'.log')).read_text() for name in ['original', 'rebuilt', 'update', 'long', 'permissions']}
counts = {}
for name, log in logs.items():
    result = re.findall(r'^RESULT (\d+) .*checks passed$', log, re.M)
    assert len(result) == 1 and 'Exception in thread' not in log, f'{name} checks not successful'
    counts[name] = int(result[0])
    assert sum(line.startswith('PASS ') for line in log.splitlines()) == counts[name]
assert logs['original'].split('RESULT')[0] == logs['rebuilt'].split('RESULT')[0], 'Reference/core scenarios disagree'
total = counts['rebuilt'] + counts['update'] + counts['long'] + counts['permissions']
report = f'''Towny Reborn Journey {version}

Current build: {total} scenarios passed ({counts['rebuilt']} recovery + {counts['update']} menu/update + {counts['long']} long-Journey/migration + {counts['permissions']} permission-import).
Preserved Beta.1 reference: the same {counts['original']} recovery scenarios passed independently.
Java sources compile with --release 21; JVM verification runs with -Xverify:all.

Scope: controlled economy/LP, inventory registry and sound/title boundaries.
No live Paper/client deployment or visual Minecraft test is claimed.
Uploaded Quests player records and quest definitions are not packaged.

'''
for name in ['rebuilt', 'update', 'long', 'permissions']:
    report += name.upper()+'\n'+'\n'.join(line for line in logs[name].splitlines() if line.startswith(('PASS ', 'RESULT ')))+'\n\n'
report = report.rstrip()+'\n'
(ROOT/'verification/JOURNEY-UPDATE-VERIFICATION.txt').write_text(report)
release = ROOT/'releases'
jar_target = release/f'TownyRebornProgression-{version}.jar'
if a.jar.resolve() != jar_target.resolve(): shutil.copyfile(a.jar, jar_target)
bundle = {
    'plugins/'+jar_target.name: jar_target.read_bytes(),
    'plugins/TownyRebornProgression/config.yml': (ROOT/'src/main/resources/config.yml').read_bytes(),
    'plugins/TAB/groups.yml': (ROOT/'ui/TAB-groups.yml').read_bytes(),
    'plugins/LyttleNametag/config.yml': (ROOT/'ui/LyttleNametag-config.yml').read_bytes(),
    'plugins/Quests/config.yml': (ROOT/'ui/Quests-config.yml').read_bytes(),
    'plugins/Quests/categories.yml': (ROOT/'ui/Quests-categories.yml').read_bytes(),
    'README-DE.md': (ROOT/'README-DE.md').read_bytes(),
    'TECHNICAL-README.md': (ROOT/'README.md').read_bytes(),
    'JOURNEY-POINTS-DE.md': (ROOT/'JOURNEY-POINTS-DE.md').read_bytes(),
    'PERKS.md': (ROOT/'PERKS.md').read_bytes(),
    'REWARDS.md': (ROOT/'REWARDS.md').read_bytes(),
    'VERIFICATION.txt': report.encode(),
}
assert not any('/journeydata/' in name or '/playerdata/' in name or name.lower().startswith('plugins/quests/quests/') for name in bundle)
for name, data in bundle.items():
    if name.endswith('.yml'): assert isinstance(yaml.safe_load(data), dict)
def write_bundle(package, files):
    entries = dict(files)
    entries['SHA256SUMS.txt'] = ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name, data in sorted(files.items())).encode()
    with zipfile.ZipFile(package, 'w', zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name)
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    with zipfile.ZipFile(package) as z:
        assert z.testzip() is None
        assert z.read('plugins/'+jar_target.name) == a.jar.read_bytes()
        for line in z.read('SHA256SUMS.txt').decode().splitlines():
            digest, name = line.split('  ', 1)
            assert hashlib.sha256(z.read(name)).hexdigest() == digest
    return len(entries)

package = release/f'TownyReborn-Journey-{version}.zip'
members = write_bundle(package, bundle)
hotfix = release/f'TownyReborn-Journey-Hotfix-{version}.zip'
hotfix_files = {name: data for name, data in bundle.items()
                if name in {'plugins/'+jar_target.name, 'plugins/TownyRebornProgression/config.yml', 'README-DE.md', 'PERKS.md', 'VERIFICATION.txt'}}
assert {name for name in hotfix_files if name.startswith('plugins/')} == {'plugins/'+jar_target.name, 'plugins/TownyRebornProgression/config.yml'}
hotfix_members = write_bundle(hotfix, hotfix_files)
hashes = {name: digest for digest, name in (line.split('  ', 1) for line in (release/'SHA256SUMS.txt').read_text().splitlines())}
for file in [jar_target, package, hotfix]: hashes[file.name] = hashlib.sha256(file.read_bytes()).hexdigest()
(release/'SHA256SUMS.txt').write_text(''.join(f'{digest}  {name}\n' for name, digest in sorted(hashes.items())))
print(f'{package}\n{total} current scenarios passed; {members} members; {package.stat().st_size} bytes; ZIP and SHA256 verification passed.')
print(f'{hotfix}\n{hotfix_members} members; {hotfix.stat().st_size} bytes; only Progression JAR/config plus instructions and verification.')
