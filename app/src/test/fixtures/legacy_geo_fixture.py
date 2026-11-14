from pathlib import Path
import argparse, json, os, subprocess
parser=argparse.ArgumentParser(description="Actual ConfigBuilder Geo and Phase 1/2 regression fixtures")
parser.add_argument('--repo', type=Path, required=True)
parser.add_argument('--classpath-file', type=Path, required=True)
parser.add_argument('--android-jar', type=Path, required=True)
parser.add_argument('--jdk', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args=parser.parse_args()
NEKO=args.repo.resolve(); LOG=args.output.resolve(); JAVA=args.jdk.resolve()
LOG.mkdir(parents=True,exist_ok=True)
def run(name, command, cwd):
    p=subprocess.run([str(x) for x in command], cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (LOG/(name+'.log')).write_bytes(p.stdout)
    print(p.stdout.decode('utf-8',errors='replace'))
    return p.returncode

import zipfile

FIX=LOG/'config-fixture'
SRC=FIX/'src'
CLASSES=FIX/'classes'
JARS=FIX/'jars'
for path in [SRC,CLASSES,JARS]: path.mkdir(parents=True,exist_ok=True)
cp=[CLASSES, NEKO/'app/build/tmp/kotlin-classes/ossDebug',NEKO/'app/build/intermediates/javac/ossDebug/compileOssDebugJavaWithJavac/classes',args.android_jar.resolve()]
for index,path in enumerate(args.classpath_file.read_text().splitlines()):
    path=Path(path)
    if path.suffix=='.aar':
        with zipfile.ZipFile(path) as z:
            for name in z.namelist():
                if name.endswith('.jar'):
                    target=JARS/(str(index)+'-'+Path(name).name)
                    target.write_bytes(z.read(name));cp.append(target)
    else: cp.append(path)

def source(name,text):
    file=SRC/(name.replace('.','/')+'.java');file.parent.mkdir(parents=True,exist_ok=True);file.write_text(text,encoding='utf-8')

source('io.nekohasekai.sagernet.database.DataStore','''package io.nekohasekai.sagernet.database;
public class DataStore {
 public static final DataStore INSTANCE=new DataStore();
 public static int ipv6=1,sniff=2; public static boolean fake=true,resolve=true,dnsRouting=true;
 public static String remote="https://dns.example/dns-query",direct="1.1.1.1",custom="";
 public String getServiceMode(){return "vpn";}
 public boolean getAllowAccess(){return false;}
 public String getRemoteDns(){return remote;}
 public String getDirectDns(){return direct;}
 public boolean getEnableDnsRouting(){return dnsRouting;}
 public boolean getEnableFakeDns(){return fake;}
 public int getTrafficSniffing(){return sniff;}
 public int getIpv6Mode(){return ipv6;}
 public boolean getEnableClashAPI(){return false;}
 public boolean getGlobalAllowInsecure(){return false;}
 public int getLogLevel(){return 2;}
 public int getTunImplementation(){return 1;}
 public int getMtu(){return 1500;}
 public boolean getResolveDestination(){return resolve;}
 public int getMixedPort(){return 2080;}
 public boolean getBypassLanInCore(){return true;}
 public String getGlobalCustomConfig(){return custom;}
 public io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore getConfigurationStore(){return new io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore();}
}
''')
source('io.nekohasekai.sagernet.database.preference.RoomPreferenceDataStore','''package io.nekohasekai.sagernet.database.preference;
public class RoomPreferenceDataStore {
 public String getString(String key){return key.equals("domain_strategy_for_server")?"prefer_ipv4":"";}
}
''')
source('io.nekohasekai.sagernet.database.SagerDatabase','''package io.nekohasekai.sagernet.database;
import java.util.*;
import java.lang.reflect.Proxy;
public class SagerDatabase {
 public static final Companion Companion=new Companion();
 public static ProxyGroup group=new ProxyGroup();
 public static List<ProxyEntity> entities=new ArrayList<>();
 public static List<RuleEntity> rules=new ArrayList<>();
 public static class Companion {
  public ProxyGroup.Dao getGroupDao(){return (ProxyGroup.Dao)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ProxyGroup.Dao.class},(o,m,a)->m.getName().equals("getById")?group:null);}
  public ProxyEntity.Dao getProxyDao(){return (ProxyEntity.Dao)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ProxyEntity.Dao.class},(o,m,a)->{
   if(m.getName().equals("getByGroup"))return entities;
   if(m.getName().equals("getById"))return entities.stream().filter(e->e.getId()==(Long)a[0]).findFirst().orElse(null);
   if(m.getName().equals("getEntities"))return entities.stream().filter(e->((List<?>)a[0]).contains(e.getId())).collect(java.util.stream.Collectors.toList());
   throw new UnsupportedOperationException(m.toString());
  });}
  public RuleEntity.Dao getRulesDao(){return (RuleEntity.Dao)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{RuleEntity.Dao.class},(o,m,a)->rules);}
 }
}
''')
source('android.util.Base64','''package android.util;
public class Base64 {
 public static final int DEFAULT=0,NO_PADDING=1,NO_WRAP=2,CRLF=4,URL_SAFE=8;
 public static byte[] decode(String s,int flags){return java.util.Base64.getMimeDecoder().decode(s);}
 public static byte[] encode(byte[] s,int flags){return java.util.Base64.getEncoder().encode(s);}
 public static String encodeToString(byte[] s,int flags){return java.util.Base64.getEncoder().encodeToString(s);}
}
''')
# UtilsKt initializes Android-only Socket reflection before this pure helper.
# Match its one-line implementation; all formatter and migration code is real.
source('io.nekohasekai.sagernet.ktx.UtilsKt','''package io.nekohasekai.sagernet.ktx;
public class UtilsKt { public static String blankAsNull(String value){return value==null||kotlin.text.StringsKt.isBlank(value)?null:value;} }
''')
source('ConfigFixture','''import io.nekohasekai.sagernet.database.*;
import io.nekohasekai.sagernet.fmt.*;
import io.nekohasekai.sagernet.fmt.socks.*;
import io.nekohasekai.sagernet.fmt.wireguard.*;
import java.nio.file.*;
public class ConfigFixture {
 static void protocol(Path output,String name,int type,String beanClass,String setter,Object[][] fields)throws Exception {
  Class<?> cls=Class.forName(beanClass);AbstractBean bean=(AbstractBean)cls.getConstructor().newInstance();bean.initializeDefaultValues();bean.name=name;bean.serverAddress="127.0.0.1";bean.serverPort=443;
  for(Object[] field:fields)cls.getField((String)field[0]).set(bean,field[1]);
  ProxyEntity proxy=new ProxyEntity();proxy.setId(100);proxy.setGroupId(1);proxy.setType(type);ProxyEntity.class.getMethod(setter,cls).invoke(proxy,bean);
  Files.writeString(output.resolve("protocol-"+name+".json"),ConfigBuilderKt.buildConfig(proxy,false,false).getConfig());
 }
 public static void main(String[] args)throws Exception {
  Path output=Paths.get(args[0]);Files.createDirectories(output);
  SOCKSBean bean=new SOCKSBean();bean.initializeDefaultValues();bean.name="fixture";bean.serverAddress="127.0.0.1";bean.serverPort=1080;
  ProxyEntity proxy=new ProxyEntity();proxy.setId(1);proxy.setGroupId(1);proxy.setType(0);proxy.setSocksBean(bean);SagerDatabase.entities.add(proxy);
  for(int ip=0;ip<4;ip++)for(int sniff=0;sniff<3;sniff++){
   DataStore.ipv6=ip;DataStore.sniff=sniff;
   Files.writeString(output.resolve("socks-ip"+ip+"-sniff"+sniff+".json"),ConfigBuilderKt.buildConfig(proxy,false,false).getConfig());
  }
  Files.writeString(output.resolve("socks-urltest.json"),ConfigBuilderKt.buildConfig(proxy,true,false).getConfig());
  String[] dnsAddresses={"8.8.8.8:5353","2001:4860:4860::8888","[2001:4860:4860::8888]:5353","udp://1.1.1.1","tcp://1.1.1.1:5353","tls://dns.example","quic://dns.example","https://dns.example:8443/custom-path","h3://dns.example/dns-query","local","local://"};
  for(int i=0;i<dnsAddresses.length;i++){
   DataStore.remote=dnsAddresses[i];
   Files.writeString(output.resolve("dns-"+i+".json"),ConfigBuilderKt.buildConfig(proxy,false,false).getConfig());
  }
  DataStore.remote="https://dns.example/dns-query";
  WireGuardBean wg=new WireGuardBean();wg.initializeDefaultValues();wg.name="wg";wg.serverAddress="127.0.0.1";wg.serverPort=51820;
  byte[] privateKey=new byte[32],publicKey=new byte[32];java.util.Arrays.fill(privateKey,(byte)1);java.util.Arrays.fill(publicKey,(byte)2);
  wg.localAddress="10.0.0.2/32,fd00::2/128";wg.privateKey=java.util.Base64.getEncoder().encodeToString(privateKey);wg.peerPublicKey=java.util.Base64.getEncoder().encodeToString(publicKey);wg.reserved="1,128,255";
  proxy.setType(18);proxy.setWgBean(wg);DataStore.ipv6=1;DataStore.sniff=2;
  Files.writeString(output.resolve("wireguard.json"),ConfigBuilderKt.buildConfig(proxy,false,false).getConfig());
  proxy.setId(2);ProxyEntity socks=new ProxyEntity();socks.setId(1);socks.setGroupId(1);socks.setSocksBean(bean);SagerDatabase.entities.add(socks);
  SagerDatabase.group.setId(1);SagerDatabase.group.setSelector(true);
  Files.writeString(output.resolve("selector-wireguard-socks.json"),ConfigBuilderKt.buildConfig(proxy,false,false).getConfig());
  SagerDatabase.group.setSelector(false);
  io.nekohasekai.sagernet.fmt.internal.ChainBean chain=new io.nekohasekai.sagernet.fmt.internal.ChainBean();chain.initializeDefaultValues();chain.name="chain";chain.proxies=java.util.Arrays.asList(1L,2L);
  ProxyEntity chained=new ProxyEntity();chained.setId(3);chained.setGroupId(1);chained.setType(8);chained.setChainBean(chain);
  Files.writeString(output.resolve("chain-socks-wireguard.json"),ConfigBuilderKt.buildConfig(chained,false,false).getConfig());
  chain.proxies=java.util.Arrays.asList(2L,1L);
  Files.writeString(output.resolve("chain-wireguard-socks.json"),ConfigBuilderKt.buildConfig(chained,false,false).getConfig());
  String fmt="io.nekohasekai.sagernet.fmt.";
  protocol(output,"http",1,fmt+"http.HttpBean","setHttpBean",new Object[][]{});
  protocol(output,"shadowsocks",2,fmt+"shadowsocks.ShadowsocksBean","setSsBean",new Object[][]{{"method","aes-128-gcm"},{"password","fixture-password"}});
  protocol(output,"vmess",4,fmt+"v2ray.VMessBean","setVmessBean",new Object[][]{{"uuid","00000000-0000-4000-8000-000000000001"}});
  protocol(output,"vless",4,fmt+"v2ray.VMessBean","setVmessBean",new Object[][]{{"uuid","00000000-0000-4000-8000-000000000001"},{"alterId",-1}});
  protocol(output,"trojan",6,fmt+"trojan.TrojanBean","setTrojanBean",new Object[][]{{"password","fixture-password"}});
  protocol(output,"hysteria",15,fmt+"hysteria.HysteriaBean","setHysteriaBean",new Object[][]{{"protocolVersion",1},{"serverPorts","443"},{"uploadMbps",10},{"downloadMbps",10}});
  protocol(output,"hysteria2",15,fmt+"hysteria.HysteriaBean","setHysteriaBean",new Object[][]{{"protocolVersion",2},{"serverPorts","443"},{"authPayload","fixture-password"}});
  protocol(output,"tuic",20,fmt+"tuic.TuicBean","setTuicBean",new Object[][]{{"protocolVersion",5},{"uuid","00000000-0000-4000-8000-000000000001"},{"token","fixture-password"}});
  protocol(output,"anytls",22,"moe.matsuri.nb4a.proxy.anytls.AnyTLSBean","setAnyTLSBean",new Object[][]{{"password","fixture-password"}});

  Path remote=output.getParent().resolve("remotes"); Files.createDirectories(remote.resolve("cache")); Files.createDirectories(remote.resolve("configs"));
  for(String tag:new String[]{"first","second"}) {
    Path cache=remote.resolve("cache").resolve(tag+".json");
    Files.writeString(cache,"{\\\"version\\\":3,\\\"rules\\\":[{\\\"domain\\\":[\\\""+tag+".invalid\\\"]}]}");
    moe.matsuri.nb4a.SingBoxOptions.RuleSet set=new moe.matsuri.nb4a.SingBoxOptions.RuleSet();
    set.type="local";set.tag=tag;set.format="source";set.path=cache.toAbsolutePath().toString();RemoteRuleSetManager.sets.add(set);
  }
  SagerDatabase.rules.clear();
  RuleEntity ordinary=new RuleEntity();ordinary.setId(90);ordinary.setDomains("ordinary.invalid");ordinary.setOutbound(-1);SagerDatabase.rules.add(ordinary);
  RuleEntity remoteRule=new RuleEntity();remoteRule.setId(91);remoteRule.setRemoteRuleSetTags("first\\nsecond");SagerDatabase.rules.add(remoteRule);
  RuleEntity site=new RuleEntity();site.setId(92);site.setDomains("geosite:github");site.setOutbound(-1);SagerDatabase.rules.add(site);
  RuleEntity ipRule=new RuleEntity();ipRule.setId(93);ipRule.setIp("geoip:us");ipRule.setOutbound(-1);SagerDatabase.rules.add(ipRule);
  proxy.setType(0);
  for(long outbound:new long[]{0,-1,-2,1}) {
    remoteRule.setOutbound(outbound);
    Files.writeString(remote.resolve("configs").resolve("remote-outbound-"+outbound+".json"),ConfigBuilderKt.buildConfig(proxy,false,false).getConfig());
  }

// Insert in the existing actual ConfigBuilder JVM fixture after its original 37 cases.
Path geoOutput = output.getParent().resolve("geo-configs");
Files.createDirectories(geoOutput);
proxy.setType(0);
String[][] geoInputs = {
 {"site", "geosite:github", ""},
 {"ip", "", "geoip:us"},
 {"mixed", "geosite:github", "geoip:us"},
 {"multiple", "geosite:github\\ngeosite:google", "geoip:us\\ngeoip:cn"}
};
for (String[] input : geoInputs) for (boolean dnsRouting : new boolean[]{false,true})
for (boolean fake : new boolean[]{false,true}) for (long outbound : new long[]{0,-1,-2,1}) {
 SagerDatabase.rules.clear();
 RuleEntity geo = new RuleEntity(); geo.setId(99); geo.setEnabled(true);
 geo.setDomains(input[1]); geo.setIp(input[2]); geo.setOutbound(outbound);
 SagerDatabase.rules.add(geo);
 DataStore.dnsRouting=dnsRouting; DataStore.fake=fake;
 String name=input[0]+"-dns"+dnsRouting+"-fake"+fake+"-out"+outbound;
 Files.writeString(geoOutput.resolve(name+".json"), ConfigBuilderKt.buildConfig(proxy,false,false).getConfig());
}
DataStore.dnsRouting=true; DataStore.fake=true;
for (String variant : new String[]{"remote", "constrained", "invert", "direct"}) {
 SagerDatabase.rules.clear();
 RuleEntity geo=new RuleEntity(); geo.setId(99); geo.setEnabled(true);
 geo.setDomains("geosite:github"); geo.setIp("geoip:us"); geo.setOutbound(-1);
 if (variant.equals("remote")) geo.setRemoteRuleSetTags("first\\nsecond");
 if (variant.equals("constrained")) {
  geo.setPort("443"); geo.setSourcePort("1500"); geo.setSource("172.16.0.0/16");
  geo.setNetwork("tcp"); geo.setProtocol("tls"); geo.setConfig("{\\"user_id\\":[10001]}");
 }
 if (variant.equals("invert")) geo.setConfig("{\\"invert\\":true}");
 if (variant.equals("direct")) geo.setConfig("{\\"outbound\\":\\"direct\\"}");
 RuleEntity beforeGeo=new RuleEntity();beforeGeo.setDomains("before.invalid");beforeGeo.setOutbound(-1);
 RuleEntity afterGeo=new RuleEntity();afterGeo.setDomains("after.invalid");afterGeo.setOutbound(-2);
 SagerDatabase.rules.add(beforeGeo);SagerDatabase.rules.add(geo);SagerDatabase.rules.add(afterGeo);
 Files.writeString(geoOutput.resolve("mixed-"+variant+".json"),ConfigBuilderKt.buildConfig(proxy,false,false).getConfig());
}
System.out.println("Generated 68 Geo fixtures from actual ConfigBuilder");

 }
}
''')

source('io.nekohasekai.sagernet.database.RemoteRuleSetManager', 'package io.nekohasekai.sagernet.database;\nimport java.util.*;\nimport moe.matsuri.nb4a.SingBoxOptions.RuleSet;\npublic class RemoteRuleSetManager {\n public static final RemoteRuleSetManager INSTANCE = new RemoteRuleSetManager();\n public static java.util.List<RuleSet> sets = new ArrayList<>();\n public java.util.List<RuleSet> config(java.util.List<RuleEntity> rules) { return sets; }\n}\n')

argfile=FIX/'javac.args'
args=['-classpath',os.pathsep.join(str(p) for p in cp),'-d',str(CLASSES)]+[str(p) for p in SRC.rglob('*.java')]
argfile.write_text('\n'.join('"'+str(a).replace('\\','/')+'"' for a in args),encoding='utf-8')
executable_suffix = '.exe' if os.name == 'nt' else ''
assert run('config-fixture-compile',[JAVA/('bin/javac'+executable_suffix),'@'+str(argfile)],FIX)==0
argfile=FIX/'java.args'
args=['-classpath',os.pathsep.join(str(p) for p in cp),'ConfigFixture',str(FIX/'configs')]
argfile.write_text('\n'.join('"'+str(a).replace('\\','/')+'"' for a in args),encoding='utf-8')
result = run('actual-config-builder',[JAVA/('bin/java'+executable_suffix),'@'+str(argfile)],FIX)
if result:
    raise SystemExit(result)
# A remote-only route must produce matching DNS conditions for direct, proxy and block.
for outbound in [0, -1, -2]:
    config = json.loads((FIX/'remotes/configs'/f'remote-outbound-{outbound}.json').read_text())
    assert any(set(rule.get('rule_set', [])) == {'first', 'second'}
               for rule in config['dns']['rules']), f'Missing remote DNS conditions: {outbound}'
print('Remote-only routes preserve rule-set conditions in DNS rules')
