/*
 * BRZ Virtual Machine — Gerenciamento de Memória & Garbage Collector
 *
 * Implementa o alocador central (brz_realloc) e o garbage collector
 * mark-and-sweep. O GC rastreia todos os objetos heap através de uma
 * lista intrusiva e libera os que não são mais acessíveis.
 *
 * Fases do GC:
 *   1. Mark   — percorre raízes (pilha, globals, closures) marcando objetos
 *   2. Sweep  — varre a lista de objetos, liberando os não-marcados
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#include "memory.h"
#include "object.h"
#include "table.h"
#include "vm.h"
#include "compiler.h"

#include <stdio.h>

/* ===========================================================================
 * Alocador central
 *
 * TODA alocação/realocação/liberação da VM passa por esta função.
 * Isso permite tracking preciso de memória usada e disparo automático
 * do garbage collector.
 * =========================================================================== */

void* brz_realloc(void* ptr, size_t old_size, size_t new_size) {
    brz_vm.bytes_allocated += new_size;
    brz_vm.bytes_allocated -= old_size;

    /* Dispara GC se necessário. */
    if (new_size > old_size) {
#ifdef BRZ_DEBUG_STRESS_GC
        brz_gc_collect();
#else
        if (brz_vm.bytes_allocated > brz_vm.gc_threshold) {
            brz_gc_collect();
        }
#endif
    }

    if (new_size == 0) {
        free(ptr);
        return NULL;
    }

    void* result = realloc(ptr, new_size);
    if (result == NULL) {
        fprintf(stderr, "[BRZ] Erro fatal: sem memória.\n");
        exit(1);
    }
    return result;
}

/* ===========================================================================
 * Fase de marcação
 * =========================================================================== */

void brz_gc_mark_value(BrzValue value) {
    if (BRZ_IS_OBJECT(value)) {
        brz_gc_mark_object(BRZ_AS_OBJECT(value));
    }
}

void brz_gc_mark_object(BrzObject* object) {
    if (object == NULL) return;
    if (object->marked) return;

#ifdef BRZ_DEBUG_LOG_GC
    printf("  [GC] mark %p ", (void*)object);
    brz_value_print(BRZ_OBJECT(object));
    printf("\n");
#endif

    object->marked = true;

    /* Adiciona à worklist para processamento. */
    if (brz_vm.gray_count + 1 > brz_vm.gray_capacity) {
        brz_vm.gray_capacity = BRZ_GROW_CAP(brz_vm.gray_capacity);
        /* Usa realloc direto — a gray stack NÃO é rastreada pelo GC. */
        brz_vm.gray_stack = (BrzObject**)realloc(
            brz_vm.gray_stack,
            sizeof(BrzObject*) * (size_t)brz_vm.gray_capacity
        );
        if (brz_vm.gray_stack == NULL) {
            fprintf(stderr, "[BRZ] Erro fatal: sem memória para gray stack.\n");
            exit(1);
        }
    }
    brz_vm.gray_stack[brz_vm.gray_count++] = object;
}

/* ---------------------------------------------------------------------------
 * Blackening — processa um objeto cinza, marcando suas referências
 * --------------------------------------------------------------------------- */

static void mark_array(BrzValueArray* array) {
    for (int i = 0; i < array->count; i++) {
        brz_gc_mark_value(array->values[i]);
    }
}

static void blacken_object(BrzObject* object) {
#ifdef BRZ_DEBUG_LOG_GC
    printf("  [GC] blacken %p ", (void*)object);
    brz_value_print(BRZ_OBJECT(object));
    printf("\n");
#endif

    switch (object->type) {
        case OBJ_STRING:
        case OBJ_NATIVE:
            /* Strings e natives não referenciam outros objetos. */
            break;

        case OBJ_FUNCTION: {
            BrzFunction* fn = (BrzFunction*)object;
            brz_gc_mark_object((BrzObject*)fn->name);
            mark_array(&fn->chunk.constants);
            break;
        }

        case OBJ_CLOSURE: {
            BrzClosure* closure = (BrzClosure*)object;
            brz_gc_mark_object((BrzObject*)closure->function);
            for (int i = 0; i < closure->upvalue_count; i++) {
                brz_gc_mark_object((BrzObject*)closure->upvalues[i]);
            }
            break;
        }

        case OBJ_UPVALUE:
            brz_gc_mark_value(((BrzUpvalue*)object)->closed);
            break;

        case OBJ_CLASS: {
            BrzClass* klass = (BrzClass*)object;
            brz_gc_mark_object((BrzObject*)klass->name);
            brz_table_mark(&klass->methods);
            if (klass->superclass) {
                brz_gc_mark_object((BrzObject*)klass->superclass);
            }
            break;
        }

        case OBJ_INSTANCE: {
            BrzInstance* instance = (BrzInstance*)object;
            brz_gc_mark_object((BrzObject*)instance->klass);
            brz_table_mark(&instance->fields);
            break;
        }

        case OBJ_BOUND_METHOD: {
            BrzBoundMethod* bound = (BrzBoundMethod*)object;
            brz_gc_mark_value(bound->receiver);
            brz_gc_mark_object((BrzObject*)bound->method);
            break;
        }

        case OBJ_LIST: {
            BrzList* list = (BrzList*)object;
            mark_array(&list->items);
            break;
        }
    }
}

