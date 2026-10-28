// A real, unmodded, loopback-only vanilla server and HTTP resource pack for the client acceptance tests.
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import net from 'node:net';
import crypto from 'node:crypto';
import zlib from 'node:zlib';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const workspace=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const base=path.join(workspace,'build/dedicated-server-runtime');fs.mkdirSync(base,{recursive:true});
const root=path.join(base,'run-'+Date.now());fs.mkdirSync(root);
const runs=fs.readdirSync(path.join(workspace,'build/regression-runtime'),{withFileTypes:true}).filter(e=>e.isDirectory()&&fs.existsSync(path.join(workspace,'build/regression-runtime',e.name,'export-26.3'))&&fs.existsSync(path.join(workspace,'build/regression-runtime',e.name,'RESULT.txt'))&&fs.readFileSync(path.join(workspace,'build/regression-runtime',e.name,'RESULT.txt'),'utf8').startsWith('PASS'))
  .map(e=>path.join(workspace,'build/regression-runtime',e.name)).sort((a,b)=>fs.statSync(b).mtimeMs-fs.statSync(a).mtimeMs);
if(!runs.length)throw Error('Run regressionTest first');
fs.cpSync(path.join(runs[0],'export-26.3'),path.join(root,'world'),{recursive:true});
const mapFile=path.join(root,'world/data/minecraft/maps/7.dat'),raw=zlib.gunzipSync(fs.readFileSync(mapFile));
const lock=Buffer.from([1,0,6,...Buffer.from('locked'),0]),lockAt=raw.indexOf(lock);
if(lockAt<0)throw Error('Expected unlocked fixture map');raw[lockAt+lock.length-1]=1;fs.writeFileSync(mapFile,zlib.gzipSync(raw));
function crc(data){let c=0xffffffff;for(const v of data){c^=v;for(let j=0;j<8;j++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^0xffffffff)>>>0;}
function pngChunk(type,data){const name=Buffer.from(type),head=Buffer.alloc(4),tail=Buffer.alloc(4);head.writeUInt32BE(data.length);tail.writeUInt32BE(crc(Buffer.concat([name,data])));return Buffer.concat([head,name,data,tail]);}
const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(16,0);ihdr.writeUInt32BE(16,4);ihdr[8]=8;ihdr[9]=6;
const pixels=Buffer.alloc(16*(1+16*4));for(let y=0;y<16;y++)for(let x=0;x<16;x++){let at=y*65+1+x*4,v=((x>>2)+(y>>2))%2;pixels[at]=v?255:0;pixels[at+1]=v?0:255;pixels[at+2]=255;pixels[at+3]=255;}
const png=Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),pngChunk('IHDR',ihdr),pngChunk('IDAT',zlib.deflateSync(pixels)),pngChunk('IEND',Buffer.alloc(0))]);
function zip(entries){const local=[],central=[];let offset=0;for(const [key,data]of entries){const name=Buffer.from(key),checksum=crc(data),h=Buffer.alloc(30);h.writeUInt32LE(0x04034b50);h.writeUInt16LE(20,4);h.writeUInt32LE(checksum,14);h.writeUInt32LE(data.length,18);h.writeUInt32LE(data.length,22);h.writeUInt16LE(name.length,26);local.push(h,name,data);const c=Buffer.alloc(46);c.writeUInt32LE(0x02014b50);c.writeUInt16LE(20,4);c.writeUInt16LE(20,6);c.writeUInt32LE(checksum,16);c.writeUInt32LE(data.length,20);c.writeUInt32LE(data.length,24);c.writeUInt16LE(name.length,28);c.writeUInt32LE(offset,42);central.push(c,name);offset+=h.length+name.length+data.length;}const directory=Buffer.concat(central),end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50);end.writeUInt16LE(entries.length,8);end.writeUInt16LE(entries.length,10);end.writeUInt32LE(directory.length,12);end.writeUInt32LE(offset,16);return Buffer.concat([...local,directory,end]);}
// A fresh package hash proves the download path even when a prior QA run populated the client cache.
const pack=zip([['pack.mcmeta',Buffer.from(JSON.stringify({pack:{min_format:[97,1],max_format:[97,1],description:'WorldBinder real server QA checker pack '+crypto.randomUUID()}}))],['assets/minecraft/textures/block/stone.png',png]]);
fs.writeFileSync(path.join(root,'server-pack.zip'),pack);
const token=crypto.randomBytes(24).toString('hex');let downloads=0,ready=false,child;
const api=http.createServer(async(req,res)=>{if(req.url==='/pack.zip'){downloads++;res.writeHead(200,{'Content-Type':'application/zip','Content-Length':pack.length});res.end(pack);return;}
 if(req.url!=='/'+token){res.writeHead(404);res.end();return;}
 if(req.method==='POST'){let body='';for await(const chunk of req)body+=chunk;const data=JSON.parse(body);if(typeof data.command!=='string'||/[\r\n]/.test(data.command))throw Error('Invalid console command');child.stdin.write(data.command+'\n');res.end('{}');}
 else res.end(JSON.stringify({ready,downloads}));});
