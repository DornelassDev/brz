package brz.lexer;

/**
 * Token — Representa uma unidade léxica individual no código-fonte BRZ.
 * 
 * Cada token carrega seu tipo, valor textual, posição no código-fonte
 * (linha e coluna) para facilitar mensagens de erro precisas.
 * 
 * @author BRZ Team
 * @version 1.0.0
 * @since 2026-03-05
 */
public class Token {

    /** Tipo do token (palavra-chave, operador, literal, etc.) */
    private final TokenType tipo;

    /** Valor textual original do token como aparece no código-fonte */
    private final String valor;

    /** Número da linha onde o token aparece (1-based) */
    private final int linha;

    /** Número da coluna onde o token começa (1-based) */
    private final int coluna;

    /** Nome do arquivo de origem (para mensagens de erro) */
    private final String arquivo;

    /**
     * Cria um novo Token com todas as informações de posição.
     * 
     * @param tipo    Tipo do token
     * @param valor   Valor textual do token
     * @param linha   Linha no código-fonte (1-based)
     * @param coluna  Coluna no código-fonte (1-based)
     * @param arquivo Nome do arquivo de origem
     */
    public Token(TokenType tipo, String valor, int linha, int coluna, String arquivo) {
        this.tipo = tipo;
        this.valor = valor;
        this.linha = linha;
        this.coluna = coluna;
        this.arquivo = arquivo;
    }

    /**
     * Cria um novo Token sem informação de arquivo.
     * 
     * @param tipo   Tipo do token
     * @param valor  Valor textual do token
     * @param linha  Linha no código-fonte (1-based)
     * @param coluna Coluna no código-fonte (1-based)
     */
    public Token(TokenType tipo, String valor, int linha, int coluna) {
        this(tipo, valor, linha, coluna, "<stdin>");
    }

    // =====================================================================
    // GETTERS
    // =====================================================================

    /**
     * Retorna o tipo deste token.
     * @return TokenType representando o tipo do token
     */
    public TokenType getTipo() {
        return tipo;
    }

    /**
     * Retorna o valor textual original deste token.
     * @return String com o valor do token
     */
    public String getValor() {
        return valor;
    }

    /**
     * Retorna o número da linha onde este token aparece.
     * @return número da linha (1-based)
     */
    public int getLinha() {
        return linha;
    }

    /**
     * Retorna o número da coluna onde este token começa.
     * @return número da coluna (1-based)
     */
    public int getColuna() {
        return coluna;
    }

    /**
     * Retorna o nome do arquivo de origem.
     * @return nome do arquivo
     */
    public String getArquivo() {
        return arquivo;
    }

    // =====================================================================
    // UTILIDADES
    // =====================================================================

    /**
     * Verifica se este token é de um tipo específico.
     * @param tipo Tipo a verificar
     * @return true se o token for do tipo especificado
     */
    public boolean is(TokenType tipo) {
        return this.tipo == tipo;
    }

    /**
     * Verifica se este token é de algum dos tipos especificados.
     * @param tipos Tipos a verificar
     * @return true se o token for de algum dos tipos
     */
    public boolean isAny(TokenType... tipos) {
        for (TokenType t : tipos) {
            if (this.tipo == t) return true;
        }
        return false;
    }

    /**
     * Retorna uma representação legível da posição do token.
     * @return String no formato "arquivo:linha:coluna"
     */
    public String posicao() {
        return arquivo + ":" + linha + ":" + coluna;
    }

    @Override
    public String toString() {
        return String.format("Token{tipo=%s, valor='%s', pos=%d:%d}", 
                tipo, valor, linha, coluna);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Token token = (Token) obj;
        return tipo == token.tipo && valor.equals(token.valor);
    }

    @Override
    public int hashCode() {
        return 31 * tipo.hashCode() + valor.hashCode();
    }
}
