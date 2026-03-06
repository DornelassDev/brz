/*
 * BRZ Virtual Machine — Gerenciamento de Memória & Garbage Collector
 *
 * Garbage collector mark-and-sweep com threshold adaptativo.
 * Toda alocação da VM passa por brz_realloc(), que rastreia o total
 * de bytes e dispara a coleta quando necessário.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#ifndef BRZ_MEMORY_H
#define BRZ_MEMORY_H

#include "common.h"
#include "value.h"

/* ---------------------------------------------------------------------------
 * API de memória
 * --------------------------------------------------------------------------- */

/* Marca um valor como acessível (fase de marcação do GC). */
void brz_gc_mark_value(BrzValue value);

/* Marca um objeto como acessível. */
void brz_gc_mark_object(BrzObject* object);

/* Executa ciclo completo de coleta de lixo. */
void brz_gc_collect(void);

/* Libera todos os objetos (chamado no shutdown da VM). */
void brz_gc_free_objects(void);

#endif /* BRZ_MEMORY_H */
