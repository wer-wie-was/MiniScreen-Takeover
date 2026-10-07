#!/usr/bin/env python3
"""Package existing emulator binaries and user-supplied firmware; do not compile anything."""
import argparse,json,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--upstream',type=Path);p.add_argument('--firmware-only',action='store_true',help='Use the engine already included in the app');p.add_argument('--firmware',type=Path,required=True);p.add_argument('--platforms',nargs='+',default=['chalk']);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
allowed={'aplite','basalt','chalk','diorite','emery','flint','gabbro'};entries=[]
for board in a.platforms:
 if board not in allowed:raise SystemExit('Unknown platform: '+board)
 engine=(a.upstream/'qemu-classic' if board in {'aplite','basalt','chalk','diorite'} else a.upstream) if a.upstream else None
 if not a.firmware_only and engine is None:raise SystemExit('--upstream is required unless --firmware-only is set')
 for name in ([] if a.firmware_only else ['qemu-system-arm.js','qemu-system-arm.wasm']):
  file=engine/name
  if not file.is_file():raise SystemExit('Missing prebuilt emulator '+str(file))
  entries.append((file,board+'/'+name))
 worker=engine/'qemu-system-arm.worker.js' if engine and not a.firmware_only else None
 if worker and worker.is_file():entries.append((worker,board+'/'+worker.name))
 for name in ['qemu_micro_flash.bin','qemu_spi_flash.bin']:
  file=a.firmware/board/name
  if not file.is_file():raise SystemExit('Missing firmware '+str(file))
  entries.append((file,board+'/'+name))
with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('runtime.json',json.dumps({'schema':1,'platforms':a.platforms,'upstream':'aefa8f180c31c63e3271fbde60ec2021a4878677'}))
 z.writestr('LICENSES.txt','QEMU: GPL-2.0. Pebble hardware models retain their original licenses. Firmware licensing depends on the supplied images. See upstream corresponding source and build scripts.\n')
 for file,name in entries:z.write(file,name)
print('Prepared runtime:',a.output,'No compilation performed.')
