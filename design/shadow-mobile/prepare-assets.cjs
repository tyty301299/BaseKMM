const fs = require('fs');
const path = require('path');
const sharp = require('/opt/homebrew/lib/node_modules/appium/node_modules/sharp');
const root=__dirname;
const source=path.resolve(root,'../../..','shadown-fourward/backend/wardrobe_seed/images');
(async()=>{
 const map={coat:'demo-ao-khoac-xanh-reu',blouse:'demo-ao-blouse-cam-dao',polo:'demo-ao-polo-hong',jeans:'demo-quan-jean-xanh',pants:'demo-quan-au-den',shoes:'demo-giay-sneaker-trang'};
 const assets={};
 for(const [key,name] of Object.entries(map)) {
  const bytes=await sharp(path.join(source,name+'.jpg')).resize({width:440,withoutEnlargement:true}).jpeg({quality:82}).toBuffer();
  assets[key]='data:image/jpeg;base64,'+bytes.toString('base64');
 }
 fs.writeFileSync(path.join(root,'assets.js'),'/* Demo assets: see ASSETS.md. */\nconst SHADOW_ASSETS = '+JSON.stringify(assets)+';\nif(typeof module!=="undefined")module.exports=SHADOW_ASSETS;\n');
 console.log('Prepared '+Object.keys(assets).length+' local demo images');
})().catch(e=>{console.error(e);process.exit(1)});
