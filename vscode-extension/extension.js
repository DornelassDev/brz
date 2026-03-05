const vscode = require('vscode');
const { exec } = require('child_process');
const path = require('path');

/**
 * BRZ Lang — Extensão VS Code
 * 
 * Funcionalidades:
 *   - Syntax highlighting (via tmLanguage)
 *   - Snippets (via snippets/brz.json)
 *   - Comando: Executar arquivo BRZ (F5)
 *   - Comando: Compilar arquivo BRZ (Ctrl+Shift+B)
 *   - Comando: Verificar erros
 *   - Diagnósticos em tempo real
 */

function activate(context) {
    console.log('BRZ Lang extension ativada!');

    // Coleção de diagnósticos
    const diagnosticCollection = vscode.languages.createDiagnosticCollection('brz');
    context.subscriptions.push(diagnosticCollection);

    // ========================================
    // Comando: Executar arquivo BRZ
    // ========================================
    const rodarCmd = vscode.commands.registerCommand('brz.rodar', () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor || editor.document.languageId !== 'brz') {
            vscode.window.showWarningMessage('Abra um arquivo .brz para executar.');
            return;
        }

        editor.document.save().then(() => {
            const filePath = editor.document.fileName;
            const terminal = getOrCreateTerminal();
            terminal.show();
            terminal.sendText(`brz rodar "${filePath}"`);
        });
    });

    // ========================================
    // Comando: Compilar arquivo BRZ
    // ========================================
    const compilarCmd = vscode.commands.registerCommand('brz.compilar', async () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor || editor.document.languageId !== 'brz') {
            vscode.window.showWarningMessage('Abra um arquivo .brz para compilar.');
            return;
        }

        const alvo = await vscode.window.showQuickPick(
            ['java', 'python', 'c', 'cpp'],
            { placeHolder: 'Escolha a linguagem alvo' }
        );

        if (!alvo) return;

        await editor.document.save();
        const filePath = editor.document.fileName;
        const terminal = getOrCreateTerminal();
        terminal.show();
        terminal.sendText(`brz compilar "${filePath}" --para ${alvo}`);
    });

    // ========================================
    // Comando: Verificar erros
    // ========================================
    const verificarCmd = vscode.commands.registerCommand('brz.verificar', () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor || editor.document.languageId !== 'brz') {
            vscode.window.showWarningMessage('Abra um arquivo .brz para verificar.');
            return;
        }

        editor.document.save().then(() => {
            const filePath = editor.document.fileName;
            
            exec(`brz verificar "${filePath}"`, (error, stdout, stderr) => {
                const output = stderr || stdout || '';
                diagnosticCollection.clear();

                if (error) {
                    const diagnostics = parseBrzErrors(output, editor.document);
                    diagnosticCollection.set(editor.document.uri, diagnostics);
                    vscode.window.showErrorMessage(`BRZ: ${diagnostics.length} erro(s) encontrado(s).`);
                } else {
                    vscode.window.showInformationMessage('BRZ: Nenhum erro encontrado! ✓');
                }
            });
        });
    });

    // ========================================
    // Verificação automática ao salvar
    // ========================================
    const onSave = vscode.workspace.onDidSaveTextDocument((document) => {
        if (document.languageId !== 'brz') return;

        exec(`brz verificar "${document.fileName}"`, (error, stdout, stderr) => {
            diagnosticCollection.delete(document.uri);
            if (error) {
                const diagnostics = parseBrzErrors(stderr || stdout || '', document);
                diagnosticCollection.set(document.uri, diagnostics);
            }
        });
    });

    // ========================================
    // Hover provider — mostra info de keywords
    // ========================================
    const hoverProvider = vscode.languages.registerHoverProvider('brz', {
        provideHover(document, position) {
            const range = document.getWordRangeAtPosition(position);
            if (!range) return;
            const word = document.getText(range);
            const info = BRZ_DOCS[word];
            if (info) {
                const md = new vscode.MarkdownString();
                md.appendMarkdown(`**${info.titulo}**\n\n`);
                md.appendMarkdown(info.descricao + '\n\n');
                if (info.exemplo) {
                    md.appendCodeblock(info.exemplo, 'brz');
                }
                return new vscode.Hover(md, range);
            }
        }
    });

    context.subscriptions.push(rodarCmd, compilarCmd, verificarCmd, onSave, hoverProvider);
}

// ========================================
// Utilidades
// ========================================

let brzTerminal = null;

function getOrCreateTerminal() {
    if (brzTerminal && brzTerminal.exitStatus === undefined) {
        return brzTerminal;
    }
    brzTerminal = vscode.window.createTerminal({
        name: 'BRZ Lang',
        iconPath: new vscode.ThemeIcon('terminal')
    });
    return brzTerminal;
}

function parseBrzErrors(output, document) {
    const diagnostics = [];
    const lines = output.split('\n');
    
    for (const line of lines) {
        // Padrão: [BRZ] ✗ Erro na linha X: mensagem
        const match = line.match(/linha\s+(\d+)/i);
        if (match) {
            const lineNum = Math.max(0, parseInt(match[1]) - 1);
            const message = line.replace(/\[BRZ\]\s*✗?\s*/, '').trim();
            const range = new vscode.Range(lineNum, 0, lineNum, 999);
            diagnostics.push(new vscode.Diagnostic(range, message, vscode.DiagnosticSeverity.Error));
        }
    }
    return diagnostics;
}

