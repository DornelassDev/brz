/*
 * BRZ Virtual Machine — Hash Table
 *
 * Implementação de tabela hash com endereçamento aberto e
 * probing linear. Tombstones são usados para manter a cadeia
 * de probing intacta após deleções.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#include "table.h"
#include "memory.h"
#include "object.h"

/* ===========================================================================
 * Inicialização e liberação
 * =========================================================================== */

void brz_table_init(BrzTable* table) {
    table->count    = 0;
    table->capacity = 0;
    table->entries  = NULL;
}

void brz_table_free(BrzTable* table) {
    BRZ_FREE_ARRAY(BrzEntry, table->entries, table->capacity);
    brz_table_init(table);
}

/* ===========================================================================
 * Busca interna
 *
 * Encontra o slot correspondente a uma key, ou o primeiro slot
 * disponível para inserção. Usa probing linear com tombstone reuse.
 * =========================================================================== */

static BrzEntry* find_entry(BrzEntry* entries, int capacity, BrzString* key) {
    uint32_t index = key->hash & (uint32_t)(capacity - 1);
    BrzEntry* tombstone = NULL;

    for (;;) {
        BrzEntry* entry = &entries[index];

        if (entry->key == NULL) {
            if (BRZ_IS_NULL(entry->value)) {
                /* Slot vazio. Retorna tombstone se encontramos um antes. */
                return tombstone != NULL ? tombstone : entry;
            } else {
                /* Tombstone. Guarda referência para reuso. */
                if (tombstone == NULL) tombstone = entry;
            }
        } else if (entry->key == key) {
            /* Strings são internadas, então comparação por ponteiro basta. */
            return entry;
        }

        index = (index + 1) & (uint32_t)(capacity - 1);
    }
}

/* ===========================================================================
 * Rehash
 * =========================================================================== */

static void adjust_capacity(BrzTable* table, int capacity) {
    BrzEntry* entries = BRZ_ALLOCATE(BrzEntry, capacity);

    for (int i = 0; i < capacity; i++) {
        entries[i].key   = NULL;
        entries[i].value = BRZ_NULL;
    }

    /* Reconta, pois tombstones não são re-inseridos. */
    table->count = 0;

    for (int i = 0; i < table->capacity; i++) {
        BrzEntry* entry = &table->entries[i];
        if (entry->key == NULL) continue;

        BrzEntry* dest = find_entry(entries, capacity, entry->key);
        dest->key   = entry->key;
        dest->value = entry->value;
        table->count++;
    }

    BRZ_FREE_ARRAY(BrzEntry, table->entries, table->capacity);
    table->entries  = entries;
    table->capacity = capacity;
}

/* ===========================================================================
 * API pública
 * =========================================================================== */

bool brz_table_set(BrzTable* table, BrzString* key, BrzValue value) {
    if (table->count + 1 > (int)(table->capacity * BRZ_TABLE_MAX_LOAD)) {
        int capacity = BRZ_GROW_CAP(table->capacity);
        adjust_capacity(table, capacity);
    }

    BrzEntry* entry = find_entry(table->entries, table->capacity, key);
    bool is_new = (entry->key == NULL);

    /* Incrementa count apenas se estamos usando um slot verdadeiramente vazio
     * (não um tombstone), para manter o fator de carga correto. */
    if (is_new && BRZ_IS_NULL(entry->value)) {
        table->count++;
    }

    entry->key   = key;
    entry->value = value;
    return is_new;
}

bool brz_table_get(BrzTable* table, BrzString* key, BrzValue* value) {
    if (table->count == 0) return false;

    BrzEntry* entry = find_entry(table->entries, table->capacity, key);
    if (entry->key == NULL) return false;

    *value = entry->value;
    return true;
}

bool brz_table_delete(BrzTable* table, BrzString* key) {
    if (table->count == 0) return false;

    BrzEntry* entry = find_entry(table->entries, table->capacity, key);
    if (entry->key == NULL) return false;

    /* Coloca tombstone: key NULL com valor true (não-nulo). */
    entry->key   = NULL;
    entry->value = BRZ_BOOL(true);
    return true;
}

void brz_table_add_all(BrzTable* from, BrzTable* to) {
    for (int i = 0; i < from->capacity; i++) {
        BrzEntry* entry = &from->entries[i];
        if (entry->key != NULL) {
            brz_table_set(to, entry->key, entry->value);
        }
    }
}

/* ===========================================================================
 * String interning — busca por conteúdo
 * =========================================================================== */

BrzString* brz_table_find_string(BrzTable* table, const char* chars,
                                  int length, uint32_t hash) {
    if (table->count == 0) return NULL;

    uint32_t index = hash & (uint32_t)(table->capacity - 1);

    for (;;) {
        BrzEntry* entry = &table->entries[index];

        if (entry->key == NULL) {
            /* Slot vazio (não tombstone) → string não existe. */
            if (BRZ_IS_NULL(entry->value)) return NULL;
        } else if (entry->key->length == length &&
                   entry->key->hash == hash &&
                   memcmp(entry->key->chars, chars, (size_t)length) == 0) {
            return entry->key;
        }

        index = (index + 1) & (uint32_t)(table->capacity - 1);
    }
}

/* ===========================================================================
 * GC helpers
 * =========================================================================== */

void brz_table_remove_white(BrzTable* table) {
    for (int i = 0; i < table->capacity; i++) {
        BrzEntry* entry = &table->entries[i];
        if (entry->key != NULL && !entry->key->obj.marked) {
            brz_table_delete(table, entry->key);
        }
    }
}

void brz_table_mark(BrzTable* table) {
    for (int i = 0; i < table->capacity; i++) {
        BrzEntry* entry = &table->entries[i];
        if (entry->key != NULL) {
            brz_gc_mark_object((BrzObject*)entry->key);
            brz_gc_mark_value(entry->value);
        }
    }
}
