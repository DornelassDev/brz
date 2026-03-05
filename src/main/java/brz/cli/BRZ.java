package brz.cli;

import brz.ast.No;
import brz.generator.c.GeradorC;
import brz.generator.java.GeradorJava;
import brz.generator.python.GeradorPython;
import brz.lexer.Lexer;
import brz.lexer.Token;
import brz.parser.Parser;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Scanner;

/**
 * BRZ — Ponto de entrada principal da linguagem BRZ.
 * 
 * Gerencia toda a interação via linha de comando:
 *   brz rodar arquivo.brz          → Executa arquivo BRZ (transpila + roda)
 *   brz compilar arquivo.brz       → Gera código Java (padrão)
 *   brz compilar arquivo.brz --para java|python|c
 *   brz console                    → REPL interativo
 *   brz verificar arquivo.brz      → Checa erros sem executar
 *   brz ajuda                      → Mostra ajuda
 *   brz versao                     → Mostra versão
 * 
 * @author BRZ Team
 * @version 1.0.0
 * @since 2026-03-05
 */
public class BRZ {

    public static final String VERSAO = "1.0.0";
    public static final String NOME = "BRZ Lang";

    // Cores ANSI para terminal
    private static final String RESET = "\u001B[0m";
    private static final String VERMELHO = "\u001B[31m";
    private static final String VERDE = "\u001B[32m";
    private static final String AMARELO = "\u001B[33m";
    private static final String AZUL = "\u001B[34m";
    private static final String CIANO = "\u001B[36m";
    private static final String NEGRITO = "\u001B[1m";

    public static void main(String[] args) {
        if (args.length == 0) {
            mostrarBanner();
            mostrarAjuda();
            return;
        }

        String comando = args[0].toLowerCase();

        switch (comando) {
            case "rodar":
            case "executar":
            case "run":
                if (args.length < 2) {
                    erro("Uso: brz rodar <arquivo.brz>");
                    return;
                }
                rodar(args[1]);
                break;

            case "compilar":
            case "compile":
            case "build":
                if (args.length < 2) {
                    erro("Uso: brz compilar <arquivo.brz> [--para java|python|c]");
                    return;
                }
                String alvo = "java";
                for (int i = 2; i < args.length; i++) {
                    if (args[i].equals("--para") || args[i].equals("--target") || args[i].equals("-t")) {
                        if (i + 1 < args.length) alvo = args[i + 1].toLowerCase();
                    }
                }
                compilar(args[1], alvo);
                break;

            case "console":
            case "repl":
                console();
                break;

            case "verificar":
            case "check":
                if (args.length < 2) {
                    erro("Uso: brz verificar <arquivo.brz>");
                    return;
                }
                verificar(args[1]);
                break;

            case "novo":
            case "new":
                if (args.length < 3) {
                    erro("Uso: brz novo projeto <NomeProjeto>");
                    return;
                }
                if (args[1].equals("projeto") || args[1].equals("project")) {
                    novoProjeto(args[2]);
                }
                break;

            case "versao":
            case "version":
            case "--version":
            case "-v":
                info(NOME + " v" + VERSAO);
                break;

            case "ajuda":
            case "help":
            case "--help":
            case "-h":
                mostrarBanner();
                mostrarAjuda();
                break;

            default:
                // Se termina com .brz, assume "rodar"
                if (comando.endsWith(".brz")) {
                    rodar(comando);
                } else {
                    erro("Comando desconhecido: '" + comando + "'. Use 'brz ajuda' para ver comandos.");
                }
                break;
        }
    }

    // =====================================================================
    // COMANDOS PRINCIPAIS
    // =====================================================================

    /**
     * Executa um arquivo BRZ: transpila para Java, compila e executa.
     */
    private static void rodar(String caminhoArquivo) {
        try {
            String fonte = lerArquivo(caminhoArquivo);
            String nomeClasse = extrairNomeClasse(caminhoArquivo);

            info("Compilando " + caminhoArquivo + "...");

            // Lexer
            Lexer lexer = new Lexer(fonte, caminhoArquivo);
            List<Token> tokens = lexer.tokenizar();
            if (lexer.temErros()) {
                for (String e : lexer.getErros()) erro(e);
                return;
            }

            // Parser
            Parser parser = new Parser(tokens, caminhoArquivo);
            No.Programa programa = parser.parsear();
            if (parser.temErros()) {
                for (String e : parser.getErros()) erro(e);
                return;
            }

            // Gerador Java
            GeradorJava gerador = new GeradorJava();
            String codigoJava = gerador.gerar(programa, nomeClasse);

            // Salva o .java
            String caminhoJava = nomeClasse + ".java";
            Files.writeString(Path.of(caminhoJava), codigoJava);

            // Compila com javac
            info("Compilando Java...");
            ProcessBuilder pb = new ProcessBuilder("javac", caminhoJava);
            pb.inheritIO();
            Process processo = pb.start();
            int exitCode = processo.waitFor();

            if (exitCode != 0) {
                erro("Erro na compilação Java. Verifique o código gerado em " + caminhoJava);
                return;
            }

            // Executa
            info("Executando...\n");
            ProcessBuilder pbRun = new ProcessBuilder("java", nomeClasse);
            pbRun.inheritIO();
            Process processoRun = pbRun.start();
            processoRun.waitFor();

            // Limpa arquivos temporários
            Files.deleteIfExists(Path.of(caminhoJava));
            Files.deleteIfExists(Path.of(nomeClasse + ".class"));

            sucesso("\nExecução concluída!");

        } catch (Exception e) {
            erro("Erro ao executar: " + e.getMessage());
        }
    }

