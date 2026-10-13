#!/usr/bin/env python3
"""Build the standalone source; the checked release is preserved separately."""
import argparse, subprocess, zipfile, shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
p.add_argument('--jdk',type=Path,required=True);p.add_argument('--deps',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
a=p.parse_args();classes=a.output.parent/'classes'
if classes.exists():shutil.rmtree(classes)
classes.mkdir(parents=True)
deps=sorted(f for f in a.deps.rglob('*.jar') if zipfile.is_zipfile(f))
subprocess.run([str(a.jdk/'javac'),'--release','21','-cp',':'.join(map(str,deps)),'-d',str(classes),*map(str,sorted((ROOT/'src/main/java').rglob('*.java')))],check=True)
a.output.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as z:
 for base in [classes,ROOT/'src/main/resources']:
  for f in sorted(base.rglob('*')):
   if not f.is_file():continue
   info=zipfile.ZipInfo(str(f.relative_to(base)));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,f.read_bytes())
a.output.chmod(0o644);print(a.output)
