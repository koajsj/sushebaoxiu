"""Bounded acceptance for the final enhancements, isolated DB only."""
from pathlib import Path
from urllib import request,error
import json,subprocess,time,struct,zlib,os,re,uuid
from urllib.parse import urlparse
from datetime import datetime,timedelta
from concurrent.futures import ThreadPoolExecutor
from check_isolation import verify_isolation
ROOT=Path(__file__).resolve().parents[2]
BASE=os.environ.get('BACKEND_URL','')
DB=os.environ.get('CHECK_DB_NAME','')
endpoint=urlparse(BASE)
if not (endpoint.scheme=='http' and endpoint.hostname=='127.0.0.1' and endpoint.port==18086 and endpoint.path in ('','/')):
 raise SystemExit('isolated release endpoint required before any requests')
if not re.fullmatch(r'campus_repair_deep_check_[a-z0-9_]+',DB):
 raise SystemExit('isolated release database required before any requests')
MYSQL=Path.home()/'.cache/campus-repair/tools/mysql-8.4.9-macos15-arm64/bin/mysql'
def sql(s):
 return subprocess.check_output([str(MYSQL),'--defaults-extra-file='+str(ROOT/'.runtime/mysql-root.cnf'),'--batch','--skip-column-names',DB,'-e',s],text=True,timeout=5).strip()
def call(path,t=None,method='GET',body=None):
 h={'Content-Type':'application/json'}
 if t:h['Authorization']='Bearer '+t
 r=request.Request(BASE+path,json.dumps(body).encode() if body is not None else None,h,method=method)
 try:
  with request.urlopen(r,timeout=5) as v:return v.status,json.load(v)
 except error.HTTPError as v:return v.code,json.load(v)
def data(path,t=None,method='GET',body=None):
 c,v=call(path,t,method,body);assert c==200,(path,c,v);return v['data']
def login(n,password='123456'):return data('/api/auth/login',method='POST',body={'username':n,'password':password})['token']
def check(ok,label):
 assert ok,label
 print('PASS',label,flush=True)
def upload(t):
 def chunk(kind,content):return struct.pack('>I',len(content))+kind+content+struct.pack('>I',zlib.crc32(kind+content))
 png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b'\x00\x5a\x96\xdc'))+chunk(b'IEND',b'')
 boundary='campus-review-image-boundary'
 body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="review.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+png+f'\r\n--{boundary}--\r\n'.encode())
 r=request.Request(BASE+'/api/images',body,{'Authorization':'Bearer '+t,'Content-Type':'multipart/form-data; boundary='+boundary},method='POST')
 with request.urlopen(r,timeout=5) as v:return json.load(v)['data']['url']
def image_status(path,t):
 try:
  with request.urlopen(request.Request(BASE+path,headers={'Authorization':'Bearer '+t}),timeout=5) as v:return v.status
 except error.HTTPError as v:return v.code
