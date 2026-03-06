/*
 * BRZ Virtual Machine — Compilador (Pratt Parser)
 *
 * Compilador single-pass que transforma tokens em bytecode.
 * Usa um Pratt parser para expressões com precedência correta,
 * e parsing recursivo descendente para statements.
 *
 * Suporta:
 *   - Variáveis locais e globais (var, const)
 *   - Funções e closures (funcao, retornar)
 *   - Classes com herança (classe, herda, este, super)
 *   - Controle de fluxo (se/senao, enquanto, para)
 *   - Listas e indexação
 *   - Built-ins (mostrar, lerTexto, lerNumero, lerDecimal)
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#include "compiler.h"
#include "scanner.h"
#include "memory.h"
#include "vm.h"

#include <stdio.h>
#include <string.h>

/* ===========================================================================
 * Parser state
 * =========================================================================== */

typedef struct {
    BrzToken current;
    BrzToken previous;
    bool     had_error;
    bool     panic_mode;
} Parser;

static Parser parser;

/* ===========================================================================
 * Precedência (do menor para o maior)
 * =========================================================================== */

typedef enum {
    PREC_NONE,
    PREC_ASSIGNMENT,    /* =                */
    PREC_OR,            /* ou / ||          */
    PREC_AND,           /* e / &&           */
    PREC_EQUALITY,      /* == !=            */
    PREC_COMPARISON,    /* < > <= >=        */
    PREC_TERM,          /* + -              */
    PREC_FACTOR,        /* * / %            */
    PREC_POWER,         /* **               */
    PREC_UNARY,         /* - ! nao          */
    PREC_CALL,          /* . () []          */
    PREC_PRIMARY,
} Precedence;

typedef void (*ParseFn)(bool can_assign);

typedef struct {
    ParseFn     prefix;
    ParseFn     infix;
    Precedence  precedence;
} ParseRule;

/* Forward declarations. */
static ParseRule* get_rule(BrzTokenType type);
static void parse_precedence(Precedence precedence);
static void expression(void);
static void statement(void);
static void declaration(void);
static void block(void);

/* ===========================================================================
 * Compilador state
 *
 * O compilador mantém uma pilha de Compiler structs para funções
 * aninhadas. Cada Compiler tem sua própria lista de variáveis locais.
 * =========================================================================== */

typedef struct {
    BrzToken name;
    int      depth;
    bool     is_captured;   /* Se foi capturada por closure. */
    bool     is_const;
} Local;

typedef struct {
    uint8_t index;
    bool    is_local;
} Upvalue;

typedef enum {
    TYPE_FUNCTION,
    TYPE_METHOD,
    TYPE_INITIALIZER,
    TYPE_SCRIPT,
} FunctionType;

typedef struct Compiler {
    struct Compiler* enclosing;
    BrzFunction*     function;
    FunctionType     type;

    Local            locals[256];
    int              local_count;
    Upvalue          upvalues[256];
    int              scope_depth;
} Compiler;

/* Contexto de classe atual (para 'este' e 'super'). */
typedef struct ClassCompiler {
    struct ClassCompiler* enclosing;
    bool                  has_superclass;
} ClassCompiler;

static Compiler*        current = NULL;
static ClassCompiler*   current_class = NULL;

/* ===========================================================================
 * Chunk atual sendo compilado
 * =========================================================================== */

static BrzChunk* current_chunk(void) {
    return &current->function->chunk;
}

/* ===========================================================================
 * Erros
 * =========================================================================== */

static void error_at(BrzToken* token, const char* message) {
    if (parser.panic_mode) return;
    parser.panic_mode = true;

    fprintf(stderr, "\033[31m[linha %d] Erro", token->line);

    if (token->type == TOKEN_EOF) {
        fprintf(stderr, " no fim do arquivo");
    } else if (token->type != TOKEN_ERROR) {
        fprintf(stderr, " em '%.*s'", token->length, token->start);
    }

    fprintf(stderr, ": %s\033[0m\n", message);
    parser.had_error = true;
}

static void error(const char* message) {
    error_at(&parser.previous, message);
}

static void error_at_current(const char* message) {
    error_at(&parser.current, message);
}

/* ===========================================================================
 * Avanço e consumo de tokens
 * =========================================================================== */

static void advance_token(void) {
    parser.previous = parser.current;

    for (;;) {
        parser.current = brz_scanner_next();
        if (parser.current.type != TOKEN_ERROR) break;
        error_at_current(parser.current.start);
    }
}

static void consume(BrzTokenType type, const char* message) {
    if (parser.current.type == type) {
        advance_token();
        return;
    }
    error_at_current(message);
}

static bool check(BrzTokenType type) {
    return parser.current.type == type;
}

static bool match_token(BrzTokenType type) {
    if (!check(type)) return false;
    advance_token();
    return true;
}

/* Pula newlines opcionais (BRZ trata \n como separador). */
static void skip_newlines(void) {
    while (check(TOKEN_NEWLINE)) advance_token();
}

/* ===========================================================================
 * Emissão de bytecodes
 * =========================================================================== */

static void emit_byte(uint8_t byte) {
    brz_chunk_write(current_chunk(), byte, parser.previous.line);
}

