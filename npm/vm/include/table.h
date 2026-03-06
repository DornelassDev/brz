/*
 * BRZ Virtual Machine — Hash Table
 *
 * Tabela hash aberta (open addressing) com probing linear.
 * Usada para variáveis globais, campos de instância, métodos
 * e interning de strings.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#ifndef BRZ_TABLE_H
#define BRZ_TABLE_H

#include "common.h"
#include "value.h"

/* ---------------------------------------------------------------------------
 * Entrada da tabela
 * --------------------------------------------------------------------------- */

typedef struct {
    BrzString*  key;    /* NULL = slot vazio ou tombstone. */
    BrzValue    value;
} BrzEntry;

/* ---------------------------------------------------------------------------
 * Hash table
 * --------------------------------------------------------------------------- */

typedef struct BrzTable {
    int         count;      /* Número de entradas ativas.      */
    int         capacity;   /* Capacidade total (potência de 2). */
    BrzEntry*   entries;
} BrzTable;

/* ---------------------------------------------------------------------------
 * API
 * --------------------------------------------------------------------------- */

void brz_table_init(BrzTable* table);
void brz_table_free(BrzTable* table);

/* Insere ou atualiza. Retorna true se a key era nova. */
bool brz_table_set(BrzTable* table, BrzString* key, BrzValue value);

/* Busca valor pela key. Retorna true se encontrou. */
bool brz_table_get(BrzTable* table, BrzString* key, BrzValue* value);

/* Remove entrada. Retorna true se existia. */
bool brz_table_delete(BrzTable* table, BrzString* key);

/* Copia todas as entradas de 'from' para 'to'. */
void brz_table_add_all(BrzTable* from, BrzTable* to);

/* Busca string internada na tabela (usada pelo string interning). */
BrzString* brz_table_find_string(BrzTable* table, const char* chars,
                                  int length, uint32_t hash);

/* Remove objetos não-marcados (usado pelo GC). */
void brz_table_remove_white(BrzTable* table);

/* Marca todas as entradas da tabela (usado pelo GC). */
void brz_table_mark(BrzTable* table);

#endif /* BRZ_TABLE_H */
