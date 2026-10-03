#!/usr/bin/env python3
"""Package production APKs, preserving the authorized debug→production lineage.

Passwords come only from environment variables. Certificates/lineage are public;
keystores remain private. Build assembleRelease with production credentials first.
"""
import argparse, os, re, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
identity = dict(line.split('=', 1) for line in (ROOT/'signing/release.properties').read_text().splitlines() if '=' in line)
p = argparse.ArgumentParser()
p.add_argument('--apk', type=Path, default=ROOT/'app/build/outputs/apk/release/app-release.apk')
p.add_argument('--output', type=Path, required=True)
p.add_argument('--migration', action='store_true')
p.add_argument('--create-lineage', action='store_true')
a=p.parse_args()
sdk=Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', str(Path.home()/'Library/Android/sdk'))))
tool=Path(os.environ.get('RALLY_APKSIGNER', sdk/'build-tools/34.0.0/apksigner'))
lineage=ROOT/'signing/rally-debug-to-production.lineage'

def run(args):
    return subprocess.run([str(tool),*map(str,args)],check=True,capture_output=True,text=True).stdout

def signer(legacy=False):
    prefix='RALLY_LEGACY_' if legacy else 'RALLY_'
    names=[prefix+x for x in ['KEYSTORE_PATH','KEYSTORE_PASSWORD','KEY_ALIAS','KEY_PASSWORD']]
    if not all(os.environ.get(x) for x in names): raise SystemExit('Missing signing variables: '+', '.join(names))
    return ['--ks',os.environ[names[0]],'--ks-key-alias',os.environ[names[2]],
            '--ks-pass','env:'+names[1],'--key-pass','env:'+names[3]]

def verify(path, minimum, expected):
    output=run(['verify','--verbose','--print-certs','--min-sdk-version',minimum,'--max-sdk-version',minimum,path])
    certs=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)',output)
    if certs!=[expected]: raise SystemExit('Unexpected signing certificate for API '+str(minimum)+': '+str(certs))
    print(path.name, 'API',minimum,'verified certificate',expected)

a.output.parent.mkdir(parents=True,exist_ok=True)
production=signer()
if a.migration:
    legacy=signer(True)
    if a.create_lineage:
        if lineage.exists():raise SystemExit('Existing lineage is immutable; do not recreate it.')
        run(['rotate','--out',lineage,'--old-signer',*legacy,'--new-signer',*production])
    if not lineage.exists():raise SystemExit('Missing authorized signing lineage.')
    run(['sign','--debuggable-apk-permitted','false','--rotation-min-sdk-version',28,
         '--lineage',lineage,*legacy,'--next-signer',*production,'--out',a.output,a.apk])
    verify(a.output,28,identity['production.sha256'])
    verify(a.output,34,identity['production.sha256'])
    verify(a.output,26,identity['legacy.debug.sha256'])
else:
    run(['sign','--debuggable-apk-permitted','false',*production,'--out',a.output,a.apk])
    verify(a.output,26,identity['production.sha256'])
