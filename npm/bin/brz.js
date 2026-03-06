#!/usr/bin/env node

/**
 * BRZ VM — CLI Wrapper
 *
 * Thin wrapper que chama o binário nativo da VM.
 * Não precisa de Java, Node, nem nada — é C puro.
 */

const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

const BINARY = path.join(__dirname, 'brz-native');

if (!fs.existsSync(BINARY)) {
    console.error('\x1b[31m[BRZ] Binário nativo não encontrado.\x1b[0m');
    console.error('Tente reinstalar: npm install -g brzlang');
    process.exit(1);
}

const args = process.argv.slice(2);

const proc = spawn(BINARY, args, {
    stdio: 'inherit'
});

proc.on('close', (code) => {
    process.exit(code || 0);
});