static void emit_bytes(uint8_t a, uint8_t b) {
    emit_byte(a);
    emit_byte(b);
}

static void emit_return(void) {
    if (current->type == TYPE_INITIALIZER) {
        emit_bytes(OP_GET_LOCAL, 0);
    } else {
        emit_byte(OP_NULL);
    }
    emit_byte(OP_RETURN);
}

static uint8_t make_constant(BrzValue value) {
    int constant = brz_chunk_add_constant(current_chunk(), value);
    if (constant > 255) {
        error("Muitas constantes em um bloco.");
        return 0;
    }
    return (uint8_t)constant;
}

static void emit_constant(BrzValue value) {
    emit_bytes(OP_CONSTANT, make_constant(value));
}

/* ===========================================================================
 * Jumps
 * =========================================================================== */

static int emit_jump(uint8_t instruction) {
    emit_byte(instruction);
    emit_byte(0xFF);
    emit_byte(0xFF);
    return current_chunk()->count - 2;
}

static void patch_jump(int offset) {
    int jump = current_chunk()->count - offset - 2;

    if (jump > 65535) {
        error("Bloco de código muito grande para salto.");
    }

    current_chunk()->code[offset]     = (uint8_t)((jump >> 8) & 0xFF);
    current_chunk()->code[offset + 1] = (uint8_t)(jump & 0xFF);
}

static void emit_loop(int loop_start) {
    emit_byte(OP_LOOP);

    int offset = current_chunk()->count - loop_start + 2;
    if (offset > 65535) error("Loop muito grande.");

    emit_byte((uint8_t)((offset >> 8) & 0xFF));
    emit_byte((uint8_t)(offset & 0xFF));
}

/* ===========================================================================
 * Escopo
 * =========================================================================== */

static void begin_scope(void) {
    current->scope_depth++;
}

static void end_scope(void) {
    current->scope_depth--;

    while (current->local_count > 0 &&
           current->locals[current->local_count - 1].depth > current->scope_depth) {
        if (current->locals[current->local_count - 1].is_captured) {
            emit_byte(OP_CLOSE_UPVALUE);
        } else {
            emit_byte(OP_POP);
        }
        current->local_count--;
    }
}

/* ===========================================================================
 * Variáveis
 * =========================================================================== */

static uint8_t identifier_constant(BrzToken* name) {
    return make_constant(BRZ_OBJECT(
        brz_string_copy(name->start, name->length)
    ));
}

static bool identifiers_equal(BrzToken* a, BrzToken* b) {
    if (a->length != b->length) return false;
    return memcmp(a->start, b->start, (size_t)a->length) == 0;
}

static int resolve_local(Compiler* compiler, BrzToken* name) {
    for (int i = compiler->local_count - 1; i >= 0; i--) {
        Local* local = &compiler->locals[i];
        if (identifiers_equal(name, &local->name)) {
            if (local->depth == -1) {
                error("Variável usada em sua própria inicialização.");
            }
            return i;
        }
    }
    return -1;
}

static int add_upvalue(Compiler* compiler, uint8_t index, bool is_local) {
    int count = compiler->function->upvalue_count;

    /* Verifica se já existe. */
    for (int i = 0; i < count; i++) {
        Upvalue* uv = &compiler->upvalues[i];
        if (uv->index == index && uv->is_local == is_local) {
            return i;
        }
    }

    if (count >= 256) {
        error("Muitas variáveis capturadas em closure.");
        return 0;
    }

    compiler->upvalues[count].is_local = is_local;
    compiler->upvalues[count].index    = index;
    return compiler->function->upvalue_count++;
}

static int resolve_upvalue(Compiler* compiler, BrzToken* name) {
    if (compiler->enclosing == NULL) return -1;

    int local = resolve_local(compiler->enclosing, name);
    if (local != -1) {
        compiler->enclosing->locals[local].is_captured = true;
        return add_upvalue(compiler, (uint8_t)local, true);
    }

    int upvalue = resolve_upvalue(compiler->enclosing, name);
    if (upvalue != -1) {
        return add_upvalue(compiler, (uint8_t)upvalue, false);
    }

    return -1;
}

static void add_local(BrzToken name, bool is_const) {
    if (current->local_count >= 256) {
        error("Muitas variáveis locais.");
        return;
    }

    Local* local = &current->locals[current->local_count++];
    local->name        = name;
    local->depth       = -1; /* Marca como não-inicializada. */
    local->is_captured = false;
    local->is_const    = is_const;
}

static void declare_variable(bool is_const) {
    if (current->scope_depth == 0) return;

    BrzToken* name = &parser.previous;

    /* Verifica duplicata no escopo atual. */
    for (int i = current->local_count - 1; i >= 0; i--) {
        Local* local = &current->locals[i];
        if (local->depth != -1 && local->depth < current->scope_depth) break;
        if (identifiers_equal(name, &local->name)) {
            error("Variável já declarada neste escopo.");
        }
    }

    add_local(*name, is_const);
}

static uint8_t parse_variable(const char* error_msg, bool is_const) {
    consume(TOKEN_IDENTIFIER, error_msg);

    declare_variable(is_const);
    if (current->scope_depth > 0) return 0;

    return identifier_constant(&parser.previous);
}

