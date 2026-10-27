#!/usr/bin/env node
// floor-guard.mjs — piso de CONSTRAINTS.md sobre el diff (implementación de referencia de constraint-driven-development,
// con los patrones de Java, Python y TypeScript de este repo).
// Uso: node tools/quality/floor-guard.mjs [--base <ref>]   (por defecto: origin/main)
// Salida: 0 limpio, 1 al menos una violación del piso (bloquea), 2 no se pudo correr (nunca cuenta como limpio).
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';

const base = (() => {
  const i = process.argv.indexOf('--base');
  return i > -1 ? process.argv[i + 1] : 'origin/main';
})();

// `git diff --no-index` sale con 1 cuando los lados difieren (lo normal para un archivo nuevo): esa salida se conserva.
const git = (args, { diffExit = false } = {}) => {
  try { return execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 256 * 1024 * 1024 }); }
  catch (e) { return diffExit && e.status === 1 && typeof e.stdout === 'string' ? e.stdout : null; }
};
const bail = (msg) => { console.error('floor-guard: ' + msg); process.exit(2); };

const top = git(['rev-parse', '--show-toplevel'])?.trim();
if (!top) bail('no es un repositorio git');
process.chdir(top);

const mergeBase = git(['merge-base', base, 'HEAD'])?.trim();
if (!mergeBase) bail('no hay merge base contra ' + base + ' (¿git fetch?)');

// .constraintsignore: un glob por línea; rutas exentas del guard (cada una es una excepción a la vista en el PR)
const ignoreGlobs = existsSync('.constraintsignore')
  ? readFileSync('.constraintsignore', 'utf8').split(/\r?\n/).map((l) => l.trim()).filter((l) => l && !l.startsWith('#'))
  : [];
const globToRegex = (g) => new RegExp('^' + g.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*\*/g, '§').replace(/\*/g, '[^/]*').replace(/§/g, '.*') + '$');
const ignored = ignoreGlobs.map(globToRegex);
const isIgnored = (f) => ignored.some((r) => r.test(f));

const tracked = git(['diff', '--unified=0', mergeBase, '--']);
if (tracked === null) bail('no se pudo calcular el diff contra ' + mergeBase);
const untrackedFiles = git(['ls-files', '--others', '--exclude-standard']);
if (untrackedFiles === null) bail('no se pudieron listar los archivos nuevos');
// Archivos nuevos: todas sus líneas son agregadas. Se leen del disco (un `git diff --no-index` por archivo tarda
// minutos con cientos de archivos sin commitear). Los binarios (con byte nulo) se saltan.
const untracked = untrackedFiles.split('\n').filter(Boolean).filter((f) => !isIgnored(f)).map((f) => {
  let content;
  try { content = readFileSync(f); } catch { bail('no se pudo leer el archivo nuevo ' + f); }
  if (content.includes(0)) return '';
  const lines = content.toString('utf8').split(/\r?\n/).map((l) => '+' + l).join('\n');
  return `diff --git a/${f} b/${f}\n--- /dev/null\n+++ b/${f}\n@@ -0,0 +1 @@\n${lines}`;
}).join('\n');
const diff = tracked + '\n' + untracked;

