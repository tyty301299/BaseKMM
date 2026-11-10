/* Stores only validated demo metadata. No camera images or arbitrary HTML/SVG are accepted. */
const ShadowLocal=(()=>{
 const key='shadow.prototype.local.v1';
 const kinds=['tryon','outfit','skill'];
 const cleanText=(v,n)=>typeof v==='string'?v.slice(0,n):'';
 function cleanState(value){
  if(!value||value.version!==1||!Array.isArray(value.records))throw new Error('Dữ liệu local chưa đọc được. Vào Tùy chọn để xóa dữ liệu demo rồi thử lại.');
  const seen=new Set();
  const records=value.records.slice(0,40).filter(r=>r&&typeof r.id==='string'&&/^[a-z0-9-]{1,80}$/i.test(r.id)&&!seen.has(r.id)&&seen.add(r.id)&&kinds.includes(r.kind)&&Number.isFinite(r.createdAt)&&r.createdAt>0).map(r=>({id:r.id,kind:r.kind,createdAt:r.createdAt,title:cleanText(r.title,40),preset:r.preset==='blouse'?'blouse':'coat',event:cleanText(r.event,30),steps:cleanSteps(r.steps)}));
  const names={};for(const k of ['coat','blouse','polo','jeans','pants','shoes'])if(value.names&&typeof value.names[k]==='string')names[k]=cleanText(value.names[k],27);
  return {records,names,steps:cleanSteps(value.steps),addedSample:value.addedSample===true};
 }
 function cleanSteps(v){const result={};for(let i=0;i<5;i++)if(v&&typeof v[i]==='string')result[i]=cleanText(v[i],27);return result;}
 function create(storage){
  let blocked=false;
  function load(){try{const raw=storage.getItem(key);return {data:raw?cleanState(JSON.parse(raw)):{records:[],names:{},steps:{},addedSample:false},error:null};}catch(e){blocked=true;return {data:{records:[],names:{},steps:{},addedSample:false},error:'Không đọc được dữ liệu local. Trình duyệt có thể đang chặn lưu trữ hoặc dữ liệu bị lỗi.'};}}
  function save(state){if(blocked)throw new Error('Local chưa sẵn sàng. Vào Tùy chọn để xóa dữ liệu demo bị lỗi hoặc kiểm tra quyền lưu trữ.');try{storage.setItem(key,JSON.stringify({version:1,records:state.records||[],names:state.names||{},steps:state.steps||{},addedSample:Boolean(state.addedSample)}));}catch(e){throw new Error('Chưa lưu được trên máy. Bộ nhớ có thể đã đầy hoặc trình duyệt đang chặn local storage.');}}
  function clear(){try{storage.removeItem(key);blocked=false;}catch(e){throw new Error('Trình duyệt không cho xóa dữ liệu local. Hãy kiểm tra quyền lưu trữ.');}}
  function retry(){blocked=false;return load();}
  return {load,save,clear,retry};
 }
 function record(kind,state){
  const random=globalThis.crypto?.randomUUID?.()||Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,10);
  const preset=(kind==='tryon'?state.capture?.preset:null)||state.outfit||'coat';
  return {id:random,createdAt:Date.now(),kind,preset,event:(kind==='tryon'?state.capture?.event:null)||state.event||'Đi làm',title:kind==='skill'?'Lắp giá đỡ điện thoại':preset==='blouse'?'Cam đào dịu nhẹ':'Xanh rêu, nhẹ tênh',steps:{...state.steps}};
 }
 return {create,record,cleanState,key};
})();
if(typeof module!=='undefined')module.exports=ShadowLocal;
