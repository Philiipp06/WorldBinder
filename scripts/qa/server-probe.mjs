// Standard status/login handshake only. Never reads credentials or claims an in-game capture.
import net from 'node:net';
import dns from 'node:dns/promises';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import zlib from 'node:zlib';
import {fileURLToPath} from 'node:url';
const workspace=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const host=process.argv[2];if(!host||!/^[a-z0-9.-]+$/i.test(host))throw Error('Pass a DNS server hostname');
let endpoint={host,port:25565};try{const records=await dns.resolveSrv('_minecraft._tcp.'+host);records.sort((a,b)=>a.priority-b.priority);if(records.length)endpoint={host:records[0].name,port:records[0].port};}catch(error){if(!['ENODATA','ENOTFOUND','ESERVFAIL'].includes(error.code))throw error;}
function varInt(value){let n=value>>>0;const bytes=[];do{let b=n&127;n>>>=7;if(n)b|=128;bytes.push(b);}while(n);return Buffer.from(bytes);}
function readVarInt(bytes,offset=0){let value=0,shift=0;for(let i=offset;i<bytes.length;i++){const b=bytes[i];value|=(b&127)<<shift;if(!(b&128))return{value,end:i+1};shift+=7;if(shift>28)throw Error('Invalid server VarInt');}return null;}
function string(value){const bytes=Buffer.from(value);return Buffer.concat([varInt(bytes.length),bytes]);}
function frame(body){return Buffer.concat([varInt(body.length),body]);}
function handshake(protocol,next){const port=Buffer.alloc(2);port.writeUInt16BE(endpoint.port);return frame(Buffer.concat([Buffer.from([0]),varInt(protocol),string(host),port,varInt(next)]));}
async function exchange(mode,protocol=-1){
 return new Promise((resolve,reject)=>{const socket=net.connect(endpoint.port,endpoint.host);let buffer=Buffer.alloc(0),compression=false,settled=false;
  const finish=value=>{if(settled)return;settled=true;socket.end();resolve(value);};socket.setTimeout(10000,()=>socket.destroy(Error('Server handshake timeout')));socket.on('error',reject);socket.on('end',()=>{if(!settled)reject(Error('Server ended the handshake'));});
  socket.on('connect',()=>{socket.write(handshake(protocol,mode==='status'?1:2));if(mode==='status')socket.write(Buffer.from([1,0]));else socket.write(frame(Buffer.concat([Buffer.from([0]),string('WBReleaseQA'),crypto.randomBytes(16)])));});
  socket.on('data',bytes=>{try{buffer=Buffer.concat([buffer,bytes]);if(buffer.length>2097152)throw Error('Server packet exceeds QA bound');for(;;){const length=readVarInt(buffer);if(!length||buffer.length<length.end+length.value)return;let packet=buffer.subarray(length.end,length.end+length.value);buffer=buffer.subarray(length.end+length.value);if(compression){const size=readVarInt(packet);packet=size.value?zlib.inflateSync(packet.subarray(size.end),{maxOutputLength:2097152}):packet.subarray(size.end);}const id=readVarInt(packet);
    if(mode==='status'){const size=readVarInt(packet,id.end);finish(JSON.parse(packet.toString('utf8',size.end,size.end+size.value)));return;}
    if(id.value===3){compression=true;continue;}
    if(id.value===1){let at=id.end;for(let i=0;i<3;i++){const field=readVarInt(packet,at);if(!field||field.value<0||field.end+field.value>packet.length)throw Error('Invalid encryption handshake');at=field.end+field.value;}const authenticate=at<packet.length?packet[at]!==0:true;finish({onlineAuthenticationRequired:authenticate,receivedPacket:'Encryption Request',serverRequestedSessionAuthentication:authenticate,scope:'No authentication attempted and no world data received'});return;}
    if(id.value===2){finish({onlineAuthenticationRequired:false,receivedPacket:'Login Success',scope:'Connection closed before configuration/world capture'});return;}
    if(id.value===0){finish({receivedPacket:'Login Disconnect',rawDiagnostic:packet.subarray(id.end).toString('utf8').replace(/[\x00-\x08\x0b\x0c\x0e-\x1f]/g,' ').slice(0,1500),scope:'Login not completed; no world data received'});return;}
    finish({receivedPacketId:id.value,scope:'Unrecognized handshake; no world data received'});return;
  }}catch(error){socket.destroy();reject(error);}});
 });
}
const result={time:new Date().toISOString(),server:host,endpoint,status:await exchange('status')};
if(process.argv.includes('--login-check')){
 const override=process.argv.find(arg=>arg.startsWith('--protocol='))?.slice(11),protocol=override==null?result.status.version.protocol:Number(override);
 if(!Number.isInteger(protocol)||protocol<0)result.login={scope:'No login attempted: the advertised status version is a placeholder; supply the actual client --protocol explicitly'};
 else{result.loginProtocol=protocol;result.login=await exchange('login',protocol);}
}
const folder=path.join(workspace,'.qa-evidence/network-closure');fs.mkdirSync(folder,{recursive:true});
fs.writeFileSync(path.join(folder,host.toLowerCase()+'-probe.json'),JSON.stringify(result,null,2)+'\n');
function text(component){if(typeof component==='string')return component;if(!component)return '';return (component.text||'')+(component.extra||[]).map(text).join('');}
console.log(JSON.stringify({time:result.time,server:result.server,endpoint:result.endpoint,advertisedVersion:result.status.version,description:text(result.status.description),online:result.status.players?.online,loginProtocol:result.loginProtocol,login:result.login},null,2));