static void mark_initialized(void) {
    if (current->scope_depth == 0) return;
    current->locals[current->local_count - 1].depth = current->scope_depth;
}

static void define_variable(uint8_t global) {
    if (current->scope_depth > 0) {
        mark_initialized();
        return;
    }
    emit_bytes(OP_DEFINE_GLOBAL, global);
}

/* ===========================================================================
 * Inicialização do compilador
 * =========================================================================== */

static void init_compiler(Compiler* compiler, FunctionType type) {
    compiler->enclosing   = current;
    compiler->function    = NULL;
    compiler->type        = type;
    compiler->local_count = 0;
    compiler->scope_depth = 0;
    compiler->function    = brz_function_new();
    current = compiler;

    if (type != TYPE_SCRIPT) {
        current->function->name = brz_string_copy(
            parser.previous.start, parser.previous.length
        );
    }

    /* Slot 0 reservado para 'este' em métodos, ou string vazia em funções. */
    Local* local = &current->locals[current->local_count++];
    local->depth       = 0;
    local->is_captured = false;
    local->is_const    = false;

    if (type != TYPE_FUNCTION) {
        local->name.start  = "este";
        local->name.length = 4;
    } else {
        local->name.start  = "";
        local->name.length = 0;
    }
}

static BrzFunction* end_compiler(void) {
    emit_return();
    BrzFunction* function = current->function;

#ifdef BRZ_DEBUG_PRINT_CODE
    if (!parser.had_error) {
        extern void brz_debug_disassemble(BrzChunk* chunk, const char* name);
        brz_debug_disassemble(current_chunk(),
            function->name ? function->name->chars : "<script>");
    }
#endif

    current = current->enclosing;
    return function;
}

/* ===========================================================================
 * Expressões — Pratt parser
 * =========================================================================== */

static void named_variable(BrzToken name, bool can_assign) {
    uint8_t get_op, set_op;
    int arg = resolve_local(current, &name);

    if (arg != -1) {
        get_op = OP_GET_LOCAL;
        set_op = OP_SET_LOCAL;
    } else if ((arg = resolve_upvalue(current, &name)) != -1) {
        get_op = OP_GET_UPVALUE;
        set_op = OP_SET_UPVALUE;
    } else {
        arg    = identifier_constant(&name);
        get_op = OP_GET_GLOBAL;
        set_op = OP_SET_GLOBAL;
    }

    if (can_assign && match_token(TOKEN_EQUAL)) {
        /* Verifica const. */
        if (arg != -1 && get_op == OP_GET_LOCAL &&
            current->locals[arg].is_const) {
            error("Não pode atribuir a constante.");
        }
        expression();
        emit_bytes(set_op, (uint8_t)arg);
    } else if (can_assign && (check(TOKEN_PLUS_EQUAL) ||
               check(TOKEN_MINUS_EQUAL) || check(TOKEN_STAR_EQUAL) ||
               check(TOKEN_SLASH_EQUAL))) {
        /* Operadores compostos: +=, -=, *=, /= */
        BrzTokenType op = parser.current.type;
        advance_token();
        emit_bytes(get_op, (uint8_t)arg);
        expression();

        switch (op) {
            case TOKEN_PLUS_EQUAL:  emit_byte(OP_ADD); break;
            case TOKEN_MINUS_EQUAL: emit_byte(OP_SUBTRACT); break;
            case TOKEN_STAR_EQUAL:  emit_byte(OP_MULTIPLY); break;
            case TOKEN_SLASH_EQUAL: emit_byte(OP_DIVIDE); break;
            default: break;
        }

        emit_bytes(set_op, (uint8_t)arg);
    } else {
        emit_bytes(get_op, (uint8_t)arg);
    }
}

/* ---------------------------------------------------------------------------
 * Prefix parse functions
 * --------------------------------------------------------------------------- */

static void number(bool can_assign) {
    (void)can_assign;

    /* Verifica se é double ou int. */
    bool is_double = false;
    for (int i = 0; i < parser.previous.length; i++) {
        if (parser.previous.start[i] == '.') {
            is_double = true;
            break;
        }
    }

    if (is_double) {
        double value = strtod(parser.previous.start, NULL);
        emit_constant(BRZ_DOUBLE(value));
    } else {
        int64_t value = strtoll(parser.previous.start, NULL, 10);
        emit_constant(BRZ_INT(value));
    }
}

static void string(bool can_assign) {
    (void)can_assign;

    /* Remove as aspas. */
    const char* start = parser.previous.start + 1;
    int length = parser.previous.length - 2;

    /* Processa escapes. */
    char* buffer = BRZ_ALLOCATE(char, length + 1);
    int j = 0;

    for (int i = 0; i < length; i++) {
        if (start[i] == '\\' && i + 1 < length) {
            switch (start[i + 1]) {
                case 'n':  buffer[j++] = '\n'; i++; break;
                case 't':  buffer[j++] = '\t'; i++; break;
                case 'r':  buffer[j++] = '\r'; i++; break;
                case '\\': buffer[j++] = '\\'; i++; break;
                case '"':  buffer[j++] = '"';  i++; break;
                case '0':  buffer[j++] = '\0'; i++; break;
                default:   buffer[j++] = start[i]; break;
            }
        } else {
            buffer[j++] = start[i];
        }
    }

    BrzString* s = brz_string_copy(buffer, j);
    BRZ_FREE_ARRAY(char, buffer, length + 1);
    emit_constant(BRZ_OBJECT(s));
}

