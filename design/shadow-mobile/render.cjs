const fs=require('fs'),path=require('path');
const sharp=require('/opt/homebrew/lib/node_modules/appium/node_modules/sharp');
const d=require('./design.js'),assets=require('./assets.js');
const root=__dirname;
const boardIds=['outfit','tryon','capture','history','history-detail','learn'];
const samples=[{id:'demo-tryon',kind:'tryon',preset:'coat',title:'Xanh rêu, nhẹ tênh',event:'Đi làm',createdAt:1794286800000},{id:'demo-outfit',kind:'outfit',preset:'blouse',title:'Cam đào dịu nhẹ',event:'Đi chơi',createdAt:1794286500000},{id:'demo-skill',kind:'skill',title:'Lắp giá đỡ điện thoại',createdAt:1794286200000,steps:{}}];
const sampleState={records:samples,selectedRecordId:'demo-tryon',capture:{preset:'coat',event:'Đi làm'},event:'Đi làm'};
(async()=>{
 for(const platform of ['ios','android']){
  for(const screen of d.screens){
   const r=d.render(screen.id,{platform,assets,state:sampleState});
   fs.writeFileSync(path.join(root,'screens',`${platform}-${screen.id}.svg`),r.svg);
  }
  const comps=[];const width=1140,height=1920;
  let labels=`<svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg"><style>text{font-family:Arial,sans-serif}</style><text x="42" y="61" fill="#ffd23f" font-size="15" letter-spacing="3">SHADOW / DESIGN LAB</text><text x="42" y="112" fill="#f4f5eb" font-size="36" font-weight="bold">${platform==='ios'?'iOS · Liquid Glass concept':'Android · adaptive glass concept'}</text><text x="42" y="146" fill="#adb8a4" font-size="15">AI Outfits + Học &amp; làm theo · bản demo minh họa · 6 màn hình chính / 27 màn hình</text>`;
  for(let i=0;i<boardIds.length;i++){
   const r=d.render(boardIds[i],{platform,assets,state:sampleState});let x=42+(i%3)*366,y=223+Math.floor(i/3)*835;
   const buffer=await sharp(Buffer.from(r.svg)).resize({width:324}).png().toBuffer();
   comps.push({input:buffer,left:x,top:y});
   labels+=`<text x="${x}" y="${y-22}" fill="#adb8a4" font-size="14">${String(i+1).padStart(2,'0')} / ${d.escape(d.screens.find(s=>s.id===boardIds[i]).title)}</text>`;
  }
  labels+=`<text x="42" y="1882" fill="#adb8a4" font-size="13">Concept v2 · kính mô phỏng thị giác, chưa phải native renderer · ảnh mẫu, không phải kết quả AI thật</text></svg>`;
  comps.push({input:Buffer.from(labels),left:0,top:0});
  await sharp({create:{width,height,channels:3,background:'#161B12'}}).composite(comps).png({palette:true}).toFile(path.join(root,`overview-${platform}.png`));
 }
 for(const [id,platform,theme,glass] of [['home','ios','dark',true],['wardrobe','ios','dark',true],['guide','android','dark',true],['home','ios','light',true],['components','ios','dark',false],['tryon','ios','dark',true],['capture','ios','dark',true],['history','ios','dark',true],['tryon','android','dark',true],['history','android','light',true]]){
  const name=`${platform}-${id}-${theme}${glass?'':'-solid'}`;
  const r=d.render(id,{platform,theme,glass,assets,state:sampleState});
  fs.writeFileSync(path.join(root,'screens',name+'.svg'),r.svg);
  await sharp(Buffer.from(r.svg)).png({palette:true}).toFile(path.join(root,'screens',name+'.png'));
 }
 console.log('Rendered '+d.screens.length*2+' platform screens and updated camera/history boards.');
})().catch(e=>{console.error(e);process.exit(1)});
