package brz.generator.cpp;

import brz.ast.No;
import brz.ast.No.*;

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
    public String gerar(Programa programa) {
        // Primeira passada: coleta funções/classes
        List<No> corpoMain = new ArrayList<>();

        for (No declaracao : programa.getDeclaracoes()) {
            if (declaracao instanceof DeclaracaoFuncao) {
                funcoesGlobais.add(declaracao.aceitar(this));
            } else if (declaracao instanceof DeclaracaoClasse) {
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
    public String visitarPrograma(Programa no) {
        return gerar(no);
    }

    @Override
    public String visitarDeclaracaoVariavel(DeclaracaoVariavel no) {
        String tipo = mapearTipo(no.getTipo());
        if (no.getValor() != null) {
            return tipo + " " + no.getNome() + " = " + no.getValor().aceitar(this) + ";";
        }
        return tipo + " " + no.getNome() + ";";
    }

    @Override
    public String visitarDeclaracaoConstante(DeclaracaoConstante no) {
        String tipo = mapearTipo(no.getTipo());
        return "const " + tipo + " " + no.getNome() + " = " + no.getValor().aceitar(this) + ";";
    }

    @Override
    public String visitarAtribuicao(Atribuicao no) {
        return no.getAlvo().aceitar(this) + " = " + no.getValor().aceitar(this) + ";";
    }

    @Override
    public String visitarAtribuicaoComposta(AtribuicaoComposta no) {
        return no.getAlvo().aceitar(this) + " " + no.getOperador() + " " + no.getValor().aceitar(this) + ";";
    }

    @Override
    public String visitarBlocoSe(BlocoSe no) {
        StringBuilder sb = new StringBuilder();
        sb.append("if (").append(no.getCondicao().aceitar(this)).append(") {\n");
        indentacao++;
        for (No stmt : no.getCorpoSe()) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");

        if (no.getSenaoSe() != null) {
            for (BlocoSe senaoSe : no.getSenaoSe()) {
                sb.append(" else if (").append(senaoSe.getCondicao().aceitar(this)).append(") {\n");
                indentacao++;
                for (No stmt : senaoSe.getCorpoSe()) {
                    sb.append(indent()).append(stmt.aceitar(this)).append("\n");
                }
                indentacao--;
                sb.append(indent()).append("}");
            }
        }

        if (no.getCorpoSenao() != null && !no.getCorpoSenao().isEmpty()) {
            sb.append(" else {\n");
            indentacao++;
            for (No stmt : no.getCorpoSenao()) {
                sb.append(indent()).append(stmt.aceitar(this)).append("\n");
            }
            indentacao--;
            sb.append(indent()).append("}");
        }
        return sb.toString();
    }

    @Override
    public String visitarBlocoPara(BlocoPara no) {
        String init = no.getInicializacao() != null ? no.getInicializacao().aceitar(this) : ";";
        if (init.endsWith(";")) init = init.substring(0, init.length() - 1);
        String cond = no.getCondicao() != null ? no.getCondicao().aceitar(this) : "";
        String inc = no.getIncremento() != null ? no.getIncremento().aceitar(this).replace(";", "") : "";

        StringBuilder sb = new StringBuilder();
        sb.append("for (").append(init).append("; ").append(cond).append("; ").append(inc).append(") {\n");
        indentacao++;
        for (No stmt : no.getCorpo()) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");
        return sb.toString();
    }

    @Override
    public String visitarParaEmLista(ParaEmLista no) {
        StringBuilder sb = new StringBuilder();
        sb.append("for (auto& ").append(no.getVariavel()).append(" : ").append(no.getLista().aceitar(this)).append(") {\n");
        indentacao++;
        for (No stmt : no.getCorpo()) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
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
        for (No stmt : no.getCorpo()) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
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
        for (No stmt : no.getCorpo()) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("} while (").append(no.getCondicao().aceitar(this)).append(");");
        return sb.toString();
    }

    @Override
    public String visitarBlocoEscolha(BlocoEscolha no) {
        StringBuilder sb = new StringBuilder();
        // C++ switch requires integral/enum type; we use if-else chain for safety
        String expr = no.getExpressao().aceitar(this);
        boolean primeiro = true;
        for (CasoEscolha caso : no.getCasos()) {
            if (primeiro) {
                sb.append("if (").append(expr).append(" == ").append(caso.getValor().aceitar(this)).append(") {\n");
                primeiro = false;
            } else {
                sb.append(" else if (").append(expr).append(" == ").append(caso.getValor().aceitar(this)).append(") {\n");
            }
            indentacao++;
            for (No stmt : caso.getCorpo()) {
                sb.append(indent()).append(stmt.aceitar(this)).append("\n");
            }
            indentacao--;
            sb.append(indent()).append("}");
        }
        if (no.getCorpoPadrao() != null && !no.getCorpoPadrao().isEmpty()) {
            sb.append(" else {\n");
            indentacao++;
            for (No stmt : no.getCorpoPadrao()) {
                sb.append(indent()).append(stmt.aceitar(this)).append("\n");
            }
            indentacao--;
            sb.append(indent()).append("}");
        }
        return sb.toString();
    }

    @Override
    public String visitarDeclaracaoFuncao(DeclaracaoFuncao no) {
        String tipoRetorno = mapearTipo(no.getTipoRetorno());
        StringBuilder sb = new StringBuilder();
        sb.append(tipoRetorno).append(" ").append(no.getNome()).append("(");

        for (int i = 0; i < no.getParametros().size(); i++) {
            Parametro p = no.getParametros().get(i);
            sb.append(mapearTipo(p.getTipo())).append(" ").append(p.getNome());
            if (i < no.getParametros().size() - 1) sb.append(", ");
        }
        sb.append(") {\n");

        indentacao++;
        for (No stmt : no.getCorpo()) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String visitarChamadaFuncao(ChamadaFuncao no) {
        StringBuilder sb = new StringBuilder();
        sb.append(no.getNome()).append("(");
        for (int i = 0; i < no.getArgumentos().size(); i++) {
            sb.append(no.getArgumentos().get(i).aceitar(this));
            if (i < no.getArgumentos().size() - 1) sb.append(", ");
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

    @Override
    public String visitarDeclaracaoClasse(DeclaracaoClasse no) {
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(no.getNome());
        if (no.getClassePai() != null) {
            sb.append(" : public ").append(no.getClassePai());
        }
        sb.append(" {\npublic:\n");

        indentacao++;

        // Atributos
        for (DeclaracaoVariavel attr : no.getAtributos()) {
            sb.append(indent()).append(attr.aceitar(this)).append("\n");
        }

        // Construtor
        sb.append(indent()).append(no.getNome()).append("(");
        if (no.getConstrutor() != null) {
            List<Parametro> params = no.getConstrutor().getParametros();
            for (int i = 0; i < params.size(); i++) {
                Parametro p = params.get(i);
                sb.append(mapearTipo(p.getTipo())).append(" ").append(p.getNome());
                if (i < params.size() - 1) sb.append(", ");
            }
        }
        sb.append(")");
        if (no.getConstrutor() != null && no.getConstrutor().getCorpo() != null && !no.getConstrutor().getCorpo().isEmpty()) {
            sb.append(" {\n");
            indentacao++;
            for (No stmt : no.getConstrutor().getCorpo()) {
                String s = stmt.aceitar(this);
                // Replace "este." with "this->"
                s = s.replace("este.", "this->");
                sb.append(indent()).append(s).append("\n");
            }
            indentacao--;
            sb.append(indent()).append("}\n");
        } else {
            sb.append(" {}\n");
        }

        // Métodos
        for (DeclaracaoFuncao metodo : no.getMetodos()) {
            sb.append("\n").append(indent()).append(metodo.aceitar(this)).append("\n");
        }

        indentacao--;
        sb.append("};");
        return sb.toString();
    }

    @Override
    public String visitarNovoObjeto(NovoObjeto no) {
        StringBuilder sb = new StringBuilder();
        sb.append(no.getClasse()).append("(");
        for (int i = 0; i < no.getArgumentos().size(); i++) {
            sb.append(no.getArgumentos().get(i).aceitar(this));
            if (i < no.getArgumentos().size() - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String visitarAcessoMembro(AcessoMembro no) {
        String obj = no.getObjeto().aceitar(this);
        String membro = no.getMembro();
        if (no.isChamadaMetodo()) {
            StringBuilder sb = new StringBuilder();
            sb.append(obj).append(".").append(membro).append("(");
            for (int i = 0; i < no.getArgumentos().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(no.getArgumentos().get(i).aceitar(this));
            }
            sb.append(")");
            return sb.toString();
        }
        return obj + "." + membro;
    }

    @Override
    public String visitarMostrar(Mostrar no) {
        StringBuilder sb = new StringBuilder("cout");
        for (No expr : no.getArgumentos()) {
            sb.append(" << ").append(expr.aceitar(this));
        }
        sb.append(" << endl;");
        return sb.toString();
    }

    @Override
    public String visitarLerTexto(LerTexto no) {
        if (no.getPrompt() != null) {
            return "([&]{ cout << " + no.getPrompt().aceitar(this) + "; string _s; getline(cin, _s); return _s; }())";
        }
        return "([&]{ string _s; getline(cin, _s); return _s; }())";
    }

    @Override
    public String visitarLerNumero(LerNumero no) {
        if (no.getPrompt() != null) {
            return "([&]{ cout << " + no.getPrompt().aceitar(this) + "; int _n; cin >> _n; return _n; }())";
        }
        return "([&]{ int _n; cin >> _n; return _n; }())";
    }

    @Override
    public String visitarLerDecimal(LerDecimal no) {
        if (no.getPrompt() != null) {
            return "([&]{ cout << " + no.getPrompt().aceitar(this) + "; double _d; cin >> _d; return _d; }())";
        }
        return "([&]{ double _d; cin >> _d; return _d; }())";
    }

    @Override
    public String visitarDeclaracaoLista(DeclaracaoLista no) {
        includes.add("<vector>");
        String tipoElem = no.getTipoElemento() != null ? mapearTipo(no.getTipoElemento()) : "auto";
        // fallback to string if auto
        if (tipoElem.equals("auto")) tipoElem = "string";

        StringBuilder sb = new StringBuilder();
        sb.append("vector<").append(tipoElem).append("> ").append(no.getNome());

        if (no.getValoresIniciais() != null && !no.getValoresIniciais().isEmpty()) {
            sb.append(" = {");
            for (int i = 0; i < no.getValoresIniciais().size(); i++) {
                sb.append(no.getValoresIniciais().get(i).aceitar(this));
                if (i < no.getValoresIniciais().size() - 1) sb.append(", ");
            }
            sb.append("}");
        }
        sb.append(";");
        return sb.toString();
    }

    @Override
    public String visitarAdicionar(Adicionar no) {
        return no.getLista().aceitar(this) + ".push_back(" + no.getValor().aceitar(this) + ");";
    }

    @Override
    public String visitarRemover(Remover no) {
        String lista = no.getLista().aceitar(this);
        String indice = no.getIndice().aceitar(this);
        return lista + ".erase(" + lista + ".begin() + " + indice + ");";
    }

    @Override
    public String visitarTamanho(Tamanho no) {
        return no.getLista().aceitar(this) + ".size()";
    }

    @Override
    public String visitarAcessoIndice(AcessoIndice no) {
        return no.getObjeto().aceitar(this) + "[" + no.getIndice().aceitar(this) + "]";
    }

    @Override
    public String visitarBlocoTentePegue(BlocoTentePegue no) {
        includes.add("<stdexcept>");
        StringBuilder sb = new StringBuilder();
        sb.append("try {\n");
        indentacao++;
        for (No stmt : no.getCorpoTente()) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("} catch (const exception& ").append(no.getNomeErro() != null ? no.getNomeErro() : "e").append(") {\n");
        indentacao++;
        for (No stmt : no.getCorpoPegue()) {
            sb.append(indent()).append(stmt.aceitar(this)).append("\n");
        }
        indentacao--;
        sb.append(indent()).append("}");

        if (no.getCorpoFinalmente() != null && !no.getCorpoFinalmente().isEmpty()) {
            sb.append("\n").append(indent()).append("// finally (executado manualmente)\n");
            for (No stmt : no.getCorpoFinalmente()) {
                sb.append(indent()).append(stmt.aceitar(this)).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String visitarLance(Lance no) {
        includes.add("<stdexcept>");
        return "throw runtime_error(" + no.getExpressao().aceitar(this) + ");";
    }

    @Override
    public String visitarExpressaoBinaria(ExpressaoBinaria no) {
        String esq = no.getEsquerda().aceitar(this);
        String dir = no.getDireita().aceitar(this);
        String op = no.getOperador();

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
    public String visitarExpressaoUnaria(ExpressaoUnaria no) {
        String operando = no.getOperando().aceitar(this);
        String op = no.getOperador();
        if (op.equals("nao") || op.equals("não")) op = "!";
        if (no.isPrefixo()) {
            return op + operando;
        }
        return operando + op;
    }

    @Override
    public String visitarExpressaoTernaria(ExpressaoTernaria no) {
        return "(" + no.getCondicao().aceitar(this) + " ? " + no.getValorSe().aceitar(this) + " : " + no.getValorSenao().aceitar(this) + ")";
    }

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
        return "\"" + no.getValor() + "\"";
    }

    @Override
    public String visitarLiteralBooleano(LiteralBooleano no) {
        return no.getValor() ? "true" : "false";
    }

    @Override
    public String visitarLiteralNulo(LiteralNulo no) {
        return "nullptr";
    }

    @Override
    public String visitarLiteralCaracter(LiteralCaracter no) {
        return "'" + no.getValor() + "'";
    }

    @Override
    public String visitarIdentificador(Identificador no) {
        if (no.getNome().equals("este")) return "this";
        return no.getNome();
    }

    @Override
    public String visitarImportar(Importar no) {
        return "// import: " + no.getModulo();
    }

    @Override
    public String visitarPare(Pare no) {
        return "break;";
    }

    @Override
    public String visitarContinue(Continue no) {
        return "continue;";
    }

    @Override
    public String visitarCrudCriar(CrudCriar no) {
        includes.add("<map>");
        includes.add("<any>");
        return "// CRUD criar: " + no.getEntidade() + " — use std::map ou banco de dados externo";
    }

    @Override
    public String visitarCrudListar(CrudListar no) {
        return "// CRUD listar: " + no.getEntidade();
    }

    @Override
    public String visitarCrudAtualizar(CrudAtualizar no) {
        return "// CRUD atualizar: " + no.getEntidade();
    }

    @Override
    public String visitarCrudDeletar(CrudDeletar no) {
        return "// CRUD deletar: " + no.getEntidade();
    }
}