static void literal(bool can_assign) {
    (void)can_assign;
    switch (parser.previous.type) {
        case TOKEN_VERDADEIRO: emit_byte(OP_TRUE); break;
        case TOKEN_FALSO:      emit_byte(OP_FALSE); break;
        case TOKEN_NULO:       emit_byte(OP_NULL); break;
        default: return;
    }
}

static void variable(bool can_assign) {
    named_variable(parser.previous, can_assign);
}

static void this_(bool can_assign) {
    (void)can_assign;
    if (current_class == NULL) {
        error("Não pode usar 'este' fora de uma classe.");
        return;
    }
    variable(false);
}

static void super_(bool can_assign) {
    (void)can_assign;

    if (current_class == NULL) {
        error("Não pode usar 'super' fora de uma classe.");
    } else if (!current_class->has_superclass) {
        error("Não pode usar 'super' em classe sem superclasse.");
    }

    consume(TOKEN_DOT, "Esperado '.' após 'super'.");
    consume(TOKEN_IDENTIFIER, "Esperado nome do método.");
    uint8_t name = identifier_constant(&parser.previous);

    named_variable((BrzToken){ .start = "este", .length = 4,
                                .type = TOKEN_IDENTIFIER, .line = parser.previous.line },
                   false);

    if (match_token(TOKEN_PAREN_LEFT)) {
        uint8_t arg_count = 0;
        if (!check(TOKEN_PAREN_RIGHT)) {
            do {
                skip_newlines();
                expression();
                arg_count++;
            } while (match_token(TOKEN_COMMA));
        }
        consume(TOKEN_PAREN_RIGHT, "Esperado ')' após argumentos.");
        named_variable((BrzToken){ .start = "super", .length = 5,
                                    .type = TOKEN_IDENTIFIER, .line = parser.previous.line },
                       false);
        emit_bytes(OP_SUPER_INVOKE, name);
        emit_byte(arg_count);
    } else {
        named_variable((BrzToken){ .start = "super", .length = 5,
                                    .type = TOKEN_IDENTIFIER, .line = parser.previous.line },
                       false);
        emit_bytes(OP_GET_SUPER, name);
    }
}

static void grouping(bool can_assign) {
    (void)can_assign;
    expression();
    consume(TOKEN_PAREN_RIGHT, "Esperado ')' após expressão.");
}

static void unary(bool can_assign) {
    (void)can_assign;
    BrzTokenType op = parser.previous.type;

    parse_precedence(PREC_UNARY);

    switch (op) {
        case TOKEN_MINUS: emit_byte(OP_NEGATE); break;
        case TOKEN_BANG:
        case TOKEN_NAO:   emit_byte(OP_NOT); break;
        default: return;
    }
}

static uint8_t argument_list(void) {
    uint8_t count = 0;
    if (!check(TOKEN_PAREN_RIGHT)) {
        do {
            skip_newlines();
            expression();
            if (count == 255) {
                error("Máximo de 255 argumentos.");
            }
            count++;
        } while (match_token(TOKEN_COMMA));
    }
    consume(TOKEN_PAREN_RIGHT, "Esperado ')' após argumentos.");
    return count;
}

static void call(bool can_assign) {
    (void)can_assign;
    uint8_t count = argument_list();
    emit_bytes(OP_CALL, count);
}

static void dot(bool can_assign) {
    consume(TOKEN_IDENTIFIER, "Esperado nome de propriedade após '.'.");
    uint8_t name = identifier_constant(&parser.previous);

    if (can_assign && match_token(TOKEN_EQUAL)) {
        expression();
        emit_bytes(OP_SET_PROPERTY, name);
    } else if (match_token(TOKEN_PAREN_LEFT)) {
        uint8_t arg_count = argument_list();
        emit_bytes(OP_INVOKE, name);
        emit_byte(arg_count);
    } else {
        emit_bytes(OP_GET_PROPERTY, name);
    }
}

static void list_literal(bool can_assign) {
    (void)can_assign;
    int count = 0;

    if (!check(TOKEN_BRACKET_RIGHT)) {
        do {
            skip_newlines();
            if (check(TOKEN_BRACKET_RIGHT)) break;
            expression();
            count++;
        } while (match_token(TOKEN_COMMA));
    }
    consume(TOKEN_BRACKET_RIGHT, "Esperado ']' após lista.");

    emit_byte(OP_LIST_NEW);
    emit_byte((uint8_t)count);
}

static void index_get(bool can_assign) {
    expression();
    consume(TOKEN_BRACKET_RIGHT, "Esperado ']' após índice.");

    if (can_assign && match_token(TOKEN_EQUAL)) {
        expression();
        emit_byte(OP_INDEX_SET);
    } else {
        emit_byte(OP_INDEX_GET);
    }
}

/* ---------------------------------------------------------------------------
 * Built-in expressions: mostrar, lerTexto, lerNumero, lerDecimal
 * --------------------------------------------------------------------------- */

