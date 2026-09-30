"""Phase4 real HTTP checks, deliberately limited to an isolated local database."""
from pathlib import Path
from urllib.parse import urlparse
import json, os, subprocess, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor
ROOT=Path(__file__).resolve().parents[2]
DB=os.environ.get('CHECK_DB_NAME','campus_repair_phase4_check_20260930')
BASE=os.environ.get('BACKEND_URL','')
u=urlparse(BASE)
assert DB.startswith('campus_repair_phase4_check_') and DB.replace('_','').isalnum(), 'isolated Phase4 database required'
assert u.scheme=='http' and u.hostname=='127.0.0.1' and u.port==18084 and u.path in ('','/'), 'isolated Phase4 endpoint required before mutation'
MYSQL=Path.home()/'.cache/campus-repair/tools/mysql-8.4.9-macos15-arm64/bin/mysql'
def sql(command):
 return subprocess.check_output([str(MYSQL),'--defaults-extra-file='+str(ROOT/'.runtime/mysql-root.cnf'),'--batch','--skip-column-names',DB,'-e',command],text=True).strip()
def req(path,token=None,method='GET',body=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 request=urllib.request.Request(BASE+path,json.dumps(body).encode() if body is not None else None,headers,method=method)
 try:
  with urllib.request.urlopen(request,timeout=15) as response:return response.status,json.load(response)
 except urllib.error.HTTPError as error:return error.code,json.load(error)
def data(path,token,method='GET',body=None):
 status,value=req(path,token,method,body);assert status==200,(path,status,value);return value['data']
count=0
def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS',label,flush=True)
# The --probe mode has no mutations: expected to fail before the new endpoints exist.
import sys
if '--probe' in sys.argv:
 status,response=req('/api/auth/login',method='POST',body={'username':'admin001','password':'123456'})
 assert status==200
 check(req('/api/map/buildings',response['data']['token'])[0]==200,'map endpoint exists')
 sys.exit(0)
tokens={role:data('/api/auth/login',None,'POST',{'username':role+'001','password':'123456'})['token'] for role in ['student','worker','admin']}
a=tokens['admin'];s=tokens['student'];w=tokens['worker']
for path in ['/api/map/orders','/api/map/workers','/api/map/buildings','/api/admin/dispatch/recommend/1']:
 check(req(path)[0]==401,'anonymous denied '+path)
 for role in ['student','worker']:check(req(path,tokens[role])[0]==403,'role denied '+role+' '+path)
check(req('/api/admin/dispatch',s,'POST',{'orderId':1,'workerId':1,'recommendationId':1})[0]==403,'student cannot dispatch')
catalog=data('/api/catalog',a);bid=catalog['buildings'][0]['id'];tid=catalog['types'][0]['id']
def create():
 return data('/api/student/orders',s,'POST',{'typeId':tid,'title':'Phase4智能派单验收','description':'灯具开关故障，仅隔离库验收','buildingId':bid,'roomNo':'301','priority':'NORMAL'})['id']
def recommend(order):return data('/api/admin/dispatch/recommend/'+str(order),a)
def confirm(order,row):return req('/api/admin/dispatch',a,'POST',{'orderId':order,'workerId':row['workerId'],'recommendationId':row['recommendationId']})
oid=create()
check(req('/api/admin/dispatch/recommend/'+str(oid),a)[0]==409,'unaudited order cannot be recommended')
data(f'/api/admin/orders/{oid}/audit',a,'PUT')
first=recommend(oid);check(len(first)>=3,'multiple real eligible candidates')
check(first[0]['workerName']=='电工示例人员','matching nearby worker A ranks first')
far=next(row for row in first if row['workerName']=='水暖示例人员')
check(first[0]['totalScore']>far['totalScore'],'A higher than B')
check(all(abs(row['totalScore']-(row['skillScore']*.4+row['distanceScore']*.3+row['loadScore']*.2+row['ratingScore']*.1))<.021 for row in first),'weighted scores calculated from real factors')
check(data(f'/api/orders/{oid}',s)['order']['status']=='WAIT_ASSIGN','recommend does not change status')
check(int(sql(f'SELECT COUNT(*) FROM dispatch_record WHERE order_id={oid}'))==len(first),'every candidate snapshot persisted')
second=recommend(oid);check(first[0]['recommendationId']!=second[0]['recommendationId'],'new recommendation creates new batch')
check(req('/api/admin/dispatch',a,'POST',{'orderId':oid,'workerId':far['workerId'],'recommendationId':first[0]['recommendationId']})[0]==400,'snapshot must match selected worker')
# Account disabled after the recommendation cannot be confirmed.
wid=first[0]['workerId'];sql(f'UPDATE worker SET status=0 WHERE id={wid}')
try:check(confirm(oid,first[0])[0]==409,'disabled worker rejected at confirmation')
finally:sql(f'UPDATE worker SET status=1 WHERE id={wid}')
# Stale input (static coordinate) and expiry cannot silently dispatch.
old=sql(f'SELECT longitude FROM worker WHERE id={wid}');sql(f'UPDATE worker SET longitude=longitude+.0000001 WHERE id={wid}')
try:check(confirm(oid,first[0])[0]==409,'changed underlying coordinate rejects snapshot')
finally:sql(f'UPDATE worker SET longitude={old} WHERE id={wid}')
sql(f"UPDATE dispatch_record SET create_time=DATE_SUB(NOW(),INTERVAL 11 MINUTE) WHERE id={first[0]['recommendationId']}")
check(confirm(oid,first[0])[0]==409,'expired snapshot rejected')
# Load must come from open orders, not completed task_count.
row=next(r for r in second if r['workerId']==1)
oldcount=sql('SELECT task_count FROM worker WHERE id=1');sql('UPDATE worker SET task_count=task_count+1000 WHERE id=1')
try:check(next(r for r in recommend(oid) if r['workerId']==1)['loadScore']==row['loadScore'],'completed counter is not active load')
finally:sql(f'UPDATE worker SET task_count={oldcount} WHERE id=1')
# Dispatch to worker001 to use its real login for the complete lifecycle.
row=next(r for r in recommend(oid) if r['workerId']==1)
with ThreadPoolExecutor(max_workers=2) as pool:statuses=list(pool.map(lambda _:confirm(oid,row)[0],range(2)))
check(sorted(statuses)==[200,409],'concurrent confirmation only succeeds once')
detail=data(f'/api/orders/{oid}',w)
check(detail['order']['status']=='ASSIGNED' and detail['order']['workerId']==1,'confirmed assignment persists ASSIGNED')
check(any(r['id']==oid for r in data('/api/worker/orders',w)['records']),'worker receives task')
check(sql(f'SELECT COUNT(*) FROM dispatch_record WHERE order_id={oid} AND confirmed=1')=='1','exactly one confirmed snapshot')
check(sum(e['action']=='ASSIGN' for e in detail['timeline'])==1,'exactly one assignment event')
check(req(f'/api/worker/orders/{oid}/accept',w,'PUT')[0]==409,'old accept cannot repeat smart assignment')
data(f'/api/worker/orders/{oid}/start',w,'PUT')
data('/api/worker/repair-record',w,'POST',{'orderId':oid,'content':'验收：更换灯具，检查正常'})
data(f'/api/worker/orders/{oid}/finish',w,'PUT')
data(f'/api/student/orders/{oid}/confirm',s,'PUT')
data('/api/student/evaluation',s,'POST',{'orderId':oid,'score':5,'content':'验收通过'})
check(data(f'/api/orders/{oid}',s)['order']['status']=='COMMENTED','smart dispatch completes original repair/evaluation loop')
# Original manual assignment still requires accept.
manual=create();data(f'/api/admin/orders/{manual}/audit',a,'PUT');data(f'/api/admin/orders/{manual}/assign',a,'PUT',{'workerId':1})
check(data(f'/api/orders/{manual}',w)['order']['status']=='WAIT_ASSIGN','manual dispatch behavior preserved')
data(f'/api/worker/orders/{manual}/accept',w,'PUT')
check(data(f'/api/orders/{manual}',w)['order']['status']=='ASSIGNED','manual worker accept preserved')
new=create();data(f'/api/admin/orders/{new}/audit',a,'PUT')
active=next(r for r in recommend(new) if r['workerId']==1)
check(active['activeTaskCount']>=1 and active['loadScore']<100,'open manual task counted as current load')
# Maps are sourced directly from persisted building/worker/order coordinates.
buildings=data('/api/map/buildings',a);check(len(buildings)==3 and all(b['longitude'] is not None for b in buildings),'buildings have seeded static coordinates')
orders=data('/api/map/orders',a);point=next(r for r in orders['records'] if r['id']==oid);building=next(b for b in buildings if b['id']==bid)
check(point['longitude']==building['longitude'] and point['latitude']==building['latitude'],'order location uses its building coordinates')
check(point['status']=='COMMENTED' and '301' in point['address'],'map order includes actual status and room')
check(all(r['status']=='ASSIGNED' for r in data('/api/map/orders?status=ASSIGNED',a)['records']),'map status filtering')
check(req('/api/map/orders?status=invalid',a)[0]==400,'invalid map status rejected')
workers=data('/api/map/workers',a);check(any(r['id']==1 and r['activeTaskCount']>=1 and r['status']=='BUSY' for r in workers),'worker map derives busy state from active orders')
check(all('password' not in json.dumps(row) and 'phone' not in row for row in workers),'map exposes only necessary user fields')
# Missing coordinates remain null, never substitute fabricated coordinates.
sql(f'UPDATE building SET longitude=NULL,latitude=NULL WHERE id={bid}')
try:
 check(next(r for r in data('/api/map/orders',a)['records'] if r['id']==oid)['longitude'] is None,'map missing coordinate stays absent')
 check(all(r['distanceScore']==0 and '坐标缺失' in r['reason'] for r in recommend(new)),'missing order coordinate explained in recommendations')
finally:sql(f"UPDATE building SET longitude={building['longitude']},latitude={building['latitude']} WHERE id={bid}")
check(confirm(oid,row)[0]==409,'finished order cannot be dispatched again')
print('PASS',count,'Phase4 HTTP/SQL assertions; complete order',oid)
