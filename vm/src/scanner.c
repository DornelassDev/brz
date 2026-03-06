/*
 * BRZ Virtual Machine — Scanner (Lexer)
 *
 * Implementação do tokenizador. Reconhece a sintaxe completa do BRZ:
 * palavras-chave em Português, literais numéricos, strings com escape,
 * operadores compostos e newlines significativos.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#include "scanner.h"

#include <string.h>

/* ===========================================================================
 * Estado do scanner
 * =========================================================================== */

typedef struct {
    const char* start;      /* Início do token atual.      */
    const char* current;    /* Posição de leitura atual.   */
    int         line;       /* Linha atual.                */
} Scanner;

static Scanner scanner;

/* ===========================================================================
 * Inicialização
 * =========================================================================== */

void brz_scanner_init(const char* source) {
    scanner.start   = source;
    scanner.current = source;
    scanner.line    = 1;
}

/* ===========================================================================
 * Helpers
 * =========================================================================== */

static bool is_at_end(void) {
    return *scanner.current == '\0';
}

static char advance(void) {
    scanner.current++;
    return scanner.current[-1];
}

static char peek(void) {
    return *scanner.current;
}

static char peek_next(void) {
    if (is_at_end()) return '\0';
    return scanner.current[1];
}

static bool match(char expected) {
    if (is_at_end()) return false;
    if (*scanner.current != expected) return false;
    scanner.current++;
    return true;
}

static bool is_digit(char c) {
    return c >= '0' && c <= '9';
}

static bool is_alpha(char c) {
    return (c >= 'a' && c <= 'z') ||
           (c >= 'A' && c <= 'Z') ||
           c == '_';
}

static bool is_alnum(char c) {
    return is_alpha(c) || is_digit(c);
}

/* Verifica se byte é início de sequência UTF-8 multi-byte (acentos). */
static bool is_utf8_continuation(char c) {
    return ((unsigned char)c & 0xC0) == 0x80;
}

static bool is_utf8_start(char c) {
    return ((unsigned char)c & 0x80) != 0;
}

/* ===========================================================================
 * Construção de tokens
 * =========================================================================== */

static BrzToken make_token(BrzTokenType type) {
    BrzToken token;
    token.type   = type;
    token.start  = scanner.start;
    token.length = (int)(scanner.current - scanner.start);
    token.line   = scanner.line;
    return token;
}

static BrzToken error_token(const char* message) {
    BrzToken token;
    token.type   = TOKEN_ERROR;
    token.start  = message;
    token.length = (int)strlen(message);
    token.line   = scanner.line;
    return token;
}

/* ===========================================================================
 * Whitespace e comentários
 * =========================================================================== */

static void skip_whitespace(void) {
    for (;;) {
        char c = peek();
        switch (c) {
            case ' ':
            case '\r':
            case '\t':
                advance();
                break;

            case '/':
                if (peek_next() == '/') {
                    /* Comentário de linha: consome até o fim da linha. */
                    while (peek() != '\n' && !is_at_end()) advance();
                    break;
                } else if (peek_next() == '*') {
                    /* Comentário de bloco. */
                    advance(); advance(); /* consome / * */
                    int depth = 1;
                    while (depth > 0 && !is_at_end()) {
                        if (peek() == '/' && peek_next() == '*') {
                            advance(); advance();
                            depth++;
                        } else if (peek() == '*' && peek_next() == '/') {
                            advance(); advance();
                            depth--;
                        } else {
                            if (peek() == '\n') scanner.line++;
                            advance();
                        }
                    }
                    break;
                }
                return;

            default:
                return;
        }
    }
}

/* ===========================================================================
 * Números
 * =========================================================================== */

static BrzToken scan_number(void) {
    while (is_digit(peek())) advance();

    /* Verifica parte decimal. */
    if (peek() == '.' && is_digit(peek_next())) {
        advance(); /* consome o '.' */
        while (is_digit(peek())) advance();
        return make_token(TOKEN_DOUBLE);
    }

    return make_token(TOKEN_INT);
}

/* ===========================================================================
 * Strings
 * =========================================================================== */

static BrzToken scan_string(void) {
    while (peek() != '"' && !is_at_end()) {
        if (peek() == '\n') scanner.line++;
        if (peek() == '\\') advance(); /* escape */
        advance();
    }

    if (is_at_end()) return error_token("String não terminada.");

    advance(); /* consome o '"' final */
    return make_token(TOKEN_STRING);
}