static void print_expr(bool can_assign) {
    (void)can_assign;
    consume(TOKEN_PAREN_LEFT, "Esperado '(' após 'mostrar'.");
    expression();
    consume(TOKEN_PAREN_RIGHT, "Esperado ')' após argumento.");
    emit_byte(OP_PRINT);
    emit_byte(OP_NULL);  /* mostrar() retorna nulo como expressão. */
}

static void read_builtin(bool can_assign, BrzTokenType type) {
    (void)can_assign;

    const char* name = (type == TOKEN_LER_TEXTO) ? "lerTexto" :
                       (type == TOKEN_LER_NUMERO) ? "lerNumero" : "lerDecimal";

    consume(TOKEN_PAREN_LEFT, "Esperado '(' após builtin.");

    /* Prompt opcional. */
    if (!check(TOKEN_PAREN_RIGHT)) {
        expression();  /* O prompt. */
        emit_byte(OP_PRINT);
    }

    consume(TOKEN_PAREN_RIGHT, "Esperado ')'.");

    /* Chama a função nativa registrada. */
    BrzToken fake = { .start = name, .length = (int)strlen(name),
                      .type = TOKEN_IDENTIFIER, .line = parser.previous.line };
    uint8_t constant = identifier_constant(&fake);
    emit_bytes(OP_GET_GLOBAL, constant);
    emit_bytes(OP_CALL, 0);
}

static void ler_texto(bool can_assign) {
    read_builtin(can_assign, TOKEN_LER_TEXTO);
}

static void ler_numero(bool can_assign) {
    read_builtin(can_assign, TOKEN_LER_NUMERO);
}

static void ler_decimal(bool can_assign) {
    read_builtin(can_assign, TOKEN_LER_DECIMAL);
}

/* ---------------------------------------------------------------------------
 * Infix parse functions
 * --------------------------------------------------------------------------- */

static void binary(bool can_assign) {
    (void)can_assign;
    BrzTokenType op = parser.previous.type;
    ParseRule* rule = get_rule(op);
    parse_precedence((Precedence)(rule->precedence + 1));

    switch (op) {
        case TOKEN_PLUS:          emit_byte(OP_ADD); break;
        case TOKEN_MINUS:         emit_byte(OP_SUBTRACT); break;
        case TOKEN_STAR:          emit_byte(OP_MULTIPLY); break;
        case TOKEN_SLASH:         emit_byte(OP_DIVIDE); break;
        case TOKEN_PERCENT:       emit_byte(OP_MODULO); break;
        case TOKEN_STAR_STAR:     emit_byte(OP_POWER); break;
        case TOKEN_EQUAL_EQUAL:   emit_byte(OP_EQUAL); break;
        case TOKEN_BANG_EQUAL:    emit_byte(OP_NOT_EQUAL); break;
        case TOKEN_GREATER:       emit_byte(OP_GREATER); break;
        case TOKEN_GREATER_EQUAL: emit_byte(OP_GREATER_EQUAL); break;
        case TOKEN_LESS:          emit_byte(OP_LESS); break;
        case TOKEN_LESS_EQUAL:    emit_byte(OP_LESS_EQUAL); break;
        default: return;
    }
}

static void and_(bool can_assign) {
    (void)can_assign;
    int end_jump = emit_jump(OP_JUMP_IF_FALSE);

    emit_byte(OP_POP);
    parse_precedence(PREC_AND);

    patch_jump(end_jump);
}

static void or_(bool can_assign) {
    (void)can_assign;
    int else_jump = emit_jump(OP_JUMP_IF_FALSE);
    int end_jump  = emit_jump(OP_JUMP);

    patch_jump(else_jump);
    emit_byte(OP_POP);

    parse_precedence(PREC_OR);
    patch_jump(end_jump);
}

/* ===========================================================================
 * Parse rules table
 * =========================================================================== */

