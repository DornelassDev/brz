package brz.ast;

import brz.lexer.Token;
import java.util.List;

/**
 * No — Classe base abstrata para todos os nós da Árvore Sintática Abstrata (AST).
 * 
 * Cada construção da linguagem BRZ (declaração, expressão, comando, etc.) é
 * representada como um nó na AST. Essa árvore é gerada pelo Parser e consumida
 * pelos geradores de código (Java, Python, C, C++).
 * 
 * @author BRZ Team
 * @version 1.0.0
 * @since 2026-03-05
 */
public abstract class No {

    /** Token de referência para rastreamento de posição */
    protected Token token;

    public No(Token token) {
        this.token = token;
    }

    public No() {
        this.token = null;
    }

    public Token getToken() {
        return token;
    }

    public int getLinha() {
        return token != null ? token.getLinha() : 0;
    }

    public int getColuna() {
        return token != null ? token.getColuna() : 0;
    }

    /**
     * Aceita um visitante para o padrão Visitor (usado pelos geradores).
     * @param visitante O visitante que processará este nó
     * @return Resultado do processamento
     */
    public abstract <T> T aceitar(Visitante<T> visitante);

    // =====================================================================
    // INTERFACE VISITOR
    // =====================================================================

    /**
     * Interface Visitor para processar nós da AST.
     * Cada gerador de código (Java, Python, C, etc.) implementa esta interface.
     */
    public interface Visitante<T> {
        T visitarPrograma(Programa no);
        T visitarDeclaracaoVariavel(DeclaracaoVariavel no);
        T visitarDeclaracaoConstante(DeclaracaoConstante no);
        T visitarAtribuicao(Atribuicao no);
        T visitarBlocoSe(BlocoSe no);
        T visitarBlocoPara(BlocoPara no);
        T visitarBlocoEnquanto(BlocoEnquanto no);
        T visitarBlocoFacaEnquanto(BlocoFacaEnquanto no);
        T visitarBlocoEscolha(BlocoEscolha no);
        T visitarDeclaracaoFuncao(DeclaracaoFuncao no);
        T visitarChamadaFuncao(ChamadaFuncao no);
        T visitarRetorne(Retorne no);
        T visitarDeclaracaoClasse(DeclaracaoClasse no);
        T visitarNovoObjeto(NovoObjeto no);
        T visitarAcessoMembro(AcessoMembro no);
        T visitarMostrar(Mostrar no);
        T visitarLerTexto(LerTexto no);
        T visitarLerNumero(LerNumero no);
        T visitarLerDecimal(LerDecimal no);
        T visitarDeclaracaoLista(DeclaracaoLista no);
        T visitarAdicionar(Adicionar no);
        T visitarRemover(Remover no);
        T visitarTamanho(Tamanho no);
        T visitarAcessoIndice(AcessoIndice no);
        T visitarBlocoTentePegue(BlocoTentePegue no);
        T visitarLance(Lance no);
        T visitarExpressaoBinaria(ExpressaoBinaria no);
        T visitarExpressaoUnaria(ExpressaoUnaria no);
        T visitarLiteralInteiro(LiteralInteiro no);
        T visitarLiteralDecimal(LiteralDecimal no);
        T visitarLiteralTexto(LiteralTexto no);
        T visitarLiteralCaracter(LiteralCaracter no);
        T visitarLiteralBooleano(LiteralBooleano no);
        T visitarLiteralNulo(LiteralNulo no);
        T visitarIdentificador(Identificador no);
        T visitarImportar(Importar no);
        T visitarPare(Pare no);
        T visitarContinue(Continue no);
        T visitarCrudCriar(CrudCriar no);
        T visitarCrudListar(CrudListar no);
        T visitarCrudAtualizar(CrudAtualizar no);
        T visitarCrudDeletar(CrudDeletar no);
        T visitarAtribuicaoComposta(AtribuicaoComposta no);
        T visitarExpressaoTernaria(ExpressaoTernaria no);
        T visitarParaEmLista(ParaEmLista no);
    }

