package brz.generator.java;

import brz.ast.No;
import brz.ast.No.*;

import java.util.List;

/**
 * GeradorJava — Converte a AST BRZ em código-fonte Java válido.
 * 
 * Implementa o padrão Visitor para percorrer cada nó da AST e gerar
 * o código Java correspondente. O código gerado é válido, compilável
 * e pode ser executado diretamente com javac + java.
 * 
 * Mapeamento de tipos BRZ → Java:
 *   numero   → int
 *   decimal  → double
 *   texto    → String
 *   caracter → char
 *   logico   → boolean
 *   vazio    → void
 *   lista    → ArrayList
 *   mapa     → HashMap
 * 
 * @author BRZ Team
 * @version 1.0.0
 * @since 2026-03-05
 */
public class GeradorJava implements No.Visitante<String> {

    /** Nível de indentação atual */
    private int indentacao = 0;

    /** Nome da classe principal (derivado do nome do arquivo) */
    private String nomeClassePrincipal;

    /** Flag: estamos dentro de uma classe? */
    private boolean dentroDeClasse = false;

    /** Flag: precisa de Scanner? */
    private boolean precisaScanner = false;

    /** Flag: precisa de ArrayList? */
    private boolean precisaArrayList = false;

    /** Flag: precisa de HashMap? */
    private boolean precisaHashMap = false;

    /**
     * Gera código Java completo a partir de um programa BRZ.
     * 
     * @param programa AST do programa BRZ
     * @param nomeClasse Nome da classe Java principal
     * @return String com código Java válido e compilável
     */
    public String gerar(Programa programa, String nomeClasse) {
        this.nomeClassePrincipal = nomeClasse;

        // Primeira passagem: gera o corpo para detectar imports necessários
        StringBuilder corpo = new StringBuilder();
        this.indentacao = 1;

        boolean temClasseDefinida = false;
        StringBuilder classesExternas = new StringBuilder();
        StringBuilder metodosCodigo = new StringBuilder();

        for (No declaracao : programa.getDeclaracoes()) {
            if (declaracao instanceof DeclaracaoClasse) {
                temClasseDefinida = true;
                classesExternas.append(declaracao.aceitar(this)).append("\n\n");
            } else if (declaracao instanceof DeclaracaoFuncao) {
                // Funções top-level viram static methods na classe principal
                DeclaracaoFuncao func = (DeclaracaoFuncao) declaracao;
                metodosCodigo.append(gerarFuncaoComoMetodoStatic(func)).append("\n\n");
            } else {
                corpo.append(indent()).append(declaracao.aceitar(this)).append("\n");
            }
        }

        // Monta o arquivo Java completo
        StringBuilder resultado = new StringBuilder();

        // Imports
        resultado.append("import java.util.*;\n");
        if (precisaScanner) {
            resultado.append("import java.util.Scanner;\n");
        }
        resultado.append("\n");

        // Classes externas (definidas no BRZ)
        if (classesExternas.length() > 0) {
            resultado.append(classesExternas);
        }

        // Classe principal
        resultado.append("public class ").append(nomeClasse).append(" {\n\n");

        // Scanner como campo estático (se necessário)
        if (precisaScanner) {
            resultado.append("    private static Scanner _scanner = new Scanner(System.in);\n\n");
            resultado.append("    private static String _lerTexto(String prompt) {\n");
            resultado.append("        System.out.print(prompt);\n");
            resultado.append("        return _scanner.nextLine();\n");
            resultado.append("    }\n\n");
        }

        // Métodos estáticos (funções top-level)
        resultado.append(metodosCodigo);

        // Main method
        resultado.append("    public static void main(String[] args) {\n");
        resultado.append(corpo);
        resultado.append("    }\n");

        resultado.append("}\n");

        return resultado.toString();
    }

    // =====================================================================
    // VISITOR — PROGRAMA
    // =====================================================================

