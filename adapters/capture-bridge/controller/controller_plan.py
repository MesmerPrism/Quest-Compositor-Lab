"""Offline file/data-only plan and validation. No execution adapter is imported."""
# SPDX-License-Identifier: AGPL-3.0-or-later
import argparse
import base64
import hashlib
import json
from pathlib import Path, PurePosixPath
import re

def load(data):
    if type(data) not in (str,bytes) or not data or len(data.encode('utf-8') if type(data) is str else data)>65536:
        raise ValueError('bounded input required')
    def pairs(items):
        value={}
        for key,item in items:
            if key in value:raise ValueError('duplicate JSON key')
            value[key]=item
        return value
    def no_number(value):raise ValueError('integer JSON tokens only')
    try:value=json.loads(data,object_pairs_hook=pairs,parse_float=no_number,parse_constant=no_number)
    except (UnicodeError,RecursionError) as error:raise ValueError('invalid bounded JSON') from error
    def bounded(item,depth=0):
        require(depth<=20,'JSON depth')
        if type(item) is dict:
            require(len(item)<=32 and all(type(k) is str and len(k)<=64 for k in item),'object bound')
            for child in item.values():bounded(child,depth+1)
        elif type(item) is list:
            require(len(item)<=32,'array bound')
            for child in item:bounded(child,depth+1)
        elif type(item) is str:require(len(item)<=1024 and '\0' not in item,'string bound')
        else:require(type(item) in (bool,int),'unsupported JSON scalar')
    bounded(value);return value

def require(value,message):
    if not value:raise ValueError(message)

def closed(value,keys):
    require(type(value) is dict and set(value)==set(keys.split()),'closed object fields required')

def integer(value,low=1,high=9223372036854775807):
    require(type(value) is int and low<=value<=high,'bounded exact integer required')

def token(value):
    require(type(value) is str and 0<len(value)<=256 and re.fullmatch(r'[A-Za-z0-9._:-]+',value),'opaque token required')

def digest(value):require(type(value) is str and re.fullmatch(r'sha256:[0-9a-f]{64}',value),'exact lowercase SHA-256 required')

def service(value):
    closed(value,'package name permission uid exported enabled')
    for key in ('package','name','permission'):token(value[key])
    integer(value['uid'],1,2147483647)
    require(value['name'].startswith(value['package']+'.') and value['exported'] is True and value['enabled'] is True,'owned enabled exported service required')

def artifact(value,base):
    closed(value,'path sha256 size_bytes');digest(value['sha256']);integer(value['size_bytes'],1,536870912)
    name=value['path'];require(type(name) is str and 0<len(name)<=256 and '\\' not in name and ':' not in name,'relative artifact path required')
    path=PurePosixPath(name);require(not path.is_absolute() and '..' not in path.parts and str(path)==name,'closed artifact locator required')
    base=Path(base).resolve();file=base/name;resolved=file.resolve(strict=True)
    require(base in resolved.parents and file.is_file() and not file.is_symlink(),'artifact must remain in selected local input root')
    require(file.stat().st_size==value['size_bytes'],'artifact size differs')
    result=hashlib.sha256();count=0
    with file.open('rb') as source:
        for block in iter(lambda:source.read(65536),b''):
            count+=len(block);require(count<=value['size_bytes'],'artifact changed during hash');result.update(block)
    require(count==value['size_bytes'] and 'sha256:'+result.hexdigest()==value['sha256'],'artifact hash differs')
    return value['sha256']