    // =====================================================================
    // NÓ RAIZ — PROGRAMA
    // =====================================================================

    /** Representa um programa completo BRZ (lista de declarações) */
    public static class Programa extends No {
        private final List<No> declaracoes;

        public Programa(List<No> declaracoes) {
            this.declaracoes = declaracoes;
        }

        public List<No> getDeclaracoes() { return declaracoes; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarPrograma(this);
        }
    }

    // =====================================================================
    // DECLARAÇÕES DE VARIÁVEIS E CONSTANTES
    // =====================================================================

    /** numero x = 10 */
    public static class DeclaracaoVariavel extends No {
        private final String tipo;
        private final String nome;
        private final No valor;

        public DeclaracaoVariavel(Token token, String tipo, String nome, No valor) {
            super(token);
            this.tipo = tipo;
            this.nome = nome;
            this.valor = valor;
        }

        public String getTipo() { return tipo; }
        public String getNome() { return nome; }
        public No getValor() { return valor; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarDeclaracaoVariavel(this);
        }
    }

    /** constante numero MAX = 100 */
    public static class DeclaracaoConstante extends No {
        private final String tipo;
        private final String nome;
        private final No valor;

        public DeclaracaoConstante(Token token, String tipo, String nome, No valor) {
            super(token);
            this.tipo = tipo;
            this.nome = nome;
            this.valor = valor;
        }

        public String getTipo() { return tipo; }
        public String getNome() { return nome; }
        public No getValor() { return valor; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarDeclaracaoConstante(this);
        }
    }

    // =====================================================================
    // ATRIBUIÇÃO
    // =====================================================================

    /** x = 42 */
    public static class Atribuicao extends No {
        private final No alvo;
        private final No valor;

        public Atribuicao(Token token, No alvo, No valor) {
            super(token);
            this.alvo = alvo;
            this.valor = valor;
        }

        public No getAlvo() { return alvo; }
        public No getValor() { return valor; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarAtribuicao(this);
        }
    }

    /** x += 5, x -= 3, etc. */
    public static class AtribuicaoComposta extends No {
        private final No alvo;
        private final String operador; // +=, -=, *=, /=, %=
        private final No valor;

        public AtribuicaoComposta(Token token, No alvo, String operador, No valor) {
            super(token);
            this.alvo = alvo;
            this.operador = operador;
            this.valor = valor;
        }

        public No getAlvo() { return alvo; }
        public String getOperador() { return operador; }
        public No getValor() { return valor; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarAtribuicaoComposta(this);
        }
    }

    // =====================================================================
    // ESTRUTURAS CONDICIONAIS
    // =====================================================================

    /** se (condição) { ... } senao se (condição) { ... } senao { ... } */
    public static class BlocoSe extends No {
        private final No condicao;
        private final List<No> corpoSe;
        private final List<BlocoSe> senaoSe; // lista de "senao se"
        private final List<No> corpoSenao;   // corpo do "senao" final

        public BlocoSe(Token token, No condicao, List<No> corpoSe, List<BlocoSe> senaoSe, List<No> corpoSenao) {
            super(token);
            this.condicao = condicao;
            this.corpoSe = corpoSe;
            this.senaoSe = senaoSe;
            this.corpoSenao = corpoSenao;
        }

        public No getCondicao() { return condicao; }
        public List<No> getCorpoSe() { return corpoSe; }
        public List<BlocoSe> getSenaoSe() { return senaoSe; }
        public List<No> getCorpoSenao() { return corpoSenao; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarBlocoSe(this);
        }
    }

    // =====================================================================
    // LOOPS
    // =====================================================================

    /** para (numero i = 0; i < 10; i++) { ... } */
    public static class BlocoPara extends No {
        private final No inicializacao;
        private final No condicao;
        private final No incremento;
        private final List<No> corpo;

