package brz.parser;

import brz.ast.No;
import brz.ast.No.*;
import brz.lexer.Token;
import brz.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser — Analisador sintático da linguagem BRZ.
 * 
 * Transforma uma lista de tokens (produzida pelo Lexer) em uma Árvore
 * Sintática Abstrata (AST). Implementa um parser descent recursivo com
 * precedência de operadores.
 * 
 * @author BRZ Team
 * @version 1.0.0
 * @since 2026-03-05
 */
public class Parser {

    /** Lista de tokens a serem analisados */
    private final List<Token> tokens;

    /** Posição atual na lista de tokens */
    private int posicao;

    /** Lista de erros encontrados durante o parsing */
    private final List<String> erros;

    /** Nome do arquivo sendo parseado */
    private final String arquivo;

    /**
     * Cria um novo Parser para a lista de tokens especificada.
     * 
     * @param tokens  Lista de tokens produzida pelo Lexer
     * @param arquivo Nome do arquivo de origem
     */
    public Parser(List<Token> tokens, String arquivo) {
        this.tokens = tokens;
        this.posicao = 0;
        this.erros = new ArrayList<>();
        this.arquivo = arquivo;
    }

    public Parser(List<Token> tokens) {
        this(tokens, "<stdin>");
    }

    // =====================================================================
    // API PÚBLICA
    // =====================================================================

    /**
     * Executa o parsing completo e retorna o programa (AST raiz).
     * @return Nó Programa contendo todas as declarações
     */
    public Programa parsear() {
        List<No> declaracoes = new ArrayList<>();

        while (!fimDosTokens()) {
            try {
                No declaracao = parsearDeclaracao();
                if (declaracao != null) {
                    declaracoes.add(declaracao);
                }
            } catch (ErroSintaxe e) {
                erros.add(e.getMessage());
                sincronizar(); // Recupera de erro e continua
            }
        }

        return new Programa(declaracoes);
    }

    public List<String> getErros() { return erros; }
    public boolean temErros() { return !erros.isEmpty(); }

    // =====================================================================
    // PARSING DE DECLARAÇÕES (top-level)
    // =====================================================================

    /**
     * Parseia uma declaração de nível superior.
     * Detecta o tipo de declaração baseado no token atual.
     */
    private No parsearDeclaracao() {
        Token tokenAtual = atual();

        // Pula ponto-e-vírgulas soltos
        if (verificar(TokenType.PONTO_VIRGULA)) {
            avancar();
            return null;
        }

        switch (tokenAtual.getTipo()) {
            // Declaração de variável tipada: numero x = 10
            case TIPO_NUMERO:
            case TIPO_DECIMAL:
            case TIPO_TEXTO:
            case TIPO_CARACTER:
            case TIPO_LOGICO:
                return parsearDeclaracaoVariavelTipada();

            // Declaração de lista: lista<numero> nomes = []
            case TIPO_LISTA:
                return parsearDeclaracaoLista();

            // Declaração com var/variável: var x = 10
            case KW_VARIAVEL:
                return parsearDeclaracaoVar();

            // Declaração de constante: constante numero MAX = 100
            case KW_CONSTANTE:
                return parsearDeclaracaoConstante();

            // Estruturas de controle
            case KW_SE:
                return parsearBlocoSe();

            case KW_PARA:
                return parsearBlocoPara();

            case KW_ENQUANTO:
                return parsearBlocoEnquanto();

            case KW_FACA:
                return parsearBlocoFacaEnquanto();

            case KW_ESCOLHA:
                return parsearBlocoEscolha();

            // Funções
            case KW_FUNCAO:
                return parsearDeclaracaoFuncao(null, false);

            case KW_PUBLICO:
            case KW_PRIVADO:
            case KW_PROTEGIDO:
                return parsearComModificador();

            case KW_ESTATICO:
                return parsearComEstatico();

            // Classes
            case KW_CLASSE:
                return parsearDeclaracaoClasse(null);

            // I/O
            case KW_MOSTRAR:
                return parsearMostrar();

            // Tratamento de erros
            case KW_TENTE:
                return parsearBlocoTentePegue();

            case KW_LANCE:
                return parsearLance();

            // CRUD
            case KW_CRIAR:
                return parsearCrudCriar();

            case KW_LISTAR:
                return parsearCrudListar();

            case KW_ATUALIZAR:
                return parsearCrudAtualizar();

            case KW_DELETAR:
                return parsearCrudDeletar();

            // Import
            case KW_IMPORTAR:
                return parsearImportar();

            // Retorne
            case KW_RETORNE:
                return parsearRetorne();

            // Pare e Continue
            case KW_PARE:
                avancar();
                consumirOpcional(TokenType.PONTO_VIRGULA);
                return new Pare(tokenAtual);

            case KW_CONTINUE:
                avancar();
                consumirOpcional(TokenType.PONTO_VIRGULA);
                return new Continue(tokenAtual);

            // Expressão/atribuição (identificador, chamada de função, etc.)
            default:
                return parsearExpressaoOuAtribuicao();
        }
    }

    // =====================================================================
    // DECLARAÇÕES DE VARIÁVEIS
    // =====================================================================

    /** Parseia: numero x = 10 */
    private No parsearDeclaracaoVariavelTipada() {
        Token tokenTipo = avancar();
        String tipo = tokenTipo.getValor();

        Token nomeToken = consumir(TokenType.IDENTIFICADOR, "Nome de variável esperado após tipo '" + tipo + "'");
        String nome = nomeToken.getValor();

        No valor = null;
        if (verificar(TokenType.OP_ATRIBUIR)) {
            avancar();
            valor = parsearExpressao();
        }

        consumirOpcional(TokenType.PONTO_VIRGULA);
        return new DeclaracaoVariavel(tokenTipo, mapearTipo(tipo), nome, valor);
    }

