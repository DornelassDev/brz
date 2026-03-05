package brz.generator.python;

import brz.ast.No;
import brz.ast.No.*;

import java.util.List;

/**
 * GeradorPython — Converte a AST BRZ em código-fonte Python 3 válido.
 * 
 * Mapeamento BRZ → Python:
 *   numero   → int
 *   decimal  → float
 *   texto    → str
 *   caracter → str (len=1)
 *   logico   → bool
 *   lista    → list
 *   mapa     → dict
 * 
 * @author BRZ Team
 * @version 1.0.0
 * @since 2026-03-05
 */
public class GeradorPython implements No.Visitante<String> {

    private int indentacao = 0;

    /**
     * Gera código Python completo a partir de um programa BRZ.
     */
    public String gerar(Programa programa) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/usr/bin/env python3\n");
        sb.append("# -*- coding: utf-8 -*-\n");
        sb.append("# Gerado automaticamente por BRZ Lang\n\n");

        // Gera classes e funções primeiro
        StringBuilder classesEFuncoes = new StringBuilder();
        StringBuilder principal = new StringBuilder();

        for (No declaracao : programa.getDeclaracoes()) {
            if (declaracao instanceof DeclaracaoClasse || declaracao instanceof DeclaracaoFuncao) {
                classesEFuncoes.append(declaracao.aceitar(this)).append("\n\n");
            } else {
                principal.append(declaracao.aceitar(this)).append("\n");
            }
        }

        sb.append(classesEFuncoes);

        // Main
        if (principal.length() > 0) {
            sb.append("\nif __name__ == \"__main__\":\n");
            indentacao = 1;
            // Re-gera o principal com indentação correta
            for (No declaracao : programa.getDeclaracoes()) {
                if (!(declaracao instanceof DeclaracaoClasse) && !(declaracao instanceof DeclaracaoFuncao)) {
                    sb.append(indent()).append(declaracao.aceitar(this)).append("\n");
                }
            }
            indentacao = 0;
        }