        public BlocoPara(Token token, No inicializacao, No condicao, No incremento, List<No> corpo) {
            super(token);
            this.inicializacao = inicializacao;
            this.condicao = condicao;
            this.incremento = incremento;
            this.corpo = corpo;
        }

        public No getInicializacao() { return inicializacao; }
        public No getCondicao() { return condicao; }
        public No getIncremento() { return incremento; }
        public List<No> getCorpo() { return corpo; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarBlocoPara(this);
        }
    }

    /** para item em lista { ... } */
    public static class ParaEmLista extends No {
        private final String variavel;
        private final No lista;
        private final List<No> corpo;

        public ParaEmLista(Token token, String variavel, No lista, List<No> corpo) {
            super(token);
            this.variavel = variavel;
            this.lista = lista;
            this.corpo = corpo;
        }

        public String getVariavel() { return variavel; }
        public No getLista() { return lista; }
        public List<No> getCorpo() { return corpo; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarParaEmLista(this);
        }
    }

    /** enquanto (condição) { ... } */
    public static class BlocoEnquanto extends No {
        private final No condicao;
        private final List<No> corpo;

        public BlocoEnquanto(Token token, No condicao, List<No> corpo) {
            super(token);
            this.condicao = condicao;
            this.corpo = corpo;
        }

        public No getCondicao() { return condicao; }
        public List<No> getCorpo() { return corpo; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarBlocoEnquanto(this);
        }
    }

    /** faca { ... } enquanto (condição) */
    public static class BlocoFacaEnquanto extends No {
        private final List<No> corpo;
        private final No condicao;

        public BlocoFacaEnquanto(Token token, List<No> corpo, No condicao) {
            super(token);
            this.corpo = corpo;
            this.condicao = condicao;
        }

        public List<No> getCorpo() { return corpo; }
        public No getCondicao() { return condicao; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarBlocoFacaEnquanto(this);
        }
    }

    /** escolha (expressão) { caso valor: ... padrao: ... } */
    public static class BlocoEscolha extends No {
        private final No expressao;
        private final List<CasoEscolha> casos;
        private final List<No> corpoPadrao;

        public BlocoEscolha(Token token, No expressao, List<CasoEscolha> casos, List<No> corpoPadrao) {
            super(token);
            this.expressao = expressao;
            this.casos = casos;
            this.corpoPadrao = corpoPadrao;
        }

        public No getExpressao() { return expressao; }
        public List<CasoEscolha> getCasos() { return casos; }
        public List<No> getCorpoPadrao() { return corpoPadrao; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarBlocoEscolha(this);
        }
    }

    /** Representa um caso dentro de um bloco escolha */
    public static class CasoEscolha {
        private final No valor;
        private final List<No> corpo;

        public CasoEscolha(No valor, List<No> corpo) {
            this.valor = valor;
            this.corpo = corpo;
        }

        public No getValor() { return valor; }
        public List<No> getCorpo() { return corpo; }
    }

    // =====================================================================
    // FUNÇÕES
    // =====================================================================

    /** funcao nome(params) retorna tipo { ... } */
    public static class DeclaracaoFuncao extends No {
        private final String nome;
        private final List<Parametro> parametros;
        private final String tipoRetorno;
        private final List<No> corpo;
        private final String modificadorAcesso; // publico, privado, protegido
        private final boolean isEstatico;

        public DeclaracaoFuncao(Token token, String nome, List<Parametro> parametros,
                                String tipoRetorno, List<No> corpo, String modificadorAcesso, boolean isEstatico) {
            super(token);
            this.nome = nome;
            this.parametros = parametros;
            this.tipoRetorno = tipoRetorno;
            this.corpo = corpo;
            this.modificadorAcesso = modificadorAcesso;
            this.isEstatico = isEstatico;
        }

