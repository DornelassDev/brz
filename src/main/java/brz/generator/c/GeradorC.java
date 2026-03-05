package brz.generator.c;

import brz.ast.No;
import brz.ast.No.*;

import java.util.List;

/**
 * GeradorC — Converte a AST BRZ em código-fonte C válido.
 * 
 * Mapeamento BRZ → C:
 *   numero   → int
 *   decimal  → double
 *   texto    → char*
 *   caracter → char
 *   logico   → int (0/1)
 *   lista    → array dinâmico (struct)
 *   vazio    → void
 * 
 * @author BRZ Team
 * @version 1.0.0
 * @since 2026-03-05
 */
public class GeradorC implements No.Visitante<String> {

    private int indentacao = 0;
    private boolean precisaStdio = true;
    private boolean precisaStdlib = false;
    private boolean precisaString = false;

    public String gerar(Programa programa) {
        StringBuilder corpo = new StringBuilder();
        StringBuilder funcoes = new StringBuilder();
        StringBuilder structs = new StringBuilder();

        indentacao = 1;
        for (No dec : programa.getDeclaracoes()) {
            if (dec instanceof DeclaracaoClasse) {
                structs.append(dec.aceitar(this)).append("\n\n");
            } else if (dec instanceof DeclaracaoFuncao) {
                indentacao = 0;
                funcoes.append(dec.aceitar(this)).append("\n\n");
                indentacao = 1;
            } else {
                corpo.append(indent()).append(dec.aceitar(this)).append("\n");
            }
        }

        StringBuilder resultado = new StringBuilder();
        resultado.append("/* Gerado automaticamente por BRZ Lang */\n");
        resultado.append("#include <stdio.h>\n");
        resultado.append("#include <stdlib.h>\n");
        resultado.append("#include <string.h>\n\n");

        if (structs.length() > 0) resultado.append(structs);
        if (funcoes.length() > 0) resultado.append(funcoes);

        resultado.append("int main() {\n");
        resultado.append(corpo);
        resultado.append("    return 0;\n");
        resultado.append("}\n");

        return resultado.toString();
    }

    @Override public String visitarPrograma(Programa no) { return gerar(no); }

    @Override public String visitarDeclaracaoVariavel(DeclaracaoVariavel no) {
        String tipoC = mapearTipoC(no.getTipo());
        if (no.getTipo().equals("texto")) {
            if (no.getValor() != null) {
                return "char* " + no.getNome() + " = " + no.getValor().aceitar(this) + ";";
            }
            return "char* " + no.getNome() + " = \"\";";
        }
        if (no.getValor() != null) {
            return tipoC + " " + no.getNome() + " = " + no.getValor().aceitar(this) + ";";
        }
        return tipoC + " " + no.getNome() + ";";
    }

    @Override public String visitarDeclaracaoConstante(DeclaracaoConstante no) {
        return "const " + mapearTipoC(no.getTipo()) + " " + no.getNome() + " = " + no.getValor().aceitar(this) + ";";
    }

    @Override public String visitarAtribuicao(Atribuicao no) {
        return no.getAlvo().aceitar(this) + " = " + no.getValor().aceitar(this) + ";";
    }

    @Override public String visitarAtribuicaoComposta(AtribuicaoComposta no) {
        return no.getAlvo().aceitar(this) + " " + no.getOperador() + " " + no.getValor().aceitar(this) + ";";
    }

    @Override public String visitarBlocoSe(BlocoSe no) {
        StringBuilder sb = new StringBuilder();
        sb.append("if (").append(no.getCondicao().aceitar(this)).append(") {\n");
        indentacao++;
        for (No dec : no.getCorpoSe()) sb.append(indent()).append(dec.aceitar(this)).append("\n");
        indentacao--;
        sb.append(indent()).append("}");

        for (BlocoSe s : no.getSenaoSe()) {
            sb.append(" else if (").append(s.getCondicao().aceitar(this)).append(") {\n");
            indentacao++;
            for (No dec : s.getCorpoSe()) sb.append(indent()).append(dec.aceitar(this)).append("\n");
            indentacao--;
            sb.append(indent()).append("}");
        }

        if (no.getCorpoSenao() != null && !no.getCorpoSenao().isEmpty()) {
            sb.append(" else {\n");
            indentacao++;
            for (No dec : no.getCorpoSenao()) sb.append(indent()).append(dec.aceitar(this)).append("\n");
            indentacao--;
            sb.append(indent()).append("}");
        }
        return sb.toString();
    }

