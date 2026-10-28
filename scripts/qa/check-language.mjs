import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {javaStrings, argumentsAt, needsTranslation, enumNames} from './java-source.mjs';

const base='src/main/resources/assets/worldbinder/lang';
const ownedKey=/^(?:worldbinder\.|key\.worldbinder\.|modmenu\.descriptionTranslation\.worldbinder$)/;
const fingerprint=value=>crypto.createHash('sha256').update(value.replaceAll('\r\n','\n').trim()).digest('hex');
export function readBundle(source, name) {
  const tokens=[...source.matchAll(/"(?:[^"\\]|\\.)*"|[{}\[\]:,]/g)].map(match=>match[0]);
  const keys=tokens.filter((_,index)=>tokens[index+1]===':').map(value=>JSON.parse(value));
  const duplicates=keys.filter((key,index)=>keys.indexOf(key)!==index);
  if(duplicates.length)throw Error(name+' duplicate keys: '+duplicates.join(', '));
  return JSON.parse(source);
}
export function placeholders(value) {
  const indexes=[];let cursor=1;
  for(let i=0;i<value.length;i++) {
    if(value[i]!=='%')continue;
    const match=/^%(?:([1-9]\d*)\$)?([%s])/.exec(value.slice(i));
    if(!match||match[2]==='%'&&match[1])throw Error('Invalid Minecraft placeholder '+value.slice(i,i+8));
    i+=match[0].length-1;
    if(match[2]!=='%')indexes.push(match[1]?+match[1]:cursor++);
  }
  return indexes.sort((a,b)=>a-b);
}
// English is the reference catalog; every translation must provide the same keys and placeholders.
export function auditBundles(en, de, fr) {
  const errors=[];
  const bundles=[['en_us',en],['de_de',de],['fr_fr',fr]].filter(([,values])=>values);
  for(const key of new Set(bundles.flatMap(([,values])=>Object.keys(values)))) {
    const missing=bundles.filter(([,values])=>typeof values[key]!=='string'||!values[key].trim());
    if(missing.length) {errors.push('Missing/empty text: '+key+' ('+missing.map(([language])=>language).join(', ')+')');continue;}
    for(const [language,values]of bundles.slice(1))
      try {if(JSON.stringify(placeholders(en[key]))!==JSON.stringify(placeholders(values[key])))errors.push('Placeholder mismatch: '+key+(language==='de_de'?'':' ('+language+')'));}
      catch(error){errors.push(key+': '+error.message);}
    for(const [language,values]of bundles)
      if(/\bworldbinder\.[a-z_\d]+\.[a-z_\d.]+\b|\u001eworldbinder:/.test(values[key]))errors.push(language+' leaked translation key/token: '+key);
  }
  return errors;
}
export function auditSource(file, source, en, exemptions=[]) {
  const errors=[];
  const exempt=exemptions.filter(entry=>entry.file===file);
  for(const token of javaStrings(source)) {
    const value=token.value;
    if(ownedKey.test(value)) {
      if(!/[._]$/.test(value)&&!/^\s*\+/.test(source.slice(token.end))&&!(value in en))errors.push(file+':'+token.line+' missing key '+value);
      continue;
    }
    const before=source.slice(Math.max(0,token.start-250),token.start);
    const machineReference=/^(?:[a-z][a-z\d_]*(?:[.:][a-z\d_]+)+|\.[a-z_]+)$/.test(value);
    const uiLiteral=/(?:Component\.literal|WbButton\.create|WbChrome\.drawKeycap|WbText\.draw\w*|context\.text)\([^;{}]*$/.test(before)
      && !machineReference && /\p{L}{2}/u.test(value) && !/^(?:WorldBinder|Minecraft |[XYZ][12]?|[A-Z]|[0-9 .:%×x+\/-]+|%.\dfx?|1:%d)$/.test(value);
    const allowed=exempt.some(entry=>entry.values?.includes(value)||entry.sha256===fingerprint(value));
    const logLiteral=/\b(?:LOGGER|logger)\.\w+\([^;]*$/.test(before);
    if((needsTranslation(value)&&!allowed||uiLiteral)&&!logLiteral)
      errors.push(file+':'+token.line+' hardcoded text '+JSON.stringify(value).slice(0,130));
  }
  // A key can exist in both catalogs and still be displayed accidentally as literal text.
  for(const match of source.matchAll(/\b(Component\.literal|Lang\.resolve|(?:context|c)\.text|WbText\.draw\w*|WbButton\.create)\s*\(/g)) {
    const args=argumentsAt(source,match.index+match[0].length);
    const index=match[1]==='WbButton.create'?4:match[1].startsWith('WbText.')?2:match[1].endsWith('.text')?1:0;
    const key=/^"([^"\\]+)"$/.exec(args[index]||'');
    if(key&&ownedKey.test(key[1]))errors.push(file+': literal translation key displayed through '+match[1]+': '+key[1]);
  }
  const calls=/\b(Lang\.(?:text|string|stored|in)|Component\.translatable(?:WithFallback)?|Chat\.\w+Key|Finding\.of)\s*\(/g;
  for(const match of source.matchAll(calls)) {
    const args=argumentsAt(source,match.index+match[0].length),keyIndex=match[1]==='Lang.in'?1:0;
    const keyExpression=args[keyIndex]||'';
    if(match[1].startsWith('Lang.'))
      for(const token of javaStrings(keyExpression))if((keyExpression.trimStart().startsWith(token.raw)||!keyExpression.includes('+')&&token.value.includes('.'))&&!ownedKey.test(token.value)&&!(token.value in en))
        errors.push(file+': unsupported language catalog key/prefix '+token.value+' through '+match[1]);
    if(keyExpression.includes('+'))continue;
    const keys=javaStrings(keyExpression).map(token=>token.value).filter(key=>ownedKey.test(key)&&key in en);
    if(!keys.length)continue;
    const values=args.slice(keyIndex+1+(match[1].endsWith('WithFallback')?1:0));
    if(values.some(arg=>/^(?:args|arguments|supported|values)$|\.toArray\(/.test(arg)))continue;
    for(const key of keys) {
      const expected=Math.max(0,...placeholders(en[key]));
      if(values.length!==expected)errors.push(file+': argument count '+key+' expected '+expected+', found '+values.length);
    }
  }
  return errors;
}

function walk(root){return fs.readdirSync(root,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?walk(path.join(root,entry.name)):[path.join(root,entry.name)]);}
export function runAudit() {
  const en=readBundle(fs.readFileSync(base+'/en_us.json','utf8'),'en_us'),de=readBundle(fs.readFileSync(base+'/de_de.json','utf8'),'de_de'),fr=readBundle(fs.readFileSync(base+'/fr_fr.json','utf8'),'fr_fr');
  const errors=auditBundles(en,de,fr);
  // Key order is kept identical so translators can compare the catalogs line by line.
  if(JSON.stringify(Object.keys(fr))!==JSON.stringify(Object.keys(en)))errors.push('fr_fr key order differs from en_us');
  const shared=JSON.parse(fs.readFileSync('scripts/qa/language-shared-values.json','utf8'));
  for(const group of shared) {
    if(!group.reason?.trim())errors.push('Unexplained shared language values');
    for(const [key,value]of Object.entries(group.entries))
      if(en[key]!==value||de[key]!==value)errors.push('Stale shared language value: '+key);
  }
  for(const key of Object.keys(en))if(en[key]===de[key]&&!shared.some(group=>group.entries[key]===en[key]))
    errors.push('Unreviewed identical DE/EN text: '+key);
  const sources=new Map(['src/main/java','src/fabric/java','neoforge/src/main/java'].flatMap(walk).filter(file=>file.endsWith('.java')).map(file=>[file.replaceAll('\\','/'),fs.readFileSync(file,'utf8')]));
  const exemptions=JSON.parse(fs.readFileSync('scripts/qa/language-literal-exemptions.json','utf8'));
  for(const [file,source]of sources)errors.push(...auditSource(file,source,en,exemptions));
  for(const entry of exemptions) {
    if(!entry.reason?.trim())errors.push('Unexplained language exemption: '+entry.file);
    const literals=javaStrings(sources.get(entry.file)||'');
    if(entry.sha256&&!literals.some(token=>fingerprint(token.value)===entry.sha256)||entry.values?.some(value=>!literals.some(token=>token.value===value)))errors.push('Stale language exemption: '+entry.file);
  }
  let dynamic=0;
  const requireKey=key=>{dynamic++;if(!(key in en)||!(key in de)||!(key in fr))errors.push('Missing dynamic key '+key);};
  const source=file=>sources.get('src/main/java/net/worldbinder/'+file);
  const family=(file,name,prefix,suffixes=[''])=>{for(const name_ of enumNames(source(file),name))for(const suffix of suffixes)requireKey(prefix+name_+suffix);};
  const ruleList=/String\[\]\s+RULES\s*=\s*\{([\s\S]*?)\};/.exec(source('config/GameRuleSettings.java'));
  if(!ruleList)errors.push('Missing canonical game-rule list for language audit');
  else for(const rule of javaStrings(ruleList[1])){requireKey('worldbinder.gamerules.rule.'+rule.value);requireKey('worldbinder.gamerules.rule.'+rule.value+'.tooltip');}
  for(const [file,name,prefix,suffixes]of [
    ['storage/StorageStage.java','StorageStage','worldbinder.storage.stage.'],
    ['version/TargetMinecraftVersion.java','GenerationProfile','worldbinder.generation_profile.'],
    ['config/WorldBinderConfig.java','MessageMode','worldbinder.messages.'],
    ['config/WorldBinderConfig.java','MovementHudStyle','worldbinder.config.movement.hud_style.'],
    ['config/WorldBinderConfig.java','MapBlockColors','worldbinder.map.block_colors.'],
    ['config/WorldBinderConfig.java','ReloadDisplayMode','worldbinder.config.reload_display.'],
    ['ui/component/WbTheme.java','Accent','worldbinder.config.display.accent.'],
    ['ui/WorldBinderWidgetEditorScreen.java','Page','worldbinder.widget_editor.page.',['','.tooltip']],
    ['scene/ExportSettings.java','Generation','worldbinder.export.generation.'],
    ['render/MapMarkers.java','Kind','worldbinder.map.entity.kind.'],
    ['capture/ContainerScanQueue.java','Phase','worldbinder.scanner.'],
    ['movement/MovementTool.java','MovementTool','worldbinder.movement.',['','.tooltip']]
  ])family(file,name,prefix,suffixes);
  for(const match of source('version/VersionFamily.java').matchAll(/^\s+[A-Z_\d]+\("([^"]+)"/gm))for(const suffix of ['','.description'])requireKey('worldbinder.version_family.'+match[1]+suffix);
  for(const match of source('util/BlockStateStrings.java').matchAll(/new ValidationException\("([^"]+)"/g))requireKey('worldbinder.export.replacement.invalid_'+match[1]);
  for(const [prefix,values]of [
    ['worldbinder.scanner.',['off']],['worldbinder.generated.legal.',[1,2,3,4,5,6,7]],
    ['worldbinder.direction.',['south','south_west','west','north_west','north','north_east','east','south_east']],
    ['worldbinder.gamerules.category.',[0,1,2,3,4,5,6,'0.tooltip','1.tooltip','2.tooltip','3.tooltip','4.tooltip','5.tooltip','6.tooltip']],['worldbinder.config.capture.page.',['containers','overlay','lists','containers.tooltip','overlay.tooltip','lists.tooltip']],['worldbinder.generated.dimensions.',['title','project','target','count','active','folder','type','height','chunks','contents','containers','note']],['worldbinder.cache.',['missing_or_empty','missing_or_corrupt']],
    ['worldbinder.quality.',['excellent','good','usable','partial','review']],
    ['worldbinder.validation.status.',['pass','warn','fail','unknown']],
    ['worldbinder.recovery.state.',['none','complete','writing','missing','failed','unknown']],
    ['worldbinder.widget_editor.anchor.',['top_left','top','top_right','left','center','right','bottom_left','bottom','bottom_right']]
  ])for(const value of values)requireKey(prefix+value);
  for(const match of source('render/MapExportInfo.java').matchAll(/line\(language,\s*"([^"]+)"/g)) {
    const key='worldbinder.generated.map.'+match[1];requireKey(key);
    const args=argumentsAt(source('render/MapExportInfo.java'),match.index+5);
    const expected=Math.max(0,...placeholders(en[key]||''));
    if(args.length-2!==expected)errors.push('Map metadata argument count '+key+' expected '+expected+', found '+(args.length-2));
  }
  // Loader metadata cannot always translate at runtime. Both loaders embed the same bilingual
  // description from the catalog; Mod Menu also supports a language-sensitive description key.
  requireKey('modmenu.descriptionTranslation.worldbinder');
  for(const file of ['src/main/resources/fabric.mod.json','neoforge/src/main/resources/META-INF/neoforge.mods.toml'])
    if(!fs.readFileSync(file,'utf8').includes('${description}'))errors.push('Loader metadata bypasses language catalog: '+file);
  return {errors,keys:Object.keys(en).length,files:sources.size,dynamic};
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const result=runAudit();
  if(result.errors.length){process.stderr.write(result.errors.join('\n')+'\n');process.exitCode=1;}
  else process.stdout.write('WORLDBINDER_LANGUAGE_PASS '+result.keys+' EN/DE/FR keys; '+result.files+' production files; '+result.dynamic+' dynamic references; literals, duplicates, arguments and placeholders checked\n');
}