        public String getNome() { return nome; }
        public List<Parametro> getParametros() { return parametros; }
        public String getTipoRetorno() { return tipoRetorno; }
        public List<No> getCorpo() { return corpo; }
        public String getModificadorAcesso() { return modificadorAcesso; }
        public boolean isEstatico() { return isEstatico; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarDeclaracaoFuncao(this);
        }
    }

    /** Representa um parâmetro de função: tipo nome */
    public static class Parametro {
        private final String tipo;
        private final String nome;

        public Parametro(String tipo, String nome) {
            this.tipo = tipo;
            this.nome = nome;
        }

        public String getTipo() { return tipo; }
        public String getNome() { return nome; }
    }

    /** chamadaFuncao(args) */
    public static class ChamadaFuncao extends No {
        private final String nome;
        private final List<No> argumentos;

        public ChamadaFuncao(Token token, String nome, List<No> argumentos) {
            super(token);
            this.nome = nome;
            this.argumentos = argumentos;
        }

        public String getNome() { return nome; }
        public List<No> getArgumentos() { return argumentos; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarChamadaFuncao(this);
        }
    }

    /** retorne valor */
    public static class Retorne extends No {
        private final No valor;

        public Retorne(Token token, No valor) {
            super(token);
            this.valor = valor;
        }

        public No getValor() { return valor; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarRetorne(this);
        }
    }

    // =====================================================================
    // CLASSES E OBJETOS
    // =====================================================================

    /** classe Nome { ... } ou classe Nome estende Pai { ... } */
    public static class DeclaracaoClasse extends No {
        private final String nome;
        private final String classePai;
        private final List<DeclaracaoVariavel> atributos;
        private final List<DeclaracaoFuncao> metodos;
        private final DeclaracaoFuncao construtor;
        private final String modificadorAcesso;

        public DeclaracaoClasse(Token token, String nome, String classePai,
                                List<DeclaracaoVariavel> atributos, List<DeclaracaoFuncao> metodos,
                                DeclaracaoFuncao construtor, String modificadorAcesso) {
            super(token);
            this.nome = nome;
            this.classePai = classePai;
            this.atributos = atributos;
            this.metodos = metodos;
            this.construtor = construtor;
            this.modificadorAcesso = modificadorAcesso;
        }

        public String getNome() { return nome; }
        public String getClassePai() { return classePai; }
        public List<DeclaracaoVariavel> getAtributos() { return atributos; }
        public List<DeclaracaoFuncao> getMetodos() { return metodos; }
        public DeclaracaoFuncao getConstrutor() { return construtor; }
        public String getModificadorAcesso() { return modificadorAcesso; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarDeclaracaoClasse(this);
        }
    }

    /** novo NomeClasse(args) */
    public static class NovoObjeto extends No {
        private final String classe;
        private final List<No> argumentos;

        public NovoObjeto(Token token, String classe, List<No> argumentos) {
            super(token);
            this.classe = classe;
            this.argumentos = argumentos;
        }

        public String getClasse() { return classe; }
        public List<No> getArgumentos() { return argumentos; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarNovoObjeto(this);
        }
    }

    /** objeto.membro ou objeto.metodo(args) */
    public static class AcessoMembro extends No {
        private final No objeto;
        private final String membro;
        private final List<No> argumentos; // null se não for chamada de método

        public AcessoMembro(Token token, No objeto, String membro, List<No> argumentos) {
            super(token);
            this.objeto = objeto;
            this.membro = membro;
            this.argumentos = argumentos;
        }

        public No getObjeto() { return objeto; }
        public String getMembro() { return membro; }
        public List<No> getArgumentos() { return argumentos; }
        public boolean isChamadaMetodo() { return argumentos != null; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarAcessoMembro(this);
        }
    }

    // =====================================================================
    // I/O — ENTRADA E SAÍDA
    // =====================================================================

    /** mostrar("texto") ou mostrar(expressão) */
    public static class Mostrar extends No {
        private final List<No> argumentos;

