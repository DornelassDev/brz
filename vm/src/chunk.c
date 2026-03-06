/*
 * BRZ Virtual Machine — Chunk de Bytecode
 *
 * Gerencia o array de bytes (instruções) e o pool de constantes
 * associado a cada unidade de compilação.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#include "chunk.h"

/* ===========================================================================
 * Inicialização e liberação
 * =========================================================================== */

void brz_chunk_init(BrzChunk* chunk) {
    chunk->count    = 0;
    chunk->capacity = 0;
    chunk->code     = NULL;
    chunk->lines    = NULL;
    brz_value_array_init(&chunk->constants);
}

void brz_chunk_free(BrzChunk* chunk) {
    BRZ_FREE_ARRAY(uint8_t, chunk->code, chunk->capacity);
    BRZ_FREE_ARRAY(int, chunk->lines, chunk->capacity);
    brz_value_array_free(&chunk->constants);
    brz_chunk_init(chunk);
}

/* ===========================================================================
 * Escrita de bytecodes
 * =========================================================================== */

void brz_chunk_write(BrzChunk* chunk, uint8_t byte, int line) {
    if (chunk->count + 1 > chunk->capacity) {
        int old_cap     = chunk->capacity;
        chunk->capacity = BRZ_GROW_CAP(old_cap);
        chunk->code     = BRZ_GROW_ARRAY(uint8_t, chunk->code,
                                          old_cap, chunk->capacity);
        chunk->lines    = BRZ_GROW_ARRAY(int, chunk->lines,
                                          old_cap, chunk->capacity);
    }
    chunk->code[chunk->count]  = byte;
    chunk->lines[chunk->count] = line;
    chunk->count++;
}

/* ===========================================================================
 * Pool de constantes
 * =========================================================================== */

int brz_chunk_add_constant(BrzChunk* chunk, BrzValue value) {
    brz_value_array_write(&chunk->constants, value);
    return chunk->constants.count - 1;
}
