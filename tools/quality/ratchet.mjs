#!/usr/bin/env node
// ratchet.mjs — "medir hoy y no empeorar" (CONSTRAINTS.md). Junta las violaciones de tamaño, complejidad y
// acoplamiento de PMD (Java), oxlint (TypeScript) y Ruff (Python) y las compara con tools/quality/baseline.json.
//
//   node tools/quality/ratchet.mjs            compara (PMD debe haber corrido: ./mvnw pmd:pmd)
//   node tools/quality/ratchet.mjs --update   baja la línea base donde se mejoró (nunca la sube)
//   node tools/quality/ratchet.mjs --init     escribe la línea base desde cero (solo al adoptar)
//
// Salida: 0 sin empeoras (o antes de enforceFrom: avisa), 1 algo empeoró, 2 no se pudo medir.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

const ROOT = join(import.meta.dirname, '..', '..');
const BASELINE = join(ROOT, 'tools', 'quality', 'baseline.json');
const mode = process.argv.includes('--init') ? 'init' : process.argv.includes('--update') ? 'update' : 'check';
const bail = (msg) => { console.error('ratchet: ' + msg); process.exit(2); };
const rel = (p) => relative(ROOT, p).split(sep).join('/');

/** key → [{ where, message }] */
const found = new Map();
const add = (key, where, message) => {
  if (!found.has(key)) found.set(key, []);
  found.get(key).push({ where, message });
};

// ── Java: target/pmd.xml de cada módulo ─────────────────────────────────────
function findPmdReports(dir, out = []) {
  for (const name of readdirSync(dir)) {
    if (name === 'node_modules' || name === '.git' || name === 'src') continue;
    const full = join(dir, name);
    if (!statSync(full).isDirectory()) continue;
    const report = join(full, 'target', 'pmd.xml');
    if (existsSync(report)) out.push({ module: rel(full).replace('services/backend/', ''), report });
    if (name !== 'target') findPmdReports(full, out);
  }
  return out;
}
const pmd = findPmdReports(join(ROOT, 'services', 'backend'));
if (pmd.length === 0) bail('no hay target/pmd.xml: corre primero "./mvnw pmd:pmd" en services/backend');
for (const { module, report } of pmd) {
  const xml = readFileSync(report, 'utf8');
  for (const fileBlock of xml.split('<file name="').slice(1)) {
    const file = rel(fileBlock.slice(0, fileBlock.indexOf('"')).replace(/&amp;/g, '&'));
    for (const v of fileBlock.split('<violation').slice(1)) {
      const rule = v.match(/rule="([^"]+)"/)?.[1];
      const line = v.match(/beginline="(\d+)"/)?.[1];
      const message = v.slice(v.indexOf('>') + 1, v.indexOf('</violation>')).trim();
      add(`java:${module}:${rule}`, `${file}:${line}`, message);
    }
  }
}

// ── TypeScript: oxlint ──────────────────────────────────────────────────────
const run = (cmd, args, cwd) => {
  try {
    return execFileSync(cmd, args, { cwd, encoding: 'utf8', shell: process.platform === 'win32', maxBuffer: 64 * 1024 * 1024 });
  } catch (e) {
    if (typeof e.stdout === 'string' && e.stdout.trim()) return e.stdout; // los linters salen con 1 si hay hallazgos
    bail(`falló "${cmd} ${args.join(' ')}" en ${rel(cwd)}: ${e.message.split('\n')[0]}`);
  }
};
for (const project of ['apps/web', 'services/scraper']) {
  const cwd = join(ROOT, project);
  const oxlint = join(cwd, 'node_modules', '.bin', process.platform === 'win32' ? 'oxlint.cmd' : 'oxlint');
  const out = run(oxlint, ['-c', '.oxlintrc.json', '--format', 'json', 'src'], cwd);
  for (const d of JSON.parse(out).diagnostics ?? []) {
    const rule = d.code.replace(/^eslint\((.*)\)$/, '$1');
    const line = d.labels?.[0]?.span?.line ?? '';
    add(`ts:${project.split('/').pop()}:${rule}`, `${project}/${d.filename}:${line}`, d.message);
  }
}

// ── Python: Ruff ────────────────────────────────────────────────────────────
{
  const cwd = join(ROOT, 'services', 'mcp-gateway');
  const ruff = join(cwd, '.venv', process.platform === 'win32' ? 'Scripts/ruff.exe' : 'bin/ruff');
  if (!existsSync(ruff)) bail('falta Ruff: en services/mcp-gateway corre "uv sync"');
  const out = run(ruff, ['check', 'src', '--output-format', 'json', '--exit-zero'], cwd);
  for (const d of JSON.parse(out)) {
    add(`py:mcp-gateway:${d.code}`, `${rel(d.filename)}:${d.location?.row ?? ''}`, d.message);
  }
}

const current = Object.fromEntries([...found.entries()].map(([k, v]) => [k, v.length]).sort());

if (mode === 'init') {
  const enforceFrom = process.argv[process.argv.indexOf('--init') + 1]?.match(/^\d{4}-\d{2}-\d{2}$/)?.[0]
    ?? new Date(Date.now() + 14 * 86_400_000).toISOString().slice(0, 10);
  writeFileSync(BASELINE, JSON.stringify({ enforceFrom, metrics: current }, null, 2) + '\n');
  console.log(`ratchet: línea base escrita (${Object.values(current).reduce((a, b) => a + b, 0)} violaciones, bloquea desde ${enforceFrom})`);
  process.exit(0);
}

if (!existsSync(BASELINE)) bail('no existe tools/quality/baseline.json: corre con --init al adoptar');
const baseline = JSON.parse(readFileSync(BASELINE, 'utf8'));
const keys = new Set([...Object.keys(baseline.metrics), ...Object.keys(current)]);
const worse = [], better = [];
for (const key of [...keys].sort()) {
  const was = baseline.metrics[key] ?? 0, now = current[key] ?? 0;
  if (now > was) worse.push({ key, was, now });
  else if (now < was) better.push({ key, was, now });
}

if (mode === 'update') {
  for (const { key, now } of better) {
    if (now === 0) delete baseline.metrics[key];
    else baseline.metrics[key] = now;
  }
  writeFileSync(BASELINE, JSON.stringify(baseline, null, 2) + '\n');
  console.log(`ratchet: línea base ajustada (${better.length} métrica(s) mejoraron)` + (worse.length ? `; ${worse.length} empeoraron y NO se registraron` : ''));
  process.exit(worse.length ? 1 : 0);
}

for (const { key, was, now } of better) console.log(`ratchet: mejoró ${key}: ${was} → ${now} (registra con --update)`);
if (worse.length === 0) {
  const total = Object.values(current).reduce((a, b) => a + b, 0);
  console.log(`ratchet: sin empeoras (${total} violaciones heredadas registradas)`);
  process.exit(0);
}

const today = new Date().toISOString().slice(0, 10);
const enforced = today >= baseline.enforceFrom;
const label = enforced ? 'ERROR' : `AVISO (bloquea desde ${baseline.enforceFrom})`;
console.error(`ratchet: ${label} — ${worse.length} métrica(s) empeoraron:`);
for (const { key, was, now } of worse) {
  console.error(`  ${key}: ${was} → ${now}`);
  for (const v of found.get(key) ?? []) console.error(`      ${v.where}  ${v.message}`);
}
console.error('\nArregla el código (divide la función, saca un objeto de parámetros, invierte la dependencia). Subir la línea base');
console.error('a mano es aflojar el listón: va con su motivo en el PR.');
process.exit(enforced ? 1 : 0);