await new Promise(resolve=>api.listen(0,'127.0.0.1',resolve));const httpPort=api.address().port;
const reservation=net.createServer();await new Promise(resolve=>reservation.listen(0,'127.0.0.1',resolve));const port=reservation.address().port;await new Promise(resolve=>reservation.close(resolve));
const sha1=crypto.createHash('sha1').update(pack).digest('hex');
fs.writeFileSync(path.join(root,'eula.txt'),'eula=true\n');
fs.writeFileSync(path.join(root,'server.properties'),`level-name=world\nserver-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nenforce-secure-profile=false\nwhite-list=false\nenforce-whitelist=false\nview-distance=4\nsimulation-distance=2\nspawn-protection=0\nmax-tick-time=60000\nresource-pack=http://127.0.0.1:${httpPort}/pack.zip\nresource-pack-sha1=${sha1}\nresource-pack-id=55cf2e03-48e1-403b-9888-883aa37abc10\nrequire-resource-pack=true\n`);
const jar=process.argv[2]||path.join(process.env.USERPROFILE,'.gradle/caches/fabric-loom/26.3/minecraft-server.jar');
const info=JSON.parse(fs.readFileSync(path.join(path.dirname(jar),'mojang_minecraft_info.json'),'utf8'));
if(crypto.createHash('sha1').update(fs.readFileSync(jar)).digest('hex')!==info.downloads.server.sha1)throw Error('Official vanilla server SHA-1 mismatch');
const log=fs.createWriteStream(path.join(root,'console.log'));
child=spawn(path.join(process.env.ProgramFiles,'Java/jdk-25/bin/java.exe'),['-Xmx2G','-jar',jar,'nogui'],{cwd:root,windowsHide:true,stdio:['pipe','pipe','pipe']});
let lines='';for(const stream of [child.stdout,child.stderr])stream.on('data',data=>{log.write(data);lines+=data.toString();if(lines.includes('Done (')&&!ready){ready=true;child.stdin.write('whitelist off\n');fs.writeFileSync(path.join(base,'control.json'),JSON.stringify({root,host:`127.0.0.1:${port}`,api:`http://127.0.0.1:${httpPort}/${token}`,textureSHA256:crypto.createHash('sha256').update(png).digest('hex'),packSHA256:crypto.createHash('sha256').update(pack).digest('hex')},null,2));console.log('WORLDBINDER_DEDICATED_READY '+root);}if(lines.length>10000)lines=lines.slice(-4000);});
child.on('exit',code=>{fs.writeFileSync(path.join(root,'server-exit.json'),JSON.stringify({exitCode:code,packDownloads:downloads,vanilla:true,loopbackOnly:true}));log.end();api.close();});
const deadline=setTimeout(()=>child.stdin.write('stop\n'),30*60*1000);deadline.unref();
