#!/usr/bin/env node

/**
 * BRZ Lang — CLI via npm
 * 
 * Instalação: npm install -g brzlang
 * Uso: brz rodar arquivo.brz
 */

const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

const JAR_PATH = path.join(__dirname, '..', 'java', 'target', 'brz.jar');
const JAR_LOCAL = path.join(__dirname, '..', 'lib', 'brz.jar');

function findJar() {
    if (fs.existsSync(JAR_PATH)) return JAR_PATH;
    if (fs.existsSync(JAR_LOCAL)) return JAR_LOCAL;
    
    console.error('\x1b[31m[BRZ] JAR não encontrado.\x1b[0m');
    console.error('Execute: npm run build');
    process.exit(1);
}

function checkJava() {
    try {
        const result = require('child_process').execSync('java -version 2>&1', { encoding: 'utf-8' });
        return true;
    } catch (e) {
        console.error('\x1b[31m[BRZ] Java não encontrado. Instale o JDK 17+.\x1b[0m');
        process.exit(1);
    }
}

checkJava();
const jarPath = findJar();
const args = process.argv.slice(2);

const proc = spawn('java', ['-jar', jarPath, ...args], {
    stdio: 'inherit',
    env: { ...process.env }
});

proc.on('close', (code) => {
    process.exit(code || 0);
});
