/* Prototype UI. Only validated demo metadata is persisted; no real camera/AI calls. */
const previewRoot=document.getElementById('shadow-prototype-root')||document;
const fixedPlatform=['ios','android'].includes(window.SHADOW_PLATFORM)?window.SHADOW_PLATFORM:null;
let demo=ShadowFlow.initial(fixedPlatform||'ios',Boolean(fixedPlatform));
const local=ShadowLocal.create({getItem:key=>window.localStorage.getItem(key),setItem:(key,value)=>window.localStorage.setItem(key,value),removeItem:key=>window.localStorage.removeItem(key)});
const loaded=local.load();demo.state={...demo.state,...loaded.data,localError:loaded.error};
let editAction=null,confirmAction=null,toastTimer;
const $=id=>previewRoot.querySelector('[id="'+id+'"]');
const available=new Set(ShadowDesign.screens.map(s=>s.id));
const first=previewRoot.dataset?.start||location.hash.slice(1);if(available.has(first))demo.screen=first==='history-detail'?'history':first;
$('screen-list').innerHTML=ShadowDesign.screens.map(s=>`<button data-go="${s.id}"><span>${s.number}</span>${s.title}</button>`).join('');
$('screen-select').innerHTML=ShadowDesign.screens.map(s=>`<option value="${s.id}">${s.number} · ${s.title}</option>`).join('');
function showToast(text){$('toast').textContent=text;clearTimeout(toastTimer);toastTimer=setTimeout(()=>{$('toast').textContent='';},5000);}
function draw(){
 $('platform').hidden=Boolean(fixedPlatform);
 const r=ShadowDesign.render(demo.screen,{...demo,assets:SHADOW_ASSETS});
 $('device').innerHTML=r.svg;$('device').classList.toggle('android',demo.platform==='android');
 $('screen-list').querySelectorAll('button').forEach(b=>{b.classList.toggle('active',b.dataset.go===demo.screen);if(b.dataset.go===demo.screen)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');});
 $('screen-select').value=demo.screen;
 $('screen-label').textContent=ShadowDesign.screens.find(s=>s.id===demo.screen).title;
 $('platform').textContent=demo.platform==='ios'?'iOS 26+':'Android';$('platform').setAttribute('aria-pressed',String(demo.platform==='android'));
 $('theme').textContent=demo.theme==='dark'?'Giao diện tối':'Giao diện sáng';$('theme').setAttribute('aria-pressed',String(demo.theme==='light'));
 $('glass').textContent='Kính: '+(demo.glass?'bật':'tắt');$('glass').setAttribute('aria-pressed',String(demo.glass));
 $('platform-note').textContent=demo.platform==='ios'?'iOS: Liquid Glass ở lớp điều hướng trên iOS 26+. Bản cũ dùng Material; Reduce Transparency chuyển sang nền đặc.':'Android: điều hướng Material, vùng chạm 48dp. Hiệu ứng kính tương ứng, không phải Apple Liquid Glass.';
 if($('local-status'))$('local-status').textContent=demo.state.localError||'Local: '+(demo.state.records||[]).length+' mục đã lưu trên trình duyệt này.';
 try{history.replaceState(null,'','#'+demo.screen);}catch{}
}
const durableAction=action=>['save-capture','save-outfit','save-skill','delete-record','select-photo'].includes(action)||action==='edit:item'||action.startsWith('edit:step:');
function applyAction(action,value){
 try{
  const before=demo.screen;
  if(action==='clear-local')local.clear();
  let base=demo;
  if(durableAction(action)){const latest=local.load();if(latest.error)throw new Error(latest.error);base={...demo,state:{...demo.state,...latest.data}};}
  const next=ShadowFlow.transition(base,action,value);
  if(durableAction(action)){local.save(next.state);next.state.localError=null;}
  if(action==='clear-local')next.state.localError=null;
  demo=next;draw();
  if(before!==demo.screen)$('device').querySelector('a')?.focus({preventScroll:true});
  return true;
 }catch(error){
  demo.state.localError=error.message||'Không lưu được dữ liệu local.';
  if(action.startsWith('save-'))demo=ShadowFlow.transition(demo,'local-error');
  draw();showToast(demo.state.localError);return false;
 }
}
function confirm(action){
 confirmAction=action;editAction=null;
 $('edit-title').textContent=action==='clear-local'?'Xóa toàn bộ dữ liệu demo?':'Xóa mục này khỏi Lịch sử?';
 $('edit-label').hidden=true;$('edit-value').hidden=true;$('edit-value').required=false;
 $('edit-help').textContent=action==='clear-local'?'Các kết quả, bộ phối, nhãn và kỹ năng mẫu đã lưu sẽ bị xóa trên trình duyệt này. Không thể hoàn tác.':'Mục đã lưu sẽ bị xóa khỏi trình duyệt này. Các mục khác được giữ nguyên.';
 $('save-edit').textContent='Xóa';$('editor').showModal();$('cancel-edit').focus();
}
function exportRecord(){
 const record=(demo.state.records||[]).find(r=>r.id===demo.state.selectedRecordId);
 if(!record||record.kind==='skill')return;
 const art=ShadowDesign.lookSVG(record.preset);
 const label='<text x="172.5" y="435" text-anchor="middle" font-family="Arial" font-size="9" fill="#273323">SHADOW · hình nhân demo, không phải ảnh AI</text>';
 const svg=art.replace('</svg>',label+'</svg>');
 const url=URL.createObjectURL(new Blob([svg],{type:'image/svg+xml'}));
 const link=document.createElement('a');link.href=url;link.download='shadow-demo-'+record.id+'.svg';link.click();setTimeout(()=>URL.revokeObjectURL(url),30000);
 showToast('Đã tạo tệp ảnh SVG minh họa để tải xuống.');
}
function dispatch(action){
 if(action==='toggle-platform'&&fixedPlatform){showToast('Đây là prototype riêng cho '+(fixedPlatform==='ios'?'iOS':'Android')+'.');return;}
 if(action==='confirm-delete'){confirm('delete-record');return;}
 if(action==='confirm-clear'){confirm('clear-local');return;}
 if(action==='export-record'){exportRecord();return;}
 if(action.startsWith('edit:')){
  editAction=action;confirmAction=null;const kind=action.split(':')[1];
  $('edit-label').hidden=false;$('edit-value').hidden=false;$('edit-value').required=true;$('save-edit').textContent='Áp dụng';
  $('edit-help').textContent=['step','item'].includes(kind)?'Thông tin mẫu này được giữ trên trình duyệt.':'Chỉ dùng trong phiên demo; không gửi tới AI.';
  $('edit-title').textContent=kind==='step'?'Sửa bước mẫu':kind==='item'?'Tên trang phục':kind==='question'?'Câu hỏi cho SHADOW':'Mong muốn của bạn';
  $('edit-value').maxLength=(kind==='step'||kind==='item')?27:74;
  $('edit-value').value=kind==='notes'?(demo.state.notes||''):kind==='question'?(demo.state.question||''):kind==='item'?(demo.state.names?.[demo.state.item||'coat']||demo.state.itemName||''):(demo.state.steps?.[action.split(':')[2]]||'');
  $('editor').showModal();$('edit-value').focus();return;
 }
 if(action==='show-connection'){showToast('Demo chưa kết nối với máy chủ hoặc AI thật.');return;}
 const kind={'save-capture':'tryon','save-outfit':'outfit','save-skill':'skill'}[action];
 if(applyAction(action,kind?ShadowLocal.record(kind,demo.state):undefined)){
  if(kind)showToast('Đã lưu '+(kind==='skill'?'kỹ năng mẫu':kind==='outfit'?'bộ phối mẫu':'ảnh minh họa')+' trên trình duyệt này.');
  if(action==='select-photo')showToast('Đã giữ thông tin món mẫu local. Không truy cập ảnh thật.');
 }
}
previewRoot.addEventListener('click',e=>{const a=e.target.closest('[data-action]');if(a){e.preventDefault();dispatch(a.dataset.action);return;}const g=e.target.closest('[data-go]');if(g)dispatch(g.dataset.go);});
previewRoot.addEventListener('keydown',e=>{if(e.key===' '&&e.target.matches('[data-action]')){e.preventDefault();dispatch(e.target.dataset.action);}});
$('screen-select').addEventListener('change',e=>dispatch(e.target.value));
$('platform').onclick=()=>dispatch('toggle-platform');$('theme').onclick=()=>dispatch('toggle-theme');$('glass').onclick=()=>dispatch('toggle-glass');$('reset').onclick=()=>dispatch('reset');
$('cancel-edit').onclick=()=>{$('editor').close();confirmAction=null;};
$('editor').addEventListener('cancel',()=>{confirmAction=null;});
$('edit-form').onsubmit=e=>{
 e.preventDefault();
 if(confirmAction){const action=confirmAction;confirmAction=null;$('editor').close();if(applyAction(action))showToast(action==='clear-local'?'Đã xóa dữ liệu demo local.':'Đã xóa mục khỏi Lịch sử.');return;}
 const value=$('edit-value').value.trim();if(!value){$('edit-value').setCustomValidity('Nhập nội dung trước khi áp dụng.');$('edit-value').reportValidity();return;}
 $('edit-value').setCustomValidity('');$('editor').close();
 if(applyAction(editAction,value)){ $('device').querySelector('[data-action="'+editAction+'"]')?.focus({preventScroll:true});showToast('Đã cập nhật nội dung mẫu.');}
};
$('edit-value').oninput=()=>{$('edit-value').setCustomValidity('');};
window.addEventListener('storage',event=>{if(event.key===ShadowLocal.key||event.key===null){const latest=local.load();demo.state={...demo.state,...latest.data,localError:latest.error};draw();}});
draw();
if(loaded.error)showToast(loaded.error);