        public Mostrar(Token token, List<No> argumentos) {
            super(token);
            this.argumentos = argumentos;
        }

        public List<No> getArgumentos() { return argumentos; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarMostrar(this);
        }
    }

    /** lerTexto() ou lerTexto("prompt") */
    public static class LerTexto extends No {
        private final No prompt;

        public LerTexto(Token token, No prompt) {
            super(token);
            this.prompt = prompt;
        }

        public No getPrompt() { return prompt; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarLerTexto(this);
        }
    }

    /** lerNumero() ou lerNumero("prompt") */
    public static class LerNumero extends No {
        private final No prompt;

        public LerNumero(Token token, No prompt) {
            super(token);
            this.prompt = prompt;
        }

        public No getPrompt() { return prompt; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarLerNumero(this);
        }
    }

    /** lerDecimal() ou lerDecimal("prompt") */
    public static class LerDecimal extends No {
        private final No prompt;

        public LerDecimal(Token token, No prompt) {
            super(token);
            this.prompt = prompt;
        }

        public No getPrompt() { return prompt; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarLerDecimal(this);
        }
    }

    // =====================================================================
    // LISTAS E COLEÇÕES
    // =====================================================================

    /** lista<tipo> nome = [] */
    public static class DeclaracaoLista extends No {
        private final String tipoElemento;
        private final String nome;
        private final List<No> valoresIniciais;

        public DeclaracaoLista(Token token, String tipoElemento, String nome, List<No> valoresIniciais) {
            super(token);
            this.tipoElemento = tipoElemento;
            this.nome = nome;
            this.valoresIniciais = valoresIniciais;
        }

        public String getTipoElemento() { return tipoElemento; }
        public String getNome() { return nome; }
        public List<No> getValoresIniciais() { return valoresIniciais; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarDeclaracaoLista(this);
        }
    }

    /** adicionar(lista, valor) ou lista.adicionar(valor) */
    public static class Adicionar extends No {
        private final No lista;
        private final No valor;

        public Adicionar(Token token, No lista, No valor) {
            super(token);
            this.lista = lista;
            this.valor = valor;
        }

        public No getLista() { return lista; }
        public No getValor() { return valor; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarAdicionar(this);
        }
    }

    /** remover(lista, indice) */
    public static class Remover extends No {
        private final No lista;
        private final No indice;

        public Remover(Token token, No lista, No indice) {
            super(token);
            this.lista = lista;
            this.indice = indice;
        }

        public No getLista() { return lista; }
        public No getIndice() { return indice; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarRemover(this);
        }
    }

    /** tamanho(lista) */
    public static class Tamanho extends No {
        private final No lista;

        public Tamanho(Token token, No lista) {
            super(token);
            this.lista = lista;
        }

        public No getLista() { return lista; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarTamanho(this);
        }
    }

    /** lista[indice] */
    public static class AcessoIndice extends No {
        private final No objeto;
        private final No indice;

        public AcessoIndice(Token token, No objeto, No indice) {
            super(token);
            this.objeto = objeto;
            this.indice = indice;
        }

        public No getObjeto() { return objeto; }
        public No getIndice() { return indice; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarAcessoIndice(this);
        }
    }

    // =====================================================================
    // TRATAMENTO DE ERROS
    // =====================================================================

    /** tente { ... } pegue (erro TipoErro) { ... } */
    public static class BlocoTentePegue extends No {
        private final List<No> corpoTente;
        private final String tipoErro;
        private final String nomeErro;
        private final List<No> corpoPegue;
        private final List<No> corpoFinalmente;

        public BlocoTentePegue(Token token, List<No> corpoTente, String tipoErro, String nomeErro,
                               List<No> corpoPegue, List<No> corpoFinalmente) {
            super(token);
            this.corpoTente = corpoTente;
            this.tipoErro = tipoErro;
            this.nomeErro = nomeErro;
            this.corpoPegue = corpoPegue;
            this.corpoFinalmente = corpoFinalmente;
        }

