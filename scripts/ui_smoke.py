#!/usr/bin/env python3
"""Own-app UI checks on the dedicated Guilong emulator; never targets another device."""
import subprocess,sys,time,re,json,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ADB=[str(ROOT/'scripts/adb.sh')]
def adb(*args):return subprocess.check_output(ADB+list(args),text=True)
def dump():
    adb('shell','uiautomator','dump','/sdcard/guilong-ui.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/guilong-ui.xml'))
def nodes():return list(dump().iter('node'))
def tap_node(n):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.3)
def tap(value,attr=None):
    found=[n for n in nodes() if (n.attrib.get(attr)==value if attr else n.attrib.get('text')==value or n.attrib.get('content-desc')==value)]
    if not found: raise AssertionError('Missing UI: '+value)
    clickable=[n for n in found if n.attrib.get('clickable')=='true'];tap_node((clickable or found)[0])
def fill(label,value):
    found=[n for n in nodes() if n.attrib.get('class')=='android.widget.EditText' and n.attrib.get('content-desc')==label]
    if not found: raise AssertionError('Missing input: '+label)
    tap_node(found[0]);adb('shell','input','text',value);adb('shell','input','keyevent','111');time.sleep(.2)
def has(text):return any(n.attrib.get('text')==text for n in nodes())
def assert_text(text):assert has(text),'Missing expected text '+text

def account(name,kind,cash):
    tap('账户','content-desc');tap('添加账户');fill('账户名称',name)
    spinner=[n for n in nodes() if n.attrib.get('class')=='android.widget.Spinner'][0];tap_node(spinner);tap(kind)
    fill('现金余额（元）',cash);tap('保存');assert_text(name)
def screenshot(name):
    target=ROOT/'docs/qa'/name
    with target.open('wb') as f:subprocess.run(ADB+['exec-out','screencap','-p'],stdout=f,check=True)
if __name__=='__main__':
    step=sys.argv[1]
    if step=='accounts':
        account('QA_Bank','银行存款','350000');account('QA_Stocks','证券账户','80000');tap('总览','content-desc');assert_text('430,000.00');print('PASS create bank+securities via UI; total 430,000.00')
    elif step=='holding':
        tap('持仓','content-desc');tap('添加持仓');fill('A 股 / ETF 代码','510300');fill('持仓数量（股 / 份）','1000');fill('每股 / 每份成本（元）','4.10');tap('保存并更新行情');time.sleep(3)
        assert_text('沪深300ETF华泰柏瑞'); state=json.loads(adb('shell','run-as','com.qianwang.privatewealth','cat','files/wealth-v1.json')); holding=state['holdings'][0]; assert holding['quoteTime'] and holding['lastPriceMicros']>0; from decimal import Decimal, ROUND_HALF_UP; market=(Decimal(holding['lastPriceMicros'])*holding['quantity']/1000000).quantize(Decimal('.01'),rounding=ROUND_HALF_UP); assert_text(f'{market:,.2f}'); screenshot('holdings.png'); tap('总览','content-desc'); total=sum(a['cashFen'] for a in state['accounts'])/100+float(market); assert_text(f'{total:,.2f}'); screenshot('overview.png'); print(f'PASS live quote + valuation: market={market}, total={total}, timestamp={holding["quoteTime"]}')
    elif step=='transfer':
        before=json.loads(adb('shell','run-as','com.qianwang.privatewealth','cat','files/wealth-v1.json')); tap('账户','content-desc');tap('账户间划转');fill('划转金额（元）','10000');tap('确认划转');after=json.loads(adb('shell','run-as','com.qianwang.privatewealth','cat','files/wealth-v1.json')); assert sum(a['cashFen'] for a in before['accounts'])==sum(a['cashFen'] for a in after['accounts']); assert before['holdings']==after['holdings']; tap('总览','content-desc'); print('PASS transfer preserves total cash and holdings')
    elif step=='privacy':
        tap('隐藏金额','content-desc');texts=[n.attrib.get('text','') for n in nodes()];assert not any(re.search(r'\d,\d{3}\.\d{2}', t) for t in texts);assert '••••••' in texts;tap('显示金额','content-desc');print('PASS privacy toggle')
    elif step=='capture':
        tap('账户','content-desc');screenshot('accounts.png');tap('总览','content-desc');screenshot('overview.png')
    else: raise ValueError(step)
