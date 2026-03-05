# BRZ

> Linguagem de programacao brasileira com transpilacao para Java, Python, C e C++.

BRZ nasceu de uma necessidade real: o ingles nao deveria ser barreira pra aprender a programar. Com BRZ, voce escreve codigo inteiro em portugues -- `se`, `para`, `funcao`, `classe`, `enquanto` -- e o compilador se encarrega de gerar codigo limpo e funcional na linguagem que voce precisar.

Nao e um wrapper, nao e macro, nao e tradutor de string. E um compilador completo: analise lexica, parsing com recursive descent, arvore sintatica abstrata e geracao de codigo com visitor pattern. Cada linguagem alvo tem seu proprio gerador que produz codigo idiomatico.

---

### Como funciona

Voce escreve um `.brz`:

```
classe Conta {
    texto dono
    decimal saldo

    construtor(texto dono, decimal saldoInicial) {
        este.dono = dono
        este.saldo = saldoInicial
    }

    funcao vazio depositar(decimal valor) {
        este.saldo += valor
        mostrar("Deposito de R$" + valor + " realizado")
    }
}

Conta c = novo Conta("Carlos", 500.0)
c.depositar(200.0)
mostrar(c.saldo)
```

E escolhe o alvo. O compilador gera o equivalente em Java (com classe, main, Scanner), Python (com indentacao, `__name__`, type hints), C (com struct, typedef, printf) ou C++ (com std::string, iostream, classes nativas). O codigo de saida compila e roda direto, sem ajuste manual.

---

### Setup

**Requer Java 17+ e Maven.**

```
git clone https://github.com/DornelassDev/brz.git
cd brz
mvn package -DskipTests
```

Isso gera o JAR em `target/`. Dai e so usar os scripts `brz` (Linux/Mac) ou `brz.bat` (Windows).

Tambem da pra instalar via npm se preferir:

```
npm install -g brzlang
```

---

### Comandos

```
brz rodar arquivo.brz                   # transpila, compila e executa
brz compilar arquivo.brz --para java    # gera .java
brz compilar arquivo.brz --para python  # gera .py
brz compilar arquivo.brz --para c       # gera .c
brz compilar arquivo.brz --para cpp     # gera .cpp
brz console                             # REPL interativo
brz verificar arquivo.brz               # analisa erros sem executar
brz novo projeto NomeDoProjeto          # scaffolding
```

---

### O que a linguagem suporta

Tipos primitivos (`numero`, `decimal`, `texto`, `logico`, `caractere`), constantes, condicionais com `se`/`senao`/`escolha`, loops com `para`/`enquanto`/`para cada`, funcoes com tipagem, classes com heranca (`estende`), listas dinamicas, operacoes CRUD nativas, I/O com `mostrar`/`lerTexto`/`lerNumero`, tratamento de excecoes com `tente`/`pegue`/`finalmente`, e operadores logicos em portugues (`e`, `ou`, `nao`).

Tem 11 programas de exemplo na pasta `exemplos/` cobrindo desde hello world ate um sistema completo de notas.

---

### Arquitetura

```
src/main/java/brz/
  lexer/       analise lexica e tokenizacao
  parser/      recursive descent parser
  ast/         arvore sintatica + interface visitor
  generator/
    java/      geracao de codigo Java
    python/    geracao de codigo Python
    c/         geracao de codigo C
    cpp/       geracao de codigo C++
  cli/         interface de linha de comando
```

Cada gerador implementa a interface `No.Visitante<String>` e percorre a AST produzindo codigo na linguagem alvo. O parser usa precedence climbing pra expressoes e recovery basico pra erros.

---

### Extras

- **VS Code**: extensao com syntax highlighting, 30+ snippets, execucao com F5, diagnostico ao salvar
- **Docs**: site completo em [DornelassDev.github.io/brz](https://DornelassDev.github.io/brz/)
- **npm**: pacote `brzlang` com CLI e API Node.js

---

MIT License