        public List<No> getCorpoTente() { return corpoTente; }
        public String getTipoErro() { return tipoErro; }
        public String getNomeErro() { return nomeErro; }
        public List<No> getCorpoPegue() { return corpoPegue; }
        public List<No> getCorpoFinalmente() { return corpoFinalmente; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarBlocoTentePegue(this);
        }
    }

    /** lance novo Erro("mensagem") */
    public static class Lance extends No {
        private final No expressao;

        public Lance(Token token, No expressao) {
            super(token);
            this.expressao = expressao;
        }

        public No getExpressao() { return expressao; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarLance(this);
        }
    }

    // =====================================================================
    // EXPRESSÕES
    // =====================================================================

    /** expressao operador expressao (ex: a + b, x > 5, etc.) */
    public static class ExpressaoBinaria extends No {
        private final No esquerda;
        private final String operador;
        private final No direita;

        public ExpressaoBinaria(Token token, No esquerda, String operador, No direita) {
            super(token);
            this.esquerda = esquerda;
            this.operador = operador;
            this.direita = direita;
        }

        public No getEsquerda() { return esquerda; }
        public String getOperador() { return operador; }
        public No getDireita() { return direita; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarExpressaoBinaria(this);
        }
    }

    /** operador expressao (ex: -x, nao condicao, ++i) */
    public static class ExpressaoUnaria extends No {
        private final String operador;
        private final No operando;
        private final boolean prefixo; // true se operador vem antes

        public ExpressaoUnaria(Token token, String operador, No operando, boolean prefixo) {
            super(token);
            this.operador = operador;
            this.operando = operando;
            this.prefixo = prefixo;
        }

        public String getOperador() { return operador; }
        public No getOperando() { return operando; }
        public boolean isPrefixo() { return prefixo; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarExpressaoUnaria(this);
        }
    }

    /** condicao ? valorSe : valorSenao */
    public static class ExpressaoTernaria extends No {
        private final No condicao;
        private final No valorSe;
        private final No valorSenao;

        public ExpressaoTernaria(Token token, No condicao, No valorSe, No valorSenao) {
            super(token);
            this.condicao = condicao;
            this.valorSe = valorSe;
            this.valorSenao = valorSenao;
        }

        public No getCondicao() { return condicao; }
        public No getValorSe() { return valorSe; }
        public No getValorSenao() { return valorSenao; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarExpressaoTernaria(this);
        }
    }

    // =====================================================================
    // LITERAIS
    // =====================================================================

    public static class LiteralInteiro extends No {
        private final int valor;
        public LiteralInteiro(Token token, int valor) { super(token); this.valor = valor; }
        public int getValor() { return valor; }
        @Override public <T> T aceitar(Visitante<T> visitante) { return visitante.visitarLiteralInteiro(this); }
    }

    public static class LiteralDecimal extends No {
        private final double valor;
        public LiteralDecimal(Token token, double valor) { super(token); this.valor = valor; }
        public double getValor() { return valor; }
        @Override public <T> T aceitar(Visitante<T> visitante) { return visitante.visitarLiteralDecimal(this); }
    }

    public static class LiteralTexto extends No {
        private final String valor;
        public LiteralTexto(Token token, String valor) { super(token); this.valor = valor; }
        public String getValor() { return valor; }
        @Override public <T> T aceitar(Visitante<T> visitante) { return visitante.visitarLiteralTexto(this); }
    }

    public static class LiteralCaracter extends No {
        private final char valor;
        public LiteralCaracter(Token token, char valor) { super(token); this.valor = valor; }
        public char getValor() { return valor; }
        @Override public <T> T aceitar(Visitante<T> visitante) { return visitante.visitarLiteralCaracter(this); }
    }

    public static class LiteralBooleano extends No {
        private final boolean valor;
        public LiteralBooleano(Token token, boolean valor) { super(token); this.valor = valor; }
        public boolean getValor() { return valor; }
        @Override public <T> T aceitar(Visitante<T> visitante) { return visitante.visitarLiteralBooleano(this); }
    }

