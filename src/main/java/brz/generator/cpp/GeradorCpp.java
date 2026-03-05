package brz.generator.cpp;

import brz.ast.No;

import java.util.*;

/**
 * GeradorCpp — Gera código C++ a partir do AST BRZ.
 *
 * Características:
 *   - Usa std::string, std::vector, std::map
 *   - iostream para I/O
 *   - Classes com construtores e métodos
 *   - try/catch com std::exception
 *   - Mais recursos que o gerador C puro
 *
 * @author BRZ Team
 * @version 1.0.0
 */
public class GeradorCpp implements No.Visitante<String> {

    private int indentacao = 0;
    private StringBuilder saida = new StringBuilder();
    private Set<String> includes = new LinkedHashSet<>();
    private List<String> funcoesGlobais = new ArrayList<>();
    private List<String> classesGeradas = new ArrayList<>();

    /**
     * Gera código C++ a partir de um programa BRZ.
     */
    public String gerar(No.Programa programa) {
        // Primeira passada: coleta funções/classes
        List<No> corpoMain = new ArrayList<>();

        for (No declaracao : programa.declaracoes) {
            if (declaracao instanceof No.DeclaracaoFuncao) {
                funcoesGlobais.add(declaracao.aceitar(this));
            } else if (declaracao instanceof No.DeclaracaoClasse) {
                classesGeradas.add(declaracao.aceitar(this));
            } else {
                corpoMain.add(declaracao);
            }
        }

        // Monta o arquivo
        StringBuilder arquivo = new StringBuilder();
        arquivo.append("// Gerado por BRZ Lang v1.0.0\n\n");

        // Includes mínimos
        includes.add("<iostream>");
        includes.add("<string>");

        // Processa corpo do main para detectar includes
        List<String> linhasMain = new ArrayList<>();
        for (No decl : corpoMain) {
            linhasMain.add(decl.aceitar(this));
        }

        // Escreve includes
        for (String inc : includes) {
            arquivo.append("#include ").append(inc).append("\n");
        }
        arquivo.append("\nusing namespace std;\n\n");

        // Classes
        for (String classe : classesGeradas) {
            arquivo.append(classe).append("\n\n");
        }

        // Funções globais
        for (String func : funcoesGlobais) {
            arquivo.append(func).append("\n\n");
        }

        // main
        arquivo.append("int main() {\n");
        for (String linha : linhasMain) {
            if (linha != null && !linha.isEmpty()) {
                arquivo.append("    ").append(linha).append("\n");
            }
        }
        arquivo.append("    return 0;\n");
        arquivo.append("}\n");

        return arquivo.toString();
    }

    // ==================================================================
    // UTILITÁRIOS
    // ==================================================================

    private String indent() {
        return "    ".repeat(indentacao);
    }

    private String mapearTipo(String tipoBrz) {
        if (tipoBrz == null) return "auto";
        switch (tipoBrz) {
            case "inteiro":
            case "numero": return "int";
            case "decimal": return "double";
            case "texto": return "string";
            case "logico": return "bool";
            case "caractere": return "char";
            case "vazio": return "void";
            case "longo": return "long long";
            default: return tipoBrz;
        }
    }

    // ==================================================================
    // VISITANTES
    // ==================================================================

    @Override
    public String visitarPrograma(No.Programa no) {
        return gerar(no);
    }

    @Override
    public String visitarDeclaracaoVariavel(No.DeclaracaoVariavel no) {
        String tipo = mapearTipo(no.tipo);
        if (no.valor != null) {
            return tipo + " " + no.nome + " = " + no.valor.aceitar(this) + ";";
        }
        return tipo + " " + no.nome + ";";
    }

    @Override
    public String visitarDeclaracaoConstante(No.DeclaracaoConstante no) {
        String tipo = mapearTipo(no.tipo);
        return "const " + tipo + " " + no.nome + " = " + no.valor.aceitar(this) + ";";
    }

    @Override
    public String visitarAtribuicao(No.Atribuicao no) {
        return no.nome + " = " + no.valor.aceitar(this) + ";";
    }

    @Override
    public String visitarAtribuicaoComposta(No.AtribuicaoComposta no) {
        return no.nome + " " + no.operador + " " + no.valor.aceitar(this) + ";";
    }