    /** Parseia: var x = 10 */
    private No parsearDeclaracaoVar() {
        Token tokenVar = avancar();

        Token nomeToken = consumir(TokenType.IDENTIFICADOR, "Nome de variável esperado após 'var'");
        String nome = nomeToken.getValor();

        consumir(TokenType.OP_ATRIBUIR, "Esperado '=' após nome da variável");
        No valor = parsearExpressao();

        consumirOpcional(TokenType.PONTO_VIRGULA);
        return new DeclaracaoVariavel(tokenVar, "var", nome, valor);
    }

    /** Parseia: constante numero MAX = 100 */
    private No parsearDeclaracaoConstante() {
        Token tokenConst = avancar();

        // Pode ter tipo ou não
        String tipo = "var";
        if (atual().getTipo().isTipo()) {
            tipo = mapearTipo(avancar().getValor());
        }

        Token nomeToken = consumir(TokenType.IDENTIFICADOR, "Nome de constante esperado");
        String nome = nomeToken.getValor();

        consumir(TokenType.OP_ATRIBUIR, "Esperado '=' após nome da constante");
        No valor = parsearExpressao();

        consumirOpcional(TokenType.PONTO_VIRGULA);
        return new DeclaracaoConstante(tokenConst, tipo, nome, valor);
    }

    /** Parseia: lista<numero> nomes = [1, 2, 3] */
    private No parsearDeclaracaoLista() {
        Token tokenLista = avancar();

        // Tipo do elemento: lista<tipo> ou lista tipo
        String tipoElemento = "Object";
        if (verificar(TokenType.OP_MENOR)) {
            avancar(); // <
            if (atual().getTipo().isTipo() || verificar(TokenType.IDENTIFICADOR)) {
                tipoElemento = mapearTipo(avancar().getValor());
            }
            consumir(TokenType.OP_MAIOR, "Esperado '>' após tipo da lista");
        }

        Token nomeToken = consumir(TokenType.IDENTIFICADOR, "Nome da lista esperado");
        String nome = nomeToken.getValor();

        List<No> valoresIniciais = new ArrayList<>();
        if (verificar(TokenType.OP_ATRIBUIR)) {
            avancar();
            if (verificar(TokenType.COLCHETE_ESQ)) {
                avancar();
                if (!verificar(TokenType.COLCHETE_DIR)) {
                    valoresIniciais.add(parsearExpressao());
                    while (verificar(TokenType.VIRGULA)) {
                        avancar();
                        valoresIniciais.add(parsearExpressao());
                    }
                }
                consumir(TokenType.COLCHETE_DIR, "Esperado ']' para fechar lista");
            }
        }

        consumirOpcional(TokenType.PONTO_VIRGULA);
        return new DeclaracaoLista(tokenLista, tipoElemento, nome, valoresIniciais);
    }

    // =====================================================================
    // ESTRUTURAS CONDICIONAIS
    // =====================================================================

    /** Parseia: se (condição) { ... } senao se (condição) { ... } senao { ... } */
    private No parsearBlocoSe() {
        Token tokenSe = avancar(); // consome 'se'

        consumir(TokenType.PAREN_ESQ, "Esperado '(' após 'se'");
        No condicao = parsearExpressao();
        consumir(TokenType.PAREN_DIR, "Esperado ')' após condição");

        List<No> corpoSe = parsearBloco();

        List<BlocoSe> senaoSe = new ArrayList<>();
        List<No> corpoSenao = null;

        while (verificar(TokenType.KW_SENAO)) {
            avancar(); // consome 'senao'
            if (verificar(TokenType.KW_SE)) {
                avancar(); // consome 'se'
                consumir(TokenType.PAREN_ESQ, "Esperado '(' após 'senao se'");
                No condSenaoSe = parsearExpressao();
                consumir(TokenType.PAREN_DIR, "Esperado ')' após condição");
                List<No> corpoSenaoSe = parsearBloco();
                senaoSe.add(new BlocoSe(tokenSe, condSenaoSe, corpoSenaoSe, new ArrayList<>(), null));
            } else {
                corpoSenao = parsearBloco();
                break;
            }
        }

        return new BlocoSe(tokenSe, condicao, corpoSe, senaoSe, corpoSenao);
    }

    // =====================================================================
    // LOOPS
    // =====================================================================

    /** Parseia: para (init; cond; inc) { ... } ou para item em lista { ... } */
    private No parsearBlocoPara() {
        Token tokenPara = avancar();

        // Verifica se é for-each: para item em lista
        if (verificar(TokenType.IDENTIFICADOR) && peek(1) != null && peek(1).is(TokenType.KW_EM)) {
            String variavel = avancar().getValor();
            avancar(); // consome 'em'
            No lista = parsearExpressao();
            List<No> corpo = parsearBloco();
            return new ParaEmLista(tokenPara, variavel, lista, corpo);
        }

        // For clássico: para (init; cond; inc) { ... }
        consumir(TokenType.PAREN_ESQ, "Esperado '(' após 'para'");

        No init = null;
        if (!verificar(TokenType.PONTO_VIRGULA)) {
            if (atual().getTipo().isTipo()) {
                init = parsearDeclaracaoVariavelTipada();
            } else {
                init = parsearExpressaoOuAtribuicao();
            }
        } else {
            avancar(); // pula ;
        }
        // O ; já foi consumido pela declaração/expressão ou avançamos manualmente

        No cond = null;
        if (!verificar(TokenType.PONTO_VIRGULA)) {
            cond = parsearExpressao();
        }
        consumir(TokenType.PONTO_VIRGULA, "Esperado ';' após condição do para");

        No inc = null;
        if (!verificar(TokenType.PAREN_DIR)) {
            inc = parsearExpressaoOuAtribuicao();
        }
        consumir(TokenType.PAREN_DIR, "Esperado ')' para fechar 'para'");

        List<No> corpo = parsearBloco();
        return new BlocoPara(tokenPara, init, cond, inc, corpo);
    }

