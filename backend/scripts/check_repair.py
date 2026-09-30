"""Phase 3 local acceptance. Creates test orders in a local development database.
Uses real HTTP; no third-party packages. Never run on production.
"""
import base64
from concurrent.futures import ThreadPoolExecutor
import json
import os
import urllib.request
import urllib.error

BASE = os.environ.get('BACKEND_URL', 'http://127.0.0.1:8080')
checks = 0

def check(ok, label):
    global checks
    assert ok, label
    checks += 1
    print('PASS', label)

def req(path, method='GET', data=None, token=None, raw=None, mime=None):
    headers = {'Authorization': 'Bearer ' + token} if token else {}
    if data is not None:
        raw = json.dumps(data).encode()
        headers['Content-Type'] = 'application/json'
    if mime: headers['Content-Type'] = mime
    try:
        r = urllib.request.urlopen(urllib.request.Request(BASE + path, data=raw, headers=headers, method=method), timeout=15)
    except urllib.error.HTTPError as e: r = e
    with r:
        body = r.read()
        return r.status, json.loads(body) if r.headers.get_content_type() == 'application/json' else body

def data(path, token, method='GET', body=None):
    status, result = req(path, method, body, token)
    check(status == 200 and result['code'] == 0, method + ' ' + path)
    return result['data']

tokens = {}
for role in ('student', 'worker', 'admin'):
    status, result = req('/api/auth/login', 'POST', {'username': role+'001', 'password': '123456'})
    assert status == 200, 'existing login must work'
    tokens[role] = result['data']['token']

# Fails with404 on Phase2; all following checks describe Phase3 contract.
status, result = req('/api/student/orders', 'POST', {'title': '验收：宿舍灯具故障', 'description': '灯具无法点亮，请检查线路。', 'typeId': 1, 'buildingId': 1, 'roomNo': '301', 'priority': 'NORMAL'}, tokens['student'])
check(status == 200, 'student creates a real repair order')
order = result['data']; oid = order['id']; path = f'/api/orders/{oid}'
check(order['status'] == 'WAIT_AUDIT', 'initial status is WAIT_AUDIT')
check(req(path)[0] == 401, 'anonymous detail denied')
check(req(path, token=tokens['worker'])[0] == 404, 'unassigned worker cannot inspect order')
check(req(f'/api/admin/orders/{oid}/audit', 'PUT', token=tokens['student'])[0] == 403, 'student cannot audit')
check(req(f'/api/student/orders/{oid}/confirm', 'PUT', token=tokens['student'])[0] == 409, 'early confirmation denied')
check(data('/api/student/orders?status=WAIT_AUDIT&page=1&size=1', tokens['student'])['total'] >= 1, 'student paginated filter')
check(req('/api/student/orders?size=101', token=tokens['student'])[0] == 400, 'oversized page denied')
check(req('/api/admin/orders?from=2026-10-02&to=2026-10-01', token=tokens['admin'])[0] == 400, 'reversed dates denied')
check(data('/api/admin/orders?typeId=1', tokens['admin'])['total'] >= 1, 'admin type filter')
workers = data('/api/admin/workers', tokens['admin'])
wid = next(w['id'] for w in workers if w['username'] == 'worker001')
data(f'/api/admin/orders/{oid}/audit', tokens['admin'], 'PUT')
check(data(path, tokens['student'])['order']['status'] == 'WAIT_ASSIGN', 'audit moves to WAIT_ASSIGN')
check(req(f'/api/worker/orders/{oid}/accept', 'PUT', token=tokens['worker'])[0] == 404, 'cannot accept before assignment')
data(f'/api/admin/orders/{oid}/assign', tokens['admin'], 'PUT', {'workerId': wid})
check(data('/api/worker/orders', tokens['worker'])['total'] >= 1, 'assigned worker sees task')
check(req(f'/api/admin/orders/{oid}/audit', 'PUT', token=tokens['admin'])[0] == 409, 'duplicate audit denied')
data(f'/api/worker/orders/{oid}/accept', tokens['worker'], 'PUT')
check(req(f'/api/worker/orders/{oid}/finish', 'PUT', token=tokens['worker'])[0] == 409, 'cannot skip start')
data(f'/api/worker/orders/{oid}/start', tokens['worker'], 'PUT')
check(req(f'/api/worker/orders/{oid}/finish', 'PUT', token=tokens['worker'])[0] == 409, 'record required before finish')
# Real JPEG/PNG uploaded through multipart, not fake image URLs.
png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jZ1kAAAAASUVORK5CYII=')
def upload(token, content=png, content_type='image/png'):
    boundary='campus-repair-test-boundary'
    raw=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="test.png"\r\nContent-Type: {content_type}\r\n\r\n'.encode()+content+f'\r\n--{boundary}--\r\n'.encode())
    return req('/api/images', 'POST', token=token, raw=raw, mime='multipart/form-data; boundary='+boundary)
status, image=upload(tokens['worker'])
check(status == 200, 'valid image upload')
url=image['data']['url']
check(req(url)[0] == 401, 'private image needs authentication')
check(req(url, token=tokens['student'])[0] == 404, 'unbound upload private to owner')
check(req(url, token=tokens['worker'])[0] == 200, 'owner reads uploaded bytes')
check(upload(tokens['worker'], b'<script>bad</script>')[0] == 400, 'fake PNG rejected')
check(upload(tokens['worker'], png, 'image/jpeg')[0] == 400, 'MIME mismatch rejected')
check(upload(tokens['admin'])[0] == 403, 'admin upload denied')
check(req('/api/worker/repair-record', 'POST', {'orderId':oid,'content':'测试','imageUrl':'/api/images/not-owned'}, tokens['worker'])[0] == 400, 'unowned URL rejected')
data('/api/worker/repair-record', tokens['worker'], 'POST', {'orderId':oid,'content':'已更换灯具并确认照明恢复正常。','imageUrl':url})
check(req(url, token=tokens['student'])[0] == 200, 'student can read bound repair photo')
with ThreadPoolExecutor(max_workers=2) as pool:
    statuses = list(pool.map(lambda _: req(f'/api/worker/orders/{oid}/finish', 'PUT', token=tokens['worker'])[0], range(2)))
check(sorted(statuses) == [200,409], 'concurrent finish applies exactly once')
check(data(path, tokens['student'])['order']['status'] == 'WAIT_CONFIRM', 'finish waits for student')
data(f'/api/student/orders/{oid}/confirm', tokens['student'], 'PUT')
with ThreadPoolExecutor(max_workers=2) as pool:
    statuses = list(pool.map(lambda _: req('/api/student/evaluation', 'POST', {'orderId':oid,'score':5,'content':'处理及时，谢谢。'}, tokens['student'])[0], range(2)))
check(sorted(statuses) == [200,409], 'concurrent evaluation applies exactly once')
detail=data(path, tokens['student'])
check(detail['order']['status']=='COMMENTED' and detail['evaluation']['score']==5, 'final status/evaluation persisted')
check(len(detail['records'])==1 and detail['records'][0]['finishTime'] is not None, 'repair record and completion saved')
check([e['action'] for e in detail['timeline']]==['SUBMIT','AUDIT','ASSIGN','ACCEPT','START','FINISH','CONFIRM','EVALUATE'], 'real timeline covers full loop')
for role in ('student','worker'):
    check(data(f'/api/{role}/summary',tokens[role])['total'] >= 1, role+' basic counters')
check(req('/api/student/evaluation','POST',{'orderId':oid,'score':6},tokens['student'])[0]==400, 'rating range validated')
print('PASS',checks,'HTTP business assertions; order',oid)
