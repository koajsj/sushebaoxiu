"""Final bounded real-HTTP core acceptance check. Mutates only its named isolated DB."""
from pathlib import Path
from decimal import Decimal,ROUND_HALF_UP
from urllib import request, error
import json, subprocess

ROOT=Path(__file__).resolve().parents[2]
DB='campus_repair_phase5_check_20260930'
BASE='http://127.0.0.1:18085'
MYSQL=Path.home()/'.cache/campus-repair/tools/mysql-8.4.9-macos15-arm64/bin/mysql'
def sql(statement):
    return subprocess.check_output([str(MYSQL),'--defaults-extra-file='+str(ROOT/'.runtime/mysql-root.cnf'),
        '--batch','--skip-column-names',DB,'-e',statement],text=True,timeout=5).strip()
def call(path,token=None,method='GET',body=None):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    payload=json.dumps(body).encode() if body is not None else None
    req=request.Request(BASE+path,payload,headers,method=method)
    try:
        with request.urlopen(req,timeout=4) as response:return response.status,json.load(response)
    except error.HTTPError as response:return response.code,json.load(response)
def data(path,token=None,method='GET',body=None):
    code,reply=call(path,token,method,body)
    assert code==200,(path,code,reply)
    return reply['data']
def login(name):return data('/api/auth/login',method='POST',body={'username':name,'password':'123456'})['token']
def check(condition,name):
    assert condition,name
    print('PASS',name)

# A second student exists only in the isolated database, to test order ownership.
sql("INSERT INTO `user`(username,password,real_name,role,status,token_version) SELECT 'student_phase5',password,'其他学生','STUDENT',1,0 FROM `user` WHERE username='student001' AND NOT EXISTS(SELECT 1 FROM `user` WHERE username='student_phase5')")
sql("INSERT INTO student(user_id,student_no,college,class_name,building_id,room_no) SELECT u.id,'2026999002','测试学院','测试班',s.building_id,'302' FROM `user` u JOIN `user` origin ON origin.username='student001' JOIN student s ON s.user_id=origin.id WHERE u.username='student_phase5' AND NOT EXISTS(SELECT 1 FROM student x WHERE x.user_id=u.id)")
check(call('/api/auth/login',method='POST',body={'username':'student001','password':'wrong-password'})[0]==401,'wrong password rejected')
admin,student,other,worker,unrelated=[login(name) for name in ('admin001','student001','student_phase5','worker002','worker001')]
check(call('/api/admin/statistics/overview')[0]==401,'anonymous statistics denied')
check(call('/api/admin/statistics/overview',student)[0]==403,'student statistics denied')
check(call('/api/notifications')[0]==401,'anonymous notifications denied')
initial=data('/api/admin/statistics/overview',admin)
check(initial['totalCount']==int(sql('SELECT COUNT(*) FROM repair_order')),'overview uses real order count')
check(initial['activeCount']==int(sql("SELECT COUNT(*) FROM repair_order WHERE status IN ('ASSIGNED','PROCESSING','WAIT_CONFIRM','REWORK_PENDING')")),'active count matches SQL')
types=data('/api/admin/statistics/types',admin)
check(sum(item['count'] for item in types)==initial['totalCount'],'type counts match all orders')
check(len(data('/api/admin/statistics/trend',admin))==14,'trend has fourteen dated points')
check(len(data('/api/admin/statistics/workers',admin))>=3,'workers computed from real profiles')

catalog=data('/api/catalog',student);building=catalog['buildings'][0]['id'];kind=catalog['types'][0]['id']
order=data('/api/student/orders',student,'POST',{'typeId':kind,'title':'Phase5沟通验收','description':'隔离库真实工单','buildingId':building,'roomNo':'302','priority':'NORMAL'})['id']
check(call(f'/api/orders/{order}/messages')[0]==401,'anonymous order messages denied')
check(call(f'/api/orders/{order}/messages',other)[0]==404,'unrelated student cannot read messages')
check(call(f'/api/orders/{order}/messages',unrelated)[0]==404,'unrelated worker cannot read messages')
check(call(f'/api/orders/{order}/messages',student,'POST',{'content':'待派单'})[0]==409,'chat waits for assigned worker')
check(data(f'/api/orders/{order}/messages',admin)==[],'admin may read empty conversation')
check(call(f'/api/orders/{order}/messages',admin,'POST',{'content':'管理员不可发言'})[0]==403,'admin cannot send')
student_notice=data('/api/notifications',student)
admin_notice=data('/api/notifications',admin)
check(any(n['title']=='报修提交成功' for n in student_notice['records']),'student submission notification')
check(any(n['title']=='新报修待审核' for n in admin_notice['records']),'admin new order notification')
check(call('/api/notifications/'+str(student_notice['records'][0]['id'])+'/read',other,'PUT')[0]==404,'notification ownership enforced')
notice_id=student_notice['records'][0]['id'];data('/api/notifications/'+str(notice_id)+'/read',student,'PUT')
check(any(n['id']==notice_id and n['readStatus']==1 for n in data('/api/notifications',student)['records']),'notification read persisted')

