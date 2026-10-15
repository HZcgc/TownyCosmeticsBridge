#!/usr/bin/env python3
"""Package only the connector hotfix; never overwrite credentials, queues or Journey data."""
from pathlib import Path
import argparse, hashlib, re, zipfile

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--connector',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
with zipfile.ZipFile(a.connector) as jar:
    version=re.search(r'^version:\s*(\S+)\s*$',jar.read('plugin.yml').decode(),re.M)[1]
assert version=='1.5.1'
entries={
    f'SERVER-UPDATE/plugins/TownyRebornStaffConnector-{version}.jar':a.connector.read_bytes(),
    'INSTALL-DE.md':(ROOT/'docs/INSTALL-5.2.1-DE.md').read_bytes(),
    'VALIDATION.md':(ROOT/'docs/VALIDATION-5.2.1.md').read_bytes(),
}
entries['SHA256SUMS.txt']=''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name,data in sorted(entries.items())).encode()
a.output.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as archive:
    for name,data in sorted(entries.items()):
        info=zipfile.ZipInfo(name,date_time=(2026,10,15,0,0,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,data)
with zipfile.ZipFile(a.output) as archive:
    assert archive.testzip() is None
    for line in archive.read('SHA256SUMS.txt').decode().splitlines():
        digest,name=line.split('  ',1);assert hashlib.sha256(archive.read(name)).hexdigest()==digest
print(a.output.resolve())
