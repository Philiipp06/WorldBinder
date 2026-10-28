// External, unshipped original proxy/protocol tools. --refresh records current publisher metadata.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
const workspace=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const output=path.join(workspace,'build/qa-network-downloads');fs.mkdirSync(output,{recursive:true});
const headers={'User-Agent':'WorldBinder-Local-QA/1.7.0'};
async function json(url){const response=await fetch(url,{headers});if(!response.ok)throw Error(`${response.status}: ${url}`);return response.json();}
let inventory;
if(process.argv.includes('--refresh')){
 const bungee=await json('https://ci.md-5.net/job/BungeeCord/lastSuccessfulBuild/api/json?tree=number,artifacts[fileName,relativePath],url');
 const waterfall=await json('https://fill.papermc.io/v3/projects/waterfall/versions/1.21/builds/latest');
 const via=await json('https://api.github.com/repos/ViaVersion/ViaVersion/releases/latest');
 inventory=[];
 for(const name of ['BungeeCord.jar','cmd_server.jar']){
  const artifact=bungee.artifacts.find(a=>a.fileName===name);if(!artifact)throw Error('Missing Bungee artifact '+name);
  inventory.push({id:name==='BungeeCord.jar'?'bungee':'bungee-command',version:String(bungee.number),file:`bungee-${bungee.number}-${name}`,url:`${bungee.url}artifact/${artifact.relativePath}`,source:bungee.url,algorithm:'sha256'});
 }
 for(const [id,key] of [['waterfall','server:default'],['waterfall-command','module:cmd_server']]){
  const artifact=waterfall.downloads[key];inventory.push({id,version:`1.21 build ${waterfall.id}`,file:artifact.name,url:artifact.url,source:`https://fill.papermc.io/v3/projects/waterfall/versions/1.21/builds/${waterfall.id}`,algorithm:'sha256',hash:artifact.checksums.sha256});
 }
 const viaJar=via.assets.find(a=>a.name.endsWith('.jar'));if(!viaJar?.digest?.startsWith('sha256:'))throw Error('Missing publisher ViaVersion digest');
 inventory.push({id:'viaversion',version:via.tag_name,file:viaJar.name,url:viaJar.browser_download_url,source:via.html_url,algorithm:'sha256',hash:viaJar.digest.slice(7)});
 for(const id of ['viafabric','viafabricplus']){
  const releases=await json(`https://api.modrinth.com/v2/project/${id}/version?game_versions=%5B%2226.3%22%5D&loaders=%5B%22fabric%22%5D`);
  const release=releases.find(r=>r.version_type==='release')||releases[0];if(!release)throw Error('Missing compatible build '+id);
  const artifact=release.files.find(f=>f.primary)||release.files[0];
  inventory.push({id,version:release.version_number,versionType:release.version_type,file:artifact.filename,url:artifact.url,source:`https://modrinth.com/mod/${id}/version/${release.id}`,algorithm:'sha512',hash:artifact.hashes.sha512,minecraft:release.game_versions});
 }
}else inventory=JSON.parse(fs.readFileSync(path.join(workspace,'scripts/qa/network-tools.json'),'utf8'));
for(const tool of inventory){
 if(path.basename(tool.file)!==tool.file||new URL(tool.url).protocol!=='https:')throw Error('Invalid external artifact');
 const target=path.join(output,tool.file);
 let bytes=fs.existsSync(target)?fs.readFileSync(target):null;
 if(!bytes){const response=await fetch(tool.url,{headers});if(!response.ok)throw Error(`${tool.id}: ${response.status}`);bytes=Buffer.from(await response.arrayBuffer());}
 const hash=crypto.createHash(tool.algorithm).update(bytes).digest('hex');
 if(tool.hash&&tool.hash!==hash)throw Error('Publisher checksum mismatch: '+tool.id);
 if(!tool.hash){tool.hash=hash;tool.checksumProvenance='Locally measured SHA-256 of the pinned official Jenkins build; no publisher SHA-256 was supplied';}
 fs.writeFileSync(target,bytes);console.log(`${tool.id} ${tool.version}: ${hash}`);
}
fs.writeFileSync(path.join(output,'inventory.json'),JSON.stringify(inventory,null,2)+'\n');
