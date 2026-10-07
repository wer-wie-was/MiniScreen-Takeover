import {PebblePhone} from './vendor/pebble-transport.js';
import {parsePbw} from './vendor/pbw.js';
import {AppInstaller} from './vendor/app-install.js';
import {AppMessageClient} from './vendor/appmessage.js';
import {PkjsRuntime,makeIframeSandbox} from './vendor/pkjs-runtime.js';
import {steadyDisplayBinary} from './vendor/steady-display.js';
import {PulseConsole} from './vendor/pulse-console.js';

const boards={
 aplite:{machine:'pebble-bb2',width:144,height:168,classic:true},
 basalt:{machine:'pebble-snowy-bb',width:144,height:168,classic:true,spi:true},
 chalk:{machine:'pebble-s4-bb',width:180,height:180,classic:true,spi:true},
 diorite:{machine:'pebble-silk-bb',width:144,height:168,classic:true},
 emery:{machine:'pebble-emery',width:200,height:228},
 flint:{machine:'pebble-flint',width:144,height:168},
 gabbro:{machine:'pebble-gabbro',width:260,height:260}
};
const query=new URLSearchParams(location.search),platform=query.get('board'),board=boards[platform];
// Granted only for our exact bundled modern engine; custom engines stay untouched.
const steady=query.get('steady')==='1'&&!board?.classic;
const canvas=document.querySelector('#display'),ctx=canvas.getContext('2d');
let qemu,phone,pkjs,pulse,frameCount=-1,frameBusy=false,lastFrameTime=0,settings={fps:10},installed=false,installing=false,configCallback=null,installError=false;
let uartOffset=0,serialAddr=0,buttonMask=0,healthReady=false,healthChecked=false;
const pending=[],storageWrites=new Set();
function syncTime(){if(!phone)return;const zone=new TextEncoder().encode(Intl.DateTimeFormat().resolvedOptions().timeZone||'UTC');const payload=new Uint8Array(8+zone.length),dv=new DataView(payload.buffer);payload[0]=3;dv.setUint32(1,Math.floor(Date.now()/1000),false);dv.setInt16(5,-new Date().getTimezoneOffset(),false);payload[7]=zone.length;payload.set(zone,8);phone.sendPP(0x0b,payload);}
const status=text=>fetch('status',{method:'POST',body:String(text).slice(0,400)}).catch(()=>{});
const log=()=>{}; // Untrusted watchface logs and health values never enter app diagnostics.
const bytes=async url=>{const response=await fetch(url);if(!response.ok)throw Error(`Asset unavailable (${response.status})`);return new Uint8Array(await response.arrayBuffer());};
const scope=prefix=>{
 let cache=Object.create(null);const id=String(prefix).replace(/-/g,'').toLowerCase();
 try{const xhr=new XMLHttpRequest();xhr.open('GET','pkjs/'+id,false);xhr.send();if(xhr.status===200)cache=JSON.parse(xhr.responseText);}catch(e){}
 let writes=Promise.resolve();const save=()=>{const data=JSON.stringify(cache);writes=writes.then(async()=>{const response=await fetch('pkjs/'+id,{method:'POST',body:data});if(!response.ok)throw Error('Storage save failed');}).catch(()=>{});const task=writes;storageWrites.add(task);task.then(()=>storageWrites.delete(task));};
 return {getItem:k=>Object.hasOwn(cache,String(k))?cache[String(k)]:null,setItem(k,v){cache[String(k)]=String(v);save();},removeItem(k){delete cache[String(k)];save();},clear(){cache=Object.create(null);save();},key:i=>Object.keys(cache)[i]??null,get length(){return Object.keys(cache).length;}};
};
let savedHash=-1,saveTask=Promise.resolve();
function persist(force=false){
 saveTask=saveTask.catch(()=>{}).then(async()=>{
  if(!qemu)return;
  const flash=qemu.FS.readFile('/firmware/qemu_spi_flash.bin');let hash=2166136261;
  for(let i=0;i<flash.length;i+=64)hash=Math.imul(hash^flash[i],16777619)>>>0;
  if(!force&&hash===savedHash)return;
  const response=await fetch('state/'+platform,{method:'POST',body:flash});
  if(!response.ok)throw Error('Flash save failed');savedHash=hash;
 }).catch(()=>{status('state_save_failed');});return saveTask;
}
function writeConsole(bytes){
 if(!qemu)return false;if(!serialAddr&&qemu._pebble_wasm_console_ctrl)serialAddr=Number(qemu._pebble_wasm_console_ctrl());if(!serialAddr)return false;
 const base=serialAddr>>2,u32=qemu.HEAPU32,buf=u32[base],size=u32[base+1],head=Atomics.load(u32,base+2),tail=Atomics.load(u32,base+3);if(size-(head-tail)<bytes.length)return false;
 for(let j=0;j<bytes.length;j++)qemu.HEAPU8[buf+((head+j)%size)]=bytes[j];Atomics.store(u32,base+2,(head+bytes.length)>>>0);return true;
}
function feedConsole(){if(!qemu||!pulse)return;try{const bytes=qemu.FS.readFile('/tmp/uart2.log');if(bytes.length>uartOffset){pulse.feed(bytes.subarray(uartOffset));uartOffset=bytes.length;}if(bytes.length>1024*1024){qemu.FS.writeFile('/tmp/uart2.log',new Uint8Array());uartOffset=0;}}catch(e){}}
function buttons(name,down){const bit={back:1,up:2,select:4,down:8}[name];if(!bit)return;buttonMask=down?buttonMask|bit:buttonMask&~bit;
 if(board.classic){phone?.sendQemuFrame(8,Uint8Array.of(buttonMask));return;}
 const addr=qemu?Number(qemu._pebble_wasm_button_state_addr?.()):0;if(addr)Atomics.store(qemu.HEAPU32,addr>>2,buttonMask);
}
async function install(){
 if(installing||installed||installError||!phone)return;installing=true;
 try{status('installing');const pbw=await parsePbw(await bytes('watchface.pbw'),platform),installer=new AppInstaller(phone,()=>{},log);
 // The installer validates the binary header and obtains its UUID.
 let info;for(let attempt=0;attempt<3;attempt++){try{info=await installer.install(pbw);break;}catch(e){if(attempt===2)throw e;await new Promise(r=>setTimeout(r,3000));}}
 if(pbw.js){pkjs.registerApp(info.uuid,{js:pbw.js,appKeys:pbw.appinfo.appKeys||pbw.appinfo.messageKeys||{},name:info.name});
  // The launch notification can race installation; start only the matching JS after registration.
  const run=new Uint8Array(17);run[0]=1;run.set(info.uuid,1);phone.sendPP(0x34,run);
 }
 installed=true;status('running');await persist();
 }catch(e){installError=true;status('install_failed');}finally{installing=false;}
}
function attach(){
 if(!qemu||phone)return;phone=new PebblePhone(qemu,log);
 phone.onPhoneVersionRequest=()=>{syncTime();setTimeout(install,1500);};
 const am=new AppMessageClient(phone,log);
 pkjs=new PkjsRuntime(phone,am,{createSandbox:makeIframeSandbox(new URL('network?url=',location.href).href,log),storage:scope,tokenStore:localStorage,openUrl:(url,onClosed)=>{configCallback=onClosed;fetch('config',{method:'POST',body:url}).catch(()=>{});},log});
 pulse=new PulseConsole(writeConsole,{onPrompt:(text)=>{if(text&&text.includes('TAKEOVER_HEALTH_V1'))healthReady=true;},onLog:log,onRaw:log,log});
 setInterval(()=>{phone.poll();feedConsole();},20);
 // Firmware explicitly advertises our health bridge; no fabricated success for standard images.
 setTimeout(()=>{pulse.command('takeover_health version');healthChecked=true;},10000);
}
async function execute(cmd){
 if(cmd.type==='settings'){settings=cmd.value||{};if(phone){syncTime();phone.sendQemuFrame(9,Uint8Array.of(settings.twentyFour?1:0));if(settings.battery)phone.sendQemuFrame(5,Uint8Array.of(settings.battery.level,settings.battery.charging?1:0));}return;}
 if(cmd.type==='restart'){await persist(true);await Promise.all([...storageWrites]);await status('restart_ready:'+String(cmd.value));return;}
 if(cmd.type==='persist'){await persist(true);return;}
 if(cmd.type==='configClosed'){if(configCallback){const callback=configCallback;configCallback=null;callback(cmd.value||'');}return;}
 if(!phone){pending.push(cmd);return;}
 if(cmd.type==='button'){const name=String(cmd.value);buttons(name,true);setTimeout(()=>buttons(name,false),300);}
 else if(cmd.type==='configuration'){if(!pkjs?.showConfiguration())status('no_configuration');}
 else if(cmd.type==='health'){
  const value=cmd.value||{};if(!healthReady){if(healthChecked&&(value.steps!=null||value.heartRate!=null))status('health_bridge_missing');return;}
  const now=Math.floor(Date.now()/1000),age=(settings.healthMaxAge||15)*60;
  const steps=Number.isSafeInteger(value.steps)&&value.dayStart&&value.stepsTime&&now-value.stepsTime<180?Math.max(0,value.steps):-1;
  const bpm=Number.isSafeInteger(value.heartRate)&&value.heartTime&&now-value.heartTime<=age?value.heartRate:-1;
  pulse.command(`takeover_health ${steps} ${bpm} ${bpm<0?0:value.heartTime} ${steps<0?0:value.dayStart}`);
 }
}
async function commands(){try{for(const cmd of await (await fetch('commands')).json())await execute(cmd);if(phone)while(pending.length)await execute(pending.shift());}catch(e){}}
function frame(){if(!qemu)return;const fc=qemu._pebble_wasm_display_frame_count();if(fc===frameCount||frameBusy||performance.now()-lastFrameTime<1000/Math.max(1,settings.fps||10))return;
 const rawW=qemu._pebble_wasm_display_width(),rawH=qemu._pebble_wasm_display_height(),stride=qemu._pebble_wasm_display_stride(),ptr=Number(qemu._pebble_wasm_display_data());if(!ptr||rawW<1||rawH<1||rawW>512||rawH>512)return;
 // Some classic panels export a two-pixel hardware border around the logical display.
 const bordered=rawW===board.width+4&&rawH===board.height+4;
 const w=bordered?board.width:rawW,h=bordered?board.height:rawH,edge=bordered?2:0;
 frameCount=fc;lastFrameTime=performance.now();canvas.width=w;canvas.height=h;const image=ctx.createImageData(w,h),dest=new Uint32Array(image.data.buffer),heap=qemu.HEAPU8;
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){const i=ptr+(y+edge)*stride+(x+edge)*4;dest[y*w+x]=heap[i+2]|heap[i+1]<<8|heap[i]<<16|0xff000000;}
 ctx.putImageData(image,0,0);frameBusy=true;canvas.toBlob(async blob=>{try{if(blob)await fetch('frame',{method:'POST',body:blob});}catch(e){}finally{frameBusy=false;}},'image/png');
}
async function boot(){
 try{if(!board)throw Error('Unknown platform');if(!globalThis.isSecureContext){status('webview_secure_context_missing');return;}
  if(!globalThis.crossOriginIsolated){status('webview_isolation_failed');return;}
  if(typeof SharedArrayBuffer==='undefined'){status('webview_shared_memory_missing');return;}
  if(typeof WebAssembly==='undefined'||typeof Worker==='undefined'){status('webview_wasm_missing');return;}
  try{const memory=new WebAssembly.Memory({initial:1,maximum:1,shared:true});if(!(memory.buffer instanceof SharedArrayBuffer))throw Error('Unshared memory');}catch(e){status('webview_shared_memory_missing');return;}
  status('booting');const base='runtime/'+platform+'/',micro=await bytes(base+'qemu_micro_flash.bin');let spi;
  try{spi=await bytes('state/'+platform);}catch(e){spi=await bytes(base+'qemu_spi_flash.bin');}
  const factory=(await import('./'+base+'qemu-system-arm.js')).default;
  const args=['-machine',board.machine,'-kernel','/firmware/qemu_micro_flash.bin','-drive',board.spi?'if=none,id=spi-flash,format=raw,file=/firmware/qemu_spi_flash.bin':'if=mtd,format=raw,file=/firmware/qemu_spi_flash.bin','-display','none','-monitor','none','-parallel','none','-serial','null','-serial','null','-serial','file:/tmp/uart2.log','-rtc','base=localtime'];
  if(board.classic)args.push('-icount','shift=4,sleep=on');

  const options={arguments:args,print:log,printErr:log,preRun:[()=>{options.FS.mkdir('/firmware');options.FS.writeFile('/firmware/qemu_micro_flash.bin',micro);options.FS.writeFile('/firmware/qemu_spi_flash.bin',spi);}],locateFile:p=>base+p,onAbort:()=>status('engine_failed')};
  if(steady)options.wasmBinary=await steadyDisplayBinary(await bytes(base+'qemu-system-arm.wasm'));
  qemu=await factory(options);attach();setInterval(frame,30);setInterval(persist,30000);
 }catch(e){status('engine_failed');}
}
setInterval(commands,250);boot();