    @Override public String visitarBlocoPara(BlocoPara no) {
        StringBuilder sb = new StringBuilder();
        sb.append("for (");
        if (no.getInicializacao() != null) {
            String init = no.getInicializacao().aceitar(this);
            if (init.endsWith(";")) init = init.substring(0, init.length() - 1);
            sb.append(init);
        }
        sb.append("; ");
        if (no.getCondicao() != null) sb.append(no.getCondicao().aceitar(this));
        sb.append("; ");
        if (no.getIncremento() != null) {
            String inc = no.getIncremento().aceitar(this);
            if (inc.endsWith(";")) inc = inc.substring(0, inc.length() - 1);
            sb.append(inc);
        }
        sb.append(") {\n");
        indentacao++;
        for (No dec : no.getCorpo()) sb.append(indent()).append(dec.aceitar(this)).append("\n");
        indentacao--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override public String visitarParaEmLista(ParaEmLista no) {
        // C não tem for-each; usamos um for com índice
        StringBuilder sb = new StringBuilder();
        String lista = no.getLista().aceitar(this);
        sb.append("for (int _i = 0; _i < sizeof(").append(lista).append(")/sizeof(")
          .append(lista).append("[0]); _i++) {\n");
        indentacao++;
        sb.append(indent()).append("var ").append(no.getVariavel()).append(" = ").append(lista).append("[_i];\n");
        for (No dec : no.getCorpo()) sb.append(indent()).append(dec.aceitar(this)).append("\n");
        indentacao--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override public String visitarBlocoEnquanto(BlocoEnquanto no) {
        StringBuilder sb = new StringBuilder();
        sb.append("while (").append(no.getCondicao().aceitar(this)).append(") {\n");
        indentacao++;
        for (No dec : no.getCorpo()) sb.append(indent()).append(dec.aceitar(this)).append("\n");
        indentacao--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override public String visitarBlocoFacaEnquanto(BlocoFacaEnquanto no) {
        StringBuilder sb = new StringBuilder();
        sb.append("do {\n");
        indentacao++;
        for (No dec : no.getCorpo()) sb.append(indent()).append(dec.aceitar(this)).append("\n");
        indentacao--;
        sb.append(indent()).append("} while (").append(no.getCondicao().aceitar(this)).append(");");
        return sb.toString();
    }

    @Override public String visitarBlocoEscolha(BlocoEscolha no) {
        StringBuilder sb = new StringBuilder();
        sb.append("switch (").append(no.getExpressao().aceitar(this)).append(") {\n");
        indentacao++;
        for (CasoEscolha c : no.getCasos()) {
            sb.append(indent()).append("case ").append(c.getValor().aceitar(this)).append(":\n");
            indentacao++;
            for (No dec : c.getCorpo()) sb.append(indent()).append(dec.aceitar(this)).append("\n");
            indentacao--;
        }
        if (no.getCorpoPadrao() != null) {
            sb.append(indent()).append("default:\n");
            indentacao++;
            for (No dec : no.getCorpoPadrao()) sb.append(indent()).append(dec.aceitar(this)).append("\n");
            indentacao--;
        }
        indentacao--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override public String visitarDeclaracaoFuncao(DeclaracaoFuncao no) {
        StringBuilder sb = new StringBuilder();
        sb.append(mapearTipoC(no.getTipoRetorno())).append(" ").append(no.getNome()).append("(");
        for (int i = 0; i < no.getParametros().size(); i++) {
            if (i > 0) sb.append(", ");
            Parametro p = no.getParametros().get(i);
            sb.append(mapearTipoC(p.getTipo())).append(" ").append(p.getNome());
        }
        sb.append(") {\n");
        indentacao++;
        for (No dec : no.getCorpo()) sb.append(indent()).append(dec.aceitar(this)).append("\n");
        indentacao--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override public String visitarChamadaFuncao(ChamadaFuncao no) {
        StringBuilder sb = new StringBuilder();
        sb.append(no.getNome()).append("(");
        for (int i = 0; i < no.getArgumentos().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(no.getArgumentos().get(i).aceitar(this));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override public String visitarRetorne(Retorne no) {
        if (no.getValor() != null) return "return " + no.getValor().aceitar(this) + ";";
        return "return;";
    }

    @Override public String visitarDeclaracaoClasse(DeclaracaoClasse no) {
        StringBuilder sb = new StringBuilder();
        sb.append("typedef struct {\n");
        indentacao++;
        for (DeclaracaoVariavel attr : no.getAtributos()) {
            sb.append(indent()).append(attr.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append("} ").append(no.getNome()).append(";\n");

        // Gera funções como funções que recebem ponteiro para a struct
        for (DeclaracaoFuncao metodo : no.getMetodos()) {
            sb.append("\n").append(mapearTipoC(metodo.getTipoRetorno())).append(" ")
              .append(no.getNome()).append("_").append(metodo.getNome()).append("(")
              .append(no.getNome()).append("* self");
            for (Parametro p : metodo.getParametros()) {
                sb.append(", ").append(mapearTipoC(p.getTipo())).append(" ").append(p.getNome());
            }
            sb.append(") {\n");
            indentacao++;
            for (No dec : metodo.getCorpo()) sb.append(indent()).append(dec.aceitar(this)).append("\n");
            indentacao--;
            sb.append("}\n");
        }
        return sb.toString();
    }

    @Override public String visitarNovoObjeto(NovoObjeto no) {
        return "(" + no.getClasse() + "){" + gerarArgs(no.getArgumentos()) + "}";
    }

    @Override public String visitarAcessoMembro(AcessoMembro no) {
        String obj = no.getObjeto().aceitar(this);
        if (no.isChamadaMetodo()) {
            return obj + "." + no.getMembro() + "(" + gerarArgs(no.getArgumentos()) + ")";
        }
        return obj + "." + no.getMembro();
    }

    @Override public String visitarMostrar(Mostrar no) {
        if (no.getArgumentos().size() == 1) {
            No arg = no.getArgumentos().get(0);
            if (arg instanceof LiteralTexto) {
                return "printf(\"%s\\n\", " + arg.aceitar(this) + ");";
            }
            if (arg instanceof LiteralInteiro || arg instanceof Identificador) {
                return "printf(\"%d\\n\", " + arg.aceitar(this) + ");";
            }
            if (arg instanceof LiteralDecimal) {
                return "printf(\"%f\\n\", " + arg.aceitar(this) + ");";
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("printf(\"");
        for (int i = 0; i < no.getArgumentos().size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append("%s");
        }
        sb.append("\\n\"");
        for (No arg : no.getArgumentos()) {
            sb.append(", ").append(arg.aceitar(this));
        }
        sb.append(");");
        return sb.toString();
    }

    @Override public String visitarLerTexto(LerTexto no) {
        return "fgets(_buffer, sizeof(_buffer), stdin)";
    }

    @Override public String visitarLerNumero(LerNumero no) {
        return "atoi(fgets(_buffer, sizeof(_buffer), stdin))";
    }

    @Override public String visitarLerDecimal(LerDecimal no) {
        return "atof(fgets(_buffer, sizeof(_buffer), stdin))";
    }

    @Override public String visitarDeclaracaoLista(DeclaracaoLista no) {
        String tipoC = mapearTipoC(no.getTipoElemento());
        if (no.getValoresIniciais().isEmpty()) {
            return tipoC + " " + no.getNome() + "[100]; int " + no.getNome() + "_len = 0;";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(tipoC).append(" ").append(no.getNome()).append("[] = {");
        for (int i = 0; i < no.getValoresIniciais().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(no.getValoresIniciais().get(i).aceitar(this));
        }
        sb.append("};");
        sb.append(" int ").append(no.getNome()).append("_len = ").append(no.getValoresIniciais().size()).append(";");
        return sb.toString();
    }

    @Override public String visitarAdicionar(Adicionar no) {
        return no.getLista().aceitar(this) + "[" + no.getLista().aceitar(this) + "_len++] = " + no.getValor().aceitar(this) + ";";
    }

    @Override public String visitarRemover(Remover no) {
        return "/* remover: operação complexa em C */";
    }

    @Override public String visitarTamanho(Tamanho no) {
        return no.getLista().aceitar(this) + "_len";
    }

    @Override public String visitarAcessoIndice(AcessoIndice no) {
        return no.getObjeto().aceitar(this) + "[" + no.getIndice().aceitar(this) + "]";
    }

    @Override public String visitarBlocoTentePegue(BlocoTentePegue no) {
        // C não tem try/catch; usamos comentário + if
        StringBuilder sb = new StringBuilder();
        sb.append("/* BRZ: tente/pegue (C não suporta nativamente) */\n");
        indentacao++;
        for (No dec : no.getCorpoTente()) sb.append(indent()).append(dec.aceitar(this)).append("\n");
        indentacao--;
        return sb.toString().stripTrailing();
    }

    @Override public String visitarLance(Lance no) {
        return "fprintf(stderr, \"Erro: %s\\n\", " + no.getExpressao().aceitar(this) + "); exit(1);";
    }

    @Override public String visitarExpressaoBinaria(ExpressaoBinaria no) {
        String op = no.getOperador();
        if (op.equals("**")) {
            return "pow(" + no.getEsquerda().aceitar(this) + ", " + no.getDireita().aceitar(this) + ")";
        }
        return "(" + no.getEsquerda().aceitar(this) + " " + op + " " + no.getDireita().aceitar(this) + ")";
    }

    @Override public String visitarExpressaoUnaria(ExpressaoUnaria no) {
        if (no.isPrefixo()) return no.getOperador() + no.getOperando().aceitar(this);
        return no.getOperando().aceitar(this) + no.getOperador();
    }

    @Override public String visitarExpressaoTernaria(ExpressaoTernaria no) {
        return "(" + no.getCondicao().aceitar(this) + " ? " + no.getValorSe().aceitar(this)
                + " : " + no.getValorSenao().aceitar(this) + ")";
    }

    @Override public String visitarLiteralInteiro(LiteralInteiro no) { return String.valueOf(no.getValor()); }
    @Override public String visitarLiteralDecimal(LiteralDecimal no) { return String.valueOf(no.getValor()); }
    @Override public String visitarLiteralTexto(LiteralTexto no) { return "\"" + no.getValor().replace("\"", "\\\"") + "\""; }
    @Override public String visitarLiteralCaracter(LiteralCaracter no) { return "'" + no.getValor() + "'"; }
    @Override public String visitarLiteralBooleano(LiteralBooleano no) { return no.getValor() ? "1" : "0"; }
    @Override public String visitarLiteralNulo(LiteralNulo no) { return "NULL"; }
    @Override public String visitarIdentificador(Identificador no) { return no.getNome(); }
    @Override public String visitarImportar(Importar no) { return "/* import: " + no.getModulo() + " */"; }
    @Override public String visitarPare(Pare no) { return "break;"; }
    @Override public String visitarContinue(Continue no) { return "continue;"; }

    @Override public String visitarCrudCriar(CrudCriar no) { return "/* CRUD criar: melhor usar Java/Python */"; }
    @Override public String visitarCrudListar(CrudListar no) { return "/* CRUD listar: melhor usar Java/Python */"; }
    @Override public String visitarCrudAtualizar(CrudAtualizar no) { return "/* CRUD atualizar: melhor usar Java/Python */"; }
    @Override public String visitarCrudDeletar(CrudDeletar no) { return "/* CRUD deletar: melhor usar Java/Python */"; }

    private String indent() { return "    ".repeat(indentacao); }

    private String mapearTipoC(String tipo) {
        if (tipo == null) return "void";
        switch (tipo.toLowerCase()) {
            case "numero": case "int": return "int";
            case "decimal": case "double": return "double";
            case "texto": case "string": return "char*";
            case "caracter": case "char": return "char";
            case "logico": case "boolean": return "int";
            case "vazio": case "void": return "void";
            default: return tipo;
        }
    }

    private String gerarArgs(List<No> args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(args.get(i).aceitar(this));
        }
        return sb.toString();
    }
}