    /** Parseia: enquanto (condição) { ... } */
    private No parsearBlocoEnquanto() {
        Token tokenEnq = avancar();

        consumir(TokenType.PAREN_ESQ, "Esperado '(' após 'enquanto'");
        No condicao = parsearExpressao();
        consumir(TokenType.PAREN_DIR, "Esperado ')' após condição");

        List<No> corpo = parsearBloco();
        return new BlocoEnquanto(tokenEnq, condicao, corpo);
    }

    /** Parseia: faca { ... } enquanto (condição) */
    private No parsearBlocoFacaEnquanto() {
        Token tokenFaca = avancar();

        List<No> corpo = parsearBloco();

        consumir(TokenType.KW_ENQUANTO, "Esperado 'enquanto' após bloco 'faca'");
        consumir(TokenType.PAREN_ESQ, "Esperado '(' após 'enquanto'");
        No condicao = parsearExpressao();
        consumir(TokenType.PAREN_DIR, "Esperado ')' após condição");
        consumirOpcional(TokenType.PONTO_VIRGULA);

        return new BlocoFacaEnquanto(tokenFaca, corpo, condicao);
    }

    /** Parseia: escolha (expr) { caso valor: ... padrao: ... } */
    private No parsearBlocoEscolha() {
        Token tokenEsc = avancar();

        consumir(TokenType.PAREN_ESQ, "Esperado '(' após 'escolha'");
        No expressao = parsearExpressao();
        consumir(TokenType.PAREN_DIR, "Esperado ')'");
        consumir(TokenType.CHAVE_ESQ, "Esperado '{' após escolha");

        List<CasoEscolha> casos = new ArrayList<>();
        List<No> corpoPadrao = null;

        while (!verificar(TokenType.CHAVE_DIR) && !fimDosTokens()) {
            if (verificar(TokenType.KW_CASO)) {
                avancar();
                No valor = parsearExpressao();
                consumir(TokenType.DOIS_PONTOS, "Esperado ':' após valor do caso");
                List<No> corpoCaso = new ArrayList<>();
                while (!verificar(TokenType.KW_CASO) && !verificar(TokenType.KW_PADRAO)
                        && !verificar(TokenType.CHAVE_DIR) && !fimDosTokens()) {
                    No dec = parsearDeclaracao();
                    if (dec != null) corpoCaso.add(dec);
                }
                casos.add(new CasoEscolha(valor, corpoCaso));
            } else if (verificar(TokenType.KW_PADRAO)) {
                avancar();
                consumir(TokenType.DOIS_PONTOS, "Esperado ':' após 'padrao'");
                corpoPadrao = new ArrayList<>();
                while (!verificar(TokenType.CHAVE_DIR) && !fimDosTokens()) {
                    No dec = parsearDeclaracao();
                    if (dec != null) corpoPadrao.add(dec);
                }
            } else {
                throw erroSintaxe("Esperado 'caso' ou 'padrao' dentro de 'escolha'");
            }
        }

        consumir(TokenType.CHAVE_DIR, "Esperado '}' para fechar 'escolha'");
        return new BlocoEscolha(tokenEsc, expressao, casos, corpoPadrao);
    }

    // =====================================================================
    // FUNÇÕES
    // =====================================================================

    /** Parseia: funcao nome(params) retorna tipo { ... } */
    private No parsearDeclaracaoFuncao(String modificadorAcesso, boolean isEstatico) {
        Token tokenFunc = avancar();

        Token nomeToken = consumir(TokenType.IDENTIFICADOR, "Nome de função esperado após 'funcao'");
        String nome = nomeToken.getValor();

        consumir(TokenType.PAREN_ESQ, "Esperado '(' após nome da função");
        List<Parametro> parametros = parsearParametros();
        consumir(TokenType.PAREN_DIR, "Esperado ')' após parâmetros");

        // Tipo de retorno opcional: retorna tipo
        String tipoRetorno = "void";
        if (verificar(TokenType.KW_RETORNA)) {
            avancar();
            if (atual().getTipo().isTipo()) {
                tipoRetorno = mapearTipo(avancar().getValor());
            } else if (verificar(TokenType.IDENTIFICADOR)) {
                tipoRetorno = avancar().getValor();
            }
        }

        List<No> corpo = parsearBloco();
        return new DeclaracaoFuncao(tokenFunc, nome, parametros, tipoRetorno, corpo, modificadorAcesso, isEstatico);
    }

    /** Parseia lista de parâmetros: (tipo nome, tipo nome, ...) */
    private List<Parametro> parsearParametros() {
        List<Parametro> params = new ArrayList<>();
        if (verificar(TokenType.PAREN_DIR)) return params;

        do {
            String tipo;
            if (atual().getTipo().isTipo()) {
                tipo = mapearTipo(avancar().getValor());
            } else if (verificar(TokenType.IDENTIFICADOR)) {
                tipo = avancar().getValor();
            } else {
                throw erroSintaxe("Tipo de parâmetro esperado");
            }

            Token nomeToken = consumir(TokenType.IDENTIFICADOR, "Nome de parâmetro esperado");
            params.add(new Parametro(tipo, nomeToken.getValor()));
        } while (verificar(TokenType.VIRGULA) && avancar() != null);

        return params;
    }

