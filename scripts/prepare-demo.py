#!/usr/bin/env python3
"""Create a fresh local demo DB and use existing APIs to build demonstration orders.
No production tables, existing database, or business timestamps are overwritten.
"""
import argparse
import base64
import configparser
import datetime as dt
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
import zipfile
import io

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = ('init', 'phase3', 'phase4', 'phase5', 'business-enhancements',
              'notification-after-commit', 'final-hardening', 'dev-users',
              'dev-business', 'dev-dispatch')
PORT = 18088
BASE = f'http://127.0.0.1:{PORT}/api'


def mysql(command, sql):
    # Errors may contain SQL; only exit status is reported, never client credentials.
    result = subprocess.run(command, input=sql, text=True, capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError('MySQL command failed; check connectivity and migration prerequisites')
    return result.stdout.strip()


def request(path, token=None, data=None, method=None, expected=200, binary=False):
    headers = {'Authorization': f'Bearer {token}'} if token else {}
    body = None
    if data is not None:
        headers['Content-Type'] = 'application/json'
        body = json.dumps(data, ensure_ascii=False).encode()
    req = urllib.request.Request(BASE + path, body, headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as failure:
        status, raw = failure.code, failure.read()
    if status != expected:
        raise RuntimeError(f'{method or req.get_method()} {path}: expected HTTP {expected}, got {status}')
    if binary or expected != 200:
        return raw
    result = json.loads(raw)
    if result.get('code') != 0:
        raise RuntimeError(f'{path}: business request failed')
    return result.get('data')


def seed():
    tokens = {name: request('/auth/login', data={'username': name, 'password': '123456'})['token']
              for name in ('student001', 'admin001', 'worker001', 'worker002', 'worker003')}
    student, admin = tokens['student001'], tokens['admin001']
    catalog = request('/catalog', student)
    types, buildings = catalog['types'], catalog['buildings']
    workers = {row['username']: row['id'] for row in request('/admin/workers', admin)}
    examples = []

    def detail(order):
        return request(f'/orders/{order}', admin)

    def action(role, order, name, data=None):
        return request(
            f'/{role}/orders/{order}/{name}', admin if role == 'admin' else student,
            data, 'PUT')

    def create(title, index, priority='NORMAL'):
        payload = {'typeId': types[index % len(types)]['id'], 'title': '演示 · ' + title,
                   'description': '毕业设计演示样例，非真实校园报修。请检查并处理该问题。',
                   'buildingId': buildings[index % len(buildings)]['id'], 'roomNo': str(301 + index),
                   'priority': priority, 'requestKey': str(uuid.uuid4())}
        order = request('/student/orders', student, payload)['id']
        examples.append(order)
        return order, payload

    def assign(order, worker='worker001', smart=False):
        action('admin', order, 'audit')
        if smart:
            candidates = request(f'/admin/dispatch/recommend/{order}', admin, method='POST')
            choice = next(row for row in candidates if row['workerId'] == workers[worker])
            request('/admin/dispatch', admin, {'orderId': order, 'workerId': workers[worker],
                                            'recommendationId': choice['recommendationId']})
        else:
            action('admin', order, 'assign', {'workerId': workers[worker]})
        return tokens[worker]

    def work(token, order, finish=False):
        request(f'/worker/orders/{order}/accept', token, method='PUT')
        request(f'/worker/orders/{order}/start', token, method='PUT')
        request('/worker/repair-record', token, {'orderId': order, 'content': '演示维修记录：检查并处理问题，等待验收。',
                                               'requestKey': str(uuid.uuid4())})
        if finish:
            request(f'/worker/orders/{order}/finish', token, method='PUT')

    create('宿舍灯具待审核', 0)
    pending, _ = create('教学楼水龙头待派单', 1)
    action('admin', pending, 'audit')
    request(f'/admin/dispatch/recommend/{pending}', admin, method='POST')
    waiting, _ = create('门锁等待维修员接单', 2)
    assign(waiting, smart=True)
    accepted, _ = create('空调待开工与预约', 3)
    token = assign(accepted)
    request(f'/worker/orders/{accepted}/accept', token, method='PUT')
    start = (dt.datetime.now(dt.timezone(dt.timedelta(hours=8))) + dt.timedelta(days=1)).replace(
        hour=14, minute=0, second=0, microsecond=0, tzinfo=None)
    request(f'/worker/orders/{accepted}/appointment', token,
            {'start': start.isoformat(), 'end': (start + dt.timedelta(hours=1)).isoformat(), 'version': detail(accepted)['order']['appointmentVersion']}, 'PUT')
    action('student', accepted, 'appointment', {'version': detail(accepted)['order']['appointmentVersion'], 'accepted': True})
    processing, _ = create('图书馆插座维修中', 0)
    token = assign(processing)
    work(token, processing)
    for who, text in [(student, '演示沟通：方便明天下午检修吗？'), (token, '可以，现场处理进展会记录在本工单中。')]:
        request(f'/orders/{processing}/messages', who, {'content': text, 'expectedWorkerId': workers['worker001']})
    request(f'/orders/{processing}/messages', tokens['worker002'], expected=404)
    request(f'/orders/{processing}/messages', admin, {'content': '管理员不能发送'}, expected=403)
    confirming, _ = create('教室风扇等待学生验收', 3)
    work(assign(confirming), confirming, True)
    rework, _ = create('排水问题等待返工安排', 1)
    work(assign(rework), rework, True)
    action('student', rework, 'acceptance-fail', {'reason': '演示反馈：试用后仍有渗水，需要再次检查。'})
    second, _ = create('家具问题第二轮维修', 2)
    work(assign(second), second, True)
    action('student', second, 'acceptance-fail', {'reason': '演示反馈：松动问题尚未完全解决。'})
    action('admin', second, 'rework', {'mode': 'ORIGINAL'})
    request(f'/worker/orders/{second}/start', tokens['worker001'], method='PUT')
    request('/worker/repair-record', tokens['worker001'], {'orderId': second, 'content': '第二轮演示记录：继续加固连接部件。', 'requestKey': str(uuid.uuid4())})
    completed, _ = create('公共设备维修已确认', 4)
    work(assign(completed, 'worker002'), completed, True)
    action('student', completed, 'confirm')
    for index, name in enumerate(('worker001', 'worker002', 'worker003')):
        rated, _ = create('已评价维修样例 ' + str(index + 1), index)
        work(assign(rated, name), rated, True)
        action('student', rated, 'confirm')
        request('/student/evaluation', student, {'orderId': rated, 'score': 5 - (index % 2), 'content': '毕业设计演示评价，非真实用户评分。'})
    rejected, _ = create('房间信息待补充', 4)
    action('admin', rejected, 'reject', {'reason': '演示审核：请补充准确的房间信息。'})
    overdue, _ = create('紧急报修接单超时样例', 1, 'HIGH')
    assign(overdue)
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        if detail(overdue)['order']['overdueType']:
            break
        time.sleep(.5)
    else:
        raise RuntimeError('Timeout example was not detected by the existing scheduler')
    details = [detail(order) for order in examples]
    phases = {row['order']['phase'] for row in details}
    required = {'WAIT_AUDIT','WAIT_DISPATCH','WAIT_ACCEPT','WAIT_START','PROCESSING',
                'WAIT_CONFIRM','REWORK_PENDING','FINISHED','COMMENTED','REJECTED'}
    if not required.issubset(phases):
        raise RuntimeError('Demo is missing required lifecycle phases')
    overview = request('/admin/statistics/overview', admin)
    if overview['totalCount'] != len(examples) or overview['overdueCount'] < 1:
        raise RuntimeError('Dashboard counts do not match demo orders')
    for kind in ('orders', 'workers', 'types'):
        blob = request('/admin/export/' + kind, admin, binary=True)
        with zipfile.ZipFile(io.BytesIO(blob)) as workbook:
            if 'xl/worksheets/sheet1.xml' not in workbook.namelist():
                raise RuntimeError('Invalid XLSX report')
        request('/admin/export/' + kind, student, expected=403)
    # Notifications are after-commit/background; bounded wait, no blind sleep.
    until = time.monotonic() + 5
    while not request('/notifications', student)['total']:
        if time.monotonic() >= until:
            raise RuntimeError('Expected business notifications were not written')
        time.sleep(.1)
    notes = request('/notifications', student)
    request('/notifications/' + str(notes['records'][0]['id']) + '/read', student, method='PUT')
    return {'orderCount': len(examples), 'overview': overview,
            'orders': [{'id': row['order']['id'], 'title': row['order']['title'], 'phase': row['order']['phase'],
                        'workerName': row['order'].get('workerName'), 'repairRound': row['order']['repairRound']}
                       for row in details],
            'checks': ['five real logins', 'manual/smart assignment', 'confirmed appointment', 'repair records',
                       'two-round rework', 'chat ownership/admin read-only', 'confirmation/evaluation',
                       'scheduler timeout', 'Dashboard counts', 'three valid XLSX and student permission', 'notification/read']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--create', action='store_true', help='explicitly create a new isolated demo database')
    parser.add_argument('--database', default='campus_repair_demo_' + dt.datetime.now().strftime('%Y%m%d_%H%M%S'))
    parser.add_argument('--mysql-config', type=Path, required=True, help='private MySQL [client] admin configuration')
    parser.add_argument('--mysql', default=shutil.which('mysql'))
    parser.add_argument('--java', default=str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java'))
    args = parser.parse_args()
    if not args.create or not re.fullmatch(r'campus_repair_demo_[a-z0-9_]{1,36}', args.database):
        parser.error('--create and a campus_repair_demo_... database name are required')
    jar = ROOT / 'backend/target/campus-repair-0.0.1-SNAPSHOT.jar'
    if not jar.is_file() or not args.mysql or not args.java:
        parser.error('build the backend and configure the existing MySQL/Java executables first')
    config = configparser.ConfigParser(interpolation=None)
    config.read(args.mysql_config.resolve())
    if not config.has_section('client'):
        parser.error('private config requires a [client] section')
    client = {key: value.strip('"\'') for key, value in config['client'].items()}
    if not client.get('user'):
        parser.error('private config requires an explicit database user')
    if client.get('host', 'localhost') not in ('localhost', '127.0.0.1'):
        parser.error('only local MySQL is allowed')
    with socket.socket() as sock:
        if sock.connect_ex(('127.0.0.1', PORT)) == 0:
            parser.error('port 18088 is already in use; do not reuse a running service')
    command = [args.mysql, '--defaults-extra-file=' + str(args.mysql_config.resolve()), '--batch', '--skip-column-names']
    if mysql(command, "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='" + args.database + "';") != '0':
        parser.error('database already exists; no data will be overwritten')
    port = mysql(command, 'SELECT @@port;')
    directory = ROOT / '.runtime' / args.database
    directory.mkdir(parents=True, exist_ok=False)
    mysql(command, f'CREATE DATABASE `{args.database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    for name in MIGRATIONS:
        sql = re.sub(r'\bcampus_repair\b', args.database, (ROOT / 'sql' / (name + '.sql')).read_text())
        mysql(command, sql)
    settings = {'JAVA_EXECUTABLE': str(Path(args.java).resolve()), 'DB_HOST': '127.0.0.1', 'DB_PORT': port, 'DB_NAME': args.database,
                'DB_USERNAME': client.get('user', ''), 'DB_PASSWORD': client.get('password', ''),
                'JWT_SECRET': base64.b64encode(secrets.token_bytes(32)).decode(), 'SERVER_PORT': str(PORT),
                'SERVER_ADDRESS': '127.0.0.1', 'UPLOAD_DIRECTORY': str(directory / 'uploads'),
                'LOG_FILE': str(directory / 'backend.log')}
    envfile = directory / 'backend.env'
    envfile.touch(mode=0o600, exist_ok=False)
    envfile.write_text(''.join(f'{key}={shlex.quote(value)}\n' for key, value in settings.items()))
    env = {key: value for key, value in os.environ.items() if not key.startswith(('DB_', 'JWT_', 'SERVER_', 'APP_SLA_'))}
    env.update(settings)
    # Only the seeding process accelerates HIGH response time. Restart uses standard SLA.
    env.update(APP_SLA_HIGH_RESPONSE='2s', APP_SLA_SCAN_DELAY_MS='1000')
    with (directory / 'startup.log').open('w') as log:
        process = subprocess.Popen([args.java, '-jar', str(jar)], cwd=ROOT / 'backend', env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 30
            while process.poll() is None:
                try:
                    health = request('/health')
                    if health['database'] == 'UP':
                        break
                except (OSError, ValueError, RuntimeError):
                    pass
                if time.monotonic() >= deadline:
                    raise RuntimeError('Backend did not become healthy within 30 seconds')
                time.sleep(.3)
            else:
                raise RuntimeError('Demo backend stopped during startup; inspect private startup.log')
            result = seed()
            result.update(database=args.database, generatedAt=dt.datetime.now(dt.timezone.utc).isoformat(), browserVerified=False)
            (directory / 'manifest.json').write_text(json.dumps(result, ensure_ascii=False, indent=2))
            print(f"Prepared {result['orderCount']} demo orders in {args.database}.")
            print('PASS ' + ', '.join(result['checks']))
            print('Private startup config and manifest: ' + str(directory))
            print('The temporary backend is stopped; follow docs/demo-data.md to start this demo.')
        finally:
            process.terminate()
            try:
                process.wait(timeout=8)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.TimeoutExpired, ValueError, KeyError) as failure:
        print(f'Preparation stopped: {failure.__class__.__name__}: {failure}. No existing database was changed; inspect private logs.', file=sys.stderr)
        sys.exit(1)