def run():
 verify_isolation(sql,BASE,DB)
 a,s,wa,wb=[login(n) for n in ('admin001','student001','worker001','worker002')]
 other_name='check_s_'+uuid.uuid4().hex[:12]
 data('/api/admin/manage/users',a,'POST',{'role':'STUDENT','username':other_name,'password':'CheckStudent1!','realName':'隔离验收学生','studentNo':other_name,'college':'验收学院','className':'验收班'})
 other=login(other_name,'CheckStudent1!')
 c=data('/api/catalog',s)
 payload={'typeId':c['types'][0]['id'],'title':'异常流程验收','description':'隔离数据库业务增强验收','buildingId':c['buildings'][0]['id'],'roomNo':'501','priority':'HIGH','requestKey':str(uuid.uuid4())}
 oid=data('/api/student/orders',s,'POST',payload)['id']
 p=lambda role,action:f'/api/{role}/orders/{oid}/{action}'
 detail=lambda t=s:data(f'/api/orders/{oid}',t)
 check(call(p('admin','reject'),a,'PUT',{'reason':' '})[0]==400,'reject reason required')
 data(p('admin','reject'),a,'PUT',{'reason':'请补充现场描述'})
 check(detail()['order']['status']=='REJECTED','audit rejection persisted')
 check(call(p('student','resubmit'),other,'PUT',payload)[0]==404,'other student cannot edit')
 payload['description']='补充完整现场描述'
 data(p('student','resubmit'),s,'PUT',payload)
 check(detail()['order']['id']==oid and detail()['order']['status']=='WAIT_AUDIT','same id resubmitted')
 check(call(p('student','resubmit'),s,'PUT',payload)[0]==409,'duplicate resubmit rejected')
 data(p('admin','audit'),a,'PUT')
 print('Flow A passed',flush=True)
 workers=data('/api/admin/workers',a)
 aid=next(w['id'] for w in workers if w['username']=='worker001')
 bid=next(w['id'] for w in workers if w['username']=='worker002')
 data(p('admin','assign'),a,'PUT',{'workerId':aid})
 data(f'/api/orders/{oid}/messages',s,'POST',{'content':'发给原维修员','expectedWorkerId':aid})
 check(call(p('worker','reject'),wb,'PUT',{'reason':'无关维修员'})[0]==404,'other worker cannot refuse')
 data(p('worker','reject'),wa,'PUT',{'reason':'当前无法到场'})
 check(detail()['order']['workerId'] is None and detail()['order']['status']=='WAIT_ASSIGN','refusal releases worker')
 check(call(p('worker','reject'),wa,'PUT',{'reason':'重复拒单'})[0]==404,'repeated refusal creates no event')
 recs=data(f'/api/admin/dispatch/recommend/{oid}',a,'POST')
 target=next(r for r in recs if r['workerId']==bid)
 data('/api/admin/dispatch',a,'POST',{'orderId':oid,'workerId':bid,'recommendationId':target['recommendationId']})
 check(call(p('worker','start'),wb,'PUT')[0]==409,'smart assignment requires explicit response')
 data(p('worker','accept'),wb,'PUT')
 check(detail()['order']['acceptedTime'] is not None,'new worker accepts smart assignment')
 history=detail()['dispatchHistory']
 check(len(history)==2 and history[0]['decision']=='REJECTED' and history[0]['rejectReason']=='当前无法到场' and history[1]['totalScore'] is not None,'manual/smart history and scores retained')
 check(call(f'/api/orders/{oid}/messages',wa)[0]==404,'replaced worker cannot read chat')
 check(call(f'/api/orders/{oid}/messages',wa,'POST',{'content':'旧负责人消息'})[0]==404,'replaced worker cannot send chat')
 check(data(f'/api/orders/{oid}/messages',wb)==[],'new worker cannot read predecessor conversation')
 check(call(f'/api/orders/{oid}/messages',s,'POST',{'content':'旧草稿','expectedWorkerId':aid})[0]==409,'stale recipient draft rejected')
 data(f'/api/orders/{oid}/messages',s,'POST',{'content':'发给现任维修员','expectedWorkerId':bid})
 check(len(data(f'/api/orders/{oid}/messages',wb))==1,'new worker receives own conversation')
 print('Flow B passed',flush=True)
 def proposal():
  now=datetime.now().replace(microsecond=0)+timedelta(days=2)
  return {'start':now.isoformat(),'end':(now+timedelta(hours=1)).isoformat(),'version':detail()['order']['appointmentVersion']}
 ap=proposal()
 data(p('worker','appointment'),wb,'PUT',ap)
 version=detail()['order']['appointmentVersion']
 check(call(p('student','appointment'),other,'PUT',{'version':version,'accepted':True})[0]==404,'appointment belongs to order student')
 data(p('student','appointment'),s,'PUT',{'version':version,'accepted':True})
 check(detail()['order']['appointmentStatus']=='ACCEPTED','appointment accepted')
 check(call(p('student','appointment'),s,'PUT',{'version':version,'accepted':True})[0]==409,'duplicate appointment reply rejected')
 check(call(p('worker','appointment'),wb,'PUT',ap)[0]==409,'stale proposal version rejected')
 ap=proposal();data(p('worker','appointment'),wb,'PUT',ap)
 version=detail()['order']['appointmentVersion']
 check(call(p('student','appointment'),s,'PUT',{'version':version,'accepted':False,'reason':' '})[0]==400,'appointment rejection reason required')
 data(p('student','appointment'),s,'PUT',{'version':version,'accepted':False,'reason':'下午有课'})
 check(detail()['order']['appointmentReason']=='下午有课','appointment rejection persisted')
 print('Flow D passed',flush=True)
 data(p('worker','start'),wb,'PUT')
 record={'orderId':oid,'content':'首次处理，仍需验收','imageUrl':'','requestKey':str(uuid.uuid4())}
 data('/api/worker/repair-record',wb,'POST',record);data('/api/worker/repair-record',wb,'POST',record)
 check(len(detail()['records'])==1,'blank-image retry does not duplicate repair record')
 data(p('worker','finish'),wb,'PUT')
 first=detail()['records'][0]
 check(call(p('student','acceptance-fail'),other,'PUT',{'reason':'越权'})[0]==404,'other student cannot fail acceptance')
 with ThreadPoolExecutor(max_workers=2) as pool:
  replies=list(pool.map(lambda _:call(p('student','acceptance-fail'),s,'PUT',{'reason':'仍然存在故障'})[0],range(2)))
 check(sorted(replies)==[200,409],'concurrent acceptance failure creates one rework')
 data(p('admin','rework'),a,'PUT',{'mode':'ORIGINAL'})
 check(detail()['order']['repairRound']==2 and detail()['order']['appointmentStatus']=='NONE','original-worker rework resets coordination')
 data(p('worker','start'),wb,'PUT')
 check(call(p('worker','finish'),wb,'PUT')[0]==409,'previous round record cannot complete new round')
 data('/api/worker/repair-record',wb,'POST',{'orderId':oid,'content':'第二次维修，完成修复','requestKey':str(uuid.uuid4())})
 data(p('worker','finish'),wb,'PUT')
 check(detail()['records'][0]==first,'first repair round never overwritten')
 data(p('student','confirm'),s,'PUT')
 data('/api/student/evaluation',s,'POST',{'orderId':oid,'score':5,'content':'返工后已解决'})
 check(detail()['order']['status']=='COMMENTED' and [r['roundNo'] for r in detail()['records']]==[1,2],'two repair rounds and final evaluation retained')
 check(call(p('student','acceptance-fail'),s,'PUT',{'reason':'已评价'})[0]==409,'commented order cannot rework')
 check([e['action'] for e in detail()['timeline']].count('ACCEPTANCE_FAIL')==1,'single actual acceptance-failure event')
 print('Flow C passed',flush=True)
 # A second order covers admin redispatch rework and both SLA types.
 payload['requestKey']=str(uuid.uuid4())
 oid=data('/api/student/orders',s,'POST',payload)['id']
 data(p('admin','audit'),a,'PUT');data(p('admin','assign'),a,'PUT',{'workerId':aid})
 def wait_overdue(kind):
  limit=time.monotonic()+75
  while time.monotonic()<limit:
   if detail()['order']['overdueType']==kind:return
   time.sleep(.15)
  raise AssertionError('SLA scheduler did not flag '+kind)
 sql(f"UPDATE repair_order SET response_due_time=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id={oid}")
 wait_overdue('RESPONSE')
 check(detail()['order']['status']=='WAIT_ASSIGN','response SLA does not alter main state')
 check(any(o['id']==oid for o in data('/api/admin/orders?overdue=true',a)['records']),'admin overdue filter')
 time.sleep(1.2)
 check(int(sql(f"SELECT COUNT(*) FROM order_event WHERE order_id={oid} AND action='SLA_RESPONSE'"))==1,'repeated response scan is idempotent')
 notices=int(sql(f"SELECT COUNT(*) FROM notification WHERE content LIKE '工单 #{oid} · 系统自动检测：接单%'"))
 check(notices==int(sql("SELECT COUNT(*) FROM `user` WHERE role='ADMIN' AND status=1")),'one timeout notification per administrator')
 data(p('worker','accept'),wa,'PUT');data(p('worker','start'),wa,'PUT')
 sql(f"UPDATE repair_order SET repair_due_time=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id={oid}")
 wait_overdue('REPAIR');time.sleep(1.2)
 check(int(sql(f"SELECT COUNT(*) FROM order_event WHERE order_id={oid} AND action='SLA_REPAIR'"))==1,'repair timeout is idempotent')
 image=upload(wa)
 check(image_status(image,wa)==200 and image_status(image,s)==404,'unbound image preview is uploader-only')
 data('/api/worker/repair-record',wa,'POST',{'orderId':oid,'content':'第一轮修复','imageUrl':image,'requestKey':str(uuid.uuid4())})
 check(image_status(image,s)==200 and image_status(image,a)==200,'bound image available to student and administrator')
 data(p('worker','finish'),wa,'PUT');data(p('student','acceptance-fail'),s,'PUT',{'reason':'仍需换人处理'})
 data(p('admin','rework'),a,'PUT',{'mode':'REDISPATCH'})
 check(detail()['order']['workerId'] is None and detail()['order']['status']=='WAIT_ASSIGN','rework redispatch releases prior worker')
 data(p('admin','assign'),a,'PUT',{'workerId':bid});data(p('worker','accept'),wb,'PUT');data(p('worker','start'),wb,'PUT')
 check(image_status(image,wa)==404 and image_status(image,wb)==200,'replaced uploader loses bound-image access; current worker retains access')
 data('/api/worker/repair-record',wb,'POST',{'orderId':oid,'content':'更换人员完成第二轮','requestKey':str(uuid.uuid4())})
 data(p('worker','finish'),wb,'PUT');data(p('student','confirm'),s,'PUT')
 check([r['workerId'] for r in detail()['records']]==[aid,bid],'rework redispatch retains both workers records')
 overview=data('/api/admin/statistics/overview',a)
 check(overview['totalCount']==int(sql('SELECT COUNT(*) FROM repair_order')),'dashboard total matches DB')
 check(overview['todayCount']==int(sql('SELECT COUNT(*) FROM repair_order WHERE create_time>=CURDATE() AND create_time<DATE_ADD(CURDATE(),INTERVAL 1 DAY)')),'dashboard today matches DB')
 check(overview['overdueCount']==int(sql('SELECT COUNT(*) FROM repair_order WHERE overdue_type IS NOT NULL')),'dashboard overdue matches DB')
 check(overview['reworkCount']==int(sql("SELECT COUNT(*) FROM repair_order WHERE repair_round>1 OR status='REWORK_PENDING'")),'dashboard rework matches DB')
 check(overview['activeCount']==int(sql("SELECT COUNT(*) FROM repair_order WHERE (status='WAIT_ASSIGN' AND worker_id IS NOT NULL) OR status IN('ASSIGNED','PROCESSING','WAIT_CONFIRM','REWORK_PENDING')")),'dashboard active state count remains accurate')
 for role,token,scope,worker_id in [('student',s,"student_id=(SELECT st.id FROM student st JOIN `user` u ON u.id=st.user_id WHERE u.username='student001')",None),('worker',wb,f'worker_id={bid}',bid)]:
  summary=data(f'/api/{role}/summary',token)
  clauses={'total':'TRUE','pending':"status IN('WAIT_AUDIT','WAIT_ASSIGN','ASSIGNED')",'active':"status IN('PROCESSING','WAIT_CONFIRM','REWORK_PENDING')",'completed':"status IN('FINISHED','COMMENTED')",'today':'create_time>=CURDATE()'}
  for key,clause in clauses.items():
   if key=='today' and worker_id is not None:
    clause="EXISTS(SELECT 1 FROM order_event e WHERE e.order_id=repair_order.id AND e.action='ASSIGN' AND e.create_time>=CURDATE())"
   check(summary[key]==int(sql(f'SELECT COUNT(*) FROM repair_order WHERE {scope} AND {clause}')),f'{role} aggregated summary {key} matches DB')
 check(bool(data('/api/map/orders',a)['records']) and bool(data('/api/map/workers',a)),'map APIs preserved')
 check(call('/api/admin/statistics/overview',s)[0]==403 and call('/api/admin/statistics/overview')[0]==401,'role and anonymous protections preserved')
 check(call('/api/auth/login',method='POST',body={'username':'student001','password':'wrong'})[0]==401,'wrong password denied')
 notifications=data('/api/notifications',s)
 target=notifications['records'][0]['id'];data(f'/api/notifications/{target}/read',s,'PUT');data(f'/api/notifications/{target}/read',s,'PUT')
 check(int(sql(f'SELECT read_status FROM notification WHERE id={target}'))==1,'notification read remains idempotent')
 print('All enhancement acceptance checks passed',flush=True)
if __name__=='__main__':run()
