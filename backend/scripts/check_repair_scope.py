"""Extra real HTTP/SQL checks. Requires the isolated Phase3 local acceptance DB."""
from pathlib import Path
import os
import subprocess
import time

DB=os.environ.get('CHECK_DB_NAME','campus_repair_phase3_check_20260930')
assert DB.startswith('campus_repair_phase3_check_') and DB.replace('_','').isalnum(), 'isolated DB required'
from urllib.parse import urlparse
endpoint=urlparse(os.environ.get('BACKEND_URL',''))
assert endpoint.scheme=='http' and endpoint.hostname=='127.0.0.1' and endpoint.port==18083 and endpoint.path in ('','/'), 'isolated server required before any HTTP mutation'
from check_repair import req, data, upload, tokens, oid, detail, png
ROOT=Path(__file__).resolve().parents[2]
MYSQL=Path.home()/'.cache/campus-repair/tools/mysql-8.4.9-macos15-arm64/bin/mysql'
def sql(command):
 return subprocess.check_output([str(MYSQL),'--defaults-extra-file='+str(ROOT/'.runtime/mysql-root.cnf'),'--batch','--skip-column-names',DB,'-e',command],text=True).strip()
count=0
def check(ok,label):
 global count
 assert ok,label
 count+=1
 print('PASS',label)
