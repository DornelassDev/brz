/*
 * BRZ Virtual Machine — Definições Comuns
 *
 * Tipos base, macros e configurações usadas por todos os módulos.
 * Este header é o primeiro include de qualquer arquivo da VM.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#ifndef BRZ_COMMON_H
#define BRZ_COMMON_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* ---------------------------------------------------------------------------
 * Configuração
 * --------------------------------------------------------------------------- */

#define BRZ_VERSION_MAJOR  2
#define BRZ_VERSION_MINOR  0
#define BRZ_VERSION_PATCH  0
#define BRZ_VERSION_STRING "2.0.0"

/* Tamanho máximo da pilha de valores. */
#define BRZ_STACK_MAX      1024

/* Tamanho máximo de frames de chamada (profundidade de recursão). */
#define BRZ_FRAMES_MAX     256

/* Capacidade inicial de arrays dinâmicos. */
#define BRZ_INITIAL_CAP    8

/* Fator de carga máximo da hash table (0.0 – 1.0). */
#define BRZ_TABLE_MAX_LOAD 0.75

/* Limiar do GC: coleta quando allocBytes ultrapassa este valor. */
#define BRZ_GC_INITIAL_THRESHOLD (1024 * 1024)  /* 1 MB */

/* Fator de crescimento do threshold após cada coleta. */
#define BRZ_GC_GROW_FACTOR 2

/* ---------------------------------------------------------------------------
 * Macros utilitárias
 * --------------------------------------------------------------------------- */

/* Cresce capacidade: 0 → INITIAL, senão dobra. */
#define BRZ_GROW_CAP(cap) \
    ((cap) < BRZ_INITIAL_CAP ? BRZ_INITIAL_CAP : (cap) * 2)

/* Realoca array tipado. */
#define BRZ_GROW_ARRAY(type, ptr, old_cap, new_cap) \
    (type*)brz_realloc((ptr), sizeof(type) * (old_cap), sizeof(type) * (new_cap))

/* Libera array tipado. */
#define BRZ_FREE_ARRAY(type, ptr, cap) \
    brz_realloc((ptr), sizeof(type) * (cap), 0)

/* Aloca um único objeto tipado. */
#define BRZ_ALLOCATE(type, count) \
    (type*)brz_realloc(NULL, 0, sizeof(type) * (count))

/* Libera um único objeto tipado. */
#define BRZ_FREE(type, ptr) \
    brz_realloc((ptr), sizeof(type), 0)

/* ---------------------------------------------------------------------------
 * Gerenciamento de memória (implementado em memory.c)
 * --------------------------------------------------------------------------- */

/*
 * Função central de alocação. Toda memória da VM passa por aqui,
 * permitindo tracking preciso para o garbage collector.
 *
 * - old_size == 0, new_size >  0  →  malloc
 * - old_size >  0, new_size >  0  →  realloc
 * - old_size >  0, new_size == 0  →  free
 */
void* brz_realloc(void* ptr, size_t old_size, size_t new_size);

/* ---------------------------------------------------------------------------
 * Debug (ativo apenas em builds de desenvolvimento)
 * --------------------------------------------------------------------------- */

#ifdef BRZ_DEBUG
  #define BRZ_DEBUG_TRACE_EXECUTION
  #define BRZ_DEBUG_PRINT_CODE
  #define BRZ_DEBUG_STRESS_GC
  #define BRZ_DEBUG_LOG_GC
#endif

#endif /* BRZ_COMMON_H */
