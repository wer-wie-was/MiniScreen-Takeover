#!/usr/bin/env python3
"""Package existing emulator binaries and user-supplied firmware; do not compile anything."""
import argparse,json,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--upstream',type=Path,required=True);p.add_argument('--firmware',type=Path,required=True);p.add_argument('--platforms',nargs='+',default=['chalk']);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
allowed={'aplite','basalt','chalk','diorite','emery','flint','gabbro'};entries=[]
for board in a.platforms:
 if board not in allowed:raise SystemExit('Unknown platform: '+board)
 engine=a.upstream/'qemu-classic' if board in {'aplite','basalt','chalk','diorite'} else a.upstream
 for name in ['qemu-system-arm.js','qemu-system-arm.wasm']:
  file=engine/name
  if not file.is_file():raise SystemExit('Missing prebuilt emulator '+str(file))
  entries.append((file,board+'/'+name))
 worker=engine/'qemu-system-arm.worker.js'
 if worker.is_file():entries.append((worker,board+'/'+worker.name))
 for name in ['qemu_micro_flash.bin','qemu_spi_flash.bin']:
  file=a.firmware/board/name
  if not file.is_file():raise SystemExit('Missing firmware '+str(file))
  entries.append((file,board+'/'+name))
with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('runtime.json',json.dumps({'schema':1,'platforms':a.platforms,'upstream':'aefa8f180c31c63e3271fbde60ec2021a4878677'}))
 z.writestr('LICENSES.txt','QEMU: GPL-2.0. Pebble hardware models retain their original licenses. Firmware licensing depends on the supplied images. See upstream corresponding source and build scripts.\n')
 for file,name in entries:z.write(file,name)
print('Prepared runtime:',a.output,'No compilation performed.')