    /** Parseia retorne */
    private No parsearRetorne() {
        Token tokenRet = avancar();
        No valor = null;
        if (!verificar(TokenType.PONTO_VIRGULA) && !verificar(TokenType.CHAVE_DIR) && !fimDosTokens()) {
            valor = parsearExpressao();
        }
        consumirOpcional(TokenType.PONTO_VIRGULA);
        return new Retorne(tokenRet, valor);
    }

    // =====================================================================
    // CLASSES
    // =====================================================================

    /** Parseia: classe Nome { ... } ou classe Nome estende Pai { ... } */
    private No parsearDeclaracaoClasse(String modificadorAcesso) {
        Token tokenClasse = avancar();

        Token nomeToken = consumir(TokenType.IDENTIFICADOR, "Nome de classe esperado");
        String nome = nomeToken.getValor();

        String classePai = null;
        if (verificar(TokenType.KW_ESTENDE)) {
            avancar();
            classePai = consumir(TokenType.IDENTIFICADOR, "Nome da classe pai esperado").getValor();
        }

        consumir(TokenType.CHAVE_ESQ, "Esperado '{' para abrir corpo da classe");

        List<DeclaracaoVariavel> atributos = new ArrayList<>();
        List<DeclaracaoFuncao> metodos = new ArrayList<>();
        DeclaracaoFuncao construtor = null;

        while (!verificar(TokenType.CHAVE_DIR) && !fimDosTokens()) {
            // Pula ponto-e-vírgulas soltos
            if (verificar(TokenType.PONTO_VIRGULA)) {
                avancar();
                continue;
            }

            String modAcesso = null;
            boolean estatico = false;

            // Modificadores de acesso
            if (atual().getTipo().isModificadorAcesso()) {
                modAcesso = avancar().getValor();
            }
            if (verificar(TokenType.KW_ESTATICO)) {
                estatico = true;
                avancar();
            }

            // Função/método
            if (verificar(TokenType.KW_FUNCAO)) {
                DeclaracaoFuncao metodo = (DeclaracaoFuncao) parsearDeclaracaoFuncao(modAcesso, estatico);
                if (metodo.getNome().equals(nome) || metodo.getNome().equals("construtor")) {
                    construtor = metodo;
                } else {
                    metodos.add(metodo);
                }
            }
            // Atributo tipado
            else if (atual().getTipo().isTipo()) {
                DeclaracaoVariavel attr = (DeclaracaoVariavel) parsearDeclaracaoVariavelTipada();
                atributos.add(attr);
            }
            // Atributo com var
            else if (verificar(TokenType.KW_VARIAVEL)) {
                DeclaracaoVariavel attr = (DeclaracaoVariavel) parsearDeclaracaoVar();
                atributos.add(attr);
            }
            else {
                throw erroSintaxe("Declaração inesperada dentro da classe: " + atual().getValor());
            }
        }

        consumir(TokenType.CHAVE_DIR, "Esperado '}' para fechar classe");
        return new DeclaracaoClasse(tokenClasse, nome, classePai, atributos, metodos, construtor, modificadorAcesso);
    }

    // =====================================================================
    // I/O
    // =====================================================================

    /** Parseia: mostrar("texto") ou mostrar(expr1, expr2) */
    private No parsearMostrar() {
        Token tokenMostrar = avancar();
        consumir(TokenType.PAREN_ESQ, "Esperado '(' após 'mostrar'");

        List<No> argumentos = new ArrayList<>();
        if (!verificar(TokenType.PAREN_DIR)) {
            argumentos.add(parsearExpressao());
            while (verificar(TokenType.VIRGULA)) {
                avancar();
                argumentos.add(parsearExpressao());
            }
        }

        consumir(TokenType.PAREN_DIR, "Esperado ')' após argumentos de 'mostrar'");
        consumirOpcional(TokenType.PONTO_VIRGULA);
        return new Mostrar(tokenMostrar, argumentos);
    }

    // =====================================================================
    // TRATAMENTO DE ERROS
    // =====================================================================

    /** Parseia: tente { ... } pegue (erro Tipo) { ... } finalmente { ... } */
    private No parsearBlocoTentePegue() {
        Token tokenTente = avancar();

        List<No> corpoTente = parsearBloco();

        String tipoErro = "Exception";
        String nomeErro = "erro";
        List<No> corpoPegue = new ArrayList<>();

        if (verificar(TokenType.KW_PEGUE)) {
            avancar();
            consumir(TokenType.PAREN_ESQ, "Esperado '(' após 'pegue'");

            // Pode ser: pegue (erro Tipo) ou pegue (Tipo erro)
            if (verificar(TokenType.IDENTIFICADOR)) {
                String primeiro = avancar().getValor();
                if (verificar(TokenType.IDENTIFICADOR)) {
                    // formato: pegue (nomeErro TipoErro)
                    nomeErro = primeiro;
                    tipoErro = avancar().getValor();
                } else {
                    // formato: pegue (TipoErro) — uso nomeErro padrão
                    tipoErro = primeiro;
                }
            }

            consumir(TokenType.PAREN_DIR, "Esperado ')' após tipo do erro");
            corpoPegue = parsearBloco();
        }

        List<No> corpoFinalmente = null;
        if (verificar(TokenType.KW_FINALMENTE)) {
            avancar();
            corpoFinalmente = parsearBloco();
        }

        return new BlocoTentePegue(tokenTente, corpoTente, tipoErro, nomeErro, corpoPegue, corpoFinalmente);
    }

