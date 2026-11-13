# SPDX-License-Identifier: AGPL-3.0-or-later
import base64
import builtins
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import unittest
from unittest import mock

MODULE=Path(__file__).resolve().parents[1]/"controller_plan.py"
def module():
    spec=importlib.util.spec_from_file_location("neutral_controller_plan",MODULE)
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

class PlanTests(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.addCleanup(self.temporary.cleanup);self.root=Path(self.temporary.name)
        def artifact(name,data):
            (self.root/name).write_bytes(data)
            return {"path":name,"sha256":"sha256:"+hashlib.sha256(data).hexdigest(),"size_bytes":len(data)}
        driver=artifact("synthetic-driver.bin",b"authored opaque fixture");apk=artifact("synthetic-app.bin",b"authored app fixture");profile=artifact("synthetic-profile.json",b"authored profile fixture")
        def service(package,permission,uid):return dict(package=package,name=package+".Service",permission=permission,uid=uid,exported=True,enabled=True)
        self.value={"schema":"quest.capture.controller.selection.v1",
            "compatibility":{"firmware_id":"synthetic-firmware","api_id":"synthetic-api","abi":"synthetic-abi","driver_profile_sha256":profile['sha256'],"profile":profile,"artifact":driver,"service":service("example.fixture.driver","example.fixture.CAPTURE",1001),"binder_descriptor":"example.fixture.TypedCapture","interface_version":1,"service_version":2,"vendor_permission":"example.fixture.CAPTURE","loader_policy":"typed-loader-v1","signature_policy":"signature-v1","default_policy":"defaults-v1","privacy_policy":"normal-privacy-v1","local_policy":"accepted_stop_grace_or_original_death"},
            "sink":{"service":service("example.fixture.app","android.permission.DUMP",1002),"apk":apk,"pid":77,"start_ticks":88,"uid_row":[1002]*4,"cmdline_base64":base64.b64encode(b"example.fixture.app\0").decode(),"descriptor_words":[1,41,2560,1280,1,768,4,0,0,1280,1280,1280,0,1280,1280,0,0,0,0]},
            "scope":{"target_id":"fixture-target","boot_id":"fixture-boot","lease_id":"fixture-lease","clock_domain":"fixture-monotonic","native_pid":5,"native_start_ticks":100,"observation_serial":10,"action_valid_until_ms":5000,"cleanup_valid_until_ms":8000,"request_id":"fixture-request","session_id":"fixture-session","nonce":"0123456789abcdef0123456789abcdef","prior_custody_resolved":True,"sole_operator_declared":True,"one_own_global_stop_authorized":True,"foreign_capture_observed":False},
            "budget":{"run_ms":100,"observation_ms":200,"join_ms":100,"local_grace_ms":50,"local_policy":"accepted_stop_grace_or_original_death"}}
    def test_import_plan_and_validate_are_inert_and_never_admit_runtime(self):
        original_import=builtins.__import__
        def guarded(name,*args,**kwargs):
            if name.split('.')[0] in {'subprocess','socket','requests','adb','private_executor'}:raise AssertionError("execution import "+name)
            return original_import(name,*args,**kwargs)
        config=self.root/'selection.json';config.write_text(json.dumps(self.value),encoding='utf-8');before=config.read_bytes()
        with mock.patch('builtins.__import__',side_effect=guarded),mock.patch.object(subprocess,'run',side_effect=AssertionError("subprocess")),mock.patch.object(socket,'socket',side_effect=AssertionError("socket")):
            tool=module();result=tool.plan(self.value,self.root);self.assertIs(result['runtimeAdmitted'],False)
            for command in ['plan','validate']:
                output=io.StringIO()
                with contextlib.redirect_stdout(output):self.assertEqual(0,tool.main([command,str(config)]))
                self.assertIs(json.loads(output.getvalue())['runtimeAdmitted'],False)
        self.assertEqual(before,config.read_bytes())
    def test_closed_duplicate_and_numeric_input_rejects(self):
        tool=module();text=json.dumps(self.value)
        with self.assertRaises(ValueError):tool.load(text.replace('{','{"schema":"quest.capture.controller.selection.v1",',1))
        with self.assertRaises(ValueError):tool.load(text.replace('100,','100.0,',1))
        for change in [lambda v:v.update(command='execute'),lambda v:v['budget'].update(run_ms=True),lambda v:v['sink'].update(uid_row=[1002]*3),lambda v:v['sink'].update(cmdline_base64=base64.b64encode(b'example.fixture.app').decode()),lambda v:v['sink']['descriptor_words'].__setitem__(1,True),lambda v:v['scope'].update(prior_custody_resolved=False),lambda v:v['scope'].update(foreign_capture_observed=True),lambda v:v['compatibility'].update(runtime_class='arbitrary.Loader')]:
            value=json.loads(text);change(value)
            with self.assertRaises(ValueError):tool.plan(value,self.root)
        with self.assertRaises(ValueError):tool.load(b' '*65537)
    def test_hash_scope_join_and_unsafe_artifact_reject(self):
        tool=module();tool.plan(self.value,self.root)
        (self.root/'synthetic-driver.bin').write_bytes(b'changed')
        with self.assertRaises(ValueError):tool.plan(self.value,self.root)
        self.value['compatibility']['artifact']['path']='../outside'
        with self.assertRaises(ValueError):tool.plan(self.value,self.root)
    def test_terminal_declaration_cannot_replace_original_process_custody(self):
        tool=module();identity=dict(request_id='fixture-request',session_id='fixture-session',nonce='0123456789abcdef0123456789abcdef',target_id='fixture-target',boot_id='fixture-boot',helper_pid=12,helper_start_ticks=13,host_invocation_id='fixture-original')
        value={'schema':'quest.capture.controller.terminal_declaration.v1','identity':identity.copy(),'local_retired':True,'vendor_retirement_ack':False,'external_custody':dict(stdout_eof=True,stderr_eof=True,child_reaped=True,device_helper_birth_absent=True,host_original_pipe_custody=True,original_invocation_handles_resolved=True)}
        self.assertIs(tool.validate_terminal(value,identity)['runtimeAdmitted'],False)
        for key in value['external_custody']:
            changed=json.loads(json.dumps(value));changed['external_custody'][key]=False
            with self.assertRaises(ValueError):tool.validate_terminal(changed,identity)
        changed=json.loads(json.dumps(value));changed['identity']['helper_start_ticks']+=1
        with self.assertRaises(ValueError):tool.validate_terminal(changed,identity)
        changed=json.loads(json.dumps(value));changed['vendor_retirement_ack']=True
        with self.assertRaises(ValueError):tool.validate_terminal(changed,identity)
        one=identity.copy();one['helper_pid']=1;changed=json.loads(json.dumps(value));changed['identity']=one.copy();changed['identity']['helper_pid']=True
        with self.assertRaises(ValueError):tool.validate_terminal(changed,one)

if __name__=='__main__':unittest.main()
