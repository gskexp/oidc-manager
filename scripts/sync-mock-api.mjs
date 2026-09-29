#!/usr/bin/env node
// Regenerates public/mock-api from the backend's report configuration (the single source of truth).
//   node scripts/sync-mock-api.mjs                     write the mock files
//   node scripts/sync-mock-api.mjs --check             exit 1 if the mock files are out of date
//   node scripts/sync-mock-api.mjs --from ../report-config-repo/report-config
//                                                      use a local checkout of the GitLab config repo
// Default source: the sample config bundled in the backend (also its dev fallback).
// SQL files are never copied: the frontend must not see SQL.

import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const fromIndex = process.argv.indexOf('--from');
const source = fromIndex > 0 ? resolve(process.argv[fromIndex + 1]) : join(root, 'backend/src/main/resources/report-config');
const target = join(root, 'public/mock-api');
const check = process.argv.includes('--check');

const readJson = (path) => JSON.parse(readFileSync(path, 'utf8'));
const format = (data) => JSON.stringify(data, null, 2) + '\n';


function expectedFiles() {
  const files = new Map();
  const categories = readJson(join(source, 'categories.json'));

  files.set('categories.json', format(categories));

  for (const category of categories) {
    files.set(`categories/${category.id}.json`, format(category));

    const subPath = join(source, 'categories', category.id, 'subcategories.json');

    if (existsSync(subPath)) {
      files.set(`categories/${category.id}/subcategories.json`, format(readJson(subPath)));
    }
  }

  for (const name of readdirSync(join(source, 'report-configs')).filter((f) => f.endsWith('.json'))) {
    files.set(`report-configs/${name}`, format(readJson(join(source, 'report-configs', name))));
  }

  return files;
}

function existingFiles(dir = target) {

  if (!existsSync(dir)) return [];

  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    return entry.isDirectory() ? existingFiles(path) : [relative(target, path)];
  });
}

const expected = expectedFiles();
const existing = existingFiles();
const changed = [...expected].filter(([path, content]) => {

  const file = join(target, path);
  return !existsSync(file) || readFileSync(file, 'utf8') !== content;
});
// report-results/ holds canned run results for mock mode (see scripts/generate-mock-results.mjs),
// not config, so it is left alone here.
const stale = existing.filter((path) => !expected.has(path) && !path.startsWith(`report-results${sep}`));

if (check) {
  if (changed.length || stale.length) {

    changed.forEach(([path]) => console.error(`out of date: public/mock-api/${path}`));

    stale.forEach((path) => console.error(`not in backend config: public/mock-api/${path}`));

    console.error('\nRun `npm run mock:sync` to regenerate public/mock-api from the backend config.');

    process.exit(1);
  }
  console.log(`public/mock-api is in sync (${expected.size} files).`);
} else {
  for (const [path, content] of changed) {

    mkdirSync(dirname(join(target, path)), { recursive: true });

    writeFileSync(join(target, path), content);

    console.log(`wrote   public/mock-api/${path}`);
  }
  for (const path of stale) {

    rmSync(join(target, path));

    console.log(`removed public/mock-api/${path}`);
  }
  console.log(`public/mock-api synced (${changed.length} written, ${stale.length} removed).`);
}