    public static class LiteralNulo extends No {
        public LiteralNulo(Token token) { super(token); }
        @Override public <T> T aceitar(Visitante<T> visitante) { return visitante.visitarLiteralNulo(this); }
    }

    /** Referência a um nome (variável, parâmetro, etc.) */
    public static class Identificador extends No {
        private final String nome;
        public Identificador(Token token, String nome) { super(token); this.nome = nome; }
        public String getNome() { return nome; }
        @Override public <T> T aceitar(Visitante<T> visitante) { return visitante.visitarIdentificador(this); }
    }

    // =====================================================================
    // IMPORTS
    // =====================================================================

    /** importar modulo ou importar funcao de modulo */
    public static class Importar extends No {
        private final String modulo;
        private final String item; // null se importar módulo inteiro
        private final String alias; // null se sem alias

        public Importar(Token token, String modulo, String item, String alias) {
            super(token);
            this.modulo = modulo;
            this.item = item;
            this.alias = alias;
        }

        public String getModulo() { return modulo; }
        public String getItem() { return item; }
        public String getAlias() { return alias; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarImportar(this);
        }
    }

    // =====================================================================
    // CONTROLE DE FLUXO
    // =====================================================================

    public static class Pare extends No {
        public Pare(Token token) { super(token); }
        @Override public <T> T aceitar(Visitante<T> visitante) { return visitante.visitarPare(this); }
    }

    public static class Continue extends No {
        public Continue(Token token) { super(token); }
        @Override public <T> T aceitar(Visitante<T> visitante) { return visitante.visitarContinue(this); }
    }

    // =====================================================================
    // CRUD
    // =====================================================================

    /** criar entidade "campo1", campo2 valor2 */
    public static class CrudCriar extends No {
        private final String entidade;
        private final List<CrudCampo> campos;

        public CrudCriar(Token token, String entidade, List<CrudCampo> campos) {
            super(token);
            this.entidade = entidade;
            this.campos = campos;
        }

        public String getEntidade() { return entidade; }
        public List<CrudCampo> getCampos() { return campos; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarCrudCriar(this);
        }
    }

    /** listar entidades */
    public static class CrudListar extends No {
        private final String entidade;

        public CrudListar(Token token, String entidade) {
            super(token);
            this.entidade = entidade;
        }

        public String getEntidade() { return entidade; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarCrudListar(this);
        }
    }

    /** atualizar entidade indice, campo1 valor1 */
    public static class CrudAtualizar extends No {
        private final String entidade;
        private final No indice;
        private final List<CrudCampo> campos;

        public CrudAtualizar(Token token, String entidade, No indice, List<CrudCampo> campos) {
            super(token);
            this.entidade = entidade;
            this.indice = indice;
            this.campos = campos;
        }

        public String getEntidade() { return entidade; }
        public No getIndice() { return indice; }
        public List<CrudCampo> getCampos() { return campos; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarCrudAtualizar(this);
        }
    }

    /** deletar entidade indice */
    public static class CrudDeletar extends No {
        private final String entidade;
        private final No indice;

        public CrudDeletar(Token token, String entidade, No indice) {
            super(token);
            this.entidade = entidade;
            this.indice = indice;
        }

        public String getEntidade() { return entidade; }
        public No getIndice() { return indice; }

        @Override
        public <T> T aceitar(Visitante<T> visitante) {
            return visitante.visitarCrudDeletar(this);
        }
    }

    /** Representa um campo em operações CRUD: nome "valor" ou idade 16 */
    public static class CrudCampo {
        private final String nome;
        private final No valor;

        public CrudCampo(String nome, No valor) {
            this.nome = nome;
            this.valor = valor;
        }

        public String getNome() { return nome; }
        public No getValor() { return valor; }
    }
}