    /** Parseia: lance novo Erro("mensagem") */
    private No parsearLance() {
        Token tokenLance = avancar();
        No expressao = parsearExpressao();
        consumirOpcional(TokenType.PONTO_VIRGULA);
        return new Lance(tokenLance, expressao);
    }

    // =====================================================================
    // CRUD
    // =====================================================================

    /** Parseia: criar cliente "João", idade 25 */
    private No parsearCrudCriar() {
        Token tokenCriar = avancar();
        String entidade = consumir(TokenType.IDENTIFICADOR, "Nome da entidade esperado após 'criar'").getValor();

        List<CrudCampo> campos = parsearCamposCrud();
        consumirOpcional(TokenType.PONTO_VIRGULA);
        return new CrudCriar(tokenCriar, entidade, campos);
    }

    /** Parseia: listar clientes */
    private No parsearCrudListar() {
        Token tokenListar = avancar();
        String entidade = consumir(TokenType.IDENTIFICADOR, "Nome da entidade esperado após 'listar'").getValor();
        consumirOpcional(TokenType.PONTO_VIRGULA);
        return new CrudListar(tokenListar, entidade);
    }

    /** Parseia: atualizar cliente 0, nome "NovoNome", idade 30 */
    private No parsearCrudAtualizar() {
        Token tokenAtualizar = avancar();
        String entidade = consumir(TokenType.IDENTIFICADOR, "Nome da entidade esperado após 'atualizar'").getValor();
        No indice = parsearExpressao();

        consumir(TokenType.VIRGULA, "Esperado ',' após índice");
        List<CrudCampo> campos = parsearCamposCrud();
        consumirOpcional(TokenType.PONTO_VIRGULA);
        return new CrudAtualizar(tokenAtualizar, entidade, indice, campos);
    }

    /** Parseia: deletar cliente 1 */
    private No parsearCrudDeletar() {
        Token tokenDeletar = avancar();
        String entidade = consumir(TokenType.IDENTIFICADOR, "Nome da entidade esperado após 'deletar'").getValor();
        No indice = parsearExpressao();
        consumirOpcional(TokenType.PONTO_VIRGULA);
        return new CrudDeletar(tokenDeletar, entidade, indice);
    }

    /** Parseia campos CRUD: nome "valor", campo2 valor2 */
    private List<CrudCampo> parsearCamposCrud() {
        List<CrudCampo> campos = new ArrayList<>();

        if (verificar(TokenType.IDENTIFICADOR) || verificar(TokenType.LITERAL_TEXTO)) {
            do {
                if (verificar(TokenType.VIRGULA)) avancar();
                String nomeCampo = consumir(TokenType.IDENTIFICADOR, "Nome do campo esperado").getValor();
                No valor = parsearExpressao();
                campos.add(new CrudCampo(nomeCampo, valor));
            } while (verificar(TokenType.VIRGULA));
        }

        return campos;
    }

    // =====================================================================
    // IMPORTS
    // =====================================================================

    /** Parseia: importar modulo ou importar funcao de modulo como alias */
    private No parsearImportar() {
        Token tokenImport = avancar();

        String primeiro = consumir(TokenType.IDENTIFICADOR, "Nome do módulo esperado").getValor();
        String modulo = primeiro;
        String item = null;
        String alias = null;

        // importar funcao de modulo
        if (verificar(TokenType.KW_DE)) {
            avancar();
            item = primeiro;
            modulo = consumir(TokenType.IDENTIFICADOR, "Nome do módulo esperado após 'de'").getValor();
        }

        // importar ... como alias
        if (verificar(TokenType.KW_COMO)) {
            avancar();
            alias = consumir(TokenType.IDENTIFICADOR, "Alias esperado após 'como'").getValor();
        }

        consumirOpcional(TokenType.PONTO_VIRGULA);
        return new Importar(tokenImport, modulo, item, alias);
    }

    // =====================================================================
    // MODIFICADORES
    // =====================================================================

    /** Parseia declaração com modificador de acesso (publico funcao ..., etc.) */
    private No parsearComModificador() {
        String modificador = avancar().getValor();

        boolean estatico = false;
        if (verificar(TokenType.KW_ESTATICO)) {
            estatico = true;
            avancar();
        }

        if (verificar(TokenType.KW_FUNCAO)) {
            return parsearDeclaracaoFuncao(modificador, estatico);
        } else if (verificar(TokenType.KW_CLASSE)) {
            return parsearDeclaracaoClasse(modificador);
        } else {
            throw erroSintaxe("Esperado 'funcao' ou 'classe' após modificador '" + modificador + "'");
        }
    }

    /** Parseia declaração com static */
    private No parsearComEstatico() {
        avancar(); // consome 'estatico'

        if (verificar(TokenType.KW_FUNCAO)) {
            return parsearDeclaracaoFuncao(null, true);
        } else {
            throw erroSintaxe("Esperado 'funcao' após 'estatico'");
        }
    }

    // =====================================================================
    // EXPRESSÕES (com precedência de operadores)
    // =====================================================================

