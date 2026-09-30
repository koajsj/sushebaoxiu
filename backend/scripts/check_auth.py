"""Local development acceptance against real HTTP/MySQL; no third-party packages.
Temporarily changes student001 status/role and restores both in finally.
Never run against production. Pass BACKEND_URL to use a different local port.
"""
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
BASE = os.environ.get('BACKEND_URL', 'http://127.0.0.1:8080')
MYSQL = Path.home() / '.cache/campus-repair/tools/mysql-8.4.9-macos15-arm64/bin/mysql'
CONF = ROOT / '.runtime/mysql-root.cnf'
checks = 0


def check(condition, description):
    global checks
    assert condition, description
    checks += 1
    print('PASS', description)


def request(path, method='GET', data=None, token=None, headers=None):
    request_headers = dict(headers or {})
    if token is not None:
        request_headers['Authorization'] = 'Bearer ' + token
    payload = None
    if data is not None:
        request_headers['Content-Type'] = 'application/json'
        payload = json.dumps(data).encode()
    req = urllib.request.Request(BASE + path, data=payload, headers=request_headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read().decode()
        try:
            body = json.loads(raw)
        except json.JSONDecodeError:
            body = raw
        return response.status, body, dict(response.headers)


def sql(command):
    return subprocess.check_output([str(MYSQL), '--defaults-extra-file=' + str(CONF),
                                   '--batch', '--skip-column-names', '-e', command], text=True).strip()


def login(username, password='123456'):
    return request('/api/auth/login', 'POST', {'username': username, 'password': password})


def encode(value):
    return base64.urlsafe_b64encode(json.dumps(value, separators=(',', ':')).encode()).decode().rstrip('=')


def sign(claims, secret, algorithm='HS256'):
    unsigned = encode({'alg': algorithm, 'typ': 'JWT'}) + '.' + encode(claims)
    signature = base64.urlsafe_b64encode(hmac.new(secret, unsigned.encode(), hashlib.sha256).digest()).decode().rstrip('=')
    return unsigned + '.' + signature


check(request('/api/health')[0] == 200, 'Phase 1 health remains accessible')
tokens = {}
for role in ['student', 'worker', 'admin']:
    status, body, _ = login(role + '001')
    check(status == 200 and body['code'] == 0 and bool(body['data']['token']), role + ': correct password returns JWT')
    check(body['data']['role'] == role.upper() and body['data']['user']['role'] == role.upper(), role + ': role and user identity agree')
    check('password' not in json.dumps(body) and '123456' not in json.dumps(body), role + ': no password/hash leakage')
    tokens[role] = body['data']['token']

wrong = login('student001', 'incorrect')
unknown = login('nonexistent-user', 'incorrect')
check(wrong[0] == 401 and wrong[1]['code'] == 40101, 'wrong password rejected')
check(unknown[:2] == wrong[:2], 'unknown account has same friendly credentials error')
check(login('student001', '')[0] == 400, 'blank password validation')
check(login('x' * 65)[0] == 400, 'oversized username validation')
check(login('student001', 'x' * 73)[0] == 400, 'oversized password validation')

for role in tokens:
    status, body, headers = request('/api/' + role + '/me')
    check(status == 401 and body['code'] == 40100, role + ': missing token rejected')
    for area in tokens:
        status, body, _ = request('/api/' + area + '/me', token=tokens[role])
        expected = 200 if area == role else 403
        check(status == expected and body['code'] == (0 if area == role else 40300), role + ' -> ' + area + ': permission matrix')

status, body, headers = request('/api/users/me', token=tokens['student'])
check(status == 200 and body['data']['username'] == 'student001', 'current user restores trusted identity')
check('JSESSIONID' not in headers.get('Set-Cookie', ''), 'stateless authentication has no session cookie')
parts = tokens['student'].split('.')
claims = json.loads(base64.urlsafe_b64decode(parts[1] + '=' * (-len(parts[1]) % 4)))
forged = dict(claims, role='ADMIN')
check(request('/api/admin/me', token=parts[0] + '.' + encode(forged) + '.' + parts[2])[0] == 401, 'tampered role/signature rejected')
check(request('/api/users/me', token='not-a-jwt')[0] == 401, 'malformed JWT rejected')
env = dict(line.split('=', 1) for line in (ROOT / '.runtime/backend.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
secret = base64.b64decode(env['JWT_SECRET'])
now = int(time.time())
variants = {
    'expired JWT': dict(claims, iat=now - 120, nbf=now - 120, exp=now - 10),
    'future JWT': dict(claims, nbf=now + 600),
    'wrong issuer': dict(claims, iss='untrusted-app'),
    'wrong version': dict(claims, ver=claims['ver'] + 1),
}
without_exp = dict(claims)
without_exp.pop('exp')
variants['missing expiration'] = without_exp
for name, value in variants.items():
    check(request('/api/users/me', token=sign(value, secret))[0] == 401, name + ' rejected')
check(request('/api/users/me', token=sign(claims, b'x' * 32))[0] == 401, 'wrong signing key rejected')
check(request('/api/users/me', token=sign(claims, secret, 'HS384'))[0] == 401, 'unexpected algorithm rejected')
check(request('/api/users/me', token=encode({'alg': 'none'}) + '.' + encode(claims) + '.')[0] == 401, 'unsigned JWT rejected')

original = sql("SELECT status, role FROM campus_repair.user WHERE username='student001'").split('\t')
try:
    sql("UPDATE campus_repair.user SET status=0 WHERE username='student001'")
    check(login('student001')[0] == 401, 'disabled user cannot log in')
    check(request('/api/users/me', token=tokens['student'])[0] == 401, 'disabled user existing token rejected immediately')
    sql("UPDATE campus_repair.user SET status=1,role='ADMIN' WHERE username='student001'")
    check(request('/api/admin/me', token=tokens['student'])[0] == 200, 'authority derives from current database role')
    check(request('/api/student/me', token=tokens['student'])[0] == 403, 'stale JWT role is not trusted')
finally:
    sql("UPDATE campus_repair.user SET status=" + original[0] + ",role='" + original[1] + "' WHERE username='student001'")

for role, token in tokens.items():
    status, body, _ = request('/api/auth/logout', 'POST', token=token)
    check(status == 200 and body['code'] == 0, role + ': logout succeeds')
    check(request('/api/users/me', token=token)[0] == 401, role + ': revoked token rejected')
    check(login(role + '001')[0] == 200, role + ': new login works after logout')

status, _, headers = request('/api/student/me', 'OPTIONS', headers={
    'Origin': 'http://127.0.0.1:5173', 'Access-Control-Request-Method': 'GET',
    'Access-Control-Request-Headers': 'authorization'})
check(status == 200 and headers.get('Access-Control-Allow-Origin') == 'http://127.0.0.1:5173', 'CORS allows exact frontend origin with Authorization')
status, _, headers = request('/api/student/me', 'OPTIONS', headers={
    'Origin': 'https://untrusted.example', 'Access-Control-Request-Method': 'GET'})
check(status == 403 and 'Access-Control-Allow-Origin' not in headers, 'CORS rejects untrusted origin')
check(sql("SELECT COUNT(*) FROM campus_repair.user WHERE password='123456'") == '0', 'database stores no plaintext development passwords')
check(sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='campus_repair' AND table_name='user'") == '1', 'Phase 2 user table remains present')
print('PASS', checks, 'real HTTP/database assertions; tokens and signing secret are not printed.')
