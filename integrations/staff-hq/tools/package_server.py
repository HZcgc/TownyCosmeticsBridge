#!/usr/bin/env python3
"""Create an explicit SMP overlay without production config, player data, tokens or queue files."""
from pathlib import Path
import argparse, hashlib, zipfile

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--connector',type=Path,required=True)
p.add_argument('--journey',type=Path,required=True)
p.add_argument('--journey-config',type=Path,required=True)
p.add_argument('--journey-guide',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
entries={
 'SERVER-UPDATE/plugins/TownyRebornStaffConnector-1.5.0.jar':a.connector.read_bytes(),
 'SERVER-UPDATE/plugins/TownyRebornProgression-2.0.0-beta.9.jar':a.journey.read_bytes(),
 'SERVER-UPDATE/plugins/TownyRebornProgression/config.yml':a.journey_config.read_bytes(),
 'SERVER-UPDATE/plugins/DiscordSRV/messages.yml':(ROOT.parent/'DiscordChannelLabels/messages.yml').read_bytes(),
 'INSTALL-DE.md':(ROOT/'docs/INSTALL-5.2.0-DE.md').read_bytes(),
 'VALIDATION.md':(ROOT/'docs/VALIDATION-5.2.0.md').read_bytes(),
 'JOURNEY-STAFF-AUDIT-DE.md':a.journey_guide.read_bytes(),
 'DISCORD-CHANNELS-DE.md':(ROOT.parent/'DiscordChannelLabels/README-DE.md').read_bytes(),
}
with zipfile.ZipFile(a.journey) as archive:assert archive.read('config.yml')==a.journey_config.read_bytes()
assert not any(any(s in name.lower() for s in ['playerdata','journeydata','pending-events','accounts.aof','config/config.php','notifications.php']) for name in entries)
entries['SHA256SUMS.txt']=''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name,data in sorted(entries.items())).encode()
a.output.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as archive:
 for name,data in sorted(entries.items()):
  info=zipfile.ZipInfo(name,date_time=(2026,10,14,0,0,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,data)
with zipfile.ZipFile(a.output) as archive:
 assert archive.testzip() is None
 for line in archive.read('SHA256SUMS.txt').decode().splitlines():
  digest,name=line.split('  ',1);assert hashlib.sha256(archive.read(name)).hexdigest()==digest
print(f'{a.output.resolve()} — {len(entries)} entries — {a.output.stat().st_size} bytes')