    /** Parseia uma expressão ou atribuição */
    private No parsearExpressaoOuAtribuicao() {
        No expr = parsearExpressao();

        // Verifica atribuição: x = valor
        if (verificar(TokenType.OP_ATRIBUIR)) {
            Token op = avancar();
            No valor = parsearExpressao();
            consumirOpcional(TokenType.PONTO_VIRGULA);
            return new Atribuicao(op, expr, valor);
        }

        // Atribuição composta: x += valor
        if (verificar(TokenType.OP_MAIS_ATRIBUIR) || verificar(TokenType.OP_MENOS_ATRIBUIR)
                || verificar(TokenType.OP_MULTIPLICAR_ATRIBUIR) || verificar(TokenType.OP_DIVIDIR_ATRIBUIR)
                || verificar(TokenType.OP_MODULO_ATRIBUIR)) {
            Token op = avancar();
            No valor = parsearExpressao();
            consumirOpcional(TokenType.PONTO_VIRGULA);
            return new AtribuicaoComposta(op, expr, op.getValor(), valor);
        }

        consumirOpcional(TokenType.PONTO_VIRGULA);
        return expr;
    }

    /** Expressão: precedência mais baixa (OR lógico) */
    private No parsearExpressao() {
        return parsearOuLogico();
    }

    /** ou lógico: ... ou ... */
    private No parsearOuLogico() {
        No esquerda = parsearELogico();
        while (verificar(TokenType.OP_OU)) {
            Token op = avancar();
            No direita = parsearELogico();
            esquerda = new ExpressaoBinaria(op, esquerda, "||", direita);
        }
        return esquerda;
    }

    /** e lógico: ... e ... */
    private No parsearELogico() {
        No esquerda = parsearIgualdade();
        while (verificar(TokenType.OP_E)) {
            Token op = avancar();
            No direita = parsearIgualdade();
            esquerda = new ExpressaoBinaria(op, esquerda, "&&", direita);
        }
        return esquerda;
    }

    /** Igualdade: == != */
    private No parsearIgualdade() {
        No esquerda = parsearComparacao();
        while (verificar(TokenType.OP_IGUAL) || verificar(TokenType.OP_DIFERENTE)) {
            Token op = avancar();
            No direita = parsearComparacao();
            esquerda = new ExpressaoBinaria(op, esquerda, op.getValor(), direita);
        }
        return esquerda;
    }

    /** Comparação: > < >= <= */
    private No parsearComparacao() {
        No esquerda = parsearAdicao();
        while (verificar(TokenType.OP_MAIOR) || verificar(TokenType.OP_MENOR)
                || verificar(TokenType.OP_MAIOR_IGUAL) || verificar(TokenType.OP_MENOR_IGUAL)) {
            Token op = avancar();
            No direita = parsearAdicao();
            esquerda = new ExpressaoBinaria(op, esquerda, op.getValor(), direita);
        }
        return esquerda;
    }

    /** Adição e subtração: + - */
    private No parsearAdicao() {
        No esquerda = parsearMultiplicacao();
        while (verificar(TokenType.OP_MAIS) || verificar(TokenType.OP_MENOS)) {
            Token op = avancar();
            No direita = parsearMultiplicacao();
            esquerda = new ExpressaoBinaria(op, esquerda, op.getValor(), direita);
        }
        return esquerda;
    }

    /** Multiplicação, divisão, módulo: * / % */
    private No parsearMultiplicacao() {
        No esquerda = parsearPotencia();
        while (verificar(TokenType.OP_MULTIPLICAR) || verificar(TokenType.OP_DIVIDIR)
                || verificar(TokenType.OP_MODULO)) {
            Token op = avancar();
            No direita = parsearPotencia();
            esquerda = new ExpressaoBinaria(op, esquerda, op.getValor(), direita);
        }
        return esquerda;
    }

    /** Potência: ** */
    private No parsearPotencia() {
        No esquerda = parsearUnario();
        while (verificar(TokenType.OP_POTENCIA)) {
            Token op = avancar();
            No direita = parsearUnario();
            esquerda = new ExpressaoBinaria(op, esquerda, "**", direita);
        }
        return esquerda;
    }

    /** Unário: -x, nao x, !x, ++x, --x */
    private No parsearUnario() {
        if (verificar(TokenType.OP_MENOS)) {
            Token op = avancar();
            No operando = parsearUnario();
            return new ExpressaoUnaria(op, "-", operando, true);
        }
        if (verificar(TokenType.OP_NAO)) {
            Token op = avancar();
            No operando = parsearUnario();
            return new ExpressaoUnaria(op, "!", operando, true);
        }
        if (verificar(TokenType.OP_INCREMENTAR)) {
            Token op = avancar();
            No operando = parsearPrimario();
            return new ExpressaoUnaria(op, "++", operando, true);
        }
        if (verificar(TokenType.OP_DECREMENTAR)) {
            Token op = avancar();
            No operando = parsearPrimario();
            return new ExpressaoUnaria(op, "--", operando, true);
        }
        return parsearPosfixo();
    }

    /** Pos-fixo: x++, x--, x[i], x.membro, x.metodo() */
    private No parsearPosfixo() {
        No expr = parsearPrimario();

        while (true) {
            if (verificar(TokenType.OP_INCREMENTAR)) {
                Token op = avancar();
                expr = new ExpressaoUnaria(op, "++", expr, false);
            } else if (verificar(TokenType.OP_DECREMENTAR)) {
                Token op = avancar();
                expr = new ExpressaoUnaria(op, "--", expr, false);
            } else if (verificar(TokenType.COLCHETE_ESQ)) {
                Token op = avancar();
                No indice = parsearExpressao();
                consumir(TokenType.COLCHETE_DIR, "Esperado ']'");
                expr = new AcessoIndice(op, expr, indice);
            } else if (verificar(TokenType.PONTO)) {
                Token op = avancar();
                String membro = consumir(TokenType.IDENTIFICADOR, "Nome de membro esperado após '.'").getValor();
                List<No> args = null;
                if (verificar(TokenType.PAREN_ESQ)) {
                    avancar();
                    args = parsearArgumentos();
                    consumir(TokenType.PAREN_DIR, "Esperado ')'");
                }
                expr = new AcessoMembro(op, expr, membro, args);
            } else {
                break;
            }
        }

        return expr;
    }

