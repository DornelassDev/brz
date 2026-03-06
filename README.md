# BRZ 🇧🇷

> Linguagem de programação brasileira com VM nativa — zero dependências externas.

BRZ nasceu de uma necessidade real: o inglês não deveria ser barreira pra aprender a programar. Com BRZ, você escreve código inteiro em português — `se`, `para`, `funcao`, `classe`, `enquanto` — e a VM executa diretamente, sem precisar de Java, Python ou qualquer outra coisa instalada.

Não é transpilador, não é wrapper, não é tradutor de string. É uma **máquina virtual completa** escrita em C: scanner, compilador single-pass com Pratt parser, bytecode de 45 opcodes, stack-based VM com garbage collector mark-and-sweep, string interning e closures de primeira classe. ~5.200 linhas de C99 puro.

---

### Como funciona

Você escreve um `.brz`:

```
classe Animal {
    iniciar(nome, som) {
        este.nome = nome
        este.som = som
    }

    falar() {
        mostrar(este.nome + " faz " + este.som)
    }
}

classe Cachorro herda Animal {
    iniciar(nome) {
        super.iniciar(nome, "Au au!")
    }

    buscar(item) {
        mostrar(este.nome + " buscou o " + item + "!")
    }
}

var rex = Cachorro("Rex")
rex.falar()
rex.buscar("graveto")
```

E roda direto:

```
$ brz animal.brz
Rex faz Au au!
Rex buscou o graveto!
```

O código é compilado pra bytecode e executado pela VM na hora. Sem arquivos intermediários, sem compilador externo.

---

### Instalação

**Só precisa de Node.js (pra instalar) e um compilador C (gcc/clang — já vem no Mac e na maioria dos Linux).**

```bash
npm install -g brzlang
```

O pacote compila a VM automaticamente durante a instalação. Pronto. Sem Java, sem Maven, sem nada.

Ou, se preferir compilar do fonte:

```bash
git clone https://github.com/DornelassDev/brz.git
cd brz/vm
make
```

O binário fica em `vm/bin/brz`.

---

### Comandos

```bash
brz arquivo.brz              # executa o programa
brz -c 'mostrar("ola")'      # executa código inline
brz                           # REPL interativo
```

---

### O que a linguagem suporta

- **Tipos**: números, strings, booleanos (`verdadeiro`/`falso`), `nulo`
- **Variáveis**: `var nome = valor`
- **Condicionais**: `se` / `senao`
- **Loops**: `enquanto`, `para`
- **Funções**: `funcao nome(args) { ... }`, closures, recursão
- **Classes**: `classe Nome { ... }`, herança com `herda`, `super`, `este`
- **Construtores**: método `iniciar()`
- **I/O**: `mostrar()`, `lerTexto()`, `lerNumero()`
- **Operadores**: `e`, `ou`, `nao` (lógicos em português)
- **Built-ins**: `relogio()` (timestamp), operações com strings

12 programas de exemplo na pasta `exemplos/` cobrindo desde hello world até um sistema CRUD completo.

---

### Arquitetura da VM

```
vm/
├── include/          headers (.h)
│   ├── common.h      tipos e flags globais
│   ├── scanner.h     análise léxica
│   ├── compiler.h    compilador single-pass (Pratt parser)
│   ├── chunk.h       bytecode chunks
│   ├── value.h       sistema de valores (tagged union)
│   ├── object.h      objetos heap (strings, funções, classes)
│   ├── vm.h          máquina virtual stack-based
│   ├── memory.h      alocador + garbage collector
│   ├── table.h       hash table (open addressing)
│   └── debug.h       disassembler de bytecode
└── src/              implementações (.c)
    ├── main.c        entry point + REPL
    ├── scanner.c     tokenização de palavras-chave PT-BR
    ├── compiler.c    ~1800 linhas, emite bytecode
    ├── vm.c          dispatch loop + runtime
    ├── object.c      alocação de objetos
    ├── memory.c      GC mark-and-sweep
    ├── table.c       hash table com tombstones
    ├── value.c       impressão e comparação
    ├── chunk.c       arrays dinâmicos de bytecode
    └── debug.c       descompilador pra debug
```

**Pipeline**: Source → Scanner (tokens) → Compiler (bytecode) → VM (execução) → GC (memória automática)

---

### Extras

- **VS Code**: extensão com syntax highlighting, 30+ snippets, execução com F5, diagnóstico ao salvar
- **Docs**: site completo em [DornelassDev.github.io/brz](https://DornelassDev.github.io/brz/)
- **npm**: `npm install -g brzlang` — CLI e API Node.js

---

MIT License
