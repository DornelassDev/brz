/*
 * BRZ Virtual Machine — Chunk de Bytecode
 *
 * Um BrzChunk armazena uma sequência de instruções (bytecodes) junto
 * com um pool de constantes e informações de linha para debug.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#ifndef BRZ_CHUNK_H
#define BRZ_CHUNK_H

#include "common.h"
#include "value.h"

/* ---------------------------------------------------------------------------
 * Opcodes — o conjunto de instruções da BRZ VM
 * --------------------------------------------------------------------------- */

typedef enum {
    /* Constantes e literais */
    OP_CONSTANT,        /* Empilha constante do pool                     */
    OP_CONSTANT_LONG,   /* Empilha constante (índice 24-bit)             */
    OP_NULL,            /* Empilha nulo                                  */
    OP_TRUE,            /* Empilha verdadeiro                            */
    OP_FALSE,           /* Empilha falso                                 */

    /* Aritmética */
    OP_ADD,             /* a + b  (números e concatenação de strings)    */
    OP_SUBTRACT,        /* a - b                                        */
    OP_MULTIPLY,        /* a * b                                        */
    OP_DIVIDE,          /* a / b  (sempre double)                       */
    OP_MODULO,          /* a % b                                        */
    OP_NEGATE,          /* -a                                           */
    OP_POWER,           /* a ** b                                       */

    /* Comparação */
    OP_EQUAL,           /* a == b                                       */
    OP_NOT_EQUAL,       /* a != b                                       */
    OP_GREATER,         /* a > b                                        */
    OP_GREATER_EQUAL,   /* a >= b                                       */
    OP_LESS,            /* a < b                                        */
    OP_LESS_EQUAL,      /* a <= b                                       */

    /* Lógica */
    OP_NOT,             /* !a / nao a                                   */

    /* Variáveis */
    OP_DEFINE_GLOBAL,   /* Define variável global                       */
    OP_GET_GLOBAL,      /* Lê variável global                           */
    OP_SET_GLOBAL,      /* Atribui a variável global                    */
    OP_GET_LOCAL,       /* Lê variável local                            */
    OP_SET_LOCAL,       /* Atribui a variável local                     */
    OP_GET_UPVALUE,     /* Lê upvalue (closure)                         */
    OP_SET_UPVALUE,     /* Atribui a upvalue                            */

    /* Pilha */
    OP_POP,             /* Remove valor do topo da pilha                */

    /* Controle de fluxo */
    OP_JUMP,            /* Salto incondicional                          */
    OP_JUMP_IF_FALSE,   /* Salto condicional (se falso)                 */
    OP_LOOP,            /* Salto para trás (loops)                      */

    /* Funções */
    OP_CALL,            /* Chama função com N argumentos                */
    OP_CLOSURE,         /* Cria closure a partir de função              */
    OP_CLOSE_UPVALUE,   /* Fecha upvalue no topo da pilha               */
    OP_RETURN,          /* Retorna de função                            */

    /* Classes e objetos */
    OP_CLASS,           /* Cria nova classe                             */
    OP_GET_PROPERTY,    /* Acessa propriedade de objeto                 */
    OP_SET_PROPERTY,    /* Atribui a propriedade de objeto              */
    OP_METHOD,          /* Define método em classe                      */
    OP_INVOKE,          /* Chama método diretamente (otimização)        */
    OP_INHERIT,         /* Herança de classe                            */
    OP_GET_SUPER,       /* Acessa método da superclasse                 */
    OP_SUPER_INVOKE,    /* Chama método da superclasse                  */

    /* Listas */
    OP_LIST_NEW,        /* Cria lista com N elementos da pilha          */
    OP_INDEX_GET,       /* Acessa elemento por índice                   */
    OP_INDEX_SET,       /* Atribui a elemento por índice                */

    /* Built-ins */
    OP_PRINT,           /* mostrar() — imprime valor                    */

    /* Total de opcodes (deve ser último). */
    OP_COUNT,
} BrzOpCode;

/* ---------------------------------------------------------------------------
 * Chunk — sequência de bytecodes
 * --------------------------------------------------------------------------- */

typedef struct {
    int      count;         /* Número de bytes escritos.               */
    int      capacity;      /* Capacidade alocada.                     */
    uint8_t* code;          /* Array de bytecodes.                     */
    int*     lines;         /* Número da linha de cada bytecode.       */
    BrzValueArray constants; /* Pool de constantes.                     */
} BrzChunk;

/* Inicializa chunk vazio. */
void brz_chunk_init(BrzChunk* chunk);

/* Escreve um byte no chunk. */
void brz_chunk_write(BrzChunk* chunk, uint8_t byte, int line);

/* Adiciona constante ao pool. Retorna índice. */
int brz_chunk_add_constant(BrzChunk* chunk, BrzValue value);

/* Libera memória do chunk. */
void brz_chunk_free(BrzChunk* chunk);

#endif /* BRZ_CHUNK_H */
