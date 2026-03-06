#!/usr/bin/env node

const { spawn, execSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const JAR = path.join(__dirname, '..', 'lib', 'brz.jar');

// Verifica se o JAR existe
if (!fs.existsSync(JAR)) {
    console.error('\x1b[31m[BRZ] Erro interno: brz.jar não encontrado.\x1b[0m');
    process.exit(1);
}

// Verifica se Java está instalado
try {
    execSync('java -version', { stdio: 'pipe' });
} catch (e) {
    console.error('\x1b[31m[BRZ] Java não encontrado.\x1b[0m');
    console.error('BRZ precisa do Java 17+ instalado.');
    console.error('');
    console.error('  macOS:   brew install openjdk@17');
    console.error('  Ubuntu:  sudo apt install openjdk-17-jdk');
    console.error('  Windows: https://adoptium.net/');
    process.exit(1);
}

let args = process.argv.slice(2);

// Sem argumentos → abre o console (REPL)
if (args.length === 0) {
    args = ['console'];
}

// Se o primeiro argumento é um arquivo .brz, assume "rodar"
if (args.length >= 1 && args[0].endsWith('.brz') && fs.existsSync(args[0])) {
    args = ['rodar', ...args];
}

const proc = spawn('java', ['-jar', JAR, ...args], {
    stdio: 'inherit'
});

proc.on('close', (code) => {
    process.exit(code || 0);
});
