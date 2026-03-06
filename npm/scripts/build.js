#!/usr/bin/env node

/**
 * BRZ VM — Build Script (postinstall)
 *
 * Compila a VM nativa em C durante `npm install -g brzlang`.
 * Requer um compilador C (cc/gcc/clang) disponível no sistema.
 */

const { execSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const ROOT = path.join(__dirname, '..');
const VM_DIR = path.join(ROOT, 'vm');
const SRC_DIR = path.join(VM_DIR, 'src');
const INC_DIR = path.join(VM_DIR, 'include');
const BIN_DIR = path.join(ROOT, 'bin');
const OUTPUT = path.join(BIN_DIR, 'brz-native');

// Verifica se já existe binário compilado
if (fs.existsSync(OUTPUT)) {
    console.log('[BRZ] Binário nativo já existe. Pulando compilação.');
    process.exit(0);
}

// Detecta compilador C
let CC = 'cc';
try {
    execSync('cc --version', { stdio: 'pipe' });
} catch {
    try {
        execSync('gcc --version', { stdio: 'pipe' });
        CC = 'gcc';
    } catch {
        try {
            execSync('clang --version', { stdio: 'pipe' });
            CC = 'clang';
        } catch {
            console.error('\x1b[31m[BRZ] Compilador C não encontrado.\x1b[0m');
            console.error('BRZ VM precisa de um compilador C (cc, gcc ou clang).');
            console.error('');
            console.error('  macOS:   xcode-select --install');
            console.error('  Ubuntu:  sudo apt install build-essential');
            process.exit(1);
        }
    }
}

// Encontra todos os .c
const sources = fs.readdirSync(SRC_DIR)
    .filter(f => f.endsWith('.c'))
    .map(f => path.join(SRC_DIR, f));

if (sources.length === 0) {
    console.error('\x1b[31m[BRZ] Fontes C não encontrados.\x1b[0m');
    process.exit(1);
}

// Compila
const cmd = `${CC} -std=c99 -O2 -DNDEBUG -Wall -Wextra -I${INC_DIR} ${sources.join(' ')} -o ${OUTPUT} -lm`;

console.log('[BRZ] Compilando VM nativa...');

try {
    execSync(cmd, { stdio: 'inherit', cwd: ROOT });
    fs.chmodSync(OUTPUT, 0o755);
    console.log('\x1b[32m[BRZ] VM nativa compilada com sucesso!\x1b[0m');
} catch (err) {
    console.error('\x1b[31m[BRZ] Falha na compilação.\x1b[0m');
    console.error(err.message);
    process.exit(1);
}