static ParseRule rules[] = {
    /* TOKEN_INT              */ { number,       NULL,      PREC_NONE       },
    /* TOKEN_DOUBLE           */ { number,       NULL,      PREC_NONE       },
    /* TOKEN_STRING           */ { string,       NULL,      PREC_NONE       },
    /* TOKEN_CHAR             */ { string,       NULL,      PREC_NONE       },
    /* TOKEN_IDENTIFIER       */ { variable,     NULL,      PREC_NONE       },
    /* TOKEN_PLUS             */ { NULL,         binary,    PREC_TERM       },
    /* TOKEN_MINUS            */ { unary,        binary,    PREC_TERM       },
    /* TOKEN_STAR             */ { NULL,         binary,    PREC_FACTOR     },
    /* TOKEN_SLASH            */ { NULL,         binary,    PREC_FACTOR     },
    /* TOKEN_PERCENT          */ { NULL,         binary,    PREC_FACTOR     },
    /* TOKEN_STAR_STAR        */ { NULL,         binary,    PREC_POWER      },
    /* TOKEN_EQUAL            */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_PLUS_EQUAL       */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_MINUS_EQUAL      */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_STAR_EQUAL       */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_SLASH_EQUAL      */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_EQUAL_EQUAL      */ { NULL,         binary,    PREC_EQUALITY   },
    /* TOKEN_BANG_EQUAL        */ { NULL,         binary,    PREC_EQUALITY   },
    /* TOKEN_GREATER          */ { NULL,         binary,    PREC_COMPARISON },
    /* TOKEN_GREATER_EQUAL    */ { NULL,         binary,    PREC_COMPARISON },
    /* TOKEN_LESS             */ { NULL,         binary,    PREC_COMPARISON },
    /* TOKEN_LESS_EQUAL       */ { NULL,         binary,    PREC_COMPARISON },
    /* TOKEN_BANG             */ { unary,        NULL,      PREC_NONE       },
    /* TOKEN_AND              */ { NULL,         and_,      PREC_AND        },
    /* TOKEN_OR               */ { NULL,         or_,       PREC_OR         },
    /* TOKEN_PAREN_LEFT       */ { grouping,     call,      PREC_CALL       },
    /* TOKEN_PAREN_RIGHT      */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_BRACE_LEFT       */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_BRACE_RIGHT      */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_BRACKET_LEFT     */ { list_literal, index_get, PREC_CALL       },
    /* TOKEN_BRACKET_RIGHT    */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_COMMA            */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_DOT              */ { NULL,         dot,       PREC_CALL       },
    /* TOKEN_SEMICOLON        */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_COLON            */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_ARROW            */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_VAR              */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_CONST            */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_SE               */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_SENAO            */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_ENQUANTO         */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_PARA             */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_FUNCAO           */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_RETORNAR         */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_CLASSE           */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_ESTE             */ { this_,        NULL,      PREC_NONE       },
    /* TOKEN_SUPER            */ { super_,       NULL,      PREC_NONE       },
    /* TOKEN_HERDA            */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_VERDADEIRO       */ { literal,      NULL,      PREC_NONE       },
    /* TOKEN_FALSO            */ { literal,      NULL,      PREC_NONE       },
    /* TOKEN_NULO             */ { literal,      NULL,      PREC_NONE       },
    /* TOKEN_E                */ { NULL,         and_,      PREC_AND        },
    /* TOKEN_OU               */ { NULL,         or_,       PREC_OR         },
    /* TOKEN_NAO              */ { unary,        NULL,      PREC_NONE       },
    /* TOKEN_MOSTRAR          */ { print_expr,   NULL,      PREC_NONE       },
    /* TOKEN_LER_TEXTO        */ { ler_texto,    NULL,      PREC_NONE       },
    /* TOKEN_LER_NUMERO       */ { ler_numero,   NULL,      PREC_NONE       },
    /* TOKEN_LER_DECIMAL      */ { ler_decimal,  NULL,      PREC_NONE       },
    /* TOKEN_SAIR             */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_CONTINUAR        */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_IMPORTAR         */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_DE               */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_EM               */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_ESCOLHA          */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_CASO             */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_PADRAO           */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_TENTE            */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_PEGUE            */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_LANCE            */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_NOVO             */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_LISTA            */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_NEWLINE          */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_ERROR            */ { NULL,         NULL,      PREC_NONE       },
    /* TOKEN_EOF              */ { NULL,         NULL,      PREC_NONE       },
};

static ParseRule* get_rule(BrzTokenType type) {
    return &rules[type];
}

static void parse_precedence(Precedence precedence) {
    skip_newlines();
    advance_token();

    ParseFn prefix = get_rule(parser.previous.type)->prefix;
    if (prefix == NULL) {
        error("Expressão esperada.");
        return;
    }

    bool can_assign = (precedence <= PREC_ASSIGNMENT);
    prefix(can_assign);

    while (precedence <= get_rule(parser.current.type)->precedence) {
        advance_token();
        ParseFn infix = get_rule(parser.previous.type)->infix;
        if (infix != NULL) infix(can_assign);
    }

    if (can_assign && match_token(TOKEN_EQUAL)) {
        error("Alvo de atribuição inválido.");
    }
}

static void expression(void) {
    parse_precedence(PREC_ASSIGNMENT);
}

/* ===========================================================================
 * Statements
 * =========================================================================== */

static void expression_statement(void) {
    expression();
    emit_byte(OP_POP);
}

static void block(void) {
    skip_newlines();
    while (!check(TOKEN_BRACE_RIGHT) && !check(TOKEN_EOF)) {
        declaration();
        skip_newlines();
    }
    consume(TOKEN_BRACE_RIGHT, "Esperado '}' após bloco.");
}

/* ---------------------------------------------------------------------------
 * var / const
 * --------------------------------------------------------------------------- */

static void var_declaration(bool is_const) {
    uint8_t global = parse_variable("Esperado nome da variável.", is_const);

    if (match_token(TOKEN_EQUAL)) {
        expression();
    } else if (is_const) {
        error("Constante deve ser inicializada.");
    } else {
        emit_byte(OP_NULL);
    }

    define_variable(global);
}

/* ---------------------------------------------------------------------------
 * se / senao
 * --------------------------------------------------------------------------- */

