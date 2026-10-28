// Reproduce compact exact-version registry facts from Mojang's generated --reports/blocks.json.
// No implementation from another mod or mapping project is embedded.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
const workspace=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const reports=path.resolve(workspace,process.argv[2]||'build/qa-target-registries');
const output=path.resolve(workspace,process.argv[3]||'src/main/resources/data/worldbinder/target-registry');
if(!output.startsWith(workspace+path.sep))throw Error('Output must stay inside this checkout');
fs.mkdirSync(output,{recursive:true});
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const provenance=[];
for(const [minecraft,dataVersion]of [['1.13.2',1631],['1.16.5',2586],['1.20.4',3700]]){
  const report=fs.readFileSync(path.join(reports,minecraft,'reports/blocks.json'));
  const source=JSON.parse(report),facts={};
  for(const id of Object.keys(source).sort()){
    const block=source[id],defaults=block.states.find(state=>state.default);
    if(!defaults)throw Error(`${minecraft} ${id}: no default state`);
    facts[id]={properties:block.properties||{},defaults:defaults.properties||{}};
  }
  const resource=Buffer.from(JSON.stringify(facts)+'\n');
  fs.writeFileSync(path.join(output,dataVersion+'.json'),resource);
  provenance.push({minecraft,dataVersion,blocks:Object.keys(facts).length,source:'Mojang official dedicated server --reports output',reportSHA256:sha(report),resourceSHA256:sha(resource)});
}
fs.writeFileSync(path.join(output,'provenance.json'),JSON.stringify(provenance,null,2)+'\n');
console.log('Generated exact registry facts:',provenance.map(p=>`${p.minecraft}: ${p.blocks} blocks`).join(', '));
