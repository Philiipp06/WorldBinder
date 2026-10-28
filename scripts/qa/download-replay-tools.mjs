// Upstream Modrinth releases, newest declared Fabric release per Minecraft version, verified against publisher SHA-512.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import zlib from 'node:zlib';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const workspace=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const directory=path.join(workspace,'build/qa-replay-downloads');fs.mkdirSync(directory,{recursive:true});
const headers={'User-Agent':'WorldBinder-Local-QA/1.7.0 (replay compatibility acceptance)'};
const inventory=[];
const loaderRoot=path.join(process.env.USERPROFILE,'.gradle/caches/modules-2/files-2.1/net.fabricmc/fabric-loader/0.19.5');
const loader=fs.readdirSync(loaderRoot).map(hash=>path.join(loaderRoot,hash,'fabric-loader-0.19.5.jar')).find(file=>fs.existsSync(file));
if(!loader)throw Error('Resolve the supported Fabric Loader 0.19.5 first');
function metadata(file){
 const bytes=fs.readFileSync(file);let end=bytes.length-22;
 for(;end>=Math.max(0,bytes.length-65557);end--)if(bytes.readUInt32LE(end)===0x06054b50&&end+22+bytes.readUInt16LE(end+20)===bytes.length)break;
 if(end<0)throw Error('Missing ZIP directory');
 const size=bytes.readUInt32LE(end+12),start=bytes.readUInt32LE(end+16);
 for(let at=start;at<start+size;){
  if(bytes.readUInt32LE(at)!==0x02014b50)throw Error('Invalid ZIP directory');
  const nameLength=bytes.readUInt16LE(at+28),extra=bytes.readUInt16LE(at+30),comment=bytes.readUInt16LE(at+32);
  if(bytes.toString('utf8',at+46,at+46+nameLength)==='fabric.mod.json'){
   const method=bytes.readUInt16LE(at+10),length=bytes.readUInt32LE(at+20),header=bytes.readUInt32LE(at+42),offset=header+30+bytes.readUInt16LE(header+26)+bytes.readUInt16LE(header+28);
   const compressed=bytes.subarray(offset,offset+length);const raw=method===0?compressed:method===8?zlib.inflateRawSync(compressed,{maxOutputLength:1048576}):null;
   if(!raw||raw.length>1048576)throw Error('Unsupported Fabric metadata');return JSON.parse(raw.toString('utf8'));
  }at+=46+nameLength+extra+comment;
 }throw Error('No Fabric metadata');
}
async function fetchJar(file){
 if(path.basename(file.filename)!==file.filename)throw Error('Invalid upstream filename');
 const dest=path.join(directory,file.filename);
 if(!fs.existsSync(dest)||crypto.createHash('sha512').update(fs.readFileSync(dest)).digest('hex')!==file.hashes.sha512){const result=await fetch(file.url,{headers});if(!result.ok)throw Error(result.statusText);const bytes=Buffer.from(await result.arrayBuffer());if(crypto.createHash('sha512').update(bytes).digest('hex')!==file.hashes.sha512)throw Error('Upstream checksum mismatch: '+file.filename);fs.writeFileSync(dest,bytes);}return dest;
}
for(const project of ['flashback','replaymod']){
 const response=await fetch(`https://api.modrinth.com/v2/project/${project}/version`,{headers});if(!response.ok)throw Error(await response.text());
 const releases=(await response.json()).filter(r=>r.loaders.includes('fabric')&&r.version_type==='release').sort((a,b)=>Date.parse(b.date_published)-Date.parse(a.date_published));
 for(const minecraft of ['26.1','26.1.1','26.1.2','26.2','26.3']){
  let selected;
  for(const release of releases.filter(r=>r.game_versions.includes(minecraft)||minecraft.startsWith('26.1.')&&r.game_versions.includes('26.1'))){
   const file=release.files.find(f=>f.primary)||release.files[0],dest=await fetchJar(file),meta=metadata(dest),predicate=meta.depends?.minecraft;
   if(!predicate&&!release.game_versions.includes(minecraft))continue;
   if(predicate){const result=spawnSync(path.join(process.env.ProgramFiles,'Java/jdk-25/bin/java.exe'),['--class-path',loader,path.join(workspace,'scripts/qa/FabricVersionPredicate.java'),minecraft,...(Array.isArray(predicate)?predicate:[predicate])],{encoding:'utf8',windowsHide:true});if(result.status!==0)throw Error(result.stderr);if(result.stdout.trim()!=='true')continue;}
   selected={project,minecraft,version:release.version_number,published:release.date_published,url:file.url,file:path.relative(workspace,dest).replaceAll('\\','/'),sha512:file.hashes.sha512,fabricMinecraftPredicate:predicate,listedInApi:release.game_versions.includes(minecraft)};break;
  }if(!selected)throw Error(`${project}: no declared compatible ${minecraft} release`);inventory.push(selected);
 }
}
fs.writeFileSync(path.join(directory,'inventory.json'),JSON.stringify(inventory,null,2)+'\n');
for(const row of inventory)console.log(row.project,row.minecraft,row.version,row.file);