static BrzToken scan_char(void) {
    if (peek() == '\\') advance(); /* escape */
    if (!is_at_end()) advance();   /* o caractere */

    if (peek() != '\'') return error_token("Caractere não terminado.");

    advance(); /* consome o '\'' final */
    return make_token(TOKEN_CHAR);
}

/* ===========================================================================
 * Palavras-chave
 *
 * Usa trie manual para performance. Compara apenas o sufixo após
 * o primeiro caractere que já foi verificado.
 * =========================================================================== */

static BrzTokenType check_keyword(int start, int length,
                                    const char* rest, BrzTokenType type) {
    if (scanner.current - scanner.start == start + length &&
        memcmp(scanner.start + start, rest, (size_t)length) == 0) {
        return type;
    }
    return TOKEN_IDENTIFIER;
}

static BrzTokenType identifier_type(void) {
    int len = (int)(scanner.current - scanner.start);

    switch (scanner.start[0]) {
        case 'c':
            if (len > 1) {
                switch (scanner.start[1]) {
                    case 'a': return check_keyword(2, 2, "so", TOKEN_CASO);
                    case 'l': return check_keyword(2, 4, "asse", TOKEN_CLASSE);
                    case 'o':
                        if (len > 2 && scanner.start[2] == 'n') {
                            if (len > 3 && scanner.start[3] == 't')
                                return check_keyword(4, 5, "inuar", TOKEN_CONTINUAR);
                            return check_keyword(2, 3, "nst", TOKEN_CONST);
                        }
                        break;
                }
            }
            break;

        case 'd': return check_keyword(1, 1, "e", TOKEN_DE);

        case 'e':
            if (len == 1) return TOKEN_E;
            if (len > 1) {
                switch (scanner.start[1]) {
                    case 'm': return (len == 2) ? TOKEN_EM : TOKEN_IDENTIFIER;
                    case 'n': return check_keyword(2, 7, "quanto", TOKEN_ENQUANTO);
                    case 's':
                        if (len > 2 && scanner.start[2] == 'c')
                            return check_keyword(3, 4, "olha", TOKEN_ESCOLHA);
                        if (len > 2 && scanner.start[2] == 't')
                            return check_keyword(3, 1, "e", TOKEN_ESTE);
                        break;
                }
            }
            break;

        case 'f':
            if (len > 1) {
                switch (scanner.start[1]) {
                    case 'a': return check_keyword(2, 3, "lso", TOKEN_FALSO);
                    case 'u': return check_keyword(2, 4, "ncao", TOKEN_FUNCAO);
                }
            }
            break;

        case 'h': return check_keyword(1, 4, "erda", TOKEN_HERDA);

        case 'i': return check_keyword(1, 7, "mportar", TOKEN_IMPORTAR);

        case 'l':
            if (len > 1) {
                switch (scanner.start[1]) {
                    case 'a': return check_keyword(2, 3, "nce", TOKEN_LANCE);
                    case 'e':
                        if (len > 2 && scanner.start[2] == 'r') {
                            if (len > 3) {
                                switch (scanner.start[3]) {
                                    case 'T': return check_keyword(4, 4, "exto", TOKEN_LER_TEXTO);
                                    case 'N': return check_keyword(4, 5, "umero", TOKEN_LER_NUMERO);
                                    case 'D': return check_keyword(4, 6, "ecimal", TOKEN_LER_DECIMAL);
                                }
                            }
                        }
                        break;
                    /* 'lista' não é mais keyword — usa [] para listas. */
                }
            }
            break;

        case 'm': return check_keyword(1, 6, "ostrar", TOKEN_MOSTRAR);

        case 'n':
            if (len > 1) {
                switch (scanner.start[1]) {
                    case 'a': return check_keyword(2, 1, "o", TOKEN_NAO);
                    case 'o': return check_keyword(2, 2, "vo", TOKEN_NOVO);
                    case 'u': return check_keyword(2, 2, "lo", TOKEN_NULO);
                }
            }
            break;

        case 'o': return check_keyword(1, 1, "u", TOKEN_OU);

        case 'p':
            if (len > 1) {
                switch (scanner.start[1]) {
                    case 'a':
                        if (len > 2 && scanner.start[2] == 'r')
                            return check_keyword(3, 1, "a", TOKEN_PARA);
                        if (len > 2 && scanner.start[2] == 'd')
                            return check_keyword(3, 3, "rao", TOKEN_PADRAO);
                        break;
                    case 'e': return check_keyword(2, 3, "gue", TOKEN_PEGUE);
                }
            }
            break;

        case 'r': return check_keyword(1, 7, "etornar", TOKEN_RETORNAR);

        case 's':
            if (len > 1) {
                switch (scanner.start[1]) {
                    case 'a': return check_keyword(2, 2, "ir", TOKEN_SAIR);
                    case 'e':
                        if (len == 2) return TOKEN_SE;
                        return check_keyword(2, 3, "nao", TOKEN_SENAO);
                    case 'u': return check_keyword(2, 3, "per", TOKEN_SUPER);
                }
            }
            break;

        case 't': return check_keyword(1, 4, "ente", TOKEN_TENTE);

        case 'v':
            if (len > 1) {
                switch (scanner.start[1]) {
                    case 'a': return check_keyword(2, 1, "r", TOKEN_VAR);
                    case 'e': return check_keyword(2, 8, "rdadeiro", TOKEN_VERDADEIRO);
                }
            }
            break;
    }

    return TOKEN_IDENTIFIER;
}