    /** Expressão primária: literais, identificadores, chamadas, novo, etc. */
    private No parsearPrimario() {
        Token tokenAtual = atual();

        switch (tokenAtual.getTipo()) {
            case LITERAL_INTEIRO: {
                avancar();
                String val = tokenAtual.getValor().replace("L", "").replace("l", "");
                return new LiteralInteiro(tokenAtual, Integer.parseInt(val));
            }
            case LITERAL_DECIMAL: {
                avancar();
                return new LiteralDecimal(tokenAtual, Double.parseDouble(tokenAtual.getValor()));
            }
            case LITERAL_TEXTO: {
                avancar();
                return new LiteralTexto(tokenAtual, tokenAtual.getValor());
            }
            case LITERAL_CARACTER: {
                avancar();
                return new LiteralCaracter(tokenAtual, tokenAtual.getValor().charAt(0));
            }
            case LITERAL_VERDADEIRO: {
                avancar();
                return new LiteralBooleano(tokenAtual, true);
            }
            case LITERAL_FALSO: {
                avancar();
                return new LiteralBooleano(tokenAtual, false);
            }
            case LITERAL_NULO: {
                avancar();
                return new LiteralNulo(tokenAtual);
            }

            // Novo objeto: novo Classe(args)
            case KW_NOVO: {
                avancar();
                String classe = consumir(TokenType.IDENTIFICADOR, "Nome de classe esperado após 'novo'").getValor();
                consumir(TokenType.PAREN_ESQ, "Esperado '(' após nome da classe");
                List<No> args = parsearArgumentos();
                consumir(TokenType.PAREN_DIR, "Esperado ')'");
                return new NovoObjeto(tokenAtual, classe, args);
            }

            // lerTexto(), lerNumero(), lerDecimal()
            case KW_LER_TEXTO: {
                avancar();
                consumir(TokenType.PAREN_ESQ, "Esperado '(' após 'lerTexto'");
                No prompt = null;
                if (!verificar(TokenType.PAREN_DIR)) {
                    prompt = parsearExpressao();
                }
                consumir(TokenType.PAREN_DIR, "Esperado ')'");
                return new LerTexto(tokenAtual, prompt);
            }
            case KW_LER_NUMERO: {
                avancar();
                consumir(TokenType.PAREN_ESQ, "Esperado '(' após 'lerNumero'");
                No prompt = null;
                if (!verificar(TokenType.PAREN_DIR)) {
                    prompt = parsearExpressao();
                }
                consumir(TokenType.PAREN_DIR, "Esperado ')'");
                return new LerNumero(tokenAtual, prompt);
            }
            case KW_LER_DECIMAL: {
                avancar();
                consumir(TokenType.PAREN_ESQ, "Esperado '(' após 'lerDecimal'");
                No prompt = null;
                if (!verificar(TokenType.PAREN_DIR)) {
                    prompt = parsearExpressao();
                }
                consumir(TokenType.PAREN_DIR, "Esperado ')'");
                return new LerDecimal(tokenAtual, prompt);
            }

            // tamanho(lista)
            case KW_TAMANHO: {
                avancar();
                consumir(TokenType.PAREN_ESQ, "Esperado '('");
                No lista = parsearExpressao();
                consumir(TokenType.PAREN_DIR, "Esperado ')'");
                return new Tamanho(tokenAtual, lista);
            }

            // adicionar(lista, valor)
            case KW_ADICIONAR: {
                avancar();
                consumir(TokenType.PAREN_ESQ, "Esperado '('");
                No lista = parsearExpressao();
                consumir(TokenType.VIRGULA, "Esperado ','");
                No valor = parsearExpressao();
                consumir(TokenType.PAREN_DIR, "Esperado ')'");
                return new Adicionar(tokenAtual, lista, valor);
            }

            // remover(lista, indice)
            case KW_REMOVER: {
                avancar();
                consumir(TokenType.PAREN_ESQ, "Esperado '('");
                No lista = parsearExpressao();
                consumir(TokenType.VIRGULA, "Esperado ','");
                No indice = parsearExpressao();
                consumir(TokenType.PAREN_DIR, "Esperado ')'");
                return new Remover(tokenAtual, lista, indice);
            }

            // contem(lista, valor) — retorna booleano
            case KW_CONTEM: {
                avancar();
                consumir(TokenType.PAREN_ESQ, "Esperado '('");
                No lista = parsearExpressao();
                consumir(TokenType.VIRGULA, "Esperado ','");
                No valor = parsearExpressao();
                consumir(TokenType.PAREN_DIR, "Esperado ')'");
                // Representamos como chamada de método: lista.contains(valor)
                return new AcessoMembro(tokenAtual, lista, "contains", List.of(valor));
            }

            // este (this)
            case KW_ESTE: {
                avancar();
                return new Identificador(tokenAtual, "this");
            }

            // Agrupamento: (expressão)
            case PAREN_ESQ: {
                avancar();
                No expr = parsearExpressao();
                consumir(TokenType.PAREN_DIR, "Esperado ')'");
                return expr;
            }

            // Array literal: [1, 2, 3]
            case COLCHETE_ESQ: {
                return parsearArrayLiteral();
            }

            // Identificador ou chamada de função
            case IDENTIFICADOR: {
                return parsearIdentificadorOuChamada();
            }

            // mostrar como expressão (para uso inline)
            case KW_MOSTRAR: {
                return parsearMostrar();
            }

            default:
                throw erroSintaxe("Expressão inesperada: '" + tokenAtual.getValor() + "' (" + tokenAtual.getTipo() + ")");
        }
    }