    @Override
    public String visitarPrograma(Programa no) {
        StringBuilder sb = new StringBuilder();
        for (No declaracao : no.getDeclaracoes()) {
            sb.append(declaracao.aceitar(this)).append("\n");
        }
        return sb.toString();
    }

    // =====================================================================
    // VISITOR — VARIÁVEIS E CONSTANTES
    // =====================================================================

    @Override
    public String visitarDeclaracaoVariavel(DeclaracaoVariavel no) {
        String tipoJava = mapearTipoParaJava(no.getTipo());
        StringBuilder sb = new StringBuilder();
        sb.append(tipoJava).append(" ").append(no.getNome());
        if (no.getValor() != null) {
            sb.append(" = ").append(no.getValor().aceitar(this));
        }
        sb.append(";");
        return sb.toString();
    }

    @Override
    public String visitarDeclaracaoConstante(DeclaracaoConstante no) {
        String tipoJava = mapearTipoParaJava(no.getTipo());
        return "final " + tipoJava + " " + no.getNome() + " = " + no.getValor().aceitar(this) + ";";
    }

    // =====================================================================
    // VISITOR — ATRIBUIÇÃO
    // =====================================================================

    @Override
    public String visitarAtribuicao(Atribuicao no) {
        return no.getAlvo().aceitar(this) + " = " + no.getValor().aceitar(this) + ";";
    }

    @Override
    public String visitarAtribuicaoComposta(AtribuicaoComposta no) {
        return no.getAlvo().aceitar(this) + " " + no.getOperador() + " " + no.getValor().aceitar(this) + ";";
    }

    // =====================================================================
    // VISITOR — CONDICIONAIS
    // =====================================================================