/* ===========================================================================
 * Identificadores e palavras-chave
 * =========================================================================== */

static BrzToken scan_identifier(void) {
    while (is_alnum(peek()) || is_utf8_start(peek())) {
        if (is_utf8_start(peek())) {
            advance(); /* primeiro byte UTF-8 */
            while (is_utf8_continuation(peek())) advance();
        } else {
            advance();
        }
    }
    return make_token(identifier_type());
}

/* ===========================================================================
 * Scanner principal
 * =========================================================================== */

BrzToken brz_scanner_next(void) {
    skip_whitespace();
    scanner.start = scanner.current;

    if (is_at_end()) return make_token(TOKEN_EOF);

    char c = advance();

    /* Newline significativo. */
    if (c == '\n') {
        scanner.line++;
        return make_token(TOKEN_NEWLINE);
    }

    /* Números. */
    if (is_digit(c)) return scan_number();

    /* Identificadores e keywords. */
    if (is_alpha(c) || is_utf8_start(c)) {
        if (is_utf8_start(c)) {
            while (is_utf8_continuation(peek())) advance();
        }
        return scan_identifier();
    }

    /* Operadores e delimitadores. */
    switch (c) {
        case '(': return make_token(TOKEN_PAREN_LEFT);
        case ')': return make_token(TOKEN_PAREN_RIGHT);
        case '{': return make_token(TOKEN_BRACE_LEFT);
        case '}': return make_token(TOKEN_BRACE_RIGHT);
        case '[': return make_token(TOKEN_BRACKET_LEFT);
        case ']': return make_token(TOKEN_BRACKET_RIGHT);
        case ',': return make_token(TOKEN_COMMA);
        case '.': return make_token(TOKEN_DOT);
        case ';': return make_token(TOKEN_SEMICOLON);
        case ':': return make_token(TOKEN_COLON);

        case '+':
            return make_token(match('=') ? TOKEN_PLUS_EQUAL : TOKEN_PLUS);
        case '-':
            return make_token(match('=') ? TOKEN_MINUS_EQUAL : TOKEN_MINUS);
        case '*':
            if (match('*')) return make_token(TOKEN_STAR_STAR);
            return make_token(match('=') ? TOKEN_STAR_EQUAL : TOKEN_STAR);
        case '/':
            return make_token(match('=') ? TOKEN_SLASH_EQUAL : TOKEN_SLASH);
        case '%':
            return make_token(TOKEN_PERCENT);

        case '!':
            return make_token(match('=') ? TOKEN_BANG_EQUAL : TOKEN_BANG);
        case '=':
            if (match('=')) return make_token(TOKEN_EQUAL_EQUAL);
            if (match('>')) return make_token(TOKEN_ARROW);
            return make_token(TOKEN_EQUAL);
        case '>':
            return make_token(match('=') ? TOKEN_GREATER_EQUAL : TOKEN_GREATER);
        case '<':
            return make_token(match('=') ? TOKEN_LESS_EQUAL : TOKEN_LESS);

        case '&':
            if (match('&')) return make_token(TOKEN_AND);
            break;
        case '|':
            if (match('|')) return make_token(TOKEN_OR);
            break;

        case '"': return scan_string();
        case '\'': return scan_char();
    }

    return error_token("Caractere inesperado.");
}
