package brz.lexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lexer — Analisador léxico da linguagem BRZ.
 * 
 * Responsável por transformar código-fonte BRZ (texto puro) em uma lista
 * de tokens reconhecidos. O Lexer percorre o código caractere por caractere,
 * identificando palavras-chave, identificadores, literais, operadores e
 * delimitadores.
 * 
 * Suporta:
 * - Todas as palavras-chave em português da BRZ
 * - Caracteres acentuados (UTF-8)
 * - Strings com escape sequences
 * - Comentários de linha (//) e bloco (/* ... * /)
 * - Números inteiros e decimais
 * - Rastreamento completo de posição (linha:coluna)
 * 
 * @author BRZ Team
 * @version 1.0.0
 * @since 2026-03-05
 */
public class Lexer {

    /** Código-fonte a ser tokenizado */
    private final String fonte;

    /** Nome do arquivo de origem */
    private final String arquivo;

    /** Posição atual no código-fonte */
    private int posicao;

    /** Linha atual (1-based) */
    private int linha;

    /** Coluna atual (1-based) */
    private int coluna;

    /** Lista de tokens gerados */
    private final List<Token> tokens;

    /** Lista de erros encontrados durante a tokenização */
    private final List<String> erros;

    /** Mapa de palavras-chave → tipos de token */
    private static final Map<String, TokenType> PALAVRAS_CHAVE = new HashMap<>();

    // Inicializa o mapa de palavras-chave
    static {
        // Tipos de dados
        PALAVRAS_CHAVE.put("numero", TokenType.TIPO_NUMERO);
        PALAVRAS_CHAVE.put("número", TokenType.TIPO_NUMERO);
        PALAVRAS_CHAVE.put("inteiro", TokenType.TIPO_NUMERO);
        PALAVRAS_CHAVE.put("decimal", TokenType.TIPO_DECIMAL);
        PALAVRAS_CHAVE.put("texto", TokenType.TIPO_TEXTO);
        PALAVRAS_CHAVE.put("caracter", TokenType.TIPO_CARACTER);
        PALAVRAS_CHAVE.put("caractere", TokenType.TIPO_CARACTER);
        PALAVRAS_CHAVE.put("logico", TokenType.TIPO_LOGICO);
        PALAVRAS_CHAVE.put("lógico", TokenType.TIPO_LOGICO);
        PALAVRAS_CHAVE.put("booleano", TokenType.TIPO_LOGICO);
        PALAVRAS_CHAVE.put("vazio", TokenType.TIPO_VAZIO);
        PALAVRAS_CHAVE.put("lista", TokenType.TIPO_LISTA);
        PALAVRAS_CHAVE.put("mapa", TokenType.TIPO_MAPA);

        // Literais booleanos
        PALAVRAS_CHAVE.put("verdadeiro", TokenType.LITERAL_VERDADEIRO);
        PALAVRAS_CHAVE.put("falso", TokenType.LITERAL_FALSO);
        PALAVRAS_CHAVE.put("nulo", TokenType.LITERAL_NULO);

        // Controle de fluxo
        PALAVRAS_CHAVE.put("se", TokenType.KW_SE);
        PALAVRAS_CHAVE.put("senao", TokenType.KW_SENAO);
        PALAVRAS_CHAVE.put("senão", TokenType.KW_SENAO);
        PALAVRAS_CHAVE.put("para", TokenType.KW_PARA);
        PALAVRAS_CHAVE.put("enquanto", TokenType.KW_ENQUANTO);
        PALAVRAS_CHAVE.put("faca", TokenType.KW_FACA);
        PALAVRAS_CHAVE.put("faça", TokenType.KW_FACA);
        PALAVRAS_CHAVE.put("escolha", TokenType.KW_ESCOLHA);
        PALAVRAS_CHAVE.put("caso", TokenType.KW_CASO);
        PALAVRAS_CHAVE.put("padrao", TokenType.KW_PADRAO);
        PALAVRAS_CHAVE.put("padrão", TokenType.KW_PADRAO);
        PALAVRAS_CHAVE.put("pare", TokenType.KW_PARE);
        PALAVRAS_CHAVE.put("continue", TokenType.KW_CONTINUE);

        // Funções e classes
        PALAVRAS_CHAVE.put("funcao", TokenType.KW_FUNCAO);
        PALAVRAS_CHAVE.put("função", TokenType.KW_FUNCAO);
        PALAVRAS_CHAVE.put("retorne", TokenType.KW_RETORNE);
        PALAVRAS_CHAVE.put("retorna", TokenType.KW_RETORNA);
        PALAVRAS_CHAVE.put("classe", TokenType.KW_CLASSE);
        PALAVRAS_CHAVE.put("novo", TokenType.KW_NOVO);
        PALAVRAS_CHAVE.put("nova", TokenType.KW_NOVO);
        PALAVRAS_CHAVE.put("este", TokenType.KW_ESTE);
        PALAVRAS_CHAVE.put("isto", TokenType.KW_ESTE);
        PALAVRAS_CHAVE.put("super", TokenType.KW_SUPER);
        PALAVRAS_CHAVE.put("estende", TokenType.KW_ESTENDE);
        PALAVRAS_CHAVE.put("implementa", TokenType.KW_IMPLEMENTA);
        PALAVRAS_CHAVE.put("interface", TokenType.KW_INTERFACE);
        PALAVRAS_CHAVE.put("abstrato", TokenType.KW_ABSTRATO);
        PALAVRAS_CHAVE.put("abstrata", TokenType.KW_ABSTRATO);
        PALAVRAS_CHAVE.put("estatico", TokenType.KW_ESTATICO);
        PALAVRAS_CHAVE.put("estático", TokenType.KW_ESTATICO);
        PALAVRAS_CHAVE.put("final", TokenType.KW_FINAL);
        PALAVRAS_CHAVE.put("publico", TokenType.KW_PUBLICO);
        PALAVRAS_CHAVE.put("público", TokenType.KW_PUBLICO);
        PALAVRAS_CHAVE.put("publica", TokenType.KW_PUBLICO);
        PALAVRAS_CHAVE.put("pública", TokenType.KW_PUBLICO);
        PALAVRAS_CHAVE.put("privado", TokenType.KW_PRIVADO);
        PALAVRAS_CHAVE.put("privada", TokenType.KW_PRIVADO);
        PALAVRAS_CHAVE.put("protegido", TokenType.KW_PROTEGIDO);
        PALAVRAS_CHAVE.put("protegida", TokenType.KW_PROTEGIDO);

        // Tratamento de erros
        PALAVRAS_CHAVE.put("tente", TokenType.KW_TENTE);
        PALAVRAS_CHAVE.put("pegue", TokenType.KW_PEGUE);
        PALAVRAS_CHAVE.put("finalmente", TokenType.KW_FINALMENTE);
        PALAVRAS_CHAVE.put("lance", TokenType.KW_LANCE);

        // I/O
        PALAVRAS_CHAVE.put("mostrar", TokenType.KW_MOSTRAR);
        PALAVRAS_CHAVE.put("imprimir", TokenType.KW_MOSTRAR);
        PALAVRAS_CHAVE.put("lerTexto", TokenType.KW_LER_TEXTO);
        PALAVRAS_CHAVE.put("lerNumero", TokenType.KW_LER_NUMERO);
        PALAVRAS_CHAVE.put("lerNúmero", TokenType.KW_LER_NUMERO);
        PALAVRAS_CHAVE.put("lerDecimal", TokenType.KW_LER_DECIMAL);

        // CRUD
        PALAVRAS_CHAVE.put("criar", TokenType.KW_CRIAR);
        PALAVRAS_CHAVE.put("listar", TokenType.KW_LISTAR);
        PALAVRAS_CHAVE.put("atualizar", TokenType.KW_ATUALIZAR);
        PALAVRAS_CHAVE.put("deletar", TokenType.KW_DELETAR);
        PALAVRAS_CHAVE.put("excluir", TokenType.KW_DELETAR);

        // Coleções
        PALAVRAS_CHAVE.put("adicionar", TokenType.KW_ADICIONAR);
        PALAVRAS_CHAVE.put("remover", TokenType.KW_REMOVER);
        PALAVRAS_CHAVE.put("tamanho", TokenType.KW_TAMANHO);
        PALAVRAS_CHAVE.put("contem", TokenType.KW_CONTEM);
        PALAVRAS_CHAVE.put("contém", TokenType.KW_CONTEM);

        // Módulos
        PALAVRAS_CHAVE.put("importar", TokenType.KW_IMPORTAR);
        PALAVRAS_CHAVE.put("pacote", TokenType.KW_PACOTE);
        PALAVRAS_CHAVE.put("de", TokenType.KW_DE);
        PALAVRAS_CHAVE.put("como", TokenType.KW_COMO);

        // Outros
        PALAVRAS_CHAVE.put("variavel", TokenType.KW_VARIAVEL);
        PALAVRAS_CHAVE.put("variável", TokenType.KW_VARIAVEL);
        PALAVRAS_CHAVE.put("var", TokenType.KW_VARIAVEL);
        PALAVRAS_CHAVE.put("constante", TokenType.KW_CONSTANTE);
        PALAVRAS_CHAVE.put("const", TokenType.KW_CONSTANTE);
        PALAVRAS_CHAVE.put("em", TokenType.KW_EM);
        PALAVRAS_CHAVE.put("ate", TokenType.KW_ATE);
        PALAVRAS_CHAVE.put("até", TokenType.KW_ATE);
        PALAVRAS_CHAVE.put("passo", TokenType.KW_PASSO);

        // Operadores lógicos (como palavras)
        PALAVRAS_CHAVE.put("e", TokenType.OP_E);
        PALAVRAS_CHAVE.put("ou", TokenType.OP_OU);
        PALAVRAS_CHAVE.put("nao", TokenType.OP_NAO);
        PALAVRAS_CHAVE.put("não", TokenType.OP_NAO);
    }

    /**
     * Cria um novo Lexer para o código-fonte especificado.
     * 
     * @param fonte   Código-fonte BRZ a ser tokenizado
     * @param arquivo Nome do arquivo de origem (para mensagens de erro)
     */
    public Lexer(String fonte, String arquivo) {
        this.fonte = fonte;
        this.arquivo = arquivo;
        this.posicao = 0;
        this.linha = 1;
        this.coluna = 1;
        this.tokens = new ArrayList<>();
        this.erros = new ArrayList<>();
    }

    /**
     * Cria um novo Lexer para o código-fonte especificado (sem nome de arquivo).
     * 
     * @param fonte Código-fonte BRZ a ser tokenizado
     */
    public Lexer(String fonte) {
        this(fonte, "<stdin>");
    }

    // =====================================================================
    // API PÚBLICA
    // =====================================================================

    /**
     * Executa a análise léxica completa e retorna todos os tokens.
     * 
     * Percorre o código-fonte do início ao fim, identificando e
     * classificando cada unidade léxica encontrada.
     * 
     * @return Lista de tokens reconhecidos (sempre termina com EOF)
     */
    public List<Token> tokenizar() {
        tokens.clear();
        erros.clear();

        while (!fimDoArquivo()) {
            pularEspacosEComentarios();
            if (fimDoArquivo()) break;

            char c = atual();

            if (Character.isDigit(c)) {
                lerNumero();
            } else if (c == '"') {
                lerTexto();
            } else if (c == '\'') {
                lerCaracter();
            } else if (isInicioIdentificador(c)) {
                lerIdentificadorOuPalavraChave();
            } else {
                lerOperadorOuDelimitador();
            }
        }

        tokens.add(new Token(TokenType.EOF, "", linha, coluna, arquivo));
        return tokens;
    }

    /**
     * Retorna os erros encontrados durante a tokenização.
     * @return Lista de mensagens de erro
     */
    public List<String> getErros() {
        return erros;
    }

    /**
     * Verifica se houve erros durante a tokenização.
     * @return true se houve pelo menos um erro
     */
    public boolean temErros() {
        return !erros.isEmpty();
    }

    // =====================================================================
    // MÉTODOS DE LEITURA
    // =====================================================================

    /**
     * Lê um número (inteiro ou decimal) do código-fonte.
     */
    private void lerNumero() {
        int inicioColuna = coluna;
        StringBuilder sb = new StringBuilder();
        boolean temPonto = false;

        while (!fimDoArquivo() && (Character.isDigit(atual()) || atual() == '.')) {
            if (atual() == '.') {
                // Verifica se é ponto decimal ou acesso a membro
                if (temPonto) break;
                if (posicao + 1 < fonte.length() && !Character.isDigit(fonte.charAt(posicao + 1))) {
                    break; // É acesso a membro, não decimal
                }
                temPonto = true;
            }
            sb.append(atual());
            avancar();
        }

        // Suporte a sufixo 'L' para long
        if (!fimDoArquivo() && (atual() == 'L' || atual() == 'l')) {
            sb.append(atual());
            avancar();
        }

        TokenType tipo = temPonto ? TokenType.LITERAL_DECIMAL : TokenType.LITERAL_INTEIRO;
        tokens.add(new Token(tipo, sb.toString(), linha, inicioColuna, arquivo));
    }

    /**
     * Lê uma string (texto entre aspas duplas) do código-fonte.
     * Suporta escape sequences: \n, \t, \\, \", etc.
     */
    private void lerTexto() {
        int inicioColuna = coluna;
        int inicioLinha = linha;
        avancar(); // pula a aspa de abertura

        StringBuilder sb = new StringBuilder();
        while (!fimDoArquivo() && atual() != '"') {
            if (atual() == '\\') {
                avancar();
                if (fimDoArquivo()) {
                    erro("Texto não finalizado: escape incompleto", inicioLinha, inicioColuna);
                    return;
                }
                switch (atual()) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    case '0': sb.append('\0'); break;
                    default:
                        erro("Sequência de escape desconhecida: \\" + atual(), linha, coluna);
                        sb.append(atual());
                }
            } else {
                if (atual() == '\n') {
                    linha++;
                    coluna = 0;
                }
                sb.append(atual());
            }
            avancar();
        }

        if (fimDoArquivo()) {
            erro("Texto não finalizado: falta fechar aspas \"", inicioLinha, inicioColuna);
            return;
        }

        avancar(); // pula a aspa de fechamento
        tokens.add(new Token(TokenType.LITERAL_TEXTO, sb.toString(), inicioLinha, inicioColuna, arquivo));
    }

    /**
     * Lê um caractere (entre aspas simples) do código-fonte.
     */
    private void lerCaracter() {
        int inicioColuna = coluna;
        avancar(); // pula a aspa simples de abertura

        if (fimDoArquivo()) {
            erro("Caractere não finalizado", linha, inicioColuna);
            return;
        }

        char c;
        if (atual() == '\\') {
            avancar();
            if (fimDoArquivo()) {
                erro("Caractere não finalizado: escape incompleto", linha, inicioColuna);
                return;
            }
            switch (atual()) {
                case 'n': c = '\n'; break;
                case 't': c = '\t'; break;
                case 'r': c = '\r'; break;
                case '\\': c = '\\'; break;
                case '\'': c = '\''; break;
                case '0': c = '\0'; break;
                default:
                    erro("Sequência de escape desconhecida em caractere: \\" + atual(), linha, coluna);
                    c = atual();
            }
        } else {
            c = atual();
        }
        avancar();

        if (fimDoArquivo() || atual() != '\'') {
            erro("Caractere não finalizado: falta fechar aspas '", linha, inicioColuna);
            return;
        }

        avancar(); // pula a aspa simples de fechamento
        tokens.add(new Token(TokenType.LITERAL_CARACTER, String.valueOf(c), linha, inicioColuna, arquivo));
    }

    /**
     * Lê um identificador ou palavra-chave do código-fonte.
     * Verifica se o identificador é uma palavra-chave registrada.
     */
    private void lerIdentificadorOuPalavraChave() {
        int inicioColuna = coluna;
        StringBuilder sb = new StringBuilder();

        while (!fimDoArquivo() && isContinuacaoIdentificador(atual())) {
            sb.append(atual());
            avancar();
        }

        String palavra = sb.toString();

        // Caso especial: "senao se" → tratamos como "senao" seguido de "se"
        // (o parser cuida da combinação)

        TokenType tipo = PALAVRAS_CHAVE.get(palavra);
        if (tipo != null) {
            tokens.add(new Token(tipo, palavra, linha, inicioColuna, arquivo));
        } else {
            tokens.add(new Token(TokenType.IDENTIFICADOR, palavra, linha, inicioColuna, arquivo));
        }
    }

    /**
     * Lê um operador ou delimitador do código-fonte.
     */
    private void lerOperadorOuDelimitador() {
        int inicioColuna = coluna;
        char c = atual();

        switch (c) {
            // Delimitadores simples
            case '(': adicionarToken(TokenType.PAREN_ESQ, "(", inicioColuna); avancar(); break;
            case ')': adicionarToken(TokenType.PAREN_DIR, ")", inicioColuna); avancar(); break;
            case '{': adicionarToken(TokenType.CHAVE_ESQ, "{", inicioColuna); avancar(); break;
            case '}': adicionarToken(TokenType.CHAVE_DIR, "}", inicioColuna); avancar(); break;
            case '[': adicionarToken(TokenType.COLCHETE_ESQ, "[", inicioColuna); avancar(); break;
            case ']': adicionarToken(TokenType.COLCHETE_DIR, "]", inicioColuna); avancar(); break;
            case ';': adicionarToken(TokenType.PONTO_VIRGULA, ";", inicioColuna); avancar(); break;
            case ',': adicionarToken(TokenType.VIRGULA, ",", inicioColuna); avancar(); break;
            case '.': adicionarToken(TokenType.PONTO, ".", inicioColuna); avancar(); break;
            case ':': adicionarToken(TokenType.DOIS_PONTOS, ":", inicioColuna); avancar(); break;

            // Operadores com possíveis composições
            case '+':
                avancar();
                if (!fimDoArquivo() && atual() == '+') {
                    adicionarToken(TokenType.OP_INCREMENTAR, "++", inicioColuna); avancar();
                } else if (!fimDoArquivo() && atual() == '=') {
                    adicionarToken(TokenType.OP_MAIS_ATRIBUIR, "+=", inicioColuna); avancar();
                } else {
                    adicionarToken(TokenType.OP_MAIS, "+", inicioColuna);
                }
                break;

            case '-':
                avancar();
                if (!fimDoArquivo() && atual() == '-') {
                    adicionarToken(TokenType.OP_DECREMENTAR, "--", inicioColuna); avancar();
                } else if (!fimDoArquivo() && atual() == '=') {
                    adicionarToken(TokenType.OP_MENOS_ATRIBUIR, "-=", inicioColuna); avancar();
                } else if (!fimDoArquivo() && atual() == '>') {
                    adicionarToken(TokenType.SETA, "->", inicioColuna); avancar();
                } else {
                    adicionarToken(TokenType.OP_MENOS, "-", inicioColuna);
                }
                break;

            case '*':
                avancar();
                if (!fimDoArquivo() && atual() == '*') {
                    adicionarToken(TokenType.OP_POTENCIA, "**", inicioColuna); avancar();
                } else if (!fimDoArquivo() && atual() == '=') {
                    adicionarToken(TokenType.OP_MULTIPLICAR_ATRIBUIR, "*=", inicioColuna); avancar();
                } else {
                    adicionarToken(TokenType.OP_MULTIPLICAR, "*", inicioColuna);
                }
                break;

            case '/':
                avancar();
                if (!fimDoArquivo() && atual() == '=') {
                    adicionarToken(TokenType.OP_DIVIDIR_ATRIBUIR, "/=", inicioColuna); avancar();
                } else {
                    adicionarToken(TokenType.OP_DIVIDIR, "/", inicioColuna);
                }
                break;

            case '%':
                avancar();
                if (!fimDoArquivo() && atual() == '=') {
                    adicionarToken(TokenType.OP_MODULO_ATRIBUIR, "%=", inicioColuna); avancar();
                } else {
                    adicionarToken(TokenType.OP_MODULO, "%", inicioColuna);
                }
                break;

            case '=':
                avancar();
                if (!fimDoArquivo() && atual() == '=') {
                    adicionarToken(TokenType.OP_IGUAL, "==", inicioColuna); avancar();
                } else if (!fimDoArquivo() && atual() == '>') {
                    adicionarToken(TokenType.SETA_DUPLA, "=>", inicioColuna); avancar();
                } else {
                    adicionarToken(TokenType.OP_ATRIBUIR, "=", inicioColuna);
                }
                break;

            case '!':
                avancar();
                if (!fimDoArquivo() && atual() == '=') {
                    adicionarToken(TokenType.OP_DIFERENTE, "!=", inicioColuna); avancar();
                } else {
                    adicionarToken(TokenType.OP_NAO, "!", inicioColuna);
                }
                break;

            case '>':
                avancar();
                if (!fimDoArquivo() && atual() == '=') {
                    adicionarToken(TokenType.OP_MAIOR_IGUAL, ">=", inicioColuna); avancar();
                } else {
                    adicionarToken(TokenType.OP_MAIOR, ">", inicioColuna);
                }
                break;

            case '<':
                avancar();
                if (!fimDoArquivo() && atual() == '=') {
                    adicionarToken(TokenType.OP_MENOR_IGUAL, "<=", inicioColuna); avancar();
                } else {
                    adicionarToken(TokenType.OP_MENOR, "<", inicioColuna);
                }
                break;

            case '&':
                avancar();
                if (!fimDoArquivo() && atual() == '&') {
                    adicionarToken(TokenType.OP_E, "&&", inicioColuna); avancar();
                } else {
                    erro("Operador '&' sozinho não é suportado. Use 'e' ou '&&'.", linha, inicioColuna);
                }
                break;

            case '|':
                avancar();
                if (!fimDoArquivo() && atual() == '|') {
                    adicionarToken(TokenType.OP_OU, "||", inicioColuna); avancar();
                } else {
                    erro("Operador '|' sozinho não é suportado. Use 'ou' ou '||'.", linha, inicioColuna);
                }
                break;

            default:
                erro("Caractere inesperado: '" + c + "' (código: " + (int) c + ")", linha, inicioColuna);
                avancar();
                break;
        }
    }

    // =====================================================================
    // MÉTODOS AUXILIARES
    // =====================================================================

    /**
     * Pula espaços em branco, tabs, novas linhas e comentários.
     */
    private void pularEspacosEComentarios() {
        while (!fimDoArquivo()) {
            char c = atual();

            // Espaços em branco
            if (c == ' ' || c == '\t' || c == '\r') {
                avancar();
                continue;
            }

            // Nova linha
            if (c == '\n') {
                linha++;
                coluna = 0;
                avancar();
                continue;
            }

            // Comentários
            if (c == '/' && posicao + 1 < fonte.length()) {
                char proximo = fonte.charAt(posicao + 1);

                // Comentário de linha: //
                if (proximo == '/') {
                    while (!fimDoArquivo() && atual() != '\n') {
                        avancar();
                    }
                    continue;
                }

                // Comentário de bloco: /* ... */
                if (proximo == '*') {
                    avancar(); // pula /
                    avancar(); // pula *
                    while (!fimDoArquivo()) {
                        if (atual() == '\n') {
                            linha++;
                            coluna = 0;
                        }
                        if (atual() == '*' && posicao + 1 < fonte.length() && fonte.charAt(posicao + 1) == '/') {
                            avancar(); // pula *
                            avancar(); // pula /
                            break;
                        }
                        avancar();
                    }
                    continue;
                }
            }

            // Comentário com #
            if (c == '#') {
                while (!fimDoArquivo() && atual() != '\n') {
                    avancar();
                }
                continue;
            }

            break; // Não é espaço nem comentário
        }
    }

    /**
     * Retorna o caractere na posição atual.
     * @return caractere atual
     */
    private char atual() {
        return fonte.charAt(posicao);
    }

    /**
     * Avança uma posição no código-fonte.
     */
    private void avancar() {
        posicao++;
        coluna++;
    }

    /**
     * Verifica se chegou ao fim do código-fonte.
     * @return true se não há mais caracteres para ler
     */
    private boolean fimDoArquivo() {
        return posicao >= fonte.length();
    }

    /**
     * Verifica se um caractere pode iniciar um identificador.
     * Aceita letras, underscore e caracteres acentuados.
     * 
     * @param c caractere a verificar
     * @return true se pode iniciar um identificador
     */
    private boolean isInicioIdentificador(char c) {
        return Character.isLetter(c) || c == '_' || Character.isHighSurrogate(c)
                || c == 'á' || c == 'à' || c == 'ã' || c == 'â'
                || c == 'é' || c == 'ê' || c == 'í' || c == 'ó'
                || c == 'ô' || c == 'õ' || c == 'ú' || c == 'ü'
                || c == 'ç' || c == 'Á' || c == 'À' || c == 'Ã'
                || c == 'Â' || c == 'É' || c == 'Ê' || c == 'Í'
                || c == 'Ó' || c == 'Ô' || c == 'Õ' || c == 'Ú'
                || c == 'Ü' || c == 'Ç';
    }

    /**
     * Verifica se um caractere pode continuar um identificador.
     * Aceita letras, dígitos, underscore e caracteres acentuados.
     * 
     * @param c caractere a verificar
     * @return true se pode continuar um identificador
     */
    private boolean isContinuacaoIdentificador(char c) {
        return isInicioIdentificador(c) || Character.isDigit(c);
    }

    /**
     * Adiciona um token à lista.
     */
    private void adicionarToken(TokenType tipo, String valor, int coluna) {
        tokens.add(new Token(tipo, valor, linha, coluna, arquivo));
    }

    /**
     * Registra um erro léxico.
     */
    private void erro(String mensagem, int linha, int coluna) {
        String msg = String.format("[Erro Léxico] %s:%d:%d - %s", arquivo, linha, coluna, mensagem);
        erros.add(msg);
    }

    /**
     * Retorna o mapa de palavras-chave (para referência/documentação).
     * @return Mapa imutável de palavras-chave
     */
    public static Map<String, TokenType> getPalavrasChave() {
        return new HashMap<>(PALAVRAS_CHAVE);
    }
}
