import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readBundle, placeholders, auditBundles, auditSource, runAudit} from './check-language.mjs';
import {javaStrings, enumNames} from './java-source.mjs';

test('flat bundles reject duplicate keys even on the same line or with escaped names', () => {
  assert.throws(()=>readBundle('{"key":"one","key":"two"}', 'test'), /duplicate keys/);
  assert.throws(()=>readBundle('{"key":"one","k\\u0065y":"two"}', 'test'), /duplicate keys/);
  assert.deepEqual(readBundle('{"key":"A quoted \\\"field\\\": value"}', 'test'), {key:'A quoted "field": value'});
});
test('Minecraft placeholders accept indexed reordering and escaped percentages only', () => {
  assert.deepEqual(placeholders('%2$s: %1$s, %2$s (100%%)'), [1,2,2]);
  for(const bad of ['%d','%0$s','%.2f','%1s','%2$%','trailing %'])assert.throws(()=>placeholders(bad), /Invalid Minecraft placeholder/);
  assert.deepEqual(auditBundles({key:'%s %s 100%%'},{key:'%2$s %1$s 100%%'}), []);
  assert.ok(auditBundles({key:'%s'},{key:'%s %s'}).length);
  assert.ok(auditBundles({key:''},{key:'text'}).length);
  assert.deepEqual(auditBundles({key:'%s'},{key:'%s'},{key:'%s'}), []);
  assert.ok(auditBundles({key:'%s'},{key:'%s'},{}).some(error=>error.includes('fr_fr')));
  assert.ok(auditBundles({key:'%s'},{key:'%s'},{key:'100%%'}).some(error=>error.includes('fr_fr')));
});
test('source audit catches raw single labels, status fragments and unreferenced helper keys', () => {
  const bundle={'worldbinder.test':'Value %s'};
  for(const java of ['Component.literal("Hello")','String status="2s ago";','String title="Preparing export";', 'toggleSpec("worldbinder.absent");'])
    assert.ok(auditSource('example.java',java,bundle).length,java);
  assert.deepEqual(auditSource('example.java','Lang.text("worldbinder.test", "minecraft:stone");',bundle), []);
  assert.ok(auditSource('example.java','Lang.in("de_de", "worldbinder.test");',bundle).some(error=>error.includes('argument count')));
  assert.ok(auditSource('example.java','Lang.text(flag ? "worldbinder.test" : "worldbinder.empty");',{...bundle,'worldbinder.empty':'Ready'}).some(error=>error.includes('argument count')));
  assert.deepEqual(auditSource('example.java','// Component.literal("Untranslated comment")\nLang.text("worldbinder.test", 1);',bundle), []);
  assert.ok(auditSource('example.java','LOGGER.info("Log only"); Component.literal("Hello");',bundle).length);
  assert.ok(auditSource('example.java','Component.literal("worldbinder.test");',bundle).some(error=>error.includes('literal translation key')));
  assert.ok(auditSource('example.java','WbText.drawClipped(context, font, "worldbinder.test", 0, 0, 100, color);',bundle).some(error=>error.includes('literal translation key')));
  assert.ok(auditBundles({'worldbinder.test':'worldbinder.shortcut.save'},{'worldbinder.test':'Test'}).length);
  assert.ok(auditSource('example.java','Lang.string("gamerule." + rule);',bundle).some(error=>error.includes('unsupported language catalog')));
});
test('lexer keeps text blocks and ignores escaped quotes, comments and characters', () => {
  assert.equal(javaStrings('/* "ignore" */ "Hello \\"world\\""; // "ignore"\n\'x\'; """\nprose block\n""";').length,2);
  assert.deepEqual(enumNames('enum Phase { IDLE, WAITING("comma, ignored"), DONE; void tick() {} }','Phase'),['idle','waiting','done']);
});
test('reviewed machine literals cannot exempt human labels at a direct UI entry point', () => {
  const exemptions=[{file:'example.java',reason:'NBT field',values:['Name']}];
  assert.deepEqual(auditSource('example.java','tag.putString("Name", "");',{},exemptions),[]);
  assert.ok(auditSource('example.java','Component.literal("Name");',{},exemptions).length);
});
test('all production sources, enum-derived keys and reviewed exceptions pass', () => {
  assert.deepEqual(runAudit().errors, []);
});
