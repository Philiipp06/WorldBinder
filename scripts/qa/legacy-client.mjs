// Original unmodified Mojang clients in an isolated checkout-local directory; no account credentials.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import zlib from 'node:zlib';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const workspace=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const option=name=>process.argv.find(a=>a.startsWith(`--${name}=`))?.slice(name.length+3);
const version=option('version')||'1.20.4';if(!['1.13.2','1.16.5','1.20.4'].includes(version))throw Error('Explicit supported QA version required');
const caseName=option('case');if(caseName&&!/^[a-z0-9][a-z0-9-]{0,63}$/.test(caseName))throw Error('Use a simple isolated QA case name');
const maxFps=Number(option('fps')||60);if(!Number.isInteger(maxFps)||maxFps<10||maxFps>260)throw Error('Use a native frame limit from 10 to 260');
const base=path.join(workspace,'build/qa-legacy-client'),downloads=path.join(base,'downloads',version),game=path.join(base,version,...(caseName?[caseName]:[]));
fs.mkdirSync(downloads,{recursive:true});fs.mkdirSync(game,{recursive:true});
async function fetchFile(url,file,sha1){
 if(fs.existsSync(file)&&crypto.createHash('sha1').update(fs.readFileSync(file)).digest('hex')===sha1)return;
 const response=await fetch(url);if(!response.ok)throw Error(`${response.status}: ${url}`);const data=Buffer.from(await response.arrayBuffer());
 if(crypto.createHash('sha1').update(data).digest('hex')!==sha1)throw Error('Official SHA-1 mismatch: '+url);
 fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,data);
}
const manifest=await(await fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')).json();
const release=manifest.versions.find(v=>v.id===version);if(!release)throw Error('Missing Mojang client version');
await fetchFile(release.url,path.join(downloads,'version.json'),release.sha1);
const info=JSON.parse(fs.readFileSync(path.join(downloads,'version.json'),'utf8'));
const clientJar=path.join(downloads,'minecraft-client.jar');await fetchFile(info.downloads.client.url,clientJar,info.downloads.client.sha1);
function allowed(rules){
 if(!rules)return true;let allow=false;for(const rule of rules){if(rule.features)continue;const os=rule.os;if(os?.name&&os.name!=='windows'||os?.arch&&!new RegExp(os.arch).test('amd64')||os?.version&&!new RegExp(os.version).test('10.0'))continue;allow=rule.action==='allow';}return allow;
}
const libraries=[],natives=[];
for(const library of info.libraries){if(!allowed(library.rules))continue;
 if(library.downloads?.artifact){const artifact=library.downloads.artifact,file=path.join(downloads,'libraries',artifact.path);libraries.push(file);natives.push({artifact,file,native:library.name.includes('natives-windows')});}
 const classifier=library.natives?.windows?.replace('${arch}','64');if(classifier){const artifact=library.downloads.classifiers[classifier];if(artifact)natives.push({artifact,file:path.join(downloads,'libraries',artifact.path),native:true});}
}
let index=0;await Promise.all(Array.from({length:8},async()=>{while(index<natives.length){const item=natives[index++];await fetchFile(item.artifact.url,item.file,item.artifact.sha1);}}));
const nativeDir=path.join(game,'natives');fs.mkdirSync(nativeDir,{recursive:true});
// Read ZIP central entries directly; extract only native DLL basenames into our own new directory.
for(const item of natives.filter(n=>n.native)){
 const bytes=fs.readFileSync(item.file);let end=bytes.length-22;while(end>=0&&bytes.readUInt32LE(end)!==0x06054b50)end--;if(end<0)throw Error('Invalid original native ZIP');
 let at=bytes.readUInt32LE(end+16);for(let i=0;i<bytes.readUInt16LE(end+10);i++){
  if(bytes.readUInt32LE(at)!==0x02014b50)throw Error('Invalid native central directory');
  const nameLength=bytes.readUInt16LE(at+28),extra=bytes.readUInt16LE(at+30),comment=bytes.readUInt16LE(at+32),name=bytes.toString('utf8',at+46,at+46+nameLength),offset=bytes.readUInt32LE(at+42),size=bytes.readUInt32LE(at+20),method=bytes.readUInt16LE(at+10);
  if(name.endsWith('.dll')){const start=offset+30+bytes.readUInt16LE(offset+26)+bytes.readUInt16LE(offset+28),packed=bytes.subarray(start,start+size),data=method===0?packed:method===8?zlib.inflateRawSync(packed):null;if(!data)throw Error('Unsupported original native compression');fs.writeFileSync(path.join(nativeDir,path.basename(name)),data);}
  at+=46+nameLength+extra+comment;
 }
}
const assets=path.join(base,'assets'),assetIndex=path.join(assets,'indexes',info.assetIndex.id+'.json');await fetchFile(info.assetIndex.url,assetIndex,info.assetIndex.sha1);
const objects=Object.values(JSON.parse(fs.readFileSync(assetIndex,'utf8')).objects);index=0;
await Promise.all(Array.from({length:12},async()=>{while(index<objects.length){const {hash}=objects[index++],suffix=hash.slice(0,2)+'/'+hash,file=path.join(assets,'objects',suffix),cached=path.join(process.env.USERPROFILE,'.gradle/caches/fabric-loom/assets/objects',suffix);if(!fs.existsSync(file)&&fs.existsSync(cached)){fs.mkdirSync(path.dirname(file),{recursive:true});fs.copyFileSync(cached,file);}await fetchFile('https://resources.download.minecraft.net/'+suffix,file,hash);}}));
const username='WBProxyQA',raw=crypto.createHash('md5').update('OfflinePlayer:'+username).digest();raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128;const uuid=raw.toString('hex');
let source=option('world'),worldName='received_export';
if(source){source=path.resolve(workspace,source);if(!source.startsWith(workspace+path.sep)||!fs.existsSync(path.join(source,'level.dat')))throw Error('Use a complete export inside this checkout');worldName='received_export_'+Date.now();fs.mkdirSync(path.join(game,'saves'),{recursive:true});fs.cpSync(source,path.join(game,'saves',worldName),{recursive:true,filter:p=>path.basename(p)!=='session.lock'});}
const settings=`lang:en_us\nrenderDistance:4\nmaxFps:${maxFps}\nenableVsync:false\npauseOnLostFocus:false\nsoundCategory_master:0.0\nfullscreen:false\ntutorialStep:none\n`;fs.writeFileSync(path.join(game,'options.txt'),settings);
const java=version==='1.13.2'?path.join(process.env.ProgramFiles,'Java/jre1.8.0_511/bin/java.exe'):version==='1.16.5'?path.join(process.env.ProgramFiles,'Java/jre1.8.0_511/bin/java.exe'):path.join(process.env.ProgramFiles,'Java/jdk-21/bin/java.exe');
const args=['-Xmx2G','-Djava.library.path='+nativeDir,'-Dorg.lwjgl.librarypath='+nativeDir,'-cp',[...libraries,clientJar].join(path.delimiter),info.mainClass,'--username',username,'--uuid',uuid,'--accessToken','0','--version',version,'--gameDir',game,'--assetsDir',assets,'--assetIndex',info.assetIndex.id,'--userType','legacy','--versionType','release','--width','1280','--height','720'];
if(source&&version==='1.20.4')args.push('--quickPlaySingleplayer',worldName);
const evidence={version,officialClientSHA1:info.downloads.client.sha1,versionManifest:release.url,world:source,worldName,game,java,username,playerUUID:uuid,assets:objects.length,maxFps,unmodifiedMojangClient:true};
fs.writeFileSync(path.join(game,'launch-evidence.json'),JSON.stringify(evidence,null,2)+'\n');
console.log(JSON.stringify(evidence,null,2));if(process.argv.includes('--prepare-only'))process.exit(0);
const log=fs.createWriteStream(path.join(game,'client-console.log'));
const child=spawn(java,args,{cwd:game,windowsHide:false,stdio:['ignore','pipe','pipe']});child.stdout.pipe(log);child.stderr.pipe(log);child.on('exit',code=>{console.log('LEGACY_CLIENT_EXIT '+code);log.end();});