    /** Parseia um identificador, que pode ser uma chamada de função: nome(args) */
    private No parsearIdentificadorOuChamada() {
        Token nomeToken = avancar();
        String nome = nomeToken.getValor();

        // Chamada de função: nome(args)
        if (verificar(TokenType.PAREN_ESQ)) {
            avancar();
            List<No> args = parsearArgumentos();
            consumir(TokenType.PAREN_DIR, "Esperado ')'");
            return new ChamadaFuncao(nomeToken, nome, args);
        }

        return new Identificador(nomeToken, nome);
    }

    /** Parseia array literal: [1, 2, 3] */
    private No parsearArrayLiteral() {
        Token tokenArr = avancar(); // [
        List<No> elementos = new ArrayList<>();

        if (!verificar(TokenType.COLCHETE_DIR)) {
            elementos.add(parsearExpressao());
            while (verificar(TokenType.VIRGULA)) {
                avancar();
                if (verificar(TokenType.COLCHETE_DIR)) break; // trailing comma
                elementos.add(parsearExpressao());
            }
        }

        consumir(TokenType.COLCHETE_DIR, "Esperado ']'");
        // Representamos como uma declaração de lista inline
        return new DeclaracaoLista(tokenArr, "Object", "_anonimo", elementos);
    }

    /** Parseia lista de argumentos: expr, expr, ... */
    private List<No> parsearArgumentos() {
        List<No> args = new ArrayList<>();
        if (verificar(TokenType.PAREN_DIR)) return args;

        args.add(parsearExpressao());
        while (verificar(TokenType.VIRGULA)) {
            avancar();
            args.add(parsearExpressao());
        }
        return args;
    }

    // =====================================================================
    // BLOCOS
    // =====================================================================

    /** Parseia um bloco: { declaracao1; declaracao2; ... } */
    private List<No> parsearBloco() {
        consumir(TokenType.CHAVE_ESQ, "Esperado '{' para abrir bloco");
        List<No> declaracoes = new ArrayList<>();

        while (!verificar(TokenType.CHAVE_DIR) && !fimDosTokens()) {
            No dec = parsearDeclaracao();
            if (dec != null) declaracoes.add(dec);
        }

        consumir(TokenType.CHAVE_DIR, "Esperado '}' para fechar bloco");
        return declaracoes;
    }

    // =====================================================================
    // MAPEAMENTO DE TIPOS
    // =====================================================================

    /**
     * Mapeia um tipo BRZ para o tipo interno da AST.
     * O mapeamento final para Java/Python/C é feito pelo gerador.
     */
    private String mapearTipo(String tipoBrz) {
        switch (tipoBrz.toLowerCase()) {
            case "numero": case "número": case "inteiro": return "numero";
            case "decimal": return "decimal";
            case "texto": return "texto";
            case "caracter": case "caractere": return "caracter";
            case "logico": case "lógico": case "booleano": return "logico";
            case "vazio": return "vazio";
            case "lista": return "lista";
            case "mapa": return "mapa";
            default: return tipoBrz; // classe customizada
        }
    }

    // =====================================================================
    // UTILITÁRIOS DO PARSER
    // =====================================================================

    private Token atual() {
        if (fimDosTokens()) return tokens.get(tokens.size() - 1);
        return tokens.get(posicao);
    }

    private Token peek(int offset) {
        int idx = posicao + offset;
        if (idx < 0 || idx >= tokens.size()) return null;
        return tokens.get(idx);
    }

    private Token avancar() {
        Token t = atual();
        if (!fimDosTokens()) posicao++;
        return t;
    }

    private boolean verificar(TokenType tipo) {
        return !fimDosTokens() && atual().getTipo() == tipo;
    }

    private boolean fimDosTokens() {
        return posicao >= tokens.size() || tokens.get(posicao).is(TokenType.EOF);
    }

    private Token consumir(TokenType tipo, String mensagemErro) {
        if (verificar(tipo)) return avancar();
        throw erroSintaxe(mensagemErro + " (encontrado: '" + atual().getValor() + "')");
    }

    private void consumirOpcional(TokenType tipo) {
        if (verificar(tipo)) avancar();
    }

    private ErroSintaxe erroSintaxe(String mensagem) {
        Token t = atual();
        String msg = String.format("[Erro Sintático] %s:%d:%d - %s",
                arquivo, t.getLinha(), t.getColuna(), mensagem);
        return new ErroSintaxe(msg);
    }

    /** Recupera de um erro e avança até um ponto seguro */
    private void sincronizar() {
        avancar();
        while (!fimDosTokens()) {
            if (tokens.get(posicao - 1).is(TokenType.PONTO_VIRGULA)) return;
            if (tokens.get(posicao - 1).is(TokenType.CHAVE_DIR)) return;

            TokenType tipo = atual().getTipo();
            if (tipo == TokenType.KW_FUNCAO || tipo == TokenType.KW_CLASSE
                    || tipo == TokenType.KW_SE || tipo == TokenType.KW_PARA
                    || tipo == TokenType.KW_ENQUANTO || tipo == TokenType.KW_RETORNE
                    || tipo == TokenType.KW_MOSTRAR) {
                return;
            }
            avancar();
        }
    }

    /** Exceção para erros de sintaxe */
    public static class ErroSintaxe extends RuntimeException {
        public ErroSintaxe(String mensagem) {
            super(mensagem);
        }
    }
}