def descriptor(words):
    require(type(words) is list and len(words)==19,'exact local sink descriptor required')
    for value in words:integer(value,0)
    require(words[0]==1 and words[1]>0,'sink ABI/generation')
    w,h=words[2:4];require(2<=w<=16384 and w%2==0 and 1<=h<=16384,'sink extent')
    require(words[4]==1 and words[5]==768 and 2<=words[6]<=64,'sink format/usage/capacity')
    require(words[7:11]==[0,0,w//2,h] and words[11:15]==[w//2,0,w//2,h],'exact half-open eye rectangles')
    require(words[15] in (0,1) and words[16] in (0,1,2,3) and words[17] in (0,1) and words[18] in (0,1),'closed raster orientation')

def plan(document, base_directory):
    closed(document,'schema compatibility sink scope budget');require(document['schema']=='quest.capture.controller.selection.v1','selection schema')
    c=document['compatibility'];closed(c,'firmware_id api_id abi driver_profile_sha256 profile artifact service binder_descriptor interface_version service_version vendor_permission loader_policy signature_policy default_policy privacy_policy local_policy')
    for key in ('firmware_id','api_id','abi','binder_descriptor','vendor_permission','loader_policy','signature_policy','default_policy','privacy_policy'):token(c[key])
    integer(c['interface_version']);integer(c['service_version']);digest(c['driver_profile_sha256']);service(c['service'])
    profile=artifact(c['profile'],base_directory);driver=artifact(c['artifact'],base_directory);require(profile==c['driver_profile_sha256'],'selected driver profile join differs')
    s=document['sink'];closed(s,'service apk pid start_ticks uid_row cmdline_base64 descriptor_words');service(s['service']);require(s['service']['permission']=='android.permission.DUMP','neutral sink DUMP contract required')
    apk=artifact(s['apk'],base_directory);integer(s['pid']);integer(s['start_ticks']);descriptor(s['descriptor_words'])
    row=s['uid_row'];require(type(row) is list and len(row)==4,'four UID rows required')
    for value in row:integer(value,1,2147483647);require(value==s['service']['uid'],'UID row join differs')
    require(type(s['cmdline_base64']) is str,'exact cmdline bytes required')
    try:cmdline=base64.b64decode(s['cmdline_base64'],validate=True)
    except Exception as error:raise ValueError('invalid cmdline encoding') from error
    require(cmdline==(s['service']['package']+'\0').encode('utf-8'),'exact NUL-delimited cmdline join differs')
    scope=document['scope'];closed(scope,'target_id boot_id lease_id clock_domain native_pid native_start_ticks observation_serial action_valid_until_ms cleanup_valid_until_ms request_id session_id nonce prior_custody_resolved sole_operator_declared one_own_global_stop_authorized foreign_capture_observed')
    for key in ('target_id','boot_id','lease_id','clock_domain','request_id','session_id'):token(scope[key])
    for key in ('native_pid','native_start_ticks','observation_serial','action_valid_until_ms','cleanup_valid_until_ms'):integer(scope[key])
    require(scope['cleanup_valid_until_ms']>=scope['action_valid_until_ms'] and scope['request_id']!=scope['session_id'],'scope identity/time join')
    require(type(scope['nonce']) is str and re.fullmatch('[0-9a-f]{32}',scope['nonce']),'explicit fresh nonce declaration required')
    require(scope['prior_custody_resolved'] is True and scope['sole_operator_declared'] is True and scope['one_own_global_stop_authorized'] is True and scope['foreign_capture_observed'] is False,'prior custody/sole own Stop declarations required')
    b=document['budget'];closed(b,'run_ms observation_ms join_ms local_grace_ms local_policy')
    for key,maximum in [('run_ms',600000),('observation_ms',10000),('join_ms',10000),('local_grace_ms',60000)]:integer(b[key],1,maximum)
    require(c['local_policy']==b['local_policy']=='accepted_stop_grace_or_original_death','explicit compatibility-reviewed local wrapper policy required')
    return {'schema':'quest.capture.controller.plan.v1','valid':True,'runtimeAdmitted':False,'startCalls':0,'stopCalls':0,
        'verifiedLocalHashes':[profile,driver,apk],
        'livePrerequisites':['genuine-framework-Shell-and-attribution','actual-exact-permissions-artifacts-Binder-and-privacy','adjacent-original-owner-boot-lease-and-serial','current-exact-original-publisher-Surface','global-capture-idle-and-output-exclusion-admission','original-worker-monitor-and-external-process-custody','build-selected-reviewed-private-vendor-provider'],
        'vendorRetirementAcknowledged':False,'appReaderDrainConfirmed':False}

def validate_terminal(document, expected_identity):
    keys='request_id session_id nonce target_id boot_id helper_pid helper_start_ticks host_invocation_id'
    closed(expected_identity,keys);closed(document,'schema identity local_retired vendor_retirement_ack external_custody');closed(document['identity'],keys)
    require(document['schema']=='quest.capture.controller.terminal_declaration.v1' and document['identity']==expected_identity,'original terminal declaration join differs')
    for identity in (expected_identity,document['identity']):
        for key in ('helper_pid','helper_start_ticks'):integer(identity[key])
        for key in ('request_id','session_id','nonce','target_id','boot_id','host_invocation_id'):token(identity[key])
    require(document['local_retired'] is True and document['vendor_retirement_ack'] is False,'helper-local terminal cannot grant vendor ACK')
    custody=document['external_custody'];closed(custody,'stdout_eof stderr_eof child_reaped device_helper_birth_absent host_original_pipe_custody original_invocation_handles_resolved')
    require(all(value is True for value in custody.values()),'original EOF/reap/birth/pipe/handle declaration incomplete')
    return {'declaredCustodyComplete':True,'runtimeAdmitted':False,'vendorRetirementAcknowledged':False,'appReaderDrainConfirmed':False,'observationTruthVerified':False}

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation",choices=("plan","validate"))
    parser.add_argument("input",type=Path)
    args=parser.parse_args(argv)
    result=plan(load(args.input.read_bytes()),args.input.parent)
    print(json.dumps(result,sort_keys=True))
    return 0

if __name__=="__main__":
    raise SystemExit(main())
