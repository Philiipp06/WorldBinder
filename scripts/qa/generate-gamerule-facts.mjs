// Reproduce exact per-version game-rule facts (id, category, type, default, bounds, feature flag) for the 26.x
// targets from the GameRules class of the official Minecraft jars that Loom already downloaded for the
// compatibility tests. Only facts are stored; no Mojang code is copied.
//
// Usage: node scripts/qa/generate-gamerule-facts.mjs [javap] (jars are located in .gradle/loom-cache)
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const workspace = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const javap = process.argv[2] || (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'javap') : 'javap');
const output = path.join(workspace, 'src/main/resources/data/worldbinder/gamerules/26.json');
const versions = [['26.1', 4786], ['26.1.1', 4788], ['26.1.2', 4790], ['26.2', 4903], ['26.3', 5023]];

function findJar(version) {
  const root = path.join(workspace, '.gradle/loom-cache/minecraftMaven/net/minecraft');
  for (const dir of fs.existsSync(root) ? fs.readdirSync(root) : []) {
    const candidate = path.join(root, dir, version, `${dir}-${version}.jar`);
    if (dir.startsWith('minecraft-merged') && fs.existsSync(candidate)) return candidate;
  }
  throw Error('Missing Minecraft jar for ' + version + ' (run the compatibility client test once)');
}

function constant(line) {
  let match = /iconst_(m1|\d)\b/.exec(line);
  if (match) return match[1] === 'm1' ? -1 : +match[1];
  match = /[bs]ipush\s+(-?\d+)/.exec(line);
  if (match) return +match[1];
  match = /ldc(?:_w)?\s+#\d+\s+\/\/ int (-?\d+)/.exec(line);
  return match ? +match[1] : null;
}

export function parseGameRules(disassembly) {
  const body = disassembly.slice(disassembly.indexOf('static {};'));
  const rules = [];
  let name = null, category = null, constants = [], flags = false, skip = false;
  for (const line of body.split(/\r?\n/)) {
    const string = /\/\/ String ([a-z0-9_]+)$/.exec(line);
    if (string && /ldc/.test(line)) { name = string[1]; category = null; constants = []; flags = false; skip = false; continue; }
    const categoryMatch = /GameRuleCategory\.([A-Z_]+):/.exec(line);
    if (categoryMatch && name) { category = categoryMatch[1].toLowerCase(); continue; }
    if (/FeatureFlags\.|FeatureFlagSet/.test(line) && !/register(Boolean|Integer)/.test(line)) flags = true;
    // `DEBUG_WORLD_RECREATE ? a : b`: keep the release branch (before the goto), skip the debug constant.
    if (/goto/.test(line) && name && category) { skip = true; continue; }
    const value = constant(line);
    if (value !== null && name && category) { if (skip) skip = false; else constants.push(value); continue; }
    const register = /Method register(Boolean|Integer):\(([^)]*)\)/.exec(line);
    if (register && name && category) {
      const rule = {id: name, category, type: register[1] === 'Boolean' ? 'bool' : 'int'};
      if (rule.type === 'bool') rule.default = constants.at(-1) === 1;
      else {
        const ints = (register[2].match(/I/g) || []).length;
        const values = constants.slice(-ints);
        rule.default = values[0];
        rule.min = values[1] ?? -2147483648;
        rule.max = ints >= 3 ? values[2] : 2147483647;
      }
      if (flags || /FeatureFlagSet/.test(register[2])) rule.experimental = true;
      rules.push(rule);
      name = null;
    }
  }
  return rules.sort((a, b) => a.id.localeCompare(b.id));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const facts = {source: 'net.minecraft.world.level.gamerules.GameRules of the official Minecraft jars', versions: {}, provenance: []};
  for (const [version, dataVersion] of versions) {
    const jar = findJar(version);
    const disassembly = execFileSync(javap, ['-c', '-p', '-cp', jar, 'net.minecraft.world.level.gamerules.GameRules'], {encoding: 'utf8', maxBuffer: 64 << 20});
    const rules = parseGameRules(disassembly);
    if (rules.length < 30) throw Error(version + ': only ' + rules.length + ' rules parsed');
    facts.versions[version] = {dataVersion, rules};
    facts.provenance.push({minecraft: version, dataVersion, rules: rules.length, jarSHA256: crypto.createHash('sha256').update(fs.readFileSync(jar)).digest('hex')});
  }
  fs.mkdirSync(path.dirname(output), {recursive: true});
  fs.writeFileSync(output, JSON.stringify(facts, null, 1) + '\n');
  console.log('Generated game-rule facts:', facts.provenance.map(p => `${p.minecraft}: ${p.rules} rules`).join(', '));
}
