# BRZ Lang — Extensão VS Code

Suporte completo para a linguagem **BRZ** no Visual Studio Code.

## Funcionalidades

- **Syntax Highlighting** — Coloração de código BRZ
- **Snippets** — 30+ snippets para agilizar codificação
- **Executar** — F5 para rodar arquivo .brz
- **Compilar** — Ctrl+Shift+B para transpilar
- **Verificação** — Erros detectados ao salvar
- **Hover Docs** — Documentação ao passar o mouse sobre palavras-chave

## Instalação

### Pelo VS Code
1. Abra o VS Code
2. Vá em Extensions (Ctrl+Shift+X)
3. Pesquise "BRZ Lang"
4. Instale

### Manual (VSIX)
```bash
cd vscode-extension
npm install
npx vsce package
code --install-extension brz-lang-1.0.0.vsix
```

## Snippets Disponíveis

| Prefixo | Descrição |
|---------|-----------|
| `mostrar` | Imprimir no console |
| `se` | Bloco condicional |
| `sesenao` | Se/Senão |
| `para` | Laço for |
| `paracada` | Laço for-each |
| `enquanto` | Laço while |
| `funcao` | Declarar função |
| `classe` | Declarar classe |
| `tente` | Try/Catch |
| `criar` | CRUD criar |
| `lista` | Declarar lista |
| `main` | Template programa |

## Atalhos

| Tecla | Ação |
|-------|------|
| F5 | Executar arquivo BRZ |
| Ctrl+Shift+B | Compilar arquivo |
