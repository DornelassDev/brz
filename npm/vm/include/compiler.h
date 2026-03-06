/*
 * BRZ Virtual Machine — Compilador
 *
 * Compilador single-pass usando Pratt parser. Transforma tokens
 * diretamente em bytecode, sem construir AST intermediário.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#ifndef BRZ_COMPILER_H
#define BRZ_COMPILER_H

#include "common.h"
#include "chunk.h"
#include "object.h"

/* Compila código-fonte para uma closure. Retorna NULL em erro. */
BrzClosure* brz_compile(const char* source);

/* Marca raízes do compilador para o GC. */
void brz_compiler_mark_roots(void);

#endif /* BRZ_COMPILER_H */