const added = [], removed = [], deleted = [];
const pathOf = (s) => s.replace(/^[ab]\//, '');
let file = '', oldFile = '', inHeader = false;
for (const line of diff.split('\n')) {
  if (line.startsWith('diff ')) inHeader = true;
  else if (line.startsWith('@@')) inHeader = false;
  else if (inHeader) {
    if (line.startsWith('--- ')) oldFile = pathOf(line.slice(4));
    else if (line.startsWith('+++ ')) {
      const newFile = pathOf(line.slice(4));
      file = newFile === '/dev/null' ? oldFile : newFile;
      if (newFile === '/dev/null') deleted.push(file);
    }
  }
  else if (isIgnored(file)) continue;
  else if (line.startsWith('+')) added.push({ file, text: line.slice(1) });
  else if (line.startsWith('-')) removed.push({ file, text: line.slice(1) });
}

const findings = [];
const flag = (rule, f, text) => findings.push({ rule, file: f, text: text.trim().slice(0, 120) });
const isTest = (f) => /\.(test|spec)\.|_test\.|(^|\/)test_|\/src\/test\/|Test\.java$|Tests\.java$/.test(f);
const isConstraints = (f) => /CONSTRAINTS\.md$/.test(f);
const isCode = (f) => /\.(java|ts|tsx|js|mjs|py)$/.test(f);

// 1. Checker silenciado. Java: @SuppressWarnings, NOPMD, CHECKSTYLE:OFF; Python: noqa, type: ignore, pragma: no cover;
//    TS: ts-ignore, eslint/oxlint-disable; y los que apagan seguridad o cobertura.
const SUPPRESSIONS = /@ts-ignore|@ts-nocheck|@ts-expect-error|eslint-disable|oxlint-disable|biome-ignore|# *noqa|# *type: *ignore|pragma: *no cover|istanbul ignore|c8 ignore|nosemgrep|gitleaks:allow|Stryker disable|@SuppressWarnings|NOPMD|CHECKSTYLE:OFF|@SuppressFBWarnings|freeze\.refreeze *= *true/;
// 4. Trabajo sin terminar.
const STUBS = /throw new (Error|UnsupportedOperationException|NotImplementedError)\(.*([Nn]ot implemented|[Nn]o implementado|TODO)|raise NotImplementedError|catch\s*\([^)]*\)\s*\{\s*\}|catch\s*\{\s*\}|except[^:]*:\s*pass\b|\bTODO\b|\bFIXME\b/;
// 2. Una prueba más fácil (skips agregados).
const SKIPS = /\.(skip|todo)\(|\bxit\(|\bxdescribe\(|@pytest\.mark\.skip\b|@Disabled\b|@Ignore\b|assumeTrue\(false/;

for (const { file, text } of added) {
  if (!isCode(file) && !/\.properties$/.test(file)) continue;
  if (SUPPRESSIONS.test(text)) flag('silenced-checker', file, text);
  if (STUBS.test(text)) flag('unfinished-work', file, text);
  if (SKIPS.test(text)) flag('test-made-easier', file, text);
}
// Adopción: si CONSTRAINTS.md no existe en la base, todas sus filas son "nuevas"; se informan sin bloquear.
// Una vez integrado, cualquier excepción agregada bloquea hasta que alguien la apruebe.
const adopting = git(['cat-file', '-e', `${mergeBase}:CONSTRAINTS.md`]) === null;
for (const { file, text } of added) {
  if (!isConstraints(file) || !/^\| *(W|E)\d+ *\|/.test(text)) continue;
  if (adopting) console.log('floor-guard: adopción, excepción registrada: ' + text.trim().slice(0, 100));
  else flag('new-exception', file, text);
}

for (const f of deleted) if (isTest(f)) flag('test-deleted', f, 'archivo eliminado');
for (const { file, text } of removed) {
  if (isTest(file) && !deleted.includes(file) && /\b(expect|assert|assertThat|should|verify)\b/.test(text)) {
    flag('assertion-removed', file, text);
  }
}

// CONSTRAINTS.md: una regla aflojada o quitada (mismo algoritmo que la referencia).
const ruleKey = (t) => {
  const s = t.trim();
  if (s.startsWith('|')) return s.split('|').map((c) => c.trim()).filter(Boolean)[0] ?? '';
  if (/^[-*] /.test(s)) return s.slice(2).split(':')[0].trim();
  return null;
};
const isException = (t) => /^\| *(W|E)\d+ *\|/.test(t.trim());
const MIN_BEFORE = /(>=|>|≥|at least|minimum|\bmin\b|no less than|not fall|not drop|al menos|mínimo)\s*$/;
const MAX_BEFORE = /(<=|<|≤|at most|maximum|\bmax\b|no more than|under|below|not grow|not exceed|máximo|como mucho)\s*$/;
const MIN_AFTER = /^\s*\S*\s*(or more|or higher|must not fall|must not drop|o más)/;
const MAX_AFTER = /^\s*\S*\s*(or less|or lower|must not grow|must not exceed|o menos)/;
const thresholds = (t) => {
  const out = [], re = /\d+(?:\.\d+)?/g;
  let m;
  while ((m = re.exec(t))) {
    const before = t.slice(Math.max(0, m.index - 24), m.index).toLowerCase();
    const after = t.slice(m.index + m[0].length, m.index + m[0].length + 40).toLowerCase();
    const dir = MIN_BEFORE.test(before) || MIN_AFTER.test(after) ? 'min'
      : MAX_BEFORE.test(before) || MAX_AFTER.test(after) ? 'max' : null;
    out.push({ n: Number(m[0]), dir });
  }
  return out;
};
const removedRules = removed.filter((l) => isConstraints(l.file) && ruleKey(l.text) !== null);
const addedRules = added.filter((l) => isConstraints(l.file) && ruleKey(l.text) !== null);
for (const r of removedRules) {
  const a = addedRules.find((x) => ruleKey(x.text) === ruleKey(r.text));
  if (!a) {
    if (!isException(r.text)) flag('rule-removed', r.file, r.text);
    continue;
  }
  const before = thresholds(r.text), after = thresholds(a.text);
  let verdict = null;
  for (const dir of ['min', 'max', null]) {
    const was = before.filter((x) => x.dir === dir), now = after.filter((x) => x.dir === dir);
    was.forEach((b, i) => {
      const n = now[i];
      if (verdict) return;
      if (!n) verdict = 'threshold-removed';
      else if (n.n === b.n) return;
      else if (dir === 'min' ? n.n < b.n : dir === 'max' ? n.n > b.n : true) {
        verdict = dir ? 'threshold-loosened' : 'threshold-changed';
      }
    });
  }
  if (verdict) flag(verdict, r.file, r.text + '  ->  ' + a.text);
}

// Excepciones de CONSTRAINTS.md: | ID | Regla | Ruta (glob) | Motivo | Dueño | Vence (AAAA-MM-DD) |
// Solo cubren el hallazgo de su regla en su ruta y hasta su fecha: vencida, vuelve a bloquear.
const today = new Date().toISOString().slice(0, 10);
const exceptions = existsSync('CONSTRAINTS.md')
  ? readFileSync('CONSTRAINTS.md', 'utf8').split(/\r?\n/).filter(isException).map((row) => {
      const [id, rule, path, , owner, expires] = row.split('|').slice(1, -1).map((c) => c.trim().replace(/`/g, ''));
      return { id, rule, path: globToRegex(path), owner, expires };
    })
  : [];
const accepted = [];
for (let i = findings.length - 1; i >= 0; i--) {
  const f = findings[i];
  const ex = exceptions.find((e) => e.rule === f.rule && e.path.test(f.file));
  if (!ex) continue;
  if (ex.expires && ex.expires < today) {
    f.text += `  (la excepción ${ex.id} venció el ${ex.expires})`;
    continue;
  }
  accepted.push(`${ex.id}: [${f.rule}] ${f.file}`);
  findings.splice(i, 1);
}
if (accepted.length) console.log('floor-guard: aceptado por excepción\n  ' + accepted.join('\n  '));

if (findings.length === 0) { console.log('floor-guard: limpio'); process.exit(0); }
console.error('floor-guard: ' + findings.length + ' violación(es) del piso:');
for (const f of findings) console.error(`  [${f.rule}] ${f.file}: ${f.text}`);
console.error('\nCada una baja el listón. Corrige el código, o regístrala como excepción con dueño y fecha en CONSTRAINTS.md.');
process.exit(1);
