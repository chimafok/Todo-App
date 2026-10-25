"""Check tracked public content; this complements manual review and GitHub scanning."""
from pathlib import Path
import re
import subprocess
import sys

SECRET_PATTERNS = (
    re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    re.compile(rb"gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}"),
    re.compile(rb"AKIA[0-9A-Z]{16}"),
    re.compile(rb"[A-Za-z]:[\\/]+Users[\\/]+[^\\/\s\"']+", re.I),
    re.compile(rb"chatgpt\.com/share/|chatgpt-conversation\x3a//"),
)
PRIVATE_SUFFIXES = {'.jks','.keystore','.p12','.pfx','.pem','.key','.dpapi','.db','.sqlite','.sqlite3','.apk','.aab','.apks','.idsig','.hprof','.lineage'}
PRIVATE_NAMES = {'local.properties','keystore.properties','signing.properties','.env'}
PRIVATE_PARTS = {'.gradle','.idea','.kotlin','work','outputs','dist','backups','captures'}

def violations(name, data):
    path=Path(name)
    issues=[]
    if path.suffix.lower() in PRIVATE_SUFFIXES or path.name in PRIVATE_NAMES or (path.name.startswith('.env.') and path.name!='.env.example'):
        issues.append('private or generated file')
    if any(part in PRIVATE_PARTS or part=='build' for part in path.parts):issues.append('private/generated directory')
    if any(pattern.search(data) for pattern in SECRET_PATTERNS):issues.append('sensitive content pattern')
    return issues

def self_test():
    assert violations('credentials.p12', b'example')
    assert violations('work/report.txt', b'example')
    assert violations('sample.txt', b'github_' + b'pat_' + b'A'*50)
    assert violations('sample.txt', b'C:' + b'\\Users\\Example\\file')
    assert not violations('docs/verification.json', b'{"sha256":"' + b'a'*64 + b'"}')
    assert not violations('tools/check-public-content.py', Path(__file__).read_bytes())
    print('Public-content checker self-test passed')

def main():
    self_test()
    names=[name for name in subprocess.check_output(['git','ls-files','-z']).decode().split('\0') if name]
    if not names:raise SystemExit('No tracked files to check')
    failed=[]
    for name in names:
        data=subprocess.check_output(['git','show',':'+name])
        issues=violations(name,data)
        if issues:failed.append((name,issues))
    for name,issues in failed:print(name+': '+', '.join(issues))
    if failed:raise SystemExit('Public content check failed')
    print(f'Public content check passed for {len(names)} tracked files')

if __name__=='__main__':
    if '--self-test' in sys.argv:self_test()
    else:main()
