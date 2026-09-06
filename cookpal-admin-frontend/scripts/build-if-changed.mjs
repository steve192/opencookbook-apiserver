// Builds the panel into the api server's resources, and does nothing when neither the sources
// nor the dependencies have changed. Maven runs it on every build, so it has to be cheap.

import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {existsSync, readFileSync, readdirSync, statSync, writeFileSync} from 'node:fs';
import {delimiter, dirname, join, relative, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const outputDir = resolve(root, '../src/main/resources/static');
const stampFile = join(outputDir, '.build-stamp');
const modules = join(root, 'node_modules');
const installedFile = join(modules, '.admin-ui-dependencies');
const lockFile = join(root, 'package-lock.json');

const watched = ['src', 'index.html', 'package.json', 'package-lock.json', 'vite.config.ts',
  'tsconfig.json', 'tsconfig.node.json'];

const sources = hashOf(watched.map((entry) => join(root, entry)).flatMap(filesUnder));
const dependencies = hashOf([lockFile]);

if (stampOf(stampFile) === sources + dependencies) {
  console.log('The admin panel bundle is up to date');
  process.exit(0);
}

// The record lives inside node_modules, so throwing that away forces a reinstall.
if (stampOf(installedFile) !== dependencies) {
  npm(existsSync(lockFile) ? ['ci'] : ['install']);
  writeFileSync(installedFile, dependencies);
}

npm(['run', 'build']);
writeFileSync(stampFile, sources + dependencies);

function stampOf(file) {
  try {
    return readFileSync(file, 'utf8');
  } catch {
    return null;
  }
}

function filesUnder(path) {
  if (!existsSync(path)) {
    return [];
  }
  if (!statSync(path).isDirectory()) {
    return [path];
  }
  return readdirSync(path).flatMap((entry) => filesUnder(join(path, entry)));
}

function hashOf(files) {
  const hash = createHash('sha256');
  for (const file of files.sort()) {
    hash.update(relative(root, file).split('\\').join('/'));
    hash.update(readFileSync(file));
  }
  return hash.digest('hex');
}

// Whatever npm is running this script, so maven's own node is the one used.
function npm(args) {
  const npmPath = process.env.npm_execpath;
  // npm only puts .bin on PATH for the directory it starts in, and a reinstall replaces it.
  const options = {
    cwd: root,
    stdio: 'inherit',
    env: {...process.env, PATH: join(modules, '.bin') + delimiter + process.env.PATH},
  };
  const result = npmPath?.endsWith('.js') ?
    spawnSync(process.execPath, [npmPath, ...args], options) :
    spawnSync(npmPath ?? 'npm', args, {...options, shell: process.platform === 'win32'});

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}
