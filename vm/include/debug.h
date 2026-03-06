/*
 * BRZ Virtual Machine — Debug / Disassembler
 *
 * Utilitários para desmontar e inspecionar bytecodes.
 * Essencial para desenvolvimento e depuração da VM.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#ifndef BRZ_DEBUG_H
#define BRZ_DEBUG_H

#include "common.h"
#include "chunk.h"

/* Desmonta todas as instruções de um chunk. */
void brz_debug_disassemble(BrzChunk* chunk, const char* name);

/* Desmonta uma única instrução. Retorna o offset da próxima. */
int brz_debug_instruction(BrzChunk* chunk, int offset);

#endif /* BRZ_DEBUG_H */
