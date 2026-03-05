package brz.lexer;

/**
 * TokenType — Define todos os tipos de tokens reconhecidos pela linguagem BRZ.
 * 
 * Cada token representa uma unidade léxica mínima da linguagem:
 * palavras-chave, operadores, literais, identificadores e delimitadores.
 * 
 * @author BRZ Team
 * @version 1.0.0
 * @since 2026-03-05
 */
public enum TokenType {

    // =====================================================================
    // LITERAIS
    // =====================================================================

    /** Número inteiro: 42, 0, -7 */
    LITERAL_INTEIRO,

    /** Número decimal: 3.14, 0.5 */
    LITERAL_DECIMAL,

    /** Texto entre aspas: "Olá mundo" */
    LITERAL_TEXTO,

    /** Caractere entre aspas simples: 'a' */
    LITERAL_CARACTER,

    /** Literal booleano: verdadeiro */
    LITERAL_VERDADEIRO,

    /** Literal booleano: falso */
    LITERAL_FALSO,

    /** Literal nulo */
    LITERAL_NULO,

    // =====================================================================
    // TIPOS DE DADOS
    // =====================================================================

    /** Tipo inteiro → int */
    TIPO_NUMERO,

    /** Tipo decimal → double */
    TIPO_DECIMAL,

    /** Tipo texto → String */
    TIPO_TEXTO,

    /** Tipo caractere → char */
    TIPO_CARACTER,

    /** Tipo booleano → boolean */
    TIPO_LOGICO,

    /** Tipo vazio → void */
    TIPO_VAZIO,

    /** Tipo lista → ArrayList */
    TIPO_LISTA,

    /** Tipo mapa → HashMap */
    TIPO_MAPA,

    // =====================================================================
    // PALAVRAS-CHAVE — CONTROLE DE FLUXO
    // =====================================================================

    /** se → if */
    KW_SE,

    /** senao → else */
    KW_SENAO,

    /** para → for */
    KW_PARA,

    /** enquanto → while */
    KW_ENQUANTO,

    /** faca → do */
    KW_FACA,

    /** escolha → switch */
    KW_ESCOLHA,

    /** caso → case */
    KW_CASO,

    /** padrao → default */
    KW_PADRAO,

    /** pare → break */
    KW_PARE,

    /** continue → continue */
    KW_CONTINUE,

    // =====================================================================
    // PALAVRAS-CHAVE — FUNÇÕES E CLASSES
    // =====================================================================

    /** funcao → function/method */
    KW_FUNCAO,

    /** retorne → return */
    KW_RETORNE,

    /** classe → class */
    KW_CLASSE,

    /** novo → new */
    KW_NOVO,

    /** este → this */
    KW_ESTE,

    /** super → super */
    KW_SUPER,

    /** estende → extends */
    KW_ESTENDE,

    /** implementa → implements */
    KW_IMPLEMENTA,

    /** interface → interface */
    KW_INTERFACE,

    /** abstrato → abstract */
    KW_ABSTRATO,

    /** estatico → static */
    KW_ESTATICO,

    /** final → final */
    KW_FINAL,

    /** publico → public */
    KW_PUBLICO,

    /** privado → private */
    KW_PRIVADO,

    /** protegido → protected */
    KW_PROTEGIDO,

    // =====================================================================
    // PALAVRAS-CHAVE — TRATAMENTO DE ERROS
    // =====================================================================

    /** tente → try */
    KW_TENTE,

    /** pegue → catch */
    KW_PEGUE,

    /** finalmente → finally */
    KW_FINALMENTE,

    /** lance → throw */
    KW_LANCE,

    // =====================================================================
    // PALAVRAS-CHAVE — I/O E BUILTINS
    // =====================================================================

    /** mostrar → System.out.println */
    KW_MOSTRAR,

    /** lerTexto → Scanner.nextLine */
    KW_LER_TEXTO,

    /** lerNumero → Scanner.nextInt */
    KW_LER_NUMERO,

    /** lerDecimal → Scanner.nextDouble */
    KW_LER_DECIMAL,

    // =====================================================================
    // PALAVRAS-CHAVE — CRUD
    // =====================================================================

    /** criar → operação de criação */
    KW_CRIAR,

    /** listar → operação de listagem */
    KW_LISTAR,

    /** atualizar → operação de atualização */
    KW_ATUALIZAR,

    /** deletar → operação de exclusão */
    KW_DELETAR,

    // =====================================================================
    // PALAVRAS-CHAVE — COLEÇÕES
    // =====================================================================

    /** adicionar → add */
    KW_ADICIONAR,

    /** remover → remove */
    KW_REMOVER,

    /** tamanho → size */
    KW_TAMANHO,

    /** contem → contains */
    KW_CONTEM,

    // =====================================================================
    // PALAVRAS-CHAVE — MÓDULOS E IMPORTS
    // =====================================================================

    /** importar → import */
    KW_IMPORTAR,

    /** pacote → package */
    KW_PACOTE,

