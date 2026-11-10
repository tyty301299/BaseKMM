const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const sharp=require('/opt/homebrew/lib/node_modules/appium/node_modules/sharp');
const {createCanvas}=require('/opt/homebrew/lib/node_modules/@mermaid-js/mermaid-cli/node_modules/@napi-rs/canvas');
const d=require('./design.js'),assets=require('./assets.js');
const context={module:{exports:{}},ShadowDesign:d};vm.runInNewContext(fs.readFileSync(__dirname+'/controller.js','utf8'),context);const f=context.module.exports;
let checked=0,warnings=[];
const decode=t=>t.replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/&apos;/g,"'");
(async()=>{
const ctx=createCanvas(1,1).getContext('2d');
for(const platform of ['ios','android'])for(const theme of ['dark','light'])for(const glass of [true,false])for(const screen of d.screens){
 const r=d.render(screen.id,{platform,theme,glass,assets});
 await sharp(Buffer.from(r.svg)).resize({width:96}).png().toBuffer();
 for(const h of r.hits){
  assert(h.x>=0&&h.y>=0&&h.x+h.w<=r.width+.01&&h.y+h.h<=r.height+.01,`${screen.id}: hit outside artboard`);
  if(!h.action.startsWith('edit:')&&!['show-connection','confirm-clear','confirm-delete','export-record'].includes(h.action))f.transition({...f.initial(),screen:screen.id},h.action);
 }
 for(const match of r.svg.matchAll(/<text ([^>]+)>([^<]*)<\/text>/g)){
  const attrs=Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(m=>[m[1],m[2]]));
  ctx.font=`${+(attrs['font-weight']||400)>=600?'bold':'normal'} ${attrs['font-size']}px Arial`;
  const width=ctx.measureText(decode(match[2])).width+(+(attrs['letter-spacing']||0))*Math.max(0,decode(match[2]).length-1),x=+attrs.x;
  const end=attrs['text-anchor']==='middle'?x+width/2:attrs['text-anchor']==='end'?x:x+width;
  if(end>r.width-12)warnings.push(`${platform}/${screen.id}: ${decode(match[2])}`);
 }
 checked++;
}
let v=f.initial();for(const action of ['wardrobe','add','permission','select-photo','occasion','event:Đi chơi','edit:notes']){
 v=f.transition(v,action,action==='edit:notes'?'Thoải mái':undefined);
}assert.equal(v.state.event,'Đi chơi');assert.equal(v.state.notes,'Thoải mái');
for(const action of ['error','generating','outfits','outfit:blouse','tryon','back'])v=f.transition(v,action);
assert.equal(v.screen,'outfit');assert.equal(v.state.outfit,'blouse');assert.equal(v.state.event,'Đi chơi');
v=f.initial();for(const action of ['learn','teach','recording','compiling','review'])v=f.transition(v,action);
v=f.transition(v,'edit:step:0','Đặt đế trên bàn');assert.equal(v.state.steps[0],'Đặt đế trên bàn');
for(const action of ['saved','guide','next-step','ask','back'])v=f.transition(v,action);
assert.equal(v.screen,'guide');assert.equal(v.state.step,2);
for(let i=0;i<4;i++)v=f.transition(v,'next-step');assert.equal(v.screen,'learn');
v=f.transition(v,'toggle-platform');v=f.transition(v,'toggle-theme');v=f.transition(v,'toggle-glass');assert.equal(v.platform,'android');assert.equal(v.theme,'light');assert.equal(v.glass,false);
console.log(`PASS: ${checked} rendered variants, all hotspots resolve, 2 main flows, recovery/back/edit/settings.`);
console.log('Text-width warnings: '+JSON.stringify([...new Set(warnings)]));
if(warnings.length)process.exitCode=1;
})().catch(e=>{console.error(e);process.exit(1)});
