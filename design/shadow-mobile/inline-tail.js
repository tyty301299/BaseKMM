const appearance={theme:demo.theme,glass:demo.glass};
if(globalThis.Tweak){const tweak=new Tweak({container:previewRoot,onChange:()=>{demo.theme=appearance.theme;demo.glass=appearance.glass;draw();}});tweak.addSelect(appearance,'theme',{label:'Giao diện',options:[{label:'Tối',value:'dark'},{label:'Sáng',value:'light'}]});tweak.addToggle(appearance,'glass',{label:'Hiệu ứng kính'});}