/* ---------------------------------------------------------------------------
 * Marcação de raízes
 * --------------------------------------------------------------------------- */

static void mark_roots(void) {
    /* Pilha de valores. */
    for (BrzValue* slot = brz_vm.stack; slot < brz_vm.stack_top; slot++) {
        brz_gc_mark_value(*slot);
    }

    /* Frames de chamada (closures). */
    for (int i = 0; i < brz_vm.frame_count; i++) {
        brz_gc_mark_object((BrzObject*)brz_vm.frames[i].closure);
    }

    /* Upvalues abertos. */
    for (BrzUpvalue* uv = brz_vm.open_upvalues; uv != NULL; uv = uv->next) {
        brz_gc_mark_object((BrzObject*)uv);
    }

    /* Variáveis globais. */
    brz_table_mark(&brz_vm.globals);

    /* Raízes do compilador (se estiver compilando). */
    brz_compiler_mark_roots();

    /* String "iniciar" (construtor). */
    brz_gc_mark_object((BrzObject*)brz_vm.init_string);
}

/* ---------------------------------------------------------------------------
 * Trace references — processa toda a gray stack
 * --------------------------------------------------------------------------- */

static void trace_references(void) {
    while (brz_vm.gray_count > 0) {
        BrzObject* object = brz_vm.gray_stack[--brz_vm.gray_count];
        blacken_object(object);
    }
}

/* ===========================================================================
 * Liberação de objetos
 * =========================================================================== */

static void free_object(BrzObject* object) {
#ifdef BRZ_DEBUG_LOG_GC
    printf("  [GC] free %p tipo %d\n", (void*)object, object->type);
#endif

    switch (object->type) {
        case OBJ_STRING: {
            BrzString* string = (BrzString*)object;
            brz_realloc(object, sizeof(BrzString) + (size_t)string->length + 1, 0);
            break;
        }

        case OBJ_FUNCTION: {
            BrzFunction* fn = (BrzFunction*)object;
            brz_chunk_free(&fn->chunk);
            BRZ_FREE(BrzFunction, object);
            break;
        }

        case OBJ_CLOSURE: {
            BrzClosure* closure = (BrzClosure*)object;
            BRZ_FREE_ARRAY(BrzUpvalue*, closure->upvalues,
                           closure->upvalue_count);
            BRZ_FREE(BrzClosure, object);
            break;
        }

        case OBJ_UPVALUE:
            BRZ_FREE(BrzUpvalue, object);
            break;

        case OBJ_NATIVE:
            BRZ_FREE(BrzNative, object);
            break;

        case OBJ_CLASS: {
            BrzClass* klass = (BrzClass*)object;
            brz_table_free(&klass->methods);
            BRZ_FREE(BrzClass, object);
            break;
        }

        case OBJ_INSTANCE: {
            BrzInstance* instance = (BrzInstance*)object;
            brz_table_free(&instance->fields);
            BRZ_FREE(BrzInstance, object);
            break;
        }

        case OBJ_BOUND_METHOD:
            BRZ_FREE(BrzBoundMethod, object);
            break;

        case OBJ_LIST: {
            BrzList* list = (BrzList*)object;
            brz_value_array_free(&list->items);
            BRZ_FREE(BrzList, object);
            break;
        }
    }
}

/* ===========================================================================
 * Fase de varredura (sweep)
 * =========================================================================== */

static void sweep(void) {
    BrzObject* previous = NULL;
    BrzObject* object   = brz_vm.objects;

    while (object != NULL) {
        if (object->marked) {
            object->marked = false;
            previous = object;
            object   = object->next;
        } else {
            BrzObject* unreached = object;
            object = object->next;

            if (previous != NULL) {
                previous->next = object;
            } else {
                brz_vm.objects = object;
            }

            free_object(unreached);
        }
    }
}

/* ===========================================================================
 * Coleta de lixo — ciclo completo
 * =========================================================================== */

void brz_gc_collect(void) {
#ifdef BRZ_DEBUG_LOG_GC
    printf("== GC início ==\n");
    size_t before = brz_vm.bytes_allocated;
#endif

    mark_roots();
    trace_references();
    brz_table_remove_white(&brz_vm.strings);
    sweep();

    brz_vm.gc_threshold = brz_vm.bytes_allocated * BRZ_GC_GROW_FACTOR;

#ifdef BRZ_DEBUG_LOG_GC
    printf("== GC fim. %zu → %zu bytes (threshold: %zu) ==\n",
           before, brz_vm.bytes_allocated, brz_vm.gc_threshold);
#endif
}

/* ===========================================================================
 * Shutdown — libera todos os objetos
 * =========================================================================== */

void brz_gc_free_objects(void) {
    BrzObject* object = brz_vm.objects;
    while (object != NULL) {
        BrzObject* next = object->next;
        free_object(object);
        object = next;
    }
    free(brz_vm.gray_stack);
}