    /** de → from (para imports) */
    KW_DE,

    /** como → as (alias) */
    KW_COMO,

    // =====================================================================
    // PALAVRAS-CHAVE — OUTROS
    // =====================================================================

    /** variavel → var (declaração) */
    KW_VARIAVEL,

    /** constante → final (imutável) */
    KW_CONSTANTE,

    /** retorna → tipo de retorno em assinatura de função */
    KW_RETORNA,

    /** em → in (para iteração) */
    KW_EM,

    /** ate → até (para range em loops) */
    KW_ATE,

    /** passo → step (incremento em loops) */
    KW_PASSO,

    // =====================================================================
    // OPERADORES ARITMÉTICOS
    // =====================================================================

    /** + */
    OP_MAIS,

    /** - */
    OP_MENOS,

    /** * */
    OP_MULTIPLICAR,

    /** / */
    OP_DIVIDIR,

    /** % */
    OP_MODULO,

    /** ** (potência) */
    OP_POTENCIA,

    // =====================================================================
    // OPERADORES DE ATRIBUIÇÃO
    // =====================================================================

    /** = */
    OP_ATRIBUIR,

    /** += */
    OP_MAIS_ATRIBUIR,

    /** -= */
    OP_MENOS_ATRIBUIR,

    /** *= */
    OP_MULTIPLICAR_ATRIBUIR,

    /** /= */
    OP_DIVIDIR_ATRIBUIR,

    /** %= */
    OP_MODULO_ATRIBUIR,

    // =====================================================================
    // OPERADORES RELACIONAIS
    // =====================================================================

    /** == */
    OP_IGUAL,

    /** != */
    OP_DIFERENTE,

    /** > */
    OP_MAIOR,

    /** < */
    OP_MENOR,

    /** >= */
    OP_MAIOR_IGUAL,

    /** <= */
    OP_MENOR_IGUAL,

    // =====================================================================
    // OPERADORES LÓGICOS
    // =====================================================================

    /** e → && */
    OP_E,

    /** ou → || */
    OP_OU,

    /** nao → ! */
    OP_NAO,

    // =====================================================================
    // OPERADORES UNÁRIOS / INCREMENTO
    // =====================================================================

    /** ++ */
    OP_INCREMENTAR,

    /** -- */
    OP_DECREMENTAR,

    // =====================================================================
    // DELIMITADORES
    // =====================================================================

    /** ( */
    PAREN_ESQ,

    /** ) */
    PAREN_DIR,

    /** { */
    CHAVE_ESQ,

    /** } */
    CHAVE_DIR,

    /** [ */
    COLCHETE_ESQ,

    /** ] */
    COLCHETE_DIR,

    /** ; */
    PONTO_VIRGULA,

    /** , */
    VIRGULA,

    /** . */
    PONTO,

    /** : */
    DOIS_PONTOS,

    /** -> (seta) */
    SETA,

    /** => (seta dupla / lambda) */
    SETA_DUPLA,

    // =====================================================================
    // ESPECIAIS
    // =====================================================================

    /** Nome de variável, função, classe, etc. */
    IDENTIFICADOR,

    /** Fim de arquivo */
    EOF,

    /** Token inválido / não reconhecido */
    INVALIDO,

    /** Nova linha (usado internamente) */
    NOVA_LINHA,

    /** Comentário de linha */
    COMENTARIO_LINHA,

    /** Comentário de bloco */
    COMENTARIO_BLOCO;

    /**
     * Verifica se este token é um tipo de dado.
     * @return true se representa um tipo de dado BRZ
     */
    public boolean isTipo() {
        return this == TIPO_NUMERO || this == TIPO_DECIMAL || this == TIPO_TEXTO
                || this == TIPO_CARACTER || this == TIPO_LOGICO || this == TIPO_VAZIO
                || this == TIPO_LISTA || this == TIPO_MAPA;
    }

    /**
     * Verifica se este token é um literal.
     * @return true se representa um valor literal
     */
    public boolean isLiteral() {
        return this == LITERAL_INTEIRO || this == LITERAL_DECIMAL || this == LITERAL_TEXTO
                || this == LITERAL_CARACTER || this == LITERAL_VERDADEIRO || this == LITERAL_FALSO
                || this == LITERAL_NULO;
    }

    /**
     * Verifica se este token é um operador.
     * @return true se representa um operador
     */
    public boolean isOperador() {
        return name().startsWith("OP_");
    }

    /**
     * Verifica se este token é uma palavra-chave.
     * @return true se representa uma palavra-chave da linguagem
     */
    public boolean isPalavraChave() {
        return name().startsWith("KW_") || name().startsWith("TIPO_");
    }

    /**
     * Verifica se este token é um modificador de acesso.
     * @return true se for publico, privado ou protegido
     */
    public boolean isModificadorAcesso() {
        return this == KW_PUBLICO || this == KW_PRIVADO || this == KW_PROTEGIDO;
    }
}