    /**
     * Compila (transpila) um arquivo BRZ para a linguagem alvo.
     */
    private static void compilar(String caminhoArquivo, String alvo) {
        try {
            String fonte = lerArquivo(caminhoArquivo);
            String nomeBase = extrairNomeClasse(caminhoArquivo);

            // Lexer + Parser
            Lexer lexer = new Lexer(fonte, caminhoArquivo);
            List<Token> tokens = lexer.tokenizar();
            if (lexer.temErros()) {
                for (String e : lexer.getErros()) erro(e);
                return;
            }

            Parser parser = new Parser(tokens, caminhoArquivo);
            No.Programa programa = parser.parsear();
            if (parser.temErros()) {
                for (String e : parser.getErros()) erro(e);
                return;
            }

            String codigoGerado;
            String extensao;

            switch (alvo) {
                case "java":
                    GeradorJava gerJava = new GeradorJava();
                    codigoGerado = gerJava.gerar(programa, nomeBase);
                    extensao = ".java";
                    break;

                case "python":
                case "py":
                    GeradorPython gerPython = new GeradorPython();
                    codigoGerado = gerPython.gerar(programa);
                    extensao = ".py";
                    break;

                case "c":
                    GeradorC gerC = new GeradorC();
                    codigoGerado = gerC.gerar(programa);
                    extensao = ".c";
                    break;

                default:
                    erro("Alvo não suportado: '" + alvo + "'. Use: java, python, c");
                    return;
            }

            String caminhoSaida = nomeBase + extensao;
            Files.writeString(Path.of(caminhoSaida), codigoGerado);
            sucesso("Código " + alvo.toUpperCase() + " gerado: " + caminhoSaida);

        } catch (Exception e) {
            erro("Erro na compilação: " + e.getMessage());
        }
    }

    /**
     * REPL interativo — console BRZ.
     */
    private static void console() {
        mostrarBanner();
        info("Modo interativo. Digite 'sair' para sair.\n");

        Scanner scanner = new Scanner(System.in);
        StringBuilder buffer = new StringBuilder();
        int contadorChaves = 0;

        while (true) {
            System.out.print(contadorChaves > 0 ? "... " : "brz> ");
            if (!scanner.hasNextLine()) break;

            String linha = scanner.nextLine();

            if (linha.trim().equals("sair") || linha.trim().equals("exit")) {
                info("Até mais! 👋");
                break;
            }

            if (linha.trim().equals("limpar") || linha.trim().equals("clear")) {
                System.out.print("\033[H\033[2J");
                System.out.flush();
                continue;
            }

            buffer.append(linha).append("\n");
            contadorChaves += contarCaracteres(linha, '{') - contarCaracteres(linha, '}');

            if (contadorChaves <= 0) {
                contadorChaves = 0;
                try {
                    String codigo = buffer.toString();
                    buffer.setLength(0);

                    Lexer lexer = new Lexer(codigo, "<console>");
                    List<Token> tokens = lexer.tokenizar();
                    if (lexer.temErros()) {
                        for (String e : lexer.getErros()) erro(e);
                        continue;
                    }

                    Parser parser = new Parser(tokens, "<console>");
                    No.Programa programa = parser.parsear();
                    if (parser.temErros()) {
                        for (String e : parser.getErros()) erro(e);
                        continue;
                    }

                    // Mostra código Java gerado
                    GeradorJava gerador = new GeradorJava();
                    String java = gerador.gerar(programa, "Console");
                    System.out.println(CIANO + "// Java gerado:" + RESET);
                    System.out.println(java);

                } catch (Exception e) {
                    erro(e.getMessage());
                }
            }
        }
        scanner.close();
    }

    /**
     * Verifica erros em um arquivo BRZ sem executar.
     */
    private static void verificar(String caminhoArquivo) {
        try {
            String fonte = lerArquivo(caminhoArquivo);

            Lexer lexer = new Lexer(fonte, caminhoArquivo);
            List<Token> tokens = lexer.tokenizar();
            boolean temErros = false;

            if (lexer.temErros()) {
                temErros = true;
                for (String e : lexer.getErros()) erro(e);
            }

            Parser parser = new Parser(tokens, caminhoArquivo);
            parser.parsear();

            if (parser.temErros()) {
                temErros = true;
                for (String e : parser.getErros()) erro(e);
            }

            if (!temErros) {
                sucesso("Nenhum erro encontrado em " + caminhoArquivo + "!");
            }

        } catch (Exception e) {
            erro("Erro ao verificar: " + e.getMessage());
        }
    }

