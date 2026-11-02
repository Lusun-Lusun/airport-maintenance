"""Local compiler workaround: exploded dependency classpath avoids JDK Windows ZIP realpath failure."""
from pathlib import Path
import zipfile,subprocess,urllib.request,os,sys
p=Path(__file__).resolve().parents[1]
runtime=p/'.runtime';deps=runtime/'compile-dependencies';deps.mkdir(exist_ok=True)
jars=[j for j in (runtime/'m2').rglob('*.jar') if not any(s in j.name for s in ['slf4j-api-1.','sources','javadoc','google-java-format'])]
for jar in jars:
 with zipfile.ZipFile(jar) as z:
  for member in z.infolist():
   if member.filename.endswith('.class') and not member.filename.startswith('META-INF/'):
    dest=deps/member.filename
    dest.parent.mkdir(parents=True,exist_ok=True)
    if len(str(dest)) < 245: dest.write_bytes(z.read(member))
jdk=runtime/'tools/jdk17.0.21_0/bin'
classes=p/'target/classes';tests=p/'target/test-classes';classes.mkdir(parents=True,exist_ok=True);tests.mkdir(parents=True,exist_ok=True)
sources=list((p/'src/main/java').rglob('*.java'))
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-parameters','--release','17','-cp',str(deps),'-d',str(classes),*[str(s) for s in sources]],check=True,cwd=p)
import shutil
shutil.copytree(p/'src/main/resources',classes,dirs_exist_ok=True)
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-parameters','--release','17','-cp',str(deps)+os.pathsep+str(classes),'-d',str(tests),*[str(s) for s in (p/'src/test/java').rglob('*.java')]],check=True,cwd=p)
console=runtime/'junit-platform-console-standalone-1.10.5.jar'
if not console.exists():console.write_bytes(urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.5/junit-platform-console-standalone-1.10.5.jar',timeout=60).read())
cp=os.pathsep.join([str(classes),str(tests),*[str(j) for j in jars]])
agent=next((runtime/'m2').rglob('org.jacoco.agent-0.8.12-runtime.jar'))
command=[str(jdk/'java.exe'),'-Dfile.encoding=UTF-8','-Duser.timezone=UTC','-javaagent:'+str(agent)+'=destfile='+str(p/'target/jacoco.exec'),'-jar',str(console),'execute','--class-path',cp,'--select-class','airport.RulesTest','--select-class','airport.WorkflowTest','--reports-dir',str(p/'reports/junit'),'--details','tree']
result=subprocess.run(command,cwd=p,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
(p/'reports/tests.log').write_bytes(result.stdout)
print(result.stdout.decode('utf-8',errors='replace')[-18000:])
if result.returncode:raise SystemExit(result.returncode)
print('Compiled and tested using exploded compile dependencies.')
