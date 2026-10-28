// Isolated original proxies + independent vanilla backends. Optional original ViaVersion translation.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import net from 'node:net';
import http from 'node:http';
import zlib from 'node:zlib';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const workspace=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const option=name=>process.argv.find(arg=>arg.startsWith(`--${name}=`))?.slice(name.length+3);
const proxyKind=option('proxy')||'velocity';if(!['velocity','bungee','waterfall'].includes(proxyKind))throw Error('Unknown proxy');
const serverVersion=option('server')||(proxyKind==='waterfall'?'26.2':'26.3');
const fixtureVersion=['26.1.1','26.1.2','26.2'].includes(serverVersion)?'26.1':serverVersion;
const translated=process.argv.includes('--via');
const contentChecks=process.argv.includes('--content-checks');
const dimensionChecks=process.argv.includes('--dimensions');
if(dimensionChecks&&serverVersion!=='26.3')throw Error('The native custom-dimension fixture is pinned to Minecraft 26.3');
const clientTranslator=option('client-translator')||null;
const base=path.join(workspace,'build/proxy-network-runtime');fs.mkdirSync(base,{recursive:true});
const root=path.join(base,'run-'+Date.now());fs.mkdirSync(root);
let vanilla=path.join(process.env.USERPROFILE,`.gradle/caches/fabric-loom/${serverVersion}/minecraft-server.jar`),info;
if(fs.existsSync(path.join(path.dirname(vanilla),'mojang_minecraft_info.json')))info=JSON.parse(fs.readFileSync(path.join(path.dirname(vanilla),'mojang_minecraft_info.json'),'utf8'));
else{
 const manifest=await(await fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')).json();
 const entry=manifest.versions.find(v=>v.id===serverVersion);if(!entry)throw Error('Unknown vanilla version');info=await(await fetch(entry.url)).json();
 vanilla=path.join(workspace,'build/qa-network-downloads',`minecraft-${serverVersion}-server.jar`);
 if(!fs.existsSync(vanilla)){const response=await fetch(info.downloads.server.url);if(!response.ok)throw Error('Vanilla download failed');fs.mkdirSync(path.dirname(vanilla),{recursive:true});fs.writeFileSync(vanilla,Buffer.from(await response.arrayBuffer()));}
}
if(crypto.createHash('sha1').update(fs.readFileSync(vanilla)).digest('hex')!==info.downloads.server.sha1)throw Error('Vanilla server checksum mismatch');
const build=JSON.parse(fs.readFileSync(path.join(workspace,'scripts/qa/proxy-tools.json'),'utf8'));
const download=build.downloads['server:default'],velocity=path.join(workspace,'build/qa-proxy-downloads',download.name);
if(path.basename(download.name)!==download.name)throw Error('Invalid pinned proxy filename');
if(!fs.existsSync(velocity)){
 const response=await fetch(download.url,{headers:{'User-Agent':'WorldBinder-Local-QA/1.7.0'}});if(!response.ok)throw Error('Proxy download failed: '+response.status);
 const bytes=Buffer.from(await response.arrayBuffer());if(crypto.createHash('sha256').update(bytes).digest('hex')!==download.checksums.sha256)throw Error('Proxy download checksum mismatch');
 fs.mkdirSync(path.dirname(velocity),{recursive:true});fs.writeFileSync(velocity,bytes);
}
if(crypto.createHash('sha256').update(fs.readFileSync(velocity)).digest('hex')!==download.checksums.sha256)throw Error('Velocity checksum mismatch');
const tools=proxyKind!=='velocity'||translated?JSON.parse(fs.readFileSync(path.join(workspace,'scripts/qa/network-tools.json'),'utf8')):[];
function original(id){const tool=tools.find(t=>t.id===id);if(!tool)throw Error('Missing pinned original '+id);const file=path.join(workspace,'build/qa-network-downloads',tool.file);if(crypto.createHash(tool.algorithm).update(fs.readFileSync(file)).digest('hex')!==tool.hash)throw Error('Original tool checksum mismatch: '+id);return file;}
const proxyJar=proxyKind==='velocity'?velocity:original(proxyKind);
const java=path.join(process.env.ProgramFiles,'Java/jdk-25/bin/java.exe');
const runs=fs.readdirSync(path.join(workspace,'build/regression-runtime'),{withFileTypes:true}).filter(e=>e.isDirectory()&&fs.existsSync(path.join(workspace,'build/regression-runtime',e.name,'RESULT.txt'))&&fs.existsSync(path.join(workspace,'build/regression-runtime',e.name,`export-${fixtureVersion}`))).map(e=>path.join(workspace,'build/regression-runtime',e.name)).filter(p=>fs.readFileSync(path.join(p,'RESULT.txt'),'utf8').startsWith('PASS')).sort((a,b)=>fs.statSync(b).mtimeMs-fs.statSync(a).mtimeMs);
if(!runs.length)throw Error('Run regressionTest first for '+serverVersion);
const fixture=path.join(runs[0],`export-${fixtureVersion}`);
async function port(){const socket=net.createServer();await new Promise(resolve=>socket.listen(0,'127.0.0.1',resolve));const value=socket.address().port;await new Promise(resolve=>socket.close(resolve));return value;}
const ports={survival:await port(),lobby:await port(),proxy:await port()};
const children={},ready=new Set();let shuttingDown=false,proxyStarted=false;
function varInt(value){let n=value>>>0;const bytes=[];do{let b=n&127;n>>>=7;if(n)b|=128;bytes.push(b);}while(n);return Buffer.from(bytes);}
async function backendProtocol(){
 return new Promise((resolve,reject)=>{const socket=net.connect(ports.survival,'127.0.0.1');let bytes=Buffer.alloc(0);socket.setTimeout(5000,()=>socket.destroy(Error('Backend status timeout')));socket.on('error',reject);
  socket.on('connect',()=>{const host=Buffer.from('127.0.0.1'),port=Buffer.alloc(2);port.writeUInt16BE(ports.survival);const handshake=Buffer.concat([Buffer.from([0]),varInt(-1),varInt(host.length),host,port,Buffer.from([1])]);socket.write(Buffer.concat([varInt(handshake.length),handshake,Buffer.from([1,0])]));});
  socket.on('data',chunk=>{bytes=Buffer.concat([bytes,chunk]);const read=offset=>{let value=0,shift=0;for(let i=offset;i<bytes.length;i++){const b=bytes[i];value|=(b&127)<<shift;if(!(b&128))return{value,end:i+1};shift+=7;if(shift>28)throw Error('Invalid backend VarInt');}return null;};const length=read(0);if(!length||bytes.length<length.end+length.value)return;const id=read(length.end),text=read(id.end);const response=JSON.parse(bytes.toString('utf8',text.end,text.end+text.value));socket.end();resolve(response.version.protocol);});
 });
}
async function startProxy(){
 if(proxyStarted||!ready.has('survival')||!ready.has('lobby'))return;proxyStarted=true;
 if(translated){const protocol=await backendProtocol();const config=path.join(root,'proxy/plugins/ViaVersion');fs.mkdirSync(config,{recursive:true});fs.writeFileSync(path.join(config,'config.yml'),`config-version: 1\ninit-config-version: 1\nvelocity-ping-interval: -1\nvelocity-ping-save: false\nvelocity-servers:\n  default: ${protocol}\n  survival: ${protocol}\n  lobby: ${protocol}\n`);console.log('REAL_BACKEND_PROTOCOL '+protocol);}
 launch('proxy',proxyJar);
}
function launch(name,jar,args=[]){
 const cwd=path.join(root,name);fs.mkdirSync(cwd,{recursive:true});const log=fs.createWriteStream(path.join(cwd,'console.log'));
 const child=spawn(java,['-Xmx1536M','-jar',jar,...args],{cwd,windowsHide:true,stdio:['pipe','pipe','pipe']});children[name]=child;
 let buffer='';for(const stream of [child.stdout,child.stderr])stream.on('data',data=>{log.write(data);buffer+=data.toString();
  if(!ready.has(name)&&(name==='proxy'&&proxyKind!=='velocity'?buffer.includes('Listening on /127.0.0.1:'):buffer.includes('Done ('))){ready.add(name);console.log('READY '+name);
   if(name!=='proxy')child.stdin.write(`forceload add 0 0
fill -16 63 -16 16 63 16 grass_block
fill -2 63 -16 2 63 16 stone_bricks
fill -16 63 -2 16 63 2 stone_bricks
fill 6 64 6 12 67 12 oak_planks hollow
fill 5 68 5 13 68 13 red_terracotta
fill 8 65 6 10 66 6 glass
fill 6 64 8 6 65 9 air
fill -12 67 -11 -6 69 -5 oak_leaves[persistent=true]
fill -9 64 -8 -9 69 -8 oak_log
fill -12 63 6 -6 63 12 water
setblock 1 64 1 ${name==='survival'?'stone':'gold_block'}
setblock 2 64 2 chest
setworldspawn 1 65 1
`);
   if(name!=='proxy')startProxy().catch(error=>{console.error(error);shutdown();});
   if(ready.size===3){const control={root,host:`127.0.0.1:${ports.proxy}`,ports,api:`http://127.0.0.1:${api.address().port}/${token}`,proxy:proxyKind,proxyFile:path.basename(proxyJar),serverMinecraft:serverVersion,nativeTransfer:serverVersion.startsWith('26.'),contentChecks,dimensionChecks,clientTranslator,fullUi:!process.argv.includes('--minimal-ui'),translation:translated?tools.find(t=>t.id==='viaversion').version:null};fs.writeFileSync(path.join(base,'control.json'),JSON.stringify(control,null,2));fs.writeFileSync(path.join(root,'fixture.json'),JSON.stringify({...control,sourceFixture:fixture,vanillaSHA1:info.downloads.server.sha1},null,2));console.log('WORLDBINDER_PROXY_READY '+root);}
  }if(buffer.length>20000)buffer=buffer.slice(-10000);
 });child.on('exit',code=>{log.end();console.log('EXIT '+name+' '+code);if(!shuttingDown)shutdown();});return child;
}
function shutdown(){if(shuttingDown)return;shuttingDown=true;for(const [name,child]of Object.entries(children))if(child.exitCode===null)child.stdin.write(name==='proxy'?(proxyKind==='velocity'?'shutdown\n':'end\n'):'stop\n');setTimeout(()=>api.close(),1500).unref();}
const token=crypto.randomBytes(20).toString('hex');
const api=http.createServer(async(req,res)=>{
 if(req.url!=='/'+token){res.writeHead(404);res.end();return;}
 if(req.method==='POST'){try{let body='';for await(const chunk of req)body+=chunk;const request=JSON.parse(body);if(request.stop){shutdown();res.end('{}');return;}if(!children[request.target]||typeof request.command!=='string'||/[\r\n]/.test(request.command))throw Error('Invalid control command');children[request.target].stdin.write(request.command+'\n');res.end('{}');}catch(error){res.writeHead(400);res.end(String(error));}}
 else res.end(JSON.stringify({ready:[...ready]}));
});await new Promise(resolve=>api.listen(0,'127.0.0.1',resolve));
for(const name of ['survival','lobby']){
 const cwd=path.join(root,name);fs.mkdirSync(cwd);fs.cpSync(fixture,path.join(cwd,'world'),{recursive:true});
 if(dimensionChecks){
  const pack=path.join(cwd,'world/datapacks/worldbinder_native_dimensions');fs.mkdirSync(path.join(pack,'data/worldbinder/dimension'),{recursive:true});
  // 121.0 is the data pack version in the pinned original 26.3 server's version.json.
  fs.writeFileSync(path.join(pack,'pack.mcmeta'),JSON.stringify({pack:{min_format:[121,0],max_format:[121,0],description:'WorldBinder owned native dimension QA'}}));
  fs.writeFileSync(path.join(pack,'data/worldbinder/dimension/received_custom.json'),JSON.stringify({type:'minecraft:overworld',generator:{type:'minecraft:flat',settings:{biome:'minecraft:plains',layers:[{block:'minecraft:bedrock',height:1}],structure_overrides:[],lakes:false,features:false}}}));
 }
 if(contentChecks){const map=path.join(cwd,'world/data/map_7.dat'),raw=zlib.gunzipSync(fs.readFileSync(map));const marker=Buffer.from([1,0,6,...Buffer.from('locked'),0]),at=raw.indexOf(marker);if(at<0)throw Error('Missing locked-map source fixture');raw[at+marker.length-1]=1;fs.writeFileSync(map,zlib.gzipSync(raw));}
 fs.writeFileSync(path.join(cwd,'eula.txt'),'eula=true\n');
 fs.writeFileSync(path.join(cwd,'server.properties'),`level-name=world\nserver-ip=127.0.0.1\nserver-port=${ports[name]}\nonline-mode=false\nenforce-secure-profile=false\naccepts-transfers=true\nwhite-list=false\nview-distance=3\nsimulation-distance=2\nspawn-protection=0\nmax-tick-time=120000\n`);
 launch(name,vanilla,['nogui']);
}
const proxyFolder=path.join(root,'proxy');fs.mkdirSync(proxyFolder);
if(proxyKind==='velocity')fs.writeFileSync(path.join(proxyFolder,'velocity.toml'),`config-version = "2.7"\nbind = "127.0.0.1:${ports.proxy}"\nmotd = "WorldBinder isolated proxy QA"\nshow-max-players = 2\nonline-mode = false\nplayer-info-forwarding-mode = "NONE"\nforwarding-secret-file = "forwarding.secret"\n[servers]\nsurvival = "127.0.0.1:${ports.survival}"\nlobby = "127.0.0.1:${ports.lobby}"\ntry = ["survival"]\n[forced-hosts]\n"lobby.example.com" = ["survival"]\n"factions.example.com" = ["survival"]\n"minigames.example.com" = ["survival"]\n[advanced]\naccepts-transfers = true\nread-timeout = 60000\nlogin-ratelimit = 0\n`);
else{
 fs.writeFileSync(path.join(proxyFolder,'config.yml'),`online_mode: false\nip_forward: false\nconnection_throttle: 0\nreject_transfers: false\nplayer_limit: 3\npermissions:\n  default:\n  - bungeecord.command.server\nservers:\n  survival:\n    address: 127.0.0.1:${ports.survival}\n    restricted: false\n  lobby:\n    address: 127.0.0.1:${ports.lobby}\n    restricted: false\nlisteners:\n- host: 127.0.0.1:${ports.proxy}\n  motd: WorldBinder isolated ${proxyKind} QA\n  max_players: 3\n  force_default_server: true\n  priorities: [survival]\n  forced_hosts: {}\n  bind_local_address: false\n  query_enabled: false\n`);
 fs.writeFileSync(path.join(proxyFolder,'modules.yml'),'version: 2\nmodules: []\n');
 fs.mkdirSync(path.join(proxyFolder,'plugins'));fs.copyFileSync(original(proxyKind+'-command'),path.join(proxyFolder,'plugins/cmd_server.jar'));
}
if(translated){fs.mkdirSync(path.join(proxyFolder,'plugins'),{recursive:true});fs.copyFileSync(original('viaversion'),path.join(proxyFolder,'plugins/ViaVersion.jar'));}
fs.mkdirSync(path.join(proxyFolder,'plugins/bStats'),{recursive:true});fs.writeFileSync(path.join(proxyFolder,'plugins/bStats/config.txt'),'enabled=false\n');
setTimeout(shutdown,45*60*1000).unref();
process.on('SIGINT',shutdown);process.on('SIGTERM',shutdown);
