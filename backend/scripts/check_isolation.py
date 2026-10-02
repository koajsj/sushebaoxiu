"""Prove the guarded HTTP endpoint uses the selected disposable database before mutations."""
import json
import re
import uuid
from urllib import request


def verify_isolation(sql, base, database):
    if not re.fullmatch(r'campus_repair_deep_check_[a-z0-9_]+', database):
        raise SystemExit('isolated release database required')
    if sql('SELECT DATABASE()') != database:
        raise SystemExit('MySQL selected database does not match isolated configuration')
    name = 'release_probe_' + uuid.uuid4().hex
    # A private nonce account exists only in this disposable schema. Copy the seeded
    # BCrypt hash rather than expose any real password or change an existing account.
    sql("INSERT INTO `user` (username,password,real_name,role,status,token_version) "
        f"SELECT '{name}',password,'隔离连接探针','STUDENT',1,0 FROM `user` WHERE username='student001'")
    try:
        body = json.dumps({'username': name, 'password': '123456'}).encode()
        req = request.Request(base + '/api/auth/login', body, {'Content-Type': 'application/json'})
        with request.urlopen(req, timeout=5) as response:
            result = json.load(response)
        identity = result['data']['user']
        if result['code'] != 0 or identity['username'] != name or str(identity['id']) != sql(f"SELECT id FROM `user` WHERE username='{name}'"):
            raise SystemExit('HTTP endpoint does not use the selected isolated database')
    finally:
        sql(f"DELETE FROM `user` WHERE username='{name}'")
