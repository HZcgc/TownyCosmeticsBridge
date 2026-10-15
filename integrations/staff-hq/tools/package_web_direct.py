#!/usr/bin/env python3
"""Full web-root overlay with a read-only deployment check; no enclosing upload folder."""
from pathlib import Path
import argparse,hashlib,json,zipfile

ROOT=Path(__file__).resolve().parents[1]
WEB=ROOT/'source/web'
MANIFEST='tr-update-manifest-5-2-1.json'
EXCLUDED={'config/config.php','config/notifications.php',MANIFEST}

def public_files():
    result={}
    for path in sorted(WEB.rglob('*')):
        relative=path.relative_to(WEB).as_posix()
        if path.is_symlink():raise ValueError('Unexpected source symlink')
        if not path.is_file() or relative in EXCLUDED or relative.startswith('storage/'):continue
        result[relative]=path.read_bytes()
    return result

def build(destination):
    entries=public_files()
    manifest={'release':'5.2.1','api_version':9,'files':{name:hashlib.sha256(data).hexdigest() for name,data in entries.items()}}
    data=(json.dumps(manifest,indent=2,ensure_ascii=True)+'\n').encode()
    (WEB/MANIFEST).write_bytes(data);entries[MANIFEST]=data
    entries['README-UPDATE-DE.txt']=(ROOT/'docs/DIRECT-UPLOAD-5.2.1-DE.txt').read_bytes()
    entries['UPDATE-SHA256SUMS.txt']=''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name,data in sorted(entries.items())).encode()
    destination.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED) as archive:
        for name,data in sorted(entries.items()):
            info=zipfile.ZipInfo(name,date_time=(2026,10,15,2,56,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,data)
    with zipfile.ZipFile(destination) as archive:
        assert archive.testzip() is None
        assert 'api.php' in archive.namelist() and not any(name.startswith('WEBSPACE-UPDATE/') for name in archive.namelist())
        for line in archive.read('UPDATE-SHA256SUMS.txt').decode().splitlines():
            sha,name=line.split('  ',1);assert hashlib.sha256(archive.read(name)).hexdigest()==sha
    print(destination.resolve())

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=ROOT/'releases/TownyReborn-StaffHQ-5.2.1-DIRECT-UPLOAD.zip')
    build(parser.parse_args().output)