static void if_statement(void) {
    consume(TOKEN_PAREN_LEFT, "Esperado '(' após 'se'.");
    expression();
    consume(TOKEN_PAREN_RIGHT, "Esperado ')' após condição.");

    int then_jump = emit_jump(OP_JUMP_IF_FALSE);
    emit_byte(OP_POP);

    skip_newlines();
    consume(TOKEN_BRACE_LEFT, "Esperado '{' após condição do 'se'.");
    begin_scope();
    block();
    end_scope();

    int else_jump = emit_jump(OP_JUMP);
    patch_jump(then_jump);
    emit_byte(OP_POP);

    skip_newlines();
    if (match_token(TOKEN_SENAO)) {
        skip_newlines();
        if (match_token(TOKEN_SE)) {
            if_statement(); /* senao se → else if */
        } else {
            consume(TOKEN_BRACE_LEFT, "Esperado '{' após 'senao'.");
            begin_scope();
            block();
            end_scope();
        }
    }

    patch_jump(else_jump);
}

/* ---------------------------------------------------------------------------
 * enquanto
 * --------------------------------------------------------------------------- */

static void while_statement(void) {
    int loop_start = current_chunk()->count;

    consume(TOKEN_PAREN_LEFT, "Esperado '(' após 'enquanto'.");
    expression();
    consume(TOKEN_PAREN_RIGHT, "Esperado ')' após condição.");

    int exit_jump = emit_jump(OP_JUMP_IF_FALSE);
    emit_byte(OP_POP);

    skip_newlines();
    consume(TOKEN_BRACE_LEFT, "Esperado '{' após condição do 'enquanto'.");
    begin_scope();
    block();
    end_scope();

    emit_loop(loop_start);

    patch_jump(exit_jump);
    emit_byte(OP_POP);
}

/* ---------------------------------------------------------------------------
 * para (for)
 *
 * para (var i = 0; i < 10; i += 1) { ... }
 * --------------------------------------------------------------------------- */

static void for_statement(void) {
    begin_scope();
    consume(TOKEN_PAREN_LEFT, "Esperado '(' após 'para'.");

    /* Inicializador. */
    if (match_token(TOKEN_VAR)) {
        var_declaration(false);
    } else if (!check(TOKEN_SEMICOLON)) {
        expression_statement();
    }
    consume(TOKEN_SEMICOLON, "Esperado ';' após inicializador do 'para'.");

    /* Condição. */
    int loop_start = current_chunk()->count;
    int exit_jump = -1;

    if (!check(TOKEN_SEMICOLON)) {
        expression();
        exit_jump = emit_jump(OP_JUMP_IF_FALSE);
        emit_byte(OP_POP);
    }
    consume(TOKEN_SEMICOLON, "Esperado ';' após condição do 'para'.");

    /* Incremento. */
    if (!check(TOKEN_PAREN_RIGHT)) {
        int body_jump = emit_jump(OP_JUMP);
        int increment_start = current_chunk()->count;

        expression();
        emit_byte(OP_POP);

        emit_loop(loop_start);
        loop_start = increment_start;
        patch_jump(body_jump);
    }
    consume(TOKEN_PAREN_RIGHT, "Esperado ')' após cláusulas do 'para'.");

    /* Corpo. */
    skip_newlines();
    consume(TOKEN_BRACE_LEFT, "Esperado '{' após 'para'.");
    begin_scope();
    block();
    end_scope();

    emit_loop(loop_start);

    if (exit_jump != -1) {
        patch_jump(exit_jump);
        emit_byte(OP_POP);
    }

    end_scope();
}

/* ---------------------------------------------------------------------------
 * funcao
 * --------------------------------------------------------------------------- */

static void function(FunctionType type) {
    Compiler compiler;
    init_compiler(&compiler, type);
    begin_scope();

    consume(TOKEN_PAREN_LEFT, "Esperado '(' após nome da função.");

    if (!check(TOKEN_PAREN_RIGHT)) {
        do {
            skip_newlines();
            current->function->arity++;
            if (current->function->arity > 255) {
                error_at_current("Máximo de 255 parâmetros.");
            }
            uint8_t constant = parse_variable("Esperado nome do parâmetro.", false);
            define_variable(constant);
        } while (match_token(TOKEN_COMMA));
    }

    consume(TOKEN_PAREN_RIGHT, "Esperado ')' após parâmetros.");
    skip_newlines();
    consume(TOKEN_BRACE_LEFT, "Esperado '{' antes do corpo da função.");
    block();

    BrzFunction* fn = end_compiler();
    emit_bytes(OP_CLOSURE, make_constant(BRZ_OBJECT(fn)));

    for (int i = 0; i < fn->upvalue_count; i++) {
        emit_byte(compiler.upvalues[i].is_local ? 1 : 0);
        emit_byte(compiler.upvalues[i].index);
    }
}

static void fun_declaration(void) {
    uint8_t global = parse_variable("Esperado nome da função.", false);
    mark_initialized();
    function(TYPE_FUNCTION);
    define_variable(global);
}

/* ---------------------------------------------------------------------------
 * retornar
 * --------------------------------------------------------------------------- */

static void return_statement(void) {
    if (current->type == TYPE_SCRIPT) {
        error("Não pode retornar fora de uma função.");
    }

    /* Verifica se tem valor após retornar. */
    if (check(TOKEN_NEWLINE) || check(TOKEN_BRACE_RIGHT) || check(TOKEN_EOF)) {
        emit_return();
    } else {
        if (current->type == TYPE_INITIALIZER) {
            error("Não pode retornar valor do construtor.");
        }
        expression();
        emit_byte(OP_RETURN);
    }
}

