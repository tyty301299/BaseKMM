const ShadowFlow = (()=>{
 const initial=(platform='ios',lockPlatform=false)=>({screen:'home',platform,lockPlatform,theme:'dark',glass:true,history:[],state:{event:'Đi làm',step:1,filter:'all',records:[],historyFilter:'all',historyPage:0,cameraFacing:'front',compare:false}});
 const valid=new Set(ShadowDesign.screens.map(s=>s.id));
 function transition(old,action,value){
  const v={...old,state:{...old.state},history:[...old.history]};
  const go=screen=>{if(screen!==v.screen){v.history.push(v.screen);v.screen=screen;}return v;};
  if(action==='reset'){const fresh=initial(old.platform,old.lockPlatform);fresh.state={...fresh.state,records:old.state.records||[],names:old.state.names||{},steps:old.state.steps||{},addedSample:Boolean(old.state.addedSample)};return fresh;}
  if(action==='back'){v.screen=v.history.pop()||'home';return v;}
  if(action==='toggle-platform'){if(v.lockPlatform)return v;v.platform=v.platform==='ios'?'android':'ios';return v;}
  if(action==='toggle-theme'){v.theme=v.theme==='dark'?'light':'dark';return v;}
  if(action==='toggle-glass'){v.glass=!v.glass;return v;}
  if(action==='select-photo'){v.state.addedSample=true;v.state.item='blouse';v.state.itemName='';return go('item');}
  if(action==='start-tryon')return go(v.state.demoCameraAllowed?'tryon':'tryon-permission');
  if(action==='allow-camera'){v.state.demoCameraAllowed=true;return go('tryon');}
  if(action==='camera-flip'){v.state.cameraFacing=v.state.cameraFacing==='front'?'back':'front';return v;}
  if(action==='compare'){v.state.compare=!v.state.compare;return v;}
  if(action.startsWith('camera-outfit:')){v.state.outfit=action.slice(14)==='blouse'?'blouse':'coat';v.state.compare=false;return v;}
  if(action==='take-photo'){v.state.capture={preset:v.state.outfit||'coat',event:v.state.event||'Đi làm'};v.state.savedCaptureId=null;return go('capture');}
  if(action==='retake'){v.state.capture=null;return go('tryon');}
  if(action==='save-capture'||action==='save-outfit'||action==='save-skill'){
   if(!value||!value.id)return v;
   if(action==='save-capture'&&!v.state.capture)return v;
   const duplicate=action==='save-capture'?v.state.savedCaptureId:action==='save-outfit'?v.state.savedOutfitId:v.state.savedSkillId;
   if(duplicate&&(v.state.records||[]).some(r=>r.id===duplicate)){v.state.selectedRecordId=duplicate;return go('history-detail');}
   if((v.state.records||[]).length>=40)throw new Error('Đã đủ 40 mục demo. Xóa bớt trong Lịch sử để lưu thêm.');
   v.state.records=[value,...(v.state.records||[])];v.state.selectedRecordId=value.id;
   if(action==='save-capture')v.state.savedCaptureId=value.id;
   if(action==='save-outfit')v.state.savedOutfitId=value.id;
   if(action==='save-skill'){v.state.savedSkillId=value.id;return go('saved');}
   return go('history-detail');
  }
  if(action.startsWith('history-filter:')){v.state.historyFilter=action.slice(15);v.state.historyPage=0;return v;}
  if(action==='history-next'){v.state.historyPage=(v.state.historyPage||0)+1;return v;}
  if(action==='history-prev'){v.state.historyPage=Math.max(0,(v.state.historyPage||0)-1);return v;}
  if(action.startsWith('history-open:')){v.state.selectedRecordId=action.slice(13);return go('history-detail');}
  if(action==='delete-record'){v.state.records=(v.state.records||[]).filter(r=>r.id!==v.state.selectedRecordId);v.state.selectedRecordId=null;v.state.historyPage=0;return go('history');}
  if(action==='clear-local'){v.state.records=[];v.state.names={};v.state.steps={};v.state.addedSample=false;v.state.selectedRecordId=null;v.state.localError=null;return go('history');}
  if(action==='use-saved'){
   const record=(v.state.records||[]).find(r=>r.id===v.state.selectedRecordId);if(!record)return go('history');
   if(record.kind==='skill'){v.state.steps={...record.steps};v.state.step=1;return go('guide');}
   v.state.outfit=record.preset;v.state.event=record.event;v.state.capture=null;v.state.savedCaptureId=null;return go('tryon');
  }
  if(action==='next-step'){if(v.state.step===5){v.state.step=1;return go('learn');}v.state.step++;return v;}
  if(action.startsWith('filter:')){v.state.filter=action.slice(7);return v;}
  if(action.startsWith('event:')){v.state.event=action.slice(6);return v;}
  if(action.startsWith('item:')){v.state.item=action.slice(5);v.state.itemName='';return go('item');}
  if(action==='outfit:blouse'){v.state.savedOutfitId=null;v.state.outfit='blouse';return go('outfit');}
  if(action==='outfit'){if(v.screen==='outfits'){v.state.outfit='coat';v.state.savedOutfitId=null;}return go('outfit');}
  if(action==='edit:notes'&&value!==undefined){v.state.notes=value;return v;}
  if(action==='edit:item'&&value!==undefined){v.state.itemName=value;v.state.names={...v.state.names,[v.state.item||'coat']:value};return v;}
  if(action==='edit:question'&&value!==undefined){v.state.question=value;return v;}
  if(action.startsWith('edit:step:')&&value!==undefined){v.state.steps={...v.state.steps,[action.slice(10)]:value};return v;}
  if(action==='recording')v.state.savedSkillId=null;
  if(valid.has(action))return go(action);
  throw new Error('Unknown action: '+action);
 }
 return {initial,transition};
})();
if(typeof module!=='undefined')module.exports=ShadowFlow;