    @Override
    public String visitarBlocoSe(BlocoSe no) {
        StringBuilder sb = new StringBuilder();
        sb.append("if (").append(no.getCondicao().aceitar(this)).append(") {\n");

        indentacao++;
        for (No dec : no.getCorpoSe()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");

        // senao se
        for (BlocoSe senaoSe : no.getSenaoSe()) {
            sb.append(" else if (").append(senaoSe.getCondicao().aceitar(this)).append(") {\n");
            indentacao++;
            for (No dec : senaoSe.getCorpoSe()) {
                sb.append(indent()).append(dec.aceitar(this)).append("\n");
            }
            indentacao--;
            sb.append(indent()).append("}");
        }

        // senao
        if (no.getCorpoSenao() != null && !no.getCorpoSenao().isEmpty()) {
            sb.append(" else {\n");
            indentacao++;
            for (No dec : no.getCorpoSenao()) {
                sb.append(indent()).append(dec.aceitar(this)).append("\n");
            }
            indentacao--;
            sb.append(indent()).append("}");
        }

        return sb.toString();
    }

    // =====================================================================
    // VISITOR — LOOPS
    // =====================================================================

    @Override
    public String visitarBlocoPara(BlocoPara no) {
        StringBuilder sb = new StringBuilder();
        sb.append("for (");

        if (no.getInicializacao() != null) {
            String init = no.getInicializacao().aceitar(this);
            // Remove trailing ; se existir (o for já tem ;)
            if (init.endsWith(";")) init = init.substring(0, init.length() - 1);
            sb.append(init);
        }
        sb.append("; ");

        if (no.getCondicao() != null) {
            sb.append(no.getCondicao().aceitar(this));
        }
        sb.append("; ");

        if (no.getIncremento() != null) {
            String inc = no.getIncremento().aceitar(this);
            if (inc.endsWith(";")) inc = inc.substring(0, inc.length() - 1);
            sb.append(inc);
        }
        sb.append(") {\n");

        indentacao++;
        for (No dec : no.getCorpo()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");

        return sb.toString();
    }

    @Override
    public String visitarParaEmLista(ParaEmLista no) {
        StringBuilder sb = new StringBuilder();
        sb.append("for (var ").append(no.getVariavel()).append(" : ")
          .append(no.getLista().aceitar(this)).append(") {\n");

        indentacao++;
        for (No dec : no.getCorpo()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");

        return sb.toString();
    }

    @Override
    public String visitarBlocoEnquanto(BlocoEnquanto no) {
        StringBuilder sb = new StringBuilder();
        sb.append("while (").append(no.getCondicao().aceitar(this)).append(") {\n");

        indentacao++;
        for (No dec : no.getCorpo()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");

        return sb.toString();
    }

    @Override
    public String visitarBlocoFacaEnquanto(BlocoFacaEnquanto no) {
        StringBuilder sb = new StringBuilder();
        sb.append("do {\n");

        indentacao++;
        for (No dec : no.getCorpo()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("} while (").append(no.getCondicao().aceitar(this)).append(");");

        return sb.toString();
    }

    @Override
    public String visitarBlocoEscolha(BlocoEscolha no) {
        StringBuilder sb = new StringBuilder();
        sb.append("switch (").append(no.getExpressao().aceitar(this)).append(") {\n");

        indentacao++;
        for (CasoEscolha caso : no.getCasos()) {
            sb.append(indent()).append("case ").append(caso.getValor().aceitar(this)).append(":\n");
            indentacao++;
            for (No dec : caso.getCorpo()) {
                sb.append(indent()).append(dec.aceitar(this)).append("\n");
            }
            indentacao--;
        }

        if (no.getCorpoPadrao() != null) {
            sb.append(indent()).append("default:\n");
            indentacao++;
            for (No dec : no.getCorpoPadrao()) {
                sb.append(indent()).append(dec.aceitar(this)).append("\n");
            }
            indentacao--;
        }
        indentacao--;
        sb.append(indent()).append("}");

        return sb.toString();
    }

    // =====================================================================
    // VISITOR — FUNÇÕES
    // =====================================================================

    @Override
    public String visitarDeclaracaoFuncao(DeclaracaoFuncao no) {
        StringBuilder sb = new StringBuilder();

        // Modificador de acesso
        if (no.getModificadorAcesso() != null) {
            sb.append(mapearModificador(no.getModificadorAcesso())).append(" ");
        }
        if (no.isEstatico()) {
            sb.append("static ");
        }

        // Tipo de retorno
        sb.append(mapearTipoParaJava(no.getTipoRetorno())).append(" ");

        // Nome e parâmetros
        sb.append(no.getNome()).append("(");
        for (int i = 0; i < no.getParametros().size(); i++) {
            Parametro p = no.getParametros().get(i);
            if (i > 0) sb.append(", ");
            sb.append(mapearTipoParaJava(p.getTipo())).append(" ").append(p.getNome());
        }
        sb.append(") {\n");

        indentacao++;
        for (No dec : no.getCorpo()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");

        return sb.toString();
    }

    /** Gera uma função top-level como método estático */
    private String gerarFuncaoComoMetodoStatic(DeclaracaoFuncao no) {
        StringBuilder sb = new StringBuilder();
        sb.append("    public static ")
          .append(mapearTipoParaJava(no.getTipoRetorno())).append(" ")
          .append(no.getNome()).append("(");

        for (int i = 0; i < no.getParametros().size(); i++) {
            Parametro p = no.getParametros().get(i);
            if (i > 0) sb.append(", ");
            sb.append(mapearTipoParaJava(p.getTipo())).append(" ").append(p.getNome());
        }
        sb.append(") {\n");

        int savedIndent = indentacao;
        indentacao = 2;
        for (No dec : no.getCorpo()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        indentacao = savedIndent;
        sb.append("    }");

        return sb.toString();
    }

    @Override
    public String visitarChamadaFuncao(ChamadaFuncao no) {
        StringBuilder sb = new StringBuilder();
        sb.append(no.getNome()).append("(");
        for (int i = 0; i < no.getArgumentos().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(no.getArgumentos().get(i).aceitar(this));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String visitarRetorne(Retorne no) {
        if (no.getValor() != null) {
            return "return " + no.getValor().aceitar(this) + ";";
        }
        return "return;";
    }

    // =====================================================================
    // VISITOR — CLASSES
    // =====================================================================

    @Override
    public String visitarDeclaracaoClasse(DeclaracaoClasse no) {
        StringBuilder sb = new StringBuilder();

        // Modificador de acesso
        String mod = no.getModificadorAcesso() != null ? mapearModificador(no.getModificadorAcesso()) + " " : "";
        sb.append(mod).append("class ").append(no.getNome());

        if (no.getClassePai() != null) {
            sb.append(" extends ").append(no.getClassePai());
        }
        sb.append(" {\n\n");

        int savedIndent = indentacao;
        indentacao = 1;
        dentroDeClasse = true;

        // Atributos
        for (DeclaracaoVariavel attr : no.getAtributos()) {
            sb.append(indent()).append(attr.aceitar(this)).append("\n");
        }
        if (!no.getAtributos().isEmpty()) sb.append("\n");

        // Construtor
        if (no.getConstrutor() != null) {
            sb.append(indent()).append("public ").append(no.getNome()).append("(");
            DeclaracaoFuncao c = no.getConstrutor();
            for (int i = 0; i < c.getParametros().size(); i++) {
                Parametro p = c.getParametros().get(i);
                if (i > 0) sb.append(", ");
                sb.append(mapearTipoParaJava(p.getTipo())).append(" ").append(p.getNome());
            }
            sb.append(") {\n");
            indentacao++;
            for (No dec : c.getCorpo()) {
                sb.append(indent()).append(dec.aceitar(this)).append("\n");
            }
            indentacao--;
            sb.append(indent()).append("}\n\n");
        }

        // Métodos
        for (DeclaracaoFuncao metodo : no.getMetodos()) {
            sb.append(indent()).append(metodo.aceitar(this)).append("\n\n");
        }

        dentroDeClasse = false;
        indentacao = savedIndent;
        sb.append("}\n");

        return sb.toString();
    }

    @Override
    public String visitarNovoObjeto(NovoObjeto no) {
        StringBuilder sb = new StringBuilder();
        sb.append("new ").append(no.getClasse()).append("(");
        for (int i = 0; i < no.getArgumentos().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(no.getArgumentos().get(i).aceitar(this));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String visitarAcessoMembro(AcessoMembro no) {
        StringBuilder sb = new StringBuilder();
        sb.append(no.getObjeto().aceitar(this)).append(".").append(no.getMembro());
        if (no.isChamadaMetodo()) {
            sb.append("(");
            for (int i = 0; i < no.getArgumentos().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(no.getArgumentos().get(i).aceitar(this));
            }
            sb.append(")");
        }
        return sb.toString();
    }

    // =====================================================================
    // VISITOR — I/O
    // =====================================================================

    @Override
    public String visitarMostrar(Mostrar no) {
        StringBuilder sb = new StringBuilder();
        sb.append("System.out.println(");
        if (no.getArgumentos().size() == 1) {
            sb.append(no.getArgumentos().get(0).aceitar(this));
        } else {
            for (int i = 0; i < no.getArgumentos().size(); i++) {
                if (i > 0) sb.append(" + \" \" + ");
                sb.append(no.getArgumentos().get(i).aceitar(this));
            }
        }
        sb.append(");");
        return sb.toString();
    }

    @Override
    public String visitarLerTexto(LerTexto no) {
        precisaScanner = true;
        if (no.getPrompt() != null) {
            return "_lerTexto(" + no.getPrompt().aceitar(this) + ")";
        }
        return "_scanner.nextLine()";
    }

    @Override
    public String visitarLerNumero(LerNumero no) {
        precisaScanner = true;
        if (no.getPrompt() != null) {
            return "Integer.parseInt(_lerTexto(" + no.getPrompt().aceitar(this) + "))";
        }
        return "Integer.parseInt(_scanner.nextLine())";
    }

    @Override
    public String visitarLerDecimal(LerDecimal no) {
        precisaScanner = true;
        if (no.getPrompt() != null) {
            return "Double.parseDouble(_lerTexto(" + no.getPrompt().aceitar(this) + "))";
        }
        return "Double.parseDouble(_scanner.nextLine())";
    }

    // =====================================================================
    // VISITOR — LISTAS
    // =====================================================================

    @Override
    public String visitarDeclaracaoLista(DeclaracaoLista no) {
        precisaArrayList = true;
        String tipoJava = mapearTipoWrapper(no.getTipoElemento());
        StringBuilder sb = new StringBuilder();

        if (no.getNome().equals("_anonimo")) {
            // Array literal inline
            sb.append("new ArrayList<>(Arrays.asList(");
            for (int i = 0; i < no.getValoresIniciais().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(no.getValoresIniciais().get(i).aceitar(this));
            }
            sb.append("))");
        } else {
            sb.append("ArrayList<").append(tipoJava).append("> ").append(no.getNome());
            sb.append(" = new ArrayList<>(");
            if (!no.getValoresIniciais().isEmpty()) {
                sb.append("Arrays.asList(");
                for (int i = 0; i < no.getValoresIniciais().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(no.getValoresIniciais().get(i).aceitar(this));
                }
                sb.append(")");
            }
            sb.append(");");
        }
        return sb.toString();
    }

    @Override
    public String visitarAdicionar(Adicionar no) {
        return no.getLista().aceitar(this) + ".add(" + no.getValor().aceitar(this) + ");";
    }

    @Override
    public String visitarRemover(Remover no) {
        return no.getLista().aceitar(this) + ".remove(" + no.getIndice().aceitar(this) + ");";
    }

    @Override
    public String visitarTamanho(Tamanho no) {
        return no.getLista().aceitar(this) + ".size()";
    }

    @Override
    public String visitarAcessoIndice(AcessoIndice no) {
        return no.getObjeto().aceitar(this) + ".get(" + no.getIndice().aceitar(this) + ")";
    }

    // =====================================================================
    // VISITOR — TRATAMENTO DE ERROS
    // =====================================================================

    @Override
    public String visitarBlocoTentePegue(BlocoTentePegue no) {
        StringBuilder sb = new StringBuilder();
        sb.append("try {\n");

        indentacao++;
        for (No dec : no.getCorpoTente()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        indentacao--;

        sb.append(indent()).append("} catch (").append(mapearTipoErro(no.getTipoErro()))
          .append(" ").append(no.getNomeErro()).append(") {\n");

        indentacao++;
        for (No dec : no.getCorpoPegue()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");

        if (no.getCorpoFinalmente() != null && !no.getCorpoFinalmente().isEmpty()) {
            sb.append(" finally {\n");
            indentacao++;
            for (No dec : no.getCorpoFinalmente()) {
                sb.append(indent()).append(dec.aceitar(this)).append("\n");
            }
            indentacao--;
            sb.append(indent()).append("}");
        }

        return sb.toString();
    }

    @Override
    public String visitarLance(Lance no) {
        return "throw " + no.getExpressao().aceitar(this) + ";";
    }

    // =====================================================================
    // VISITOR — EXPRESSÕES
    // =====================================================================

    @Override
    public String visitarExpressaoBinaria(ExpressaoBinaria no) {
        String op = no.getOperador();

        // Potência: a ** b → Math.pow(a, b)
        if (op.equals("**")) {
            return "Math.pow(" + no.getEsquerda().aceitar(this) + ", " + no.getDireita().aceitar(this) + ")";
        }

        // Igualdade/desigualdade: usa Objects.equals pra funcionar com Strings
        if (op.equals("==")) {
            return "java.util.Objects.equals(" + no.getEsquerda().aceitar(this) + ", " + no.getDireita().aceitar(this) + ")";
        }
        if (op.equals("!=")) {
            return "!java.util.Objects.equals(" + no.getEsquerda().aceitar(this) + ", " + no.getDireita().aceitar(this) + ")";
        }

        return "(" + no.getEsquerda().aceitar(this) + " " + op + " " + no.getDireita().aceitar(this) + ")";
    }

    @Override
    public String visitarExpressaoUnaria(ExpressaoUnaria no) {
        if (no.isPrefixo()) {
            return no.getOperador() + no.getOperando().aceitar(this);
        } else {
            return no.getOperando().aceitar(this) + no.getOperador();
        }
    }

    @Override
    public String visitarExpressaoTernaria(ExpressaoTernaria no) {
        return "(" + no.getCondicao().aceitar(this) + " ? "
                + no.getValorSe().aceitar(this) + " : "
                + no.getValorSenao().aceitar(this) + ")";
    }

    // =====================================================================
    // VISITOR — LITERAIS
    // =====================================================================

    @Override
    public String visitarLiteralInteiro(LiteralInteiro no) {
        return String.valueOf(no.getValor());
    }

    @Override
    public String visitarLiteralDecimal(LiteralDecimal no) {
        return String.valueOf(no.getValor());
    }

    @Override
    public String visitarLiteralTexto(LiteralTexto no) {
        return "\"" + escapeJava(no.getValor()) + "\"";
    }

    @Override
    public String visitarLiteralCaracter(LiteralCaracter no) {
        return "'" + escapeJavaChar(no.getValor()) + "'";
    }

    @Override
    public String visitarLiteralBooleano(LiteralBooleano no) {
        return String.valueOf(no.getValor());
    }

    @Override
    public String visitarLiteralNulo(LiteralNulo no) {
        return "null";
    }

    @Override
    public String visitarIdentificador(Identificador no) {
        return no.getNome();
    }

    // =====================================================================
    // VISITOR — IMPORTS
    // =====================================================================

    @Override
    public String visitarImportar(Importar no) {
        // Mapeia imports BRZ para imports Java
        return "// import BRZ: " + no.getModulo() + (no.getItem() != null ? "." + no.getItem() : "");
    }

    // =====================================================================
    // VISITOR — CONTROLE DE FLUXO
    // =====================================================================

    @Override
    public String visitarPare(Pare no) {
        return "break;";
    }

    @Override
    public String visitarContinue(Continue no) {
        return "continue;";
    }

    // =====================================================================
    // VISITOR — CRUD
    // =====================================================================

    @Override
    public String visitarCrudCriar(CrudCriar no) {
        precisaArrayList = true;
        StringBuilder sb = new StringBuilder();
        String entidade = capitalizar(no.getEntidade());

        sb.append("{ // CRUD: criar ").append(no.getEntidade()).append("\n");
        indentacao++;
        sb.append(indent()).append("HashMap<String, Object> _registro = new HashMap<>();\n");

        for (CrudCampo campo : no.getCampos()) {
            sb.append(indent()).append("_registro.put(\"").append(campo.getNome()).append("\", ")
              .append(campo.getValor().aceitar(this)).append(");\n");
        }
        sb.append(indent()).append("_").append(no.getEntidade()).append("s.add(_registro);\n");
        sb.append(indent()).append("System.out.println(\"").append(entidade)
          .append(" criado com sucesso!\");\n");

        indentacao--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override
    public String visitarCrudListar(CrudListar no) {
        StringBuilder sb = new StringBuilder();
        sb.append("{ // CRUD: listar ").append(no.getEntidade()).append("s\n");
        indentacao++;
        sb.append(indent()).append("for (int _i = 0; _i < _").append(no.getEntidade())
          .append("s.size(); _i++) {\n");
        indentacao++;
        sb.append(indent()).append("System.out.println(\"[\" + _i + \"] \" + _")
          .append(no.getEntidade()).append("s.get(_i));\n");
        indentacao--;
        sb.append(indent()).append("}\n");
        indentacao--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override
    public String visitarCrudAtualizar(CrudAtualizar no) {
        StringBuilder sb = new StringBuilder();
        sb.append("{ // CRUD: atualizar ").append(no.getEntidade()).append("\n");
        indentacao++;
        sb.append(indent()).append("HashMap<String, Object> _registro = _")
          .append(no.getEntidade()).append("s.get(").append(no.getIndice().aceitar(this)).append(");\n");

        for (CrudCampo campo : no.getCampos()) {
            sb.append(indent()).append("_registro.put(\"").append(campo.getNome()).append("\", ")
              .append(campo.getValor().aceitar(this)).append(");\n");
        }
        sb.append(indent()).append("System.out.println(\"").append(capitalizar(no.getEntidade()))
          .append(" atualizado com sucesso!\");\n");

        indentacao--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override
    public String visitarCrudDeletar(CrudDeletar no) {
        StringBuilder sb = new StringBuilder();
        sb.append("_").append(no.getEntidade()).append("s.remove(")
          .append(no.getIndice().aceitar(this)).append(");\n");
        sb.append(indent()).append("System.out.println(\"").append(capitalizar(no.getEntidade()))
          .append(" deletado com sucesso!\");");
        return sb.toString();
    }

    // =====================================================================
    // MAPEAMENTO DE TIPOS
    // =====================================================================

    /**
     * Mapeia tipo BRZ para tipo Java.
     */
    private String mapearTipoParaJava(String tipoBrz) {
        if (tipoBrz == null) return "void";
        switch (tipoBrz.toLowerCase()) {
            case "numero": case "número": case "inteiro": case "int": return "int";
            case "decimal": case "double": return "double";
            case "texto": case "string": return "String";
            case "caracter": case "caractere": case "char": return "char";
            case "logico": case "lógico": case "booleano": case "boolean": return "boolean";
            case "vazio": case "void": return "void";
            case "lista": return "ArrayList";
            case "mapa": return "HashMap";
            case "var": return "var";
            default: return tipoBrz; // classe customizada
        }
    }

    /** Mapeia tipo BRZ para tipo Java wrapper (para generics) */
    private String mapearTipoWrapper(String tipoBrz) {
        switch (tipoBrz.toLowerCase()) {
            case "numero": case "int": return "Integer";
            case "decimal": case "double": return "Double";
            case "texto": case "string": return "String";
            case "caracter": case "char": return "Character";
            case "logico": case "boolean": return "Boolean";
            default: return tipoBrz;
        }
    }

    /** Mapeia tipo de erro BRZ para Java */
    private String mapearTipoErro(String tipoErro) {
        switch (tipoErro.toLowerCase()) {
            case "erro": case "exception": return "Exception";
            case "erroexecucao": case "runtimeexception": return "RuntimeException";
            case "erronumero": case "numberformatexception": return "NumberFormatException";
            case "erroindice": case "indexoutofboundsexception": return "IndexOutOfBoundsException";
            case "erronulo": case "nullpointerexception": return "NullPointerException";
            case "erroio": case "ioexception": return "java.io.IOException";
            default: return tipoErro;
        }
    }

    /** Mapeia modificador de acesso BRZ → Java */
    private String mapearModificador(String modificador) {
        switch (modificador.toLowerCase()) {
            case "publico": case "público": case "publica": case "pública": return "public";
            case "privado": case "privada": return "private";
            case "protegido": case "protegida": return "protected";
            default: return modificador;
        }
    }

    // =====================================================================
    // UTILITÁRIOS
    // =====================================================================

    private String indent() {
        return "    ".repeat(indentacao);
    }

    private String escapeJava(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r");
    }

    private String escapeJavaChar(char c) {
        switch (c) {
            case '\\': return "\\\\";
            case '\'': return "\\'";
            case '\n': return "\\n";
            case '\t': return "\\t";
            case '\r': return "\\r";
            default: return String.valueOf(c);
        }
    }

    private String capitalizar(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
