from pathlib import Path
import json,random,datetime,shutil
p=Path(__file__).resolve().parents[1]
for f in (p/'src/main/java/airport').glob('*.java'):
 s=f.read_text(encoding='utf-8').replace(chr(2)+'4','$');f.write_text(s,encoding='utf-8')
rng=random.Random(20260801);sql=[];eq=[];tech=[]
systems=[('行李处理','BHS','机械'),('供配电','ELE','电气'),('空调通风','HVAC','暖通'),('登机桥','PBB','登机桥'),('助航灯光','AGL','灯光')]
for i in range(1,31):
 s,c,skill=systems[(i-1)%5];area=['T1','T2','飞行区'][(i-1)//10];e=dict(id=i,code=f'{c}-{i:03}',name=f'{s}设备 {i:02}',system=s,area=area,skill=skill,criticality=(i%3)+1,affectsFlights=i%4==0);eq.append(e)
 sql.append(f"INSERT INTO equipment(id,code,name,system_name,area,skill,criticality,affects_flights,status) VALUES({i},'{e['code']}','{e['name']}','{s}','{area}','{skill}',{e['criticality']},{str(e['affectsFlights']).upper()},'正常');")
for i in range(1,13):
 area=['T1','T2','飞行区'][(i-1)//4];skills=['机械,电气','暖通','登机桥','灯光'][(i-1)%4];t=dict(id=i,name=f'工程师{i:02}',area=area,skills=skills,capacity=3);tech.append(t);sql.append(f"INSERT INTO technician VALUES({i},'{t['name']}','{skills}','{area}',3,TRUE);")
records=[]
for i in range(1,121):
 eid=(i-1)%30+1;day=(i-1)//30*20+1;start=datetime.datetime(2026,8,1)+datetime.timedelta(days=day,hours=eid%8);hours=rng.choice([1,2,3,4,6,8]);repair=start+datetime.timedelta(hours=hours);accept=repair+datetime.timedelta(hours=1);e=eq[eid-1];priority='P1' if e['affectsFlights'] else {1:'P2',2:'P3',3:'P4'}[e['criticality']];tid=next(t['id'] for t in tech if t['area']==e['area'] and e['skill'] in t['skills'].split(','));records.append(dict(id=i,equipmentId=eid,openedAt=start.isoformat()+'Z',repairedAt=repair.isoformat()+'Z',acceptedAt=accept.isoformat()+'Z',repairHours=hours))
 sql.append(f"INSERT INTO inspection(id,request_key,equipment_id,abnormal,note,created_at) VALUES({i},'seed-{i}',{eid},TRUE,'合成历史巡检：功能异常','{start}');")
 sql.append(f"INSERT INTO outbox(inspection_id,state,attempts,created_at) VALUES({i},'DONE',1,'{start}');")
 sql.append(f"INSERT INTO work_order(id,inspection_id,equipment_id,priority,status,technician_id,opened_at,repaired_at,accepted_at,repair_note,acceptance_note) VALUES({i},{i},{eid},'{priority}','CLOSED',{tid},'{start}','{repair}','{accept}','更换组件并完成测试（合成记录）','运行正常，验收通过（合成记录）');")
for table,nextid in [('equipment',31),('inspection',121),('work_order',121),('outbox',121)]:sql.append(f'ALTER TABLE {table} ALTER COLUMN id RESTART WITH {nextid};')
(p/'src/main/resources/demo.sql').write_text(chr(10).join(sql),encoding='utf-8')
expected={'seed':20260801,'equipmentCount':30,'completedOrders':120,'windowFrom':'2026-08-01T00:00:00Z','windowTo':'2026-11-01T00:00:00Z','mttrHours':sum(r['repairHours'] for r in records)/120,'downtimeHours':sum(r['repairHours']+1 for r in records),'availabilityPercent':100*(1-sum(r['repairHours']+1 for r in records)/(30*92*24))}
for name,data in [('equipment',eq),('technicians',tech),('repair-history',records),('expected-metrics',expected)]: (p/f'datasets/{name}.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')

print(expected)
