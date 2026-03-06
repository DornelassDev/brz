/**
 * BRZ Lang — API Node.js
 * 
 * const brz = require('brzlang');
 * const javaCode = brz.compilar('mostrar("Olá!")', 'java');
 */

const { execSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');

class BRZLang {
    constructor() {
        this.jarPath = this._findJar();
    }

    _findJar() {
        const loc = path.join(__dirname, 'lib', 'brz.jar');
        if (fs.existsSync(loc)) return loc;
        throw new Error('[BRZ] JAR não encontrado.');
    }

    /**
     * Compila código BRZ para a linguagem alvo.
     * @param {string} codigo - Código fonte BRZ
     * @param {string} alvo - Linguagem alvo: java, python, c
     * @returns {string} Código gerado
     */
    compilar(codigo, alvo = 'java') {
        const tmpFile = path.join(os.tmpdir(), 'brz_temp_' + Date.now() + '.brz');
        fs.writeFileSync(tmpFile, codigo, 'utf-8');
        
        try {
            execSync(`java -jar "${this.jarPath}" compilar "${tmpFile}" --para ${alvo}`, {
                encoding: 'utf-8',
                stdio: ['pipe', 'pipe', 'pipe']
            });
            
            const ext = { java: '.java', python: '.py', c: '.c', cpp: '.cpp' }[alvo] || '.java';
            const outputFile = tmpFile.replace('.brz', '') + ext;
            
            // Read generated file from current dir
            const baseName = path.basename(tmpFile, '.brz');
            const outPath = baseName.charAt(0).toUpperCase() + baseName.slice(1) + ext;
            
            if (fs.existsSync(outPath)) {
                const result = fs.readFileSync(outPath, 'utf-8');
                fs.unlinkSync(outPath);
                return result;
            }
            return '// Código gerado (verifique o diretório atual)';
        } finally {
            if (fs.existsSync(tmpFile)) fs.unlinkSync(tmpFile);
        }
    }

    /**
     * Verifica erros em código BRZ.
     * @param {string} codigo - Código fonte BRZ
     * @returns {{ok: boolean, erros: string[]}}
     */
    verificar(codigo) {
        const tmpFile = path.join(os.tmpdir(), 'brz_check_' + Date.now() + '.brz');
        fs.writeFileSync(tmpFile, codigo, 'utf-8');
        
        try {
            const output = execSync(`java -jar "${this.jarPath}" verificar "${tmpFile}"`, {
                encoding: 'utf-8',
                stdio: ['pipe', 'pipe', 'pipe']
            });
            return { ok: true, erros: [] };
        } catch (e) {
            const erros = (e.stderr || e.stdout || '').split('\n').filter(l => l.trim());
            return { ok: false, erros };
        } finally {
            if (fs.existsSync(tmpFile)) fs.unlinkSync(tmpFile);
        }
    }

    /** Retorna a versão do BRZ. */
    get versao() {
        return '1.0.0';
    }
}

module.exports = new BRZLang();