        return sb.toString();
    }

    @Override public String visitarPrograma(Programa no) {
        return gerar(no);
    }

    @Override public String visitarDeclaracaoVariavel(DeclaracaoVariavel no) {
        if (no.getValor() != null) {
            return no.getNome() + " = " + no.getValor().aceitar(this);
        }
        return no.getNome() + " = " + valorPadraoPython(no.getTipo());
    }

    @Override public String visitarDeclaracaoConstante(DeclaracaoConstante no) {
        return no.getNome().toUpperCase() + " = " + no.getValor().aceitar(this) + "  # constante";
    }

    @Override public String visitarAtribuicao(Atribuicao no) {
        return no.getAlvo().aceitar(this) + " = " + no.getValor().aceitar(this);
    }

    @Override public String visitarAtribuicaoComposta(AtribuicaoComposta no) {
        return no.getAlvo().aceitar(this) + " " + no.getOperador() + " " + no.getValor().aceitar(this);
    }

    @Override public String visitarBlocoSe(BlocoSe no) {
        StringBuilder sb = new StringBuilder();
        sb.append("if ").append(no.getCondicao().aceitar(this)).append(":\n");
        indentacao++;
        for (No dec : no.getCorpoSe()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        if (no.getCorpoSe().isEmpty()) sb.append(indent()).append("pass\n");
        indentacao--;

        for (BlocoSe senaoSe : no.getSenaoSe()) {
            sb.append(indent()).append("elif ").append(senaoSe.getCondicao().aceitar(this)).append(":\n");
            indentacao++;
            for (No dec : senaoSe.getCorpoSe()) {
                sb.append(indent()).append(dec.aceitar(this)).append("\n");
            }
            if (senaoSe.getCorpoSe().isEmpty()) sb.append(indent()).append("pass\n");
            indentacao--;
        }

        if (no.getCorpoSenao() != null && !no.getCorpoSenao().isEmpty()) {
            sb.append(indent()).append("else:\n");
            indentacao++;
            for (No dec : no.getCorpoSenao()) {
                sb.append(indent()).append(dec.aceitar(this)).append("\n");
            }
            indentacao--;
        }

        return sb.toString().stripTrailing();
    }

    @Override public String visitarBlocoPara(BlocoPara no) {
        StringBuilder sb = new StringBuilder();
        // Simplificação: converte para while em Python
        if (no.getInicializacao() != null) {
            sb.append(no.getInicializacao().aceitar(this)).append("\n").append(indent());
        }
        sb.append("while ");
        if (no.getCondicao() != null) {
            sb.append(no.getCondicao().aceitar(this));
        } else {
            sb.append("True");
        }
        sb.append(":\n");
        indentacao++;
        for (No dec : no.getCorpo()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        if (no.getIncremento() != null) {
            sb.append(indent()).append(no.getIncremento().aceitar(this)).append("\n");
        }
        indentacao--;
        return sb.toString().stripTrailing();
    }

    @Override public String visitarParaEmLista(ParaEmLista no) {
        StringBuilder sb = new StringBuilder();
        sb.append("for ").append(no.getVariavel()).append(" in ").append(no.getLista().aceitar(this)).append(":\n");
        indentacao++;
        for (No dec : no.getCorpo()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        if (no.getCorpo().isEmpty()) sb.append(indent()).append("pass\n");
        indentacao--;
        return sb.toString().stripTrailing();
    }

    @Override public String visitarBlocoEnquanto(BlocoEnquanto no) {
        StringBuilder sb = new StringBuilder();
        sb.append("while ").append(no.getCondicao().aceitar(this)).append(":\n");
        indentacao++;
        for (No dec : no.getCorpo()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        if (no.getCorpo().isEmpty()) sb.append(indent()).append("pass\n");
        indentacao--;
        return sb.toString().stripTrailing();
    }

    @Override public String visitarBlocoFacaEnquanto(BlocoFacaEnquanto no) {
        StringBuilder sb = new StringBuilder();
        sb.append("while True:\n");
        indentacao++;
        for (No dec : no.getCorpo()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        sb.append(indent()).append("if not (").append(no.getCondicao().aceitar(this)).append("):\n");
        indentacao++;
        sb.append(indent()).append("break\n");
        indentacao -= 2;
        return sb.toString().stripTrailing();
    }

    @Override public String visitarBlocoEscolha(BlocoEscolha no) {
        StringBuilder sb = new StringBuilder();
        sb.append("match ").append(no.getExpressao().aceitar(this)).append(":\n");
        indentacao++;
        for (CasoEscolha caso : no.getCasos()) {
            sb.append(indent()).append("case ").append(caso.getValor().aceitar(this)).append(":\n");
            indentacao++;
            for (No dec : caso.getCorpo()) {
                sb.append(indent()).append(dec.aceitar(this)).append("\n");
            }
            if (caso.getCorpo().isEmpty()) sb.append(indent()).append("pass\n");
            indentacao--;
        }
        if (no.getCorpoPadrao() != null) {
            sb.append(indent()).append("case _:\n");
            indentacao++;
            for (No dec : no.getCorpoPadrao()) {
                sb.append(indent()).append(dec.aceitar(this)).append("\n");
            }
            indentacao--;
        }
        indentacao--;
        return sb.toString().stripTrailing();
    }

    @Override public String visitarDeclaracaoFuncao(DeclaracaoFuncao no) {
        StringBuilder sb = new StringBuilder();
        sb.append("def ").append(no.getNome()).append("(");
        for (int i = 0; i < no.getParametros().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(no.getParametros().get(i).getNome());
        }
        sb.append("):\n");
        indentacao++;
        if (no.getCorpo().isEmpty()) {
            sb.append(indent()).append("pass\n");
        } else {
            for (No dec : no.getCorpo()) {
                sb.append(indent()).append(dec.aceitar(this)).append("\n");
            }
        }
        indentacao--;
        return sb.toString().stripTrailing();
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
        if (no.getValor() != null) return "return " + no.getValor().aceitar(this);
        return "return";
    }

    @Override public String visitarDeclaracaoClasse(DeclaracaoClasse no) {
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(no.getNome());
        if (no.getClassePai() != null) {
            sb.append("(").append(no.getClassePai()).append(")");
        }
        sb.append(":\n");
        indentacao++;

        // Construtor → __init__
        if (no.getConstrutor() != null || !no.getAtributos().isEmpty()) {
            sb.append(indent()).append("def __init__(self");
            if (no.getConstrutor() != null) {
                for (Parametro p : no.getConstrutor().getParametros()) {
                    sb.append(", ").append(p.getNome());
                }
            }
            sb.append("):\n");
            indentacao++;

            for (DeclaracaoVariavel attr : no.getAtributos()) {
                sb.append(indent()).append("self.").append(attr.getNome()).append(" = ");
                if (attr.getValor() != null) {
                    sb.append(attr.getValor().aceitar(this));
                } else {
                    sb.append("None");
                }
                sb.append("\n");
            }

            if (no.getConstrutor() != null) {
                for (No dec : no.getConstrutor().getCorpo()) {
                    sb.append(indent()).append(dec.aceitar(this)).append("\n");
                }
            }
            indentacao--;
            sb.append("\n");
        }

        // Métodos
        for (DeclaracaoFuncao metodo : no.getMetodos()) {
            sb.append(indent()).append("def ").append(metodo.getNome()).append("(self");
            for (Parametro p : metodo.getParametros()) {
                sb.append(", ").append(p.getNome());
            }
            sb.append("):\n");
            indentacao++;
            if (metodo.getCorpo().isEmpty()) {
                sb.append(indent()).append("pass\n");
            } else {
                for (No dec : metodo.getCorpo()) {
                    sb.append(indent()).append(dec.aceitar(this)).append("\n");
                }
            }
            indentacao--;
            sb.append("\n");
        }

        if (no.getAtributos().isEmpty() && no.getMetodos().isEmpty() && no.getConstrutor() == null) {
            sb.append(indent()).append("pass\n");
        }

        indentacao--;
        return sb.toString().stripTrailing();
    }

    @Override public String visitarNovoObjeto(NovoObjeto no) {
        StringBuilder sb = new StringBuilder();
        sb.append(no.getClasse()).append("(");
        for (int i = 0; i < no.getArgumentos().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(no.getArgumentos().get(i).aceitar(this));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override public String visitarAcessoMembro(AcessoMembro no) {
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

    @Override public String visitarMostrar(Mostrar no) {
        StringBuilder sb = new StringBuilder();
        sb.append("print(");
        for (int i = 0; i < no.getArgumentos().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(no.getArgumentos().get(i).aceitar(this));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override public String visitarLerTexto(LerTexto no) {
        if (no.getPrompt() != null) return "input(" + no.getPrompt().aceitar(this) + ")";
        return "input()";
    }

    @Override public String visitarLerNumero(LerNumero no) {
        if (no.getPrompt() != null) return "int(input(" + no.getPrompt().aceitar(this) + "))";
        return "int(input())";
    }

    @Override public String visitarLerDecimal(LerDecimal no) {
        if (no.getPrompt() != null) return "float(input(" + no.getPrompt().aceitar(this) + "))";
        return "float(input())";
    }

    @Override public String visitarDeclaracaoLista(DeclaracaoLista no) {
        StringBuilder sb = new StringBuilder();
        if (no.getNome().equals("_anonimo")) {
            sb.append("[");
            for (int i = 0; i < no.getValoresIniciais().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(no.getValoresIniciais().get(i).aceitar(this));
            }
            sb.append("]");
        } else {
            sb.append(no.getNome()).append(" = [");
            for (int i = 0; i < no.getValoresIniciais().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(no.getValoresIniciais().get(i).aceitar(this));
            }
            sb.append("]");
        }
        return sb.toString();
    }

    @Override public String visitarAdicionar(Adicionar no) {
        return no.getLista().aceitar(this) + ".append(" + no.getValor().aceitar(this) + ")";
    }

    @Override public String visitarRemover(Remover no) {
        return no.getLista().aceitar(this) + ".pop(" + no.getIndice().aceitar(this) + ")";
    }

    @Override public String visitarTamanho(Tamanho no) {
        return "len(" + no.getLista().aceitar(this) + ")";
    }

    @Override public String visitarAcessoIndice(AcessoIndice no) {
        return no.getObjeto().aceitar(this) + "[" + no.getIndice().aceitar(this) + "]";
    }

    @Override public String visitarBlocoTentePegue(BlocoTentePegue no) {
        StringBuilder sb = new StringBuilder();
        sb.append("try:\n");
        indentacao++;
        for (No dec : no.getCorpoTente()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        if (no.getCorpoTente().isEmpty()) sb.append(indent()).append("pass\n");
        indentacao--;
        sb.append(indent()).append("except ").append(mapearTipoErroPython(no.getTipoErro()))
          .append(" as ").append(no.getNomeErro()).append(":\n");
        indentacao++;
        for (No dec : no.getCorpoPegue()) {
            sb.append(indent()).append(dec.aceitar(this)).append("\n");
        }
        if (no.getCorpoPegue().isEmpty()) sb.append(indent()).append("pass\n");
        indentacao--;

        if (no.getCorpoFinalmente() != null && !no.getCorpoFinalmente().isEmpty()) {
            sb.append(indent()).append("finally:\n");
            indentacao++;
            for (No dec : no.getCorpoFinalmente()) {
                sb.append(indent()).append(dec.aceitar(this)).append("\n");
            }
            indentacao--;
        }
        return sb.toString().stripTrailing();
    }

    @Override public String visitarLance(Lance no) {
        return "raise " + no.getExpressao().aceitar(this);
    }

    @Override public String visitarExpressaoBinaria(ExpressaoBinaria no) {
        String op = no.getOperador();
        if (op.equals("**")) {
            return "(" + no.getEsquerda().aceitar(this) + " ** " + no.getDireita().aceitar(this) + ")";
        }
        if (op.equals("&&")) op = "and";
        if (op.equals("||")) op = "or";
        return "(" + no.getEsquerda().aceitar(this) + " " + op + " " + no.getDireita().aceitar(this) + ")";
    }

    @Override public String visitarExpressaoUnaria(ExpressaoUnaria no) {
        String op = no.getOperador();
        if (op.equals("!")) op = "not ";
        if (no.isPrefixo()) {
            if (op.equals("++")) return no.getOperando().aceitar(this) + " + 1";
            if (op.equals("--")) return no.getOperando().aceitar(this) + " - 1";
            return op + no.getOperando().aceitar(this);
        } else {
            if (op.equals("++")) return no.getOperando().aceitar(this) + " + 1";
            if (op.equals("--")) return no.getOperando().aceitar(this) + " - 1";
            return no.getOperando().aceitar(this) + op;
        }
    }

    @Override public String visitarExpressaoTernaria(ExpressaoTernaria no) {
        return no.getValorSe().aceitar(this) + " if " + no.getCondicao().aceitar(this)
                + " else " + no.getValorSenao().aceitar(this);
    }

    @Override public String visitarLiteralInteiro(LiteralInteiro no) { return String.valueOf(no.getValor()); }
    @Override public String visitarLiteralDecimal(LiteralDecimal no) { return String.valueOf(no.getValor()); }
    @Override public String visitarLiteralTexto(LiteralTexto no) { return "\"" + no.getValor().replace("\"", "\\\"") + "\""; }
    @Override public String visitarLiteralCaracter(LiteralCaracter no) { return "\"" + no.getValor() + "\""; }
    @Override public String visitarLiteralBooleano(LiteralBooleano no) { return no.getValor() ? "True" : "False"; }
    @Override public String visitarLiteralNulo(LiteralNulo no) { return "None"; }
    @Override public String visitarIdentificador(Identificador no) {
        if (no.getNome().equals("this")) return "self";
        return no.getNome();
    }

    @Override public String visitarImportar(Importar no) {
        if (no.getItem() != null) {
            String result = "from " + no.getModulo() + " import " + no.getItem();
            if (no.getAlias() != null) result += " as " + no.getAlias();
            return result;
        }
        String result = "import " + no.getModulo();
        if (no.getAlias() != null) result += " as " + no.getAlias();
        return result;
    }

    @Override public String visitarPare(Pare no) { return "break"; }
    @Override public String visitarContinue(Continue no) { return "continue"; }

    @Override public String visitarCrudCriar(CrudCriar no) {
        StringBuilder sb = new StringBuilder();
        sb.append("_registro = {");
        for (int i = 0; i < no.getCampos().size(); i++) {
            CrudCampo c = no.getCampos().get(i);
            if (i > 0) sb.append(", ");
            sb.append("\"").append(c.getNome()).append("\": ").append(c.getValor().aceitar(this));
        }
        sb.append("}\n");
        sb.append(indent()).append("_").append(no.getEntidade()).append("s.append(_registro)\n");
        sb.append(indent()).append("print(\"").append(no.getEntidade()).append(" criado com sucesso!\")");
        return sb.toString();
    }

    @Override public String visitarCrudListar(CrudListar no) {
        StringBuilder sb = new StringBuilder();
        sb.append("for _i, _r in enumerate(_").append(no.getEntidade()).append("s):\n");
        indentacao++;
        sb.append(indent()).append("print(f\"[{_i}] {_r}\")");
        indentacao--;
        return sb.toString();
    }

    @Override public String visitarCrudAtualizar(CrudAtualizar no) {
        StringBuilder sb = new StringBuilder();
        sb.append("_registro = _").append(no.getEntidade()).append("s[").append(no.getIndice().aceitar(this)).append("]\n");
        for (CrudCampo c : no.getCampos()) {
            sb.append(indent()).append("_registro[\"").append(c.getNome()).append("\"] = ").append(c.getValor().aceitar(this)).append("\n");
        }
        sb.append(indent()).append("print(\"").append(no.getEntidade()).append(" atualizado com sucesso!\")");
        return sb.toString();
    }

    @Override public String visitarCrudDeletar(CrudDeletar no) {
        return "_" + no.getEntidade() + "s.pop(" + no.getIndice().aceitar(this) + ")\n"
                + indent() + "print(\"" + no.getEntidade() + " deletado com sucesso!\")";
    }

    // =====================================================================
    // UTILITÁRIOS
    // =====================================================================

    private String indent() { return "    ".repeat(indentacao); }

    private String valorPadraoPython(String tipo) {
        switch (tipo) {
            case "numero": return "0";
            case "decimal": return "0.0";
            case "texto": return "\"\"";
            case "caracter": return "\"\"";
            case "logico": return "False";
            default: return "None";
        }
    }

    private String mapearTipoErroPython(String tipo) {
        switch (tipo.toLowerCase()) {
            case "erro": case "exception": return "Exception";
            case "erroexecucao": case "runtimeexception": return "RuntimeError";
            case "erronumero": return "ValueError";
            case "erroindice": return "IndexError";
            case "erronulo": return "TypeError";
            case "erroio": return "IOError";
            default: return tipo;
        }
    }
}