/* ---------------------------------------------------------------------------
 * classe
 * --------------------------------------------------------------------------- */

static void method(void) {
    consume(TOKEN_IDENTIFIER, "Esperado nome do método.");
    uint8_t constant = identifier_constant(&parser.previous);

    FunctionType type = TYPE_METHOD;
    if (parser.previous.length == 7 &&
        memcmp(parser.previous.start, "iniciar", 7) == 0) {
        type = TYPE_INITIALIZER;
    }

    function(type);
    emit_bytes(OP_METHOD, constant);
}

static void class_declaration(void) {
    consume(TOKEN_IDENTIFIER, "Esperado nome da classe.");
    BrzToken class_name = parser.previous;
    uint8_t name_const = identifier_constant(&parser.previous);
    declare_variable(false);

    emit_bytes(OP_CLASS, name_const);
    define_variable(name_const);

    ClassCompiler class_compiler;
    class_compiler.enclosing      = current_class;
    class_compiler.has_superclass = false;
    current_class = &class_compiler;

    /* Herança. */
    if (match_token(TOKEN_HERDA)) {
        consume(TOKEN_IDENTIFIER, "Esperado nome da superclasse.");
        variable(false);

        if (identifiers_equal(&class_name, &parser.previous)) {
            error("Classe não pode herdar de si mesma.");
        }

        begin_scope();
        add_local((BrzToken){ .start = "super", .length = 5,
                              .type = TOKEN_IDENTIFIER, .line = parser.previous.line }, false);
        define_variable(0);

        named_variable(class_name, false);
        emit_byte(OP_INHERIT);
        class_compiler.has_superclass = true;
    }

    named_variable(class_name, false);

    skip_newlines();
    consume(TOKEN_BRACE_LEFT, "Esperado '{' antes do corpo da classe.");
    skip_newlines();

    while (!check(TOKEN_BRACE_RIGHT) && !check(TOKEN_EOF)) {
        skip_newlines();
        if (match_token(TOKEN_FUNCAO)) {
            method();
        } else if (check(TOKEN_IDENTIFIER)) {
            method();
        } else {
            skip_newlines();
            if (check(TOKEN_BRACE_RIGHT)) break;
            error("Esperado definição de método na classe.");
            advance_token();
        }
        skip_newlines();
    }

    consume(TOKEN_BRACE_RIGHT, "Esperado '}' após corpo da classe.");
    emit_byte(OP_POP);

    if (class_compiler.has_superclass) {
        end_scope();
    }

    current_class = current_class->enclosing;
}

/* ---------------------------------------------------------------------------
 * Synchronize after error
 * --------------------------------------------------------------------------- */

static void synchronize(void) {
    parser.panic_mode = false;

    while (parser.current.type != TOKEN_EOF) {
        if (parser.previous.type == TOKEN_NEWLINE) return;
        if (parser.previous.type == TOKEN_SEMICOLON) return;

        switch (parser.current.type) {
            case TOKEN_VAR:
            case TOKEN_CONST:
            case TOKEN_FUNCAO:
            case TOKEN_CLASSE:
            case TOKEN_SE:
            case TOKEN_ENQUANTO:
            case TOKEN_PARA:
            case TOKEN_RETORNAR:
            case TOKEN_MOSTRAR:
                return;
            default:
                break;
        }

        advance_token();
    }
}

/* ===========================================================================
 * Declarations — entry point do parser
 * =========================================================================== */

static void declaration(void) {
    skip_newlines();

    if (check(TOKEN_EOF)) return;

    if (match_token(TOKEN_VAR)) {
        var_declaration(false);
    } else if (match_token(TOKEN_CONST)) {
        var_declaration(true);
    } else if (match_token(TOKEN_FUNCAO)) {
        fun_declaration();
    } else if (match_token(TOKEN_CLASSE)) {
        class_declaration();
    } else {
        statement();
    }

    skip_newlines();

    if (parser.panic_mode) synchronize();
}

static void statement(void) {
    if (match_token(TOKEN_SE)) {
        if_statement();
    } else if (match_token(TOKEN_ENQUANTO)) {
        while_statement();
    } else if (match_token(TOKEN_PARA)) {
        for_statement();
    } else if (match_token(TOKEN_RETORNAR)) {
        return_statement();
    } else if (match_token(TOKEN_BRACE_LEFT)) {
        begin_scope();
        block();
        end_scope();
    } else {
        expression_statement();
    }
}

/* ===========================================================================
 * API pública
 * =========================================================================== */

BrzClosure* brz_compile(const char* source) {
    brz_scanner_init(source);

    Compiler compiler;
    init_compiler(&compiler, TYPE_SCRIPT);

    parser.had_error  = false;
    parser.panic_mode = false;

    advance_token();

    while (!match_token(TOKEN_EOF)) {
        declaration();
    }

    BrzFunction* function = end_compiler();
    return parser.had_error ? NULL : brz_closure_new(function);
}

void brz_compiler_mark_roots(void) {
    Compiler* compiler = current;
    while (compiler != NULL) {
        brz_gc_mark_object((BrzObject*)compiler->function);
        compiler = compiler->enclosing;
    }
}
