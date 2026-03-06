/*
 * BRZ Virtual Machine — Scanner (Lexer)
 *
 * Transforma código-fonte BRZ em uma sequência de tokens.
 * Reconhece todas as palavras-chave em Português, literais
 * (inteiros, decimais, strings, caracteres) e operadores.
 *
 * O scanner é lazy: produz um token por vez via brz_scanner_next().
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#ifndef BRZ_SCANNER_H
#define BRZ_SCANNER_H

#include "common.h"

/* ---------------------------------------------------------------------------
 * Tipos de token
 * --------------------------------------------------------------------------- */

typedef enum {
    /* Literais */
    TOKEN_INT,              /* 42, 0xFF                    */
    TOKEN_DOUBLE,           /* 3.14                        */
    TOKEN_STRING,           /* "hello"                     */
    TOKEN_CHAR,             /* 'a'                         */
    TOKEN_IDENTIFIER,       /* nome_variavel               */

    /* Operadores aritméticos */
    TOKEN_PLUS,             /* +                           */
    TOKEN_MINUS,            /* -                           */
    TOKEN_STAR,             /* *                           */
    TOKEN_SLASH,            /* /                           */
    TOKEN_PERCENT,          /* %                           */
    TOKEN_STAR_STAR,        /* **                          */

    /* Operadores de atribuição */
    TOKEN_EQUAL,            /* =                           */
    TOKEN_PLUS_EQUAL,       /* +=                          */
    TOKEN_MINUS_EQUAL,      /* -=                          */
    TOKEN_STAR_EQUAL,       /* *=                          */
    TOKEN_SLASH_EQUAL,      /* /=                          */

    /* Operadores de comparação */
    TOKEN_EQUAL_EQUAL,      /* ==                          */
    TOKEN_BANG_EQUAL,       /* !=                          */
    TOKEN_GREATER,          /* >                           */
    TOKEN_GREATER_EQUAL,    /* >=                          */
    TOKEN_LESS,             /* <                           */
    TOKEN_LESS_EQUAL,       /* <=                          */

    /* Operadores lógicos */
    TOKEN_BANG,             /* !                           */
    TOKEN_AND,              /* &&                          */
    TOKEN_OR,               /* ||                          */

    /* Delimitadores */
    TOKEN_PAREN_LEFT,       /* (                           */
    TOKEN_PAREN_RIGHT,      /* )                           */
    TOKEN_BRACE_LEFT,       /* {                           */
    TOKEN_BRACE_RIGHT,      /* }                           */
    TOKEN_BRACKET_LEFT,     /* [                           */
    TOKEN_BRACKET_RIGHT,    /* ]                           */
    TOKEN_COMMA,            /* ,                           */
    TOKEN_DOT,              /* .                           */
    TOKEN_SEMICOLON,        /* ;                           */
    TOKEN_COLON,            /* :                           */
    TOKEN_ARROW,            /* =>                          */

    /* Palavras-chave — Português */
    TOKEN_VAR,              /* var                         */
    TOKEN_CONST,            /* const                       */
    TOKEN_SE,               /* se (if)                     */
    TOKEN_SENAO,            /* senao (else)                */
    TOKEN_ENQUANTO,         /* enquanto (while)            */
    TOKEN_PARA,             /* para (for)                  */
    TOKEN_FUNCAO,           /* funcao (function)           */
    TOKEN_RETORNAR,         /* retornar (return)           */
    TOKEN_CLASSE,           /* classe (class)              */
    TOKEN_ESTE,             /* este (this)                 */
    TOKEN_SUPER,            /* super                       */
    TOKEN_HERDA,            /* herda (extends)             */
    TOKEN_VERDADEIRO,       /* verdadeiro (true)           */
    TOKEN_FALSO,            /* falso (false)               */
    TOKEN_NULO,             /* nulo (null)                 */
    TOKEN_E,                /* e (and)                     */
    TOKEN_OU,               /* ou (or)                     */
    TOKEN_NAO,              /* nao (not)                   */
    TOKEN_MOSTRAR,          /* mostrar (print)             */
    TOKEN_LER_TEXTO,        /* lerTexto (input string)     */
    TOKEN_LER_NUMERO,       /* lerNumero (input int)       */
    TOKEN_LER_DECIMAL,      /* lerDecimal (input double)   */
    TOKEN_SAIR,             /* sair (break)                */
    TOKEN_CONTINUAR,        /* continuar (continue)        */
    TOKEN_IMPORTAR,         /* importar (import)           */
    TOKEN_DE,               /* de (from/of)                */
    TOKEN_EM,               /* em (in)                     */
    TOKEN_ESCOLHA,          /* escolha (switch)            */
    TOKEN_CASO,             /* caso (case)                 */
    TOKEN_PADRAO,           /* padrao (default)            */
    TOKEN_TENTE,            /* tente (try)                 */
    TOKEN_PEGUE,            /* pegue (catch)               */
    TOKEN_LANCE,            /* lance (throw)               */
    TOKEN_NOVO,             /* novo (new)                  */
    TOKEN_LISTA,            /* lista (list type)           */

    /* Especiais */
    TOKEN_NEWLINE,          /* \n (significativo em BRZ)   */
    TOKEN_ERROR,            /* Token de erro               */
    TOKEN_EOF,              /* Fim do arquivo              */
} BrzTokenType;

/* ---------------------------------------------------------------------------
 * Token
 * --------------------------------------------------------------------------- */

typedef struct {
    BrzTokenType type;
    const char*  start;     /* Ponteiro para o início no source.   */
    int          length;    /* Comprimento em bytes.               */
    int          line;      /* Linha no código-fonte.              */
} BrzToken;

/* ---------------------------------------------------------------------------
 * API do Scanner
 * --------------------------------------------------------------------------- */

/* Inicializa o scanner com o código-fonte. */
void brz_scanner_init(const char* source);

/* Produz o próximo token. */
BrzToken brz_scanner_next(void);

#endif /* BRZ_SCANNER_H */