    /**
     * Cria um novo projeto BRZ.
     */
    private static void novoProjeto(String nome) {
        try {
            Path dir = Path.of(nome);
            Files.createDirectories(dir.resolve("src"));
            Files.createDirectories(dir.resolve("build"));

            // Arquivo principal
            String main = "// " + nome + " — Projeto BRZ\n"
                    + "// Criado com BRZ Lang v" + VERSAO + "\n\n"
                    + "mostrar(\"Olá mundo! Bem-vindo ao " + nome + "!\")\n";
            Files.writeString(dir.resolve("src/principal.brz"), main);

            // README
            String readme = "# " + nome + "\n\n"
                    + "Projeto criado com **BRZ Lang** v" + VERSAO + "\n\n"
                    + "## Como executar\n\n"
                    + "```bash\nbrz rodar src/principal.brz\n```\n\n"
                    + "## Como compilar para Java\n\n"
                    + "```bash\nbrz compilar src/principal.brz --para java\n```\n";
            Files.writeString(dir.resolve("README.md"), readme);

            sucesso("Projeto '" + nome + "' criado com sucesso!");
            info("  " + nome + "/src/principal.brz");
            info("\n  Para começar: cd " + nome + " && brz rodar src/principal.brz");

        } catch (Exception e) {
            erro("Erro ao criar projeto: " + e.getMessage());
        }
    }

    // =====================================================================
    // UTILITÁRIOS
    // =====================================================================

    private static String lerArquivo(String caminho) throws IOException {
        Path path = Path.of(caminho);
        if (!Files.exists(path)) {
            throw new IOException("Arquivo não encontrado: " + caminho);
        }
        return Files.readString(path);
    }

    private static String extrairNomeClasse(String caminhoArquivo) {
        String nome = Path.of(caminhoArquivo).getFileName().toString();
        if (nome.endsWith(".brz")) nome = nome.substring(0, nome.length() - 4);
        // Capitaliza e remove caracteres inválidos para nome de classe Java
        nome = nome.substring(0, 1).toUpperCase() + nome.substring(1);
        return nome.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static int contarCaracteres(String s, char c) {
        int count = 0;
        for (char ch : s.toCharArray()) {
            if (ch == c) count++;
        }
        return count;
    }

    private static void mostrarBanner() {
        System.out.println(AZUL + NEGRITO);
        System.out.println("  ██████╗ ██████╗ ███████╗");
        System.out.println("  ██╔══██╗██╔══██╗╚══███╔╝");
        System.out.println("  ██████╔╝██████╔╝  ███╔╝ ");
        System.out.println("  ██╔══██╗██╔══██╗ ███╔╝  ");
        System.out.println("  ██████╔╝██║  ██║███████╗");
        System.out.println("  ╚═════╝ ╚═╝  ╚═╝╚══════╝");
        System.out.println(RESET);
        System.out.println("  " + NOME + " v" + VERSAO + " — Programação em Português");
        System.out.println("  Transpila para Java, Python e C");
        System.out.println();
    }

    private static void mostrarAjuda() {
        System.out.println(NEGRITO + "USO:" + RESET);
        System.out.println("  brz <comando> [opções]");
        System.out.println();
        System.out.println(NEGRITO + "COMANDOS:" + RESET);
        System.out.println("  " + VERDE + "rodar" + RESET + "     <arquivo.brz>                    Executa arquivo BRZ");
        System.out.println("  " + VERDE + "compilar" + RESET + "  <arquivo.brz> [--para java|python|c]  Transpila para outra linguagem");
        System.out.println("  " + VERDE + "console" + RESET + "                                    REPL interativo");
        System.out.println("  " + VERDE + "verificar" + RESET + " <arquivo.brz>                    Verifica erros sem executar");
        System.out.println("  " + VERDE + "novo" + RESET + "      projeto <NomeProjeto>            Cria novo projeto BRZ");
        System.out.println("  " + VERDE + "versao" + RESET + "                                     Mostra versão");
        System.out.println("  " + VERDE + "ajuda" + RESET + "                                      Mostra esta mensagem");
        System.out.println();
        System.out.println(NEGRITO + "EXEMPLOS:" + RESET);
        System.out.println("  brz rodar meuPrograma.brz");
        System.out.println("  brz compilar meuCrud.brz --para python");
        System.out.println("  brz novo projeto MeuApp");
        System.out.println("  brz console");
        System.out.println();
    }

    private static void info(String msg) {
        System.out.println(AZUL + "[BRZ] " + RESET + msg);
    }

    private static void sucesso(String msg) {
        System.out.println(VERDE + "[BRZ] ✓ " + RESET + msg);
    }

    private static void erro(String msg) {
        System.err.println(VERMELHO + "[BRZ] ✗ " + RESET + msg);
    }

    private static void aviso(String msg) {
        System.out.println(AMARELO + "[BRZ] ⚠ " + RESET + msg);
    }
}
