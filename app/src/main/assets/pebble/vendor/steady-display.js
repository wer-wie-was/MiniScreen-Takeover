// Keep virtual Pebble backlight fading out of the Takeover framebuffer.
// This equal-length patch targets the exact bundled QEMU module only. It
// changes the display driver's scale (170 + 85 * brightness) to constant 255.
// No firmware, input, palette or watchface animation is modified.
const expected='41ec34518b370fb78bd16b24241ca49f7efa1fc60dc0f0cb17be151dd7251aed';
const edits=[
 [0xaee9e,[0x41,0xaa,0x01,0x21,0x01],[0x41,0xff,0x01,0x21,0x01]],
 [0xaeeb6,[0x43,0x00,0x00,0xaa,0x42],[0x43,0x00,0x00,0x00,0x00]],
 [0xaeebc,[0x43,0x00,0x00,0x2a,0x43],[0x43,0x00,0x00,0x7f,0x43]]
];
export async function steadyDisplayBinary(bytes){
 const hash=new Uint8Array(await crypto.subtle.digest('SHA-256',bytes));
 const hex=Array.from(hash,b=>b.toString(16).padStart(2,'0')).join('');
 if(hex!==expected)throw Error('Unexpected bundled display engine');
 for(const [offset,before,after] of edits){
  if(before.length!==after.length||before.some((b,i)=>bytes[offset+i]!==b))throw Error('Display patch guard failed');
 }
 for(const [offset,before,after] of edits)bytes.set(after,offset);
 return bytes;
}