// ========================================
// Documentação inline para hover
// ========================================
const BRZ_DOCS = {
    'mostrar': {
        titulo: 'mostrar(expressão)',
        descricao: 'Imprime uma ou mais expressões no console.',
        exemplo: 'mostrar("Olá, mundo!")\nmostrar("Nome: " + nome)'
    },
    'se': {
        titulo: 'se (condição) { ... }',
        descricao: 'Executa um bloco de código se a condição for verdadeira.',
        exemplo: 'se (idade >= 18) {\n    mostrar("Maior de idade")\n}'
    },
    'senao': {
        titulo: 'senao { ... }',
        descricao: 'Bloco executado quando a condição do `se` é falsa.',
        exemplo: 'se (x > 0) {\n    mostrar("Positivo")\n} senao {\n    mostrar("Não positivo")\n}'
    },
    'para': {
        titulo: 'para (init; cond; inc) { ... }',
        descricao: 'Laço de repetição com inicialização, condição e incremento.',
        exemplo: 'para (numero i = 0; i < 10; i++) {\n    mostrar(i)\n}'
    },
    'enquanto': {
        titulo: 'enquanto (condição) { ... }',
        descricao: 'Repete enquanto a condição for verdadeira.',
        exemplo: 'enquanto (x > 0) {\n    x -= 1\n}'
    },
    'funcao': {
        titulo: 'funcao tipo nome(params) { ... }',
        descricao: 'Declara uma função.',
        exemplo: 'funcao numero somar(numero a, numero b) {\n    retorne a + b\n}'
    },
    'classe': {
        titulo: 'classe Nome { ... }',
        descricao: 'Declara uma classe com atributos, construtor e métodos.',
        exemplo: 'classe Pessoa {\n    texto nome\n    construtor(texto nome) {\n        este.nome = nome\n    }\n}'
    },
    'tente': {
        titulo: 'tente { ... } pegue (erro) { ... }',
        descricao: 'Bloco de tratamento de erros.',
        exemplo: 'tente {\n    numero r = 10 / 0\n} pegue (erro) {\n    mostrar("Erro: " + erro)\n}'
    },
    'criar': {
        titulo: 'criar(entidade, dados)',
        descricao: 'CRUD: Cria um novo registro.',
        exemplo: 'criar("Produto", {nome: "Mouse", preco: 49.90})'
    },
    'listar': {
        titulo: 'listar(entidade)',
        descricao: 'CRUD: Lista todos os registros.',
        exemplo: 'listar("Produto")'
    },
    'lerTexto': {
        titulo: 'lerTexto()',
        descricao: 'Lê uma linha de texto digitada pelo usuário.',
        exemplo: 'texto nome = lerTexto()'
    },
    'lerNumero': {
        titulo: 'lerNumero()',
        descricao: 'Lê um número inteiro digitado pelo usuário.',
        exemplo: 'numero idade = lerNumero()'
    },
    'retorne': {
        titulo: 'retorne expressão',
        descricao: 'Retorna um valor de uma função.',
        exemplo: 'funcao numero dobro(numero n) {\n    retorne n * 2\n}'
    },
    'lista': {
        titulo: 'lista tipo nome = [...]',
        descricao: 'Declara uma lista (array) tipada.',
        exemplo: 'lista numero nums = [1, 2, 3, 4, 5]'
    },
    'constante': {
        titulo: 'constante tipo NOME = valor',
        descricao: 'Declara uma constante (valor não pode ser alterado).',
        exemplo: 'constante numero MAX = 100'
    },
    'verdadeiro': {
        titulo: 'verdadeiro',
        descricao: 'Literal booleano: valor verdadeiro (true).',
        exemplo: 'logico ativo = verdadeiro'
    },
    'falso': {
        titulo: 'falso',
        descricao: 'Literal booleano: valor falso (false).',
        exemplo: 'logico ativo = falso'
    },
    'nulo': {
        titulo: 'nulo',
        descricao: 'Representa a ausência de valor.',
        exemplo: 'texto x = nulo'
    },
    'lance': {
        titulo: 'lance(expressão)',
        descricao: 'Lança um erro (exceção).',
        exemplo: 'lance("Valor inválido!")'
    },
    'escolha': {
        titulo: 'escolha (expr) { caso val: { ... } }',
        descricao: 'Estrutura de seleção múltipla (switch).',
        exemplo: 'escolha (opcao) {\n    caso 1: { mostrar("Um") }\n    caso 2: { mostrar("Dois") }\n    padrao: { mostrar("Outro") }\n}'
    },
    'novo': {
        titulo: 'novo Classe(args)',
        descricao: 'Cria uma nova instância de uma classe.',
        exemplo: 'Pessoa p = novo Pessoa("João", 25)'
    }
};

function deactivate() {
    console.log('BRZ Lang extension desativada.');
}

module.exports = { activate, deactivate };
