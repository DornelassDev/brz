/**
 * BRZ Lang — API Node.js
 *
 * const brz = require('brzlang');
 * brz.executar('mostrar("Olá!")');
 */

const { execSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');

class BRZLang {
    constructor() {
        this.binary = path.join(__dirname, 'bin', 'brz-native');
        if (!fs.existsSync(this.binary)) {
            throw new Error('[BRZ] Binário nativo não encontrado. Reinstale: npm install -g brzlang');
        }
    }

    /**
     * Executa código BRZ inline.
     * @param {string} code - Código BRZ a executar
     * @returns {string} - Saída do programa
     */
    executar(code) {
        try {
            return execSync(`${this.binary} -c ${JSON.stringify(code)}`, {
                encoding: 'utf-8',
                timeout: 30000
            }).trim();
        } catch (err) {
            throw new Error(err.stderr || err.message);
        }
    }

    /**
     * Executa um arquivo .brz.
     * @param {string} filePath - Caminho do arquivo .brz
     * @returns {string} - Saída do programa
     */
    executarArquivo(filePath) {
        if (!fs.existsSync(filePath)) {
            throw new Error(`Arquivo não encontrado: ${filePath}`);
        }
        try {
            return execSync(`${this.binary} ${filePath}`, {
                encoding: 'utf-8',
                timeout: 30000
            }).trim();
        } catch (err) {
            throw new Error(err.stderr || err.message);
        }
    }

    /**
     * Retorna a versão da VM.
     * @returns {string}
     */
    versao() {
        return execSync(`${this.binary} --versao`, { encoding: 'utf-8' }).trim();
    }
}

module.exports = new BRZLang();
