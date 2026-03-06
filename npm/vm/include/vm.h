/*
 * BRZ Virtual Machine — Máquina Virtual
 *
 * A VM executa bytecodes compilados a partir do código-fonte BRZ.
 * Usa uma pilha de valores, frames de chamada e um dispatch loop
 * para interpretar instruções.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#ifndef BRZ_VM_H
#define BRZ_VM_H

#include "common.h"
#include "value.h"
#include "chunk.h"
#include "table.h"
#include "object.h"

/* ---------------------------------------------------------------------------
 * Frame de chamada
 *
 * Cada chamada de função cria um CallFrame que rastreia a closure
 * sendo executada, o instruction pointer e a base da pilha.
 * --------------------------------------------------------------------------- */

typedef struct {
    BrzClosure* closure;    /* Closure em execução.                     */
    uint8_t*    ip;         /* Instruction pointer (próximo bytecode).  */
    BrzValue*   slots;      /* Base da janela de pilha deste frame.     */
} CallFrame;

/* ---------------------------------------------------------------------------
 * Resultado de interpretação
 * --------------------------------------------------------------------------- */

typedef enum {
    INTERPRET_OK,
    INTERPRET_COMPILE_ERROR,
    INTERPRET_RUNTIME_ERROR,
} BrzInterpretResult;

/* ---------------------------------------------------------------------------
 * Struct principal da VM
 *
 * Instância global — uma por processo. Contém toda a state
 * necessária para executar bytecodes BRZ.
 * --------------------------------------------------------------------------- */

typedef struct {
    /* --- Frames de chamada ------------------------------------------- */
    CallFrame   frames[BRZ_FRAMES_MAX];
    int         frame_count;

    /* --- Pilha de valores -------------------------------------------- */
    BrzValue    stack[BRZ_STACK_MAX];
    BrzValue*   stack_top;

    /* --- Variáveis globais ------------------------------------------- */
    BrzTable    globals;

    /* --- Interning de strings ---------------------------------------- */
    BrzTable    strings;

    /* --- Lista de upvalues abertos ----------------------------------- */
    BrzUpvalue* open_upvalues;

    /* --- String "iniciar" (cache para lookup de construtor) ----------- */
    BrzString*  init_string;

    /* --- Garbage collector ------------------------------------------- */
    BrzObject*  objects;            /* Lista intrusiva de todos os objetos.  */
    size_t      bytes_allocated;    /* Total de bytes alocados pela VM.     */
    size_t      gc_threshold;       /* Limiar para disparar coleta.         */

    BrzObject** gray_stack;         /* Worklist do GC (tricolor marking).   */
    int         gray_count;
    int         gray_capacity;
} BrzVM;

/* ---------------------------------------------------------------------------
 * Instância global da VM (definida em vm.c)
 * --------------------------------------------------------------------------- */

extern BrzVM brz_vm;

/* ---------------------------------------------------------------------------
 * API pública
 * --------------------------------------------------------------------------- */

/* Inicializa a VM. Deve ser chamada antes de qualquer operação. */
void brz_vm_init(void);

/* Libera todos os recursos da VM. */
void brz_vm_free(void);

/* Interpreta código-fonte BRZ. */
BrzInterpretResult brz_vm_interpret(const char* source);

/* Empilha um valor. */
void brz_vm_push(BrzValue value);

/* Desempilha um valor. */
BrzValue brz_vm_pop(void);

/* Registra função nativa global. */
void brz_vm_define_native(const char* name, BrzNativeFn function, int arity);

#endif /* BRZ_VM_H */