nonce=str(time.time_ns())
sname='scope_s_'+nonce;wname='scope_w_'+nonce
try:
 sql(f"INSERT INTO {DB}.user(username,password,real_name,role) SELECT '{sname}',password,'测试其他学生','STUDENT' FROM {DB}.user WHERE username='student001'; INSERT INTO {DB}.user(username,password,real_name,role) SELECT '{wname}',password,'测试其他维修员','WORKER' FROM {DB}.user WHERE username='worker001'; INSERT INTO {DB}.student(user_id,student_no,college,class_name) SELECT id,'{nonce}','测试学院','测试班' FROM {DB}.user WHERE username='{sname}'; INSERT INTO {DB}.worker(user_id,skill_type,status) SELECT id,'测试',0 FROM {DB}.user WHERE username='{wname}';")
 st=req('/api/auth/login','POST',{'username':sname,'password':'123456'})[1]['data']['token']
 wt=req('/api/auth/login','POST',{'username':wname,'password':'123456'})[1]['data']['token']
 check(req(f'/api/orders/{oid}',token=st)[0]==404,'another student cannot read order')
 check(req(f'/api/student/orders/{oid}/confirm','PUT',token=st)[0]==404,'another student cannot confirm')
 check(req('/api/student/evaluation','POST',{'orderId':oid,'score':5},st)[0]==404,'another student cannot evaluate')
 check(req(f'/api/orders/{oid}',token=wt)[0]==404,'another worker cannot read order')
 check(req(f'/api/worker/orders/{oid}/accept','PUT',token=wt)[0]==404,'another worker cannot accept')
 check(data('/api/worker/orders',wt)['total']==0,'other worker list excludes all foreign tasks')
 url=detail['records'][0]['imageUrl']
 check(req(url,token=st)[0]==404 and req(url,token=wt)[0]==404,'other participants cannot read bound repair image')
 check(req(url,token=tokens['admin'])[0]==200,'administrator can read bound repair image')
 check(req('/api/images/../../backend.env',token=tokens['student'])[0] in (400,404),'image traversal denied')
 check(req('/api/student/orders?status=INVALID',token=tokens['student'])[0]==400,'invalid status filter rejected')
 check(req('/api/orders/0',token=tokens['student'])[0]==400,'nonpositive order id rejected')
 check(req(f'/api/orders/{oid}','PUT',{'status':'FINISHED'},tokens['student'])[0]==405,'no generic client status mutation endpoint')
 status,img=upload(st);check(status==200,'another student owns a separate upload');foreign=img['data']['url']
 baseline=sql(f'SELECT COUNT(*) FROM {DB}.repair_order')
 body={'typeId':1,'title':'权限验收','description':'只用于权限验收','buildingId':1,'roomNo':'301','priority':'NORMAL','imageUrl':foreign}
 check(req('/api/student/orders','POST',body,tokens['student'])[0]==400,'cannot attach another uploader photo')
 check(sql(f'SELECT COUNT(*) FROM {DB}.repair_order')==baseline,'bad attachment rolls back newly inserted order')
 body.pop('imageUrl');body['status']='COMMENTED';body['workerId']=1
 created=data('/api/student/orders',tokens['student'],'POST',body)
 check(created['status']=='WAIT_AUDIT' and created['workerId'] is None,'spoofed status/worker ignored by create DTO')
 new_id=created['id'];data(f'/api/admin/orders/{new_id}/audit',tokens['admin'],'PUT')
 wid=sql(f"SELECT w.id FROM {DB}.worker w JOIN {DB}.user u ON u.id=w.user_id WHERE u.username='{wname}'")
 check(req(f'/api/admin/orders/{new_id}/assign','PUT',{'workerId':int(wid)},tokens['admin'])[0]==409,'disabled worker cannot be assigned')
 check(data(f'/api/orders/{new_id}',tokens['student'])['order']['workerId'] is None,'failed assignment leaves order unchanged')
 check(upload(tokens['worker'], b'x'*(5*1024*1024+1))[0]==400,'multipart oversized upload rejected')
 for method,path in [('GET','/api/catalog'),('POST','/api/images'),('GET','/api/admin/workers'),('GET','/api/student/orders'),('GET','/api/worker/orders')]:
  check(req(path,method)[0]==401,'missing token rejected: '+path)
 # The successful imported loop has one row per evaluation and eight chronological events.
 check(sql(f'SELECT status FROM {DB}.repair_order WHERE id={oid}')=='COMMENTED','MySQL persists final status')
 check(sql(f'SELECT COUNT(*) FROM {DB}.evaluation WHERE order_id={oid}')=='1','MySQL has only one evaluation')
 check(sql(f'SELECT COUNT(*) FROM {DB}.order_event WHERE order_id={oid}')=='8','MySQL persists eight timeline events')
 check(sql(f'SELECT COUNT(*) FROM {DB}.repair_record WHERE order_id={oid} AND finish_time IS NOT NULL')=='1','MySQL persists completed record')
 check(sql(f"SELECT task_count FROM {DB}.worker WHERE user_id=(SELECT id FROM {DB}.user WHERE username='worker001')")==sql(f"SELECT COUNT(*) FROM {DB}.repair_order o JOIN {DB}.worker w ON o.worker_id=w.id JOIN {DB}.user u ON w.user_id=u.id WHERE u.username='worker001' AND o.status IN('WAIT_CONFIRM','FINISHED','COMMENTED')"),'worker completed counter equals completed orders')
 check(sql(f"SELECT score FROM {DB}.worker WHERE user_id=(SELECT id FROM {DB}.user WHERE username='worker001')")==sql(f"SELECT ROUND(AVG(e.score),2) FROM {DB}.evaluation e JOIN {DB}.repair_order o ON o.id=e.order_id JOIN {DB}.worker w ON o.worker_id=w.id JOIN {DB}.user u ON w.user_id=u.id WHERE u.username='worker001'"),'worker rating maintained transactionally')
 # Two different orders evaluated simultaneously must maintain one worker's mean.
 worker_id=int(sql(f"SELECT id FROM {DB}.worker WHERE user_id=(SELECT id FROM {DB}.user WHERE username='worker001')"))
 rating_ids=[]
 for index in range(2):
  row=data('/api/student/orders',tokens['student'],'POST',{'typeId':1,'title':'并发平均分验收','description':'用于不同订单并发评价验收','buildingId':1,'roomNo':'301','priority':'NORMAL'})
  rid=row['id'];rating_ids.append(rid)
  before_today=data('/api/worker/summary',tokens['worker'])['today']
  sql(f'UPDATE {DB}.repair_order SET create_time=DATE_SUB(NOW(),INTERVAL 2 DAY) WHERE id={rid}')
  data(f'/api/admin/orders/{rid}/audit',tokens['admin'],'PUT');data(f'/api/admin/orders/{rid}/assign',tokens['admin'],'PUT',{'workerId':worker_id})
  check(data('/api/worker/summary',tokens['worker'])['today']==before_today+1,'today tasks include older orders assigned today')
  data(f'/api/worker/orders/{rid}/accept',tokens['worker'],'PUT');data(f'/api/worker/orders/{rid}/start',tokens['worker'],'PUT')
  check(req('/api/worker/repair-record','POST',{'orderId':rid,'content':'尝试重复绑定','imageUrl':url},tokens['worker'])[0]==400,'cannot reuse already-bound image')
  data('/api/worker/repair-record',tokens['worker'],'POST',{'orderId':rid,'content':'维修完成'})
  data(f'/api/worker/orders/{rid}/finish',tokens['worker'],'PUT');data(f'/api/student/orders/{rid}/confirm',tokens['student'],'PUT')
 from concurrent.futures import ThreadPoolExecutor
 with ThreadPoolExecutor(max_workers=2) as pool:
  statuses=list(pool.map(lambda pair:req('/api/student/evaluation','POST',{'orderId':pair[0],'score':pair[1]},tokens['student'])[0],zip(rating_ids,[1,3])))
 check(statuses==[200,200],'different orders evaluated concurrently both succeed')
 check(sql(f'SELECT score FROM {DB}.worker WHERE id={worker_id}')==sql(f'SELECT ROUND(AVG(e.score),2) FROM {DB}.evaluation e JOIN {DB}.repair_order o ON e.order_id=o.id WHERE o.worker_id={worker_id}'),'concurrent different-order evaluations preserve exact average')
finally:
 # Only temporary extra accounts are cleaned. Acceptance orders remain in the isolated DB.
 image_ids=sql(f"SELECT i.id FROM {DB}.repair_image i JOIN {DB}.user u ON u.id=i.owner_id WHERE u.username='{sname}'").splitlines()
 sql(f"DELETE i FROM {DB}.repair_image i JOIN {DB}.user u ON u.id=i.owner_id WHERE u.username='{sname}'; DELETE s FROM {DB}.student s JOIN {DB}.user u ON u.id=s.user_id WHERE u.username='{sname}'; DELETE w FROM {DB}.worker w JOIN {DB}.user u ON u.id=w.user_id WHERE u.username='{wname}'; DELETE FROM {DB}.user WHERE username IN('{sname}','{wname}');")
 for image_id in image_ids:
  (ROOT/'.runtime/phase3-check-images'/image_id).unlink(missing_ok=True)
print('PASS',count,'additional ownership/SQL/upload assertions')