data(f'/api/admin/orders/{order}/audit',admin,'PUT')
check(any(n['title']=='报修审核完成' for n in data('/api/notifications',student)['records']),'audit notification')
recs=data(f'/api/admin/dispatch/recommend/{order}',admin)
target=next(r for r in recs if r['workerName']=='电工示例人员')
data('/api/admin/dispatch',admin,'POST',{'orderId':order,'workerId':target['workerId'],'recommendationId':target['recommendationId']})
check(data(f'/api/orders/{order}',student)['order']['status']=='ASSIGNED','smart dispatch still assigns')
check(any(n['title']=='维修人员已安排' for n in data('/api/notifications',student)['records']),'student dispatch notification')
check(any(n['title']=='收到新维修任务' for n in data('/api/notifications',worker)['records']),'worker task notification')
check(call(f'/api/orders/{order}/messages',unrelated)[0]==404,'other worker cannot read assigned order chat')
message=data(f'/api/orders/{order}/messages',student,'POST',{'content':'请检查宿舍灯具'})
check(message['receiverId']==int(sql(f'SELECT w.user_id FROM repair_order o JOIN worker w ON w.id=o.worker_id WHERE o.id={order}')),'student message targets assigned worker')
check(len(data(f'/api/orders/{order}/messages',worker))==1,'assigned worker reads student message')
check(int(sql(f'SELECT read_status FROM chat_message WHERE id={message["id"]}'))==1,'message read status persisted')
reply=data(f'/api/orders/{order}/messages',worker,'POST',{'content':'收到，准备检查'})
check(reply['receiverId']==int(sql(f'SELECT s.user_id FROM repair_order o JOIN student s ON s.id=o.student_id WHERE o.id={order}')),'worker reply targets student')
check(len(data(f'/api/orders/{order}/messages',admin))==2,'admin can inspect conversation')
check(int(sql(f'SELECT read_status FROM chat_message WHERE id={reply["id"]}'))==0,'admin read does not consume student receipt')
student_id=int(sql("SELECT id FROM `user` WHERE username='student001'"))
worker_user_id=int(sql("SELECT id FROM `user` WHERE username='worker002'"))
batch=','.join(f"({order},{worker_user_id},{student_id},'历史消息{i}',0,NOW())" for i in range(101))
sql('INSERT INTO chat_message(order_id,sender_id,receiver_id,content,read_status,create_time) VALUES '+batch)
oldest=int(sql(f'SELECT MAX(id)-100 FROM chat_message WHERE order_id={order}'))
latest=int(sql(f'SELECT MAX(id) FROM chat_message WHERE order_id={order}'))
check(len(data(f'/api/orders/{order}/messages',student))==100,'conversation returns latest bounded page')
check(int(sql(f'SELECT read_status FROM chat_message WHERE id={oldest}'))==0 and
      int(sql(f'SELECT read_status FROM chat_message WHERE id={latest}'))==1,'only displayed messages become read')
data(f'/api/worker/orders/{order}/accept',worker,'PUT')
data(f'/api/worker/orders/{order}/start',worker,'PUT')
data('/api/worker/repair-record',worker,'POST',{'orderId':order,'content':'更换灯具并测试'})
data(f'/api/worker/orders/{order}/finish',worker,'PUT')
check(any(n['title']=='维修已完成' for n in data('/api/notifications',student)['records']),'finish notification')
data(f'/api/student/orders/{order}/confirm',student,'PUT')
data('/api/student/evaluation',student,'POST',{'orderId':order,'score':4,'content':'已解决'})
check(data(f'/api/orders/{order}',student)['order']['status']=='COMMENTED','core evaluation flow preserved')
check(sql(f'SELECT score FROM worker WHERE id=(SELECT worker_id FROM repair_order WHERE id={order})')==sql(f'SELECT ROUND(AVG(CAST(e.score AS DECIMAL(20,12))),2) FROM evaluation e JOIN repair_order o ON o.id=e.order_id WHERE o.worker_id=(SELECT worker_id FROM repair_order WHERE id={order})'),'worker average rating matches SQL aggregate')
check(call('/api/student/evaluation',student,'POST',{'orderId':order,'score':1})[0]==409,'repeated evaluation denied')
updated=data('/api/admin/statistics/overview',admin)
check(updated['totalCount']==int(sql('SELECT COUNT(*) FROM repair_order')),'updated overview matches persisted data')
completed=int(sql("SELECT COUNT(*) FROM repair_order WHERE status IN ('FINISHED','COMMENTED')"))
expected=float((Decimal(completed*100)/Decimal(updated['totalCount'])).quantize(Decimal('0.1'),rounding=ROUND_HALF_UP))
check(updated['completionRate']==expected,'completion rate matches confirmed orders')
today=data('/api/admin/statistics/trend',admin)[-1]
check(today['created']>=1 and today['finished']>=1,'trend reflects current real events')
check(data('/api/map/buildings',admin)!=[],'map regression smoke')
manual=data('/api/student/orders',student,'POST',{'typeId':kind,'title':'Phase5人工派单通知验收','description':'隔离库人工派单','buildingId':building,'roomNo':'303','priority':'NORMAL'})['id']
data(f'/api/admin/orders/{manual}/audit',admin,'PUT')
worker_id=int(sql("SELECT id FROM worker WHERE user_id=(SELECT id FROM `user` WHERE username='worker001')"))
data(f'/api/admin/orders/{manual}/assign',admin,'PUT',{'workerId':worker_id})
check(data(f'/api/orders/{manual}',admin)['order']['status']=='WAIT_ASSIGN','manual dispatch state preserved')
check(any(n['title']=='收到新维修任务' and f'#{manual}' in n['content'] for n in data('/api/notifications',unrelated)['records']),'manual assignment also notifies worker')
data(f'/api/worker/orders/{manual}/accept',unrelated,'PUT')
check(data(f'/api/orders/{manual}',admin)['order']['status']=='ASSIGNED','manual accept preserved')
print('Final isolated HTTP core acceptance passed')
