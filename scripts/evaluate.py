import urllib.request,json,time,statistics,sys,uuid,pathlib
base=sys.argv[1] if len(sys.argv)>1 else 'http://127.0.0.1:8080'
def get(path,body=None):
 data=json.dumps(body).encode() if body is not None else None
 req=urllib.request.Request(base+'/api/'+path,data=data,headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(req,timeout=20) as r:return json.loads(r.read())
def run():
 result={'window':'2026-08-01T00:00:00Z / 2026-11-01T00:00:00Z (UTC)','syntheticData':True}
 for name,path in [('health','health'),('equipment','equipment'),('technicians','technicians'),('orders','orders'),('messages','messages'),('notifications','notifications'),('stats','stats?from=2026-08-01T00:00:00Z&to=2026-11-01T00:00:00Z')]:result[name]=get(path)
 metrics=result['stats']
 expected={'equipmentCount':30,'orderCount':120,'mttrHours':3.8666666666666667,'downtimeHours':584.0,'availabilityPercent':99.11835748792271}
 result['expected']=expected
 result['checks']={k:abs(float(metrics[k])-v)<1e-7 if isinstance(v,float) else metrics[k]==v for k,v in expected.items()}
 equipment=get('equipment')[0];key='eval-'+str(uuid.uuid4());before=len(get('orders'))
 get('inspections',{'requestKey':key,'equipmentId':equipment['id'],'abnormal':True,'note':'evaluation workflow'})
 deadline=time.time()+12
 while time.time()<deadline:
  orders=get('orders')
  if len(orders)>before:break
  time.sleep(.25)
 result['workflow']={'createdWithin12Seconds':len(orders)==before+1,'priority':orders[0]['priority'] if len(orders)>before else None,'status':orders[0]['status'] if len(orders)>before else None,'state':orders[0].get('technician_name') if len(orders)>before else None}
 samples=[]
 for _ in range(80):
  t=time.perf_counter();get('health');samples.append((time.perf_counter()-t)*1000)
 samples.sort();result['httpProbe80']={'meanMs':statistics.mean(samples),'p50Ms':samples[39],'p95Ms':samples[75],'p99Ms':samples[79],'rpsSequential':80/(sum(samples)/1000)}
 result['checks']['workflowCreated']=result['workflow']['createdWithin12Seconds']
 result['checks']['noPending']=len(get('messages'))==len([m for m in get('messages') if m['state']=='DONE'])
 result['checks']['allPassed']=all(result['checks'].values())
 p=pathlib.Path(__file__).resolve().parents[1]/'reports'/'evaluation.json';p.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps(result,ensure_ascii=False,indent=2))
 if not result['checks']['allPassed']:raise SystemExit('evaluation mismatch')
if __name__=='__main__':run()
