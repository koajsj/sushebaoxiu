"""Release-only real HTTP checks. Reuses the guarded isolated enhancement helpers."""
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
import io
import time
import uuid
import zipfile
import xml.etree.ElementTree as ET
from urllib import request, error
from check_enhancements import BASE, DB, call, data, login, sql, check
from check_isolation import verify_isolation


def race(*actions):
    barrier = Barrier(len(actions))
    def execute(action):
        barrier.wait(timeout=5)
        return action()
    with ThreadPoolExecutor(max_workers=len(actions)) as pool:
        return list(pool.map(execute, actions))


def run():
    verify_isolation(sql, BASE, DB)
    admin, student, worker_a, worker_b = [login(name) for name in ('admin001', 'student001', 'worker001', 'worker002')]
    catalog = data('/api/catalog', student)
    workers = {row['username']: row['id'] for row in data('/api/admin/workers', admin)}
    aid, bid = workers['worker001'], workers['worker002']
    def payload():
        return {'typeId': catalog['types'][0]['id'], 'title': '=发布并发验收', 'description': '仅隔离验收库',
                'buildingId': catalog['buildings'][0]['id'], 'roomNo': '901', 'priority': 'NORMAL', 'requestKey': str(uuid.uuid4())}
    def create():
        return data('/api/student/orders', student, 'POST', payload())['id']
    def action(role, order, name, token, body=None):
        return call(f'/api/{role}/orders/{order}/{name}', token, 'PUT', body)[0]
    def detail(order):
        return data(f'/api/orders/{order}', admin)
    def assigned():
        order = create()
        assert action('admin', order, 'audit', admin) == 200
        assert action('admin', order, 'assign', admin, {'workerId': aid}) == 200
        return order
    def recall(order):
        row = detail(order)['order']
        return action('admin', order, 'recall', admin, {'reason': '发布验收收回', 'confirmProcessing': True,
                      'expectedPhase': row['phase'], 'expectedDispatchRound': row['dispatchRound']})

    draft = payload()
    responses = race(lambda: call('/api/student/orders', student, 'POST', draft),
                     lambda: call('/api/student/orders', student, 'POST', draft))
    assert all(status == 200 for status, _ in responses)
    order = responses[0][1]['data']['id']
    assert responses[1][1]['data']['id'] == order
    check(sql(f"SELECT COUNT(*) FROM order_event WHERE order_id={order} AND action='SUBMIT'") == '1', 'concurrent same-key submit creates one order/event')
    check(call('/api/student/orders', student, 'POST', {**draft, 'title': '不同载荷'})[0] == 409, 'same-key different-payload submit conflicts')

    order = create()
    assert action('admin', order, 'audit', admin) == 200
    results = race(lambda: action('admin', order, 'assign', admin, {'workerId': aid}),
                   lambda: action('admin', order, 'assign', admin, {'workerId': bid}))
    check(sorted(results) == [200, 409] and len(detail(order)['dispatchHistory']) == 1, 'competing manual assignments commit one assignment')
    assert recall(order) == 200

    order = create()
    assert action('admin', order, 'audit', admin) == 200
    candidate = data(f'/api/admin/dispatch/recommend/{order}', admin, 'POST')[0]
    dispatch = {'orderId': order, 'workerId': candidate['workerId'], 'recommendationId': candidate['recommendationId']}
    results = race(lambda: call('/api/admin/dispatch', admin, 'POST', dispatch)[0],
                   lambda: call('/api/admin/dispatch', admin, 'POST', dispatch)[0])
    check(sorted(results) == [200, 409] and len(detail(order)['dispatchHistory']) == 1, 'duplicate smart confirmation commits one assignment')
    assert recall(order) == 200
    check(call('/api/admin/dispatch', admin, 'POST', dispatch)[0] == 400, 'previous-round recommendation cannot redispatch')

    for transition, phase in [('accept', 'WAIT_ACCEPT'), ('start', 'WAIT_START')]:
        order = assigned()
        if transition == 'start':
            assert action('worker', order, 'accept', worker_a) == 200
        snapshot = detail(order)['order']
        results = race(lambda: action('worker', order, transition, worker_a),
                       lambda: action('admin', order, 'recall', admin, {'reason': '并发收回', 'confirmProcessing': False,
                                      'expectedPhase': phase, 'expectedDispatchRound': snapshot['dispatchRound']}))
        check(results.count(200) == 1 and all(status in (200, 404, 409) for status in results), transition + ' vs recall has exactly one winner')
        if detail(order)['order']['workerId'] is not None:
            assert recall(order) == 200
        check(call(f'/api/orders/{order}', worker_a)[0] == 404 and
              action('worker', order, 'start', worker_a) == 404, transition + ': recalled worker loses view/action access')
        assert action('admin', order, 'assign', admin, {'workerId': bid}) == 200
        check(call(f'/api/orders/{order}/messages', worker_a)[0] == 404 and
              call(f'/api/orders/{order}/messages/context', worker_b)[0] == 200, transition + ': reassignment keeps old worker out')

    order = assigned()
    assert action('worker', order, 'accept', worker_a) == 200
    assert action('worker', order, 'start', worker_a) == 200
    record = {'orderId': order, 'content': '并发保存维修记录', 'requestKey': str(uuid.uuid4())}
    results = race(lambda: call('/api/worker/repair-record', worker_a, 'POST', record)[0],
                   lambda: call('/api/worker/repair-record', worker_a, 'POST', record)[0])
    check(results == [200, 200] and len(detail(order)['records']) == 1, 'same-key concurrent repair records are idempotent')
    assert action('worker', order, 'finish', worker_a) == 200
    results = race(lambda: action('student', order, 'confirm', student),
                   lambda: action('student', order, 'acceptance-fail', student, {'reason': '仍有问题'}))
    check(sorted(results) == [200, 409] and detail(order)['order']['status'] in ('FINISHED', 'REWORK_PENDING'), 'confirm vs rework cannot both commit')

    order = assigned()
    assert action('worker', order, 'accept', worker_a) == 200
    sql(f"UPDATE repair_order SET start_due_time=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id={order}")
    deadline = time.monotonic() + 75
    while time.monotonic() < deadline:
        if detail(order)['order']['overdueType'] == 'START':
            break
        time.sleep(.2)
    else:
        raise AssertionError('default SLA scheduler did not flag START')
    row = detail(order)['order']
    check(row['phase'] == 'WAIT_START' and row['status'] == 'ASSIGNED' and
          sql(f"SELECT COUNT(*) FROM order_event WHERE order_id={order} AND action='SLA_START'") == '1',
          'default scheduler flags start timeout once without changing workflow')
    assert action('worker', order, 'start', worker_a) == 200
    check(detail(order)['order']['overdueType'] is None, 'starting repair clears previous start timeout')

    tables = 'user student worker building repair_type repair_order repair_record evaluation order_event repair_image dispatch_record chat_message notification'.split()
    def checksum():
        return sql('CHECKSUM TABLE ' + ','.join('`' + table + '`' for table in tables) + ' EXTENDED')
    # Let AFTER_COMMIT deliveries finish before asserting export is read-only.
    previous = checksum()
    for _ in range(50):
        time.sleep(.1)
        current = checksum()
        if previous == current:
            break
        previous = current
    before = checksum()
    ns = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
    for kind in ('orders', 'workers', 'types'):
        req = request.Request(BASE + '/api/admin/export/' + kind, headers={'Authorization': 'Bearer ' + admin})
        with request.urlopen(req, timeout=15) as response:
            assert response.status == 200 and 'no-store' in response.headers['Cache-Control']
            assert response.headers['Content-Type'].startswith('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
            with zipfile.ZipFile(io.BytesIO(response.read())) as book:
                sheet = ET.fromstring(book.read('xl/worksheets/sheet1.xml'))
                assert sheet.find('m:sheetViews/m:sheetView/m:pane', ns).get('ySplit') in ('1', '1.0')
                assert sheet.findall('.//m:f', ns) == []
                rows = sheet.findall('m:sheetData/m:row', ns)
                if kind == 'orders':
                    assert len(rows) - 1 == int(sql('SELECT COUNT(*) FROM repair_order'))
                check(len(rows) > 1, kind + ': real Excel, frozen header, no formulas, valid rows')
        for token in (student, worker_a, None):
            denied = request.Request(BASE + '/api/admin/export/' + kind,
                                     headers={'Authorization': 'Bearer ' + token} if token else {})
            try:
                request.urlopen(denied, timeout=5)
                raise AssertionError('unauthorized export succeeded')
            except error.HTTPError as failure:
                assert failure.code == (403 if token else 401)
    check(before == checksum(), 'all business tables unchanged by three real exports')
    print('PASS isolated release HTTP/concurrency/export checks; browser remains unverified')


if __name__ == '__main__':
    run()