    @Override
    public String visitarBlocoSe(No.BlocoSe no) {
        StringBuilder sb = new StringBuilder();
        sb.append("if (").append(no.condicao.aceitar(this)).append(") {\n");
        indentacao++;
        for (No stmt : no.corpoSe) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");

        if (no.blocosSenaoSe != null) {
            for (No.BlocoSe senaoSe : no.blocosSenaoSe) {
                sb.append(" else if (").append(senaoSe.condicao.aceitar(this)).append(") {\n");
                indentacao++;
                for (No stmt : senaoSe.corpoSe) {
                    sb.append(indent()).append(stmt.aceitar(this)).append("\n");
                }
                indentacao--;
                sb.append(indent()).append("}");
            }
        }

        if (no.corpoSenao != null && !no.corpoSenao.isEmpty()) {
            sb.append(" else {\n");
            indentacao++;
            for (No stmt : no.corpoSenao) {
                sb.append(indent()).append(stmt.aceitar(this)).append("\n");
            }
            indentacao--;
            sb.append(indent()).append("}");
        }
        return sb.toString();
    }

    @Override
    public String visitarBlocoPara(No.BlocoPara no) {
        String init = no.inicializacao != null ? no.inicializacao.aceitar(this) : ";";
        if (init.endsWith(";")) init = init.substring(0, init.length() - 1);
        String cond = no.condicao != null ? no.condicao.aceitar(this) : "";
        String inc = no.incremento != null ? no.incremento.aceitar(this).replace(";", "") : "";

        StringBuilder sb = new StringBuilder();
        sb.append("for (").append(init).append("; ").append(cond).append("; ").append(inc).append(") {\n");
        indentacao++;
        for (No stmt : no.corpo) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override
    public String visitarParaEmLista(No.ParaEmLista no) {
        StringBuilder sb = new StringBuilder();
        sb.append("for (auto& ").append(no.variavel).append(" : ").append(no.lista.aceitar(this)).append(") {\n");
        indentacao++;
        for (No stmt : no.corpo) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override
    public String visitarBlocoEnquanto(No.BlocoEnquanto no) {
        StringBuilder sb = new StringBuilder();
        sb.append("while (").append(no.condicao.aceitar(this)).append(") {\n");
        indentacao++;
        for (No stmt : no.corpo) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override
    public String visitarBlocoFacaEnquanto(No.BlocoFacaEnquanto no) {
        StringBuilder sb = new StringBuilder();
        sb.append("do {\n");
        indentacao++;
        for (No stmt : no.corpo) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("} while (").append(no.condicao.aceitar(this)).append(");");
        return sb.toString();
    }

    @Override
    public String visitarBlocoEscolha(No.BlocoEscolha no) {
        StringBuilder sb = new StringBuilder();
        // C++ switch requires integral/enum type; we use if-else chain for safety
        String expr = no.expressao.aceitar(this);
        boolean primeiro = true;
        for (No.BlocoEscolha.Caso caso : no.casos) {
            if (primeiro) {
                sb.append("if (").append(expr).append(" == ").append(caso.valor.aceitar(this)).append(") {\n");
                primeiro = false;
            } else {
                sb.append(" else if (").append(expr).append(" == ").append(caso.valor.aceitar(this)).append(") {\n");
            }
            indentacao++;
            for (No stmt : caso.corpo) {
                sb.append(indent()).append(stmt.aceitar(this)).append("\n");
            }
            indentacao--;
            sb.append(indent()).append("}");
        }
        if (no.casoPadrao != null && !no.casoPadrao.isEmpty()) {
            sb.append(" else {\n");
            indentacao++;
            for (No stmt : no.casoPadrao) {
                sb.append(indent()).append(stmt.aceitar(this)).append("\n");
            }
            indentacao--;
            sb.append(indent()).append("}");
        }
        return sb.toString();
    }

    @Override
    public String visitarDeclaracaoFuncao(No.DeclaracaoFuncao no) {
        String tipoRetorno = mapearTipo(no.tipoRetorno);
        StringBuilder sb = new StringBuilder();
        sb.append(tipoRetorno).append(" ").append(no.nome).append("(");

        for (int i = 0; i < no.parametros.size(); i++) {
            No.DeclaracaoFuncao.Parametro p = no.parametros.get(i);
            sb.append(mapearTipo(p.tipo)).append(" ").append(p.nome);
            if (i < no.parametros.size() - 1) sb.append(", ");
        }
        sb.append(") {\n");

        indentacao++;
        for (No stmt : no.corpo) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String visitarChamadaFuncao(No.ChamadaFuncao no) {
        StringBuilder sb = new StringBuilder();
        sb.append(no.nome).append("(");
        for (int i = 0; i < no.argumentos.size(); i++) {
            sb.append(no.argumentos.get(i).aceitar(this));
            if (i < no.argumentos.size() - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String visitarRetorne(No.Retorne no) {
        if (no.valor != null) {
            return "return " + no.valor.aceitar(this) + ";";
        }
        return "return;";
    }

    @Override
    public String visitarDeclaracaoClasse(No.DeclaracaoClasse no) {
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(no.nome);
        if (no.superclasse != null) {
            sb.append(" : public ").append(no.superclasse);
        }
        sb.append(" {\npublic:\n");

        indentacao++;

        // Atributos
        for (No membro : no.membros) {
            if (membro instanceof No.DeclaracaoVariavel) {
                sb.append(indent()).append(membro.aceitar(this)).append("\n");
            }
        }

        // Construtor
        sb.append(indent()).append(no.nome).append("(");
        if (no.construtorParams != null) {
            for (int i = 0; i < no.construtorParams.size(); i++) {
                No.DeclaracaoFuncao.Parametro p = no.construtorParams.get(i);
                sb.append(mapearTipo(p.tipo)).append(" ").append(p.nome);
                if (i < no.construtorParams.size() - 1) sb.append(", ");
            }
        }
        sb.append(")");
        if (no.construtorCorpo != null && !no.construtorCorpo.isEmpty()) {
            sb.append(" {\n");
            indentacao++;
            for (No stmt : no.construtorCorpo) {
                String s = stmt.aceitar(this);
                // Replace "this." with "this->"
                s = s.replace("este.", "this->");
                sb.append(indent()).append(s).append("\n");
            }
            indentacao--;
            sb.append(indent()).append("}\n");
        } else {
            sb.append(" {}\n");
        }

        // Métodos
        for (No membro : no.membros) {
            if (membro instanceof No.DeclaracaoFuncao) {
                sb.append("\n").append(indent()).append(membro.aceitar(this)).append("\n");
            }
        }

        indentacao--;
        sb.append("};");
        return sb.toString();
    }

    @Override
    public String visitarNovoObjeto(No.NovoObjeto no) {
        StringBuilder sb = new StringBuilder();
        sb.append(no.classe).append("(");
        for (int i = 0; i < no.argumentos.size(); i++) {
            sb.append(no.argumentos.get(i).aceitar(this));
            if (i < no.argumentos.size() - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String visitarAcessoMembro(No.AcessoMembro no) {
        return no.objeto.aceitar(this) + "." + no.membro;
    }

    @Override
    public String visitarMostrar(No.Mostrar no) {
        StringBuilder sb = new StringBuilder("cout");
        for (No expr : no.expressoes) {
            sb.append(" << ").append(expr.aceitar(this));
        }
        sb.append(" << endl;");
        return sb.toString();
    }

    @Override
    public String visitarLerTexto(No.LerTexto no) {
        return "getline(cin, " + no.variavel + ")";
    }

    @Override
    public String visitarLerNumero(No.LerNumero no) {
        return "cin >> " + no.variavel;
    }

    @Override
    public String visitarLerDecimal(No.LerDecimal no) {
        return "cin >> " + no.variavel;
    }

    @Override
    public String visitarDeclaracaoLista(No.DeclaracaoLista no) {
        includes.add("<vector>");
        String tipoElem = no.tipoElemento != null ? mapearTipo(no.tipoElemento) : "auto";
        // fallback to string if auto
        if (tipoElem.equals("auto")) tipoElem = "string";

        StringBuilder sb = new StringBuilder();
        sb.append("vector<").append(tipoElem).append("> ").append(no.nome);

        if (no.elementos != null && !no.elementos.isEmpty()) {
            sb.append(" = {");
            for (int i = 0; i < no.elementos.size(); i++) {
                sb.append(no.elementos.get(i).aceitar(this));
                if (i < no.elementos.size() - 1) sb.append(", ");
            }
            sb.append("}");
        }
        sb.append(";");
        return sb.toString();
    }

    @Override
    public String visitarAdicionar(No.Adicionar no) {
        return no.lista + ".push_back(" + no.valor.aceitar(this) + ");";
    }

    @Override
    public String visitarRemover(No.Remover no) {
        includes.add("<algorithm>");
        String val = no.valor.aceitar(this);
        return no.lista + ".erase(remove(" + no.lista + ".begin(), " + no.lista + ".end(), " + val + "), " + no.lista + ".end());";
    }

    @Override
    public String visitarTamanho(No.Tamanho no) {
        return no.lista + ".size()";
    }

    @Override
    public String visitarAcessoIndice(No.AcessoIndice no) {
        return no.lista + "[" + no.indice.aceitar(this) + "]";
    }

    @Override
    public String visitarBlocoTentePegue(No.BlocoTentePegue no) {
        includes.add("<stdexcept>");
        StringBuilder sb = new StringBuilder();
        sb.append("try {\n");
        indentacao++;
        for (No stmt : no.corpoTente) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("} catch (const exception& ").append(no.variavelErro != null ? no.variavelErro : "e").append(") {\n");
        indentacao++;
        for (No stmt : no.corpoPegue) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");

        if (no.corpoFinalmente != null && !no.corpoFinalmente.isEmpty()) {
            sb.append("\n").append(indent()).append("// finally (executado manualmente)\n");
            for (No stmt : no.corpoFinalmente) {
                sb.append(indent()).append(stmt.aceitar(this)).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String visitarLance(No.Lance no) {
        includes.add("<stdexcept>");
        return "throw runtime_error(" + no.expressao.aceitar(this) + ");";
    }

    @Override
    public String visitarExpressaoBinaria(No.ExpressaoBinaria no) {
        String esq = no.esquerda.aceitar(this);
        String dir = no.direita.aceitar(this);
        String op = no.operador;

        switch (op) {
            case "e": case "&&": op = "&&"; break;
            case "ou": case "||": op = "||"; break;
            case "==": op = "=="; break;
            case "!=": op = "!="; break;
            case "**":
                includes.add("<cmath>");
                return "pow(" + esq + ", " + dir + ")";
        }
        return "(" + esq + " " + op + " " + dir + ")";
    }

    @Override
    public String visitarExpressaoUnaria(No.ExpressaoUnaria no) {
        String operando = no.operando.aceitar(this);
        String op = no.operador;
        if (op.equals("nao") || op.equals("não")) op = "!";
        if (no.prefixo) {
            return op + operando;
        }
        return operando + op;
    }

    @Override
    public String visitarExpressaoTernaria(No.ExpressaoTernaria no) {
        return "(" + no.condicao.aceitar(this) + " ? " + no.valorVerdadeiro.aceitar(this) + " : " + no.valorFalso.aceitar(this) + ")";
    }

    @Override
    public String visitarLiteralNumero(No.LiteralNumero no) {
        return String.valueOf(no.valor);
    }

    @Override
    public String visitarLiteralDecimal(No.LiteralDecimal no) {
        return String.valueOf(no.valor);
    }

    @Override
    public String visitarLiteralTexto(No.LiteralTexto no) {
        return "\"" + no.valor + "\"";
    }

    @Override
    public String visitarLiteralLogico(No.LiteralLogico no) {
        return no.valor ? "true" : "false";
    }

    @Override
    public String visitarLiteralNulo(No.LiteralNulo no) {
        return "nullptr";
    }

    @Override
    public String visitarLiteralCaractere(No.LiteralCaractere no) {
        return "'" + no.valor + "'";
    }

    @Override
    public String visitarIdentificador(No.Identificador no) {
        if (no.nome.equals("este")) return "this";
        return no.nome;
    }

    @Override
    public String visitarImportar(No.Importar no) {
        return "// import: " + no.modulo;
    }

    @Override
    public String visitarPare(No.Pare no) {
        return "break;";
    }

    @Override
    public String visitarContinue(No.Continue no) {
        return "continue;";
    }

    @Override
    public String visitarCrudCriar(No.CrudCriar no) {
        includes.add("<map>");
        includes.add("<any>");
        return "// CRUD criar: " + no.entidade + " — use std::map ou banco de dados externo";
    }

    @Override
    public String visitarCrudListar(No.CrudListar no) {
        return "// CRUD listar: " + no.entidade;
    }

    @Override
    public String visitarCrudAtualizar(No.CrudAtualizar no) {
        return "// CRUD atualizar: " + no.entidade;
    }

    @Override
    public String visitarCrudDeletar(No.CrudDeletar no) {
        return "// CRUD deletar: " + no.entidade;
    }
}
