#!/usr/bin/env python3
"""UI smoke test for our disposable 1080x2160 Android 15 emulator only.

Clears the emulator's CloudTown data. Requires an AOSP image with adb root.
Never targets a physical phone. Run after tools/build.py.
"""
from pathlib import Path
import os
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
ADB=ROOT/'.toolchain/platform-tools/adb'
SERIAL=os.environ.get('CITY_GAME_TEST_SERIAL','emulator-5580')
assert SERIAL.startswith('emulator-'), 'This test only runs on a disposable emulator'
def adb(*args, binary=False):
    return subprocess.check_output([str(ADB),'-s',SERIAL,*args],timeout=30,text=not binary)
assert adb('shell','getprop','ro.kernel.qemu').strip()=='1'
assert adb('shell','wm','size').strip().splitlines()[-1].endswith('1080x2160'), 'Reset emulator screen size before testing'
adb('root'); adb('wait-for-device')
adb('install','-r',str(ROOT/'dist/CloudTown.apk'))
adb('shell','pm','clear','com.cloudtown.game')
adb('logcat','-c')
adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
def launch():
    adb('shell','am','start','-W','-n','com.cloudtown.game/.MainActivity');time.sleep(1.2)
def tap(x,y,delay=.25):
    adb('shell','input','tap',str(round(x*2.7)),str(round(63+y*2.7)));time.sleep(delay)
def state():
    time.sleep(.4)
    tree=ET.fromstring(adb('shell','cat','/data/user/0/com.cloudtown.game/shared_prefs/cloudtown.xml'))
    raw=tree.find("string[@name='city']").text.split(';')
    if raw[0] in ('2','3'):
        cells={}
        for cell in raw[8+int(raw[7]):]:
            if not cell or cell.startswith(('P,','D,')): continue
            r,c,kind,level=cell.split(',');cells[(int(r),int(c))]=kind+','+level
        tiles=[cells[(r,c)] for r in range(9) for c in range(9)]
    else:
        tiles=raw[7:88]
    return {'coins':int(raw[1]),'quest':int(raw[4]),'tiles':tiles}
launch()
(ROOT/'dist/first-launch.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
tap(200,515) # welcome
tap(353,210) # pause for deterministic accounting
tap(181,357) # house (3,2)
tap(143,376) # house (5,2)
before=state()
assert before['tiles'][3*9+2]=='1,1' and before['tiles'][5*9+2]=='1,1',before
assert before['coins']==220,before
tap(330,155) # quest claim
assert state()['quest']==1
tap(181,337,delay=.4) # allow single tap to resolve before pressing the inspector's button
tap(200,664) # locked upgrade before 60 residents
upgraded=state()
assert upgraded['tiles'][3*9+2]=='1,1',upgraded
assert upgraded['coins']==320,upgraded
tap(363,579) # inspector close
adb('shell','am','force-stop','com.cloudtown.game');launch()
restored=state()
assert restored['tiles']==upgraded['tiles'] and restored['quest']==1 and restored['coins']==320,restored
(ROOT/'dist/screenshot.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
errors=adb('logcat','-d','-s','AndroidRuntime:E')
assert 'FATAL EXCEPTION' not in errors,errors
print('PASS: offline launch, touch construction, pause, reward, roof selection, locked upgrade, persistence, no Android crashes')
