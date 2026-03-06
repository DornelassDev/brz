/*
 * BRZ Virtual Machine — Objetos Heap
 *
 * Implementa alocação, impressão e conversão dos objetos que
 * vivem na heap: strings, funções, closures, classes, instâncias, listas.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#include "object.h"
#include "memory.h"
#include "table.h"
#include "vm.h"

#include <stdio.h>
#include <string.h>

/* ===========================================================================
 * Alocação base de objetos
 *
 * Todo objeto começa com um BrzObject header. Esta função aloca
 * o bloco, inicializa o header e insere na lista do GC.
 * =========================================================================== */

static BrzObject* allocate_object(size_t size, BrzObjectType type) {
    BrzObject* object = (BrzObject*)brz_realloc(NULL, 0, size);
    object->type   = type;
    object->marked = false;

    /* Insere na lista intrusiva de objetos (para o GC rastrear). */
    object->next   = brz_vm.objects;
    brz_vm.objects = object;

#ifdef BRZ_DEBUG_LOG_GC
    printf("  [GC] alloc %zu bytes para %d\n", size, type);
#endif

    return object;
}

#define ALLOCATE_OBJ(type, obj_type) \
    (type*)allocate_object(sizeof(type), obj_type)

/* ===========================================================================
 * Hash FNV-1a — usado para strings
 * =========================================================================== */

static uint32_t hash_string(const char* key, int length) {
    uint32_t hash = 2166136261u;
    for (int i = 0; i < length; i++) {
        hash ^= (uint8_t)key[i];
        hash *= 16777619u;
    }
    return hash;
}

/* ===========================================================================
 * Strings
 * =========================================================================== */

static BrzString* allocate_string(char* chars, int length, uint32_t hash) {
    /* Aloca struct + flexible array. */
    BrzString* string = (BrzString*)allocate_object(
        sizeof(BrzString) + (size_t)length + 1, OBJ_STRING
    );
    string->length = length;
    string->hash   = hash;
    memcpy(string->chars, chars, (size_t)length);
    string->chars[length] = '\0';

    /* Interna a string na tabela global. */
    /* Precisamos marcar antes de inserir na tabela para evitar que o GC
     * colete a string durante a inserção (que pode alocar). */
    brz_gc_mark_object((BrzObject*)string);
    brz_table_set(&brz_vm.strings, string, BRZ_NULL);

    return string;
}

BrzString* brz_string_copy(const char* chars, int length) {
    uint32_t hash = hash_string(chars, length);

    /* Verifica se já existe (interning). */
    BrzString* interned = brz_table_find_string(
        &brz_vm.strings, chars, length, hash
    );
    if (interned != NULL) return interned;

    return allocate_string((char*)chars, length, hash);
}

BrzString* brz_string_take(char* chars, int length) {
    uint32_t hash = hash_string(chars, length);

    BrzString* interned = brz_table_find_string(
        &brz_vm.strings, chars, length, hash
    );
    if (interned != NULL) {
        BRZ_FREE_ARRAY(char, chars, (size_t)length + 1);
        return interned;
    }

    BrzString* string = allocate_string(chars, length, hash);
    /* A string foi copiada para dentro da flexible array, liberar original. */
    BRZ_FREE_ARRAY(char, chars, (size_t)length + 1);
    return string;
}

BrzString* brz_string_concat(BrzString* a, BrzString* b) {
    int length = a->length + b->length;
    char* chars = BRZ_ALLOCATE(char, length + 1);
    memcpy(chars, a->chars, (size_t)a->length);
    memcpy(chars + a->length, b->chars, (size_t)b->length);
    chars[length] = '\0';

    uint32_t hash = hash_string(chars, length);

    BrzString* interned = brz_table_find_string(
        &brz_vm.strings, chars, length, hash
    );
    if (interned != NULL) {
        BRZ_FREE_ARRAY(char, chars, (size_t)length + 1);
        return interned;
    }

    BrzString* result = allocate_string(chars, length, hash);
    BRZ_FREE_ARRAY(char, chars, (size_t)length + 1);
    return result;
}

/* ===========================================================================
 * Funções
 * =========================================================================== */

BrzFunction* brz_function_new(void) {
    BrzFunction* function = ALLOCATE_OBJ(BrzFunction, OBJ_FUNCTION);
    function->arity         = 0;
    function->upvalue_count = 0;
    function->name          = NULL;
    brz_chunk_init(&function->chunk);
    return function;
}

/* ===========================================================================
 * Closures
 * =========================================================================== */

BrzClosure* brz_closure_new(BrzFunction* function) {
    BrzUpvalue** upvalues = BRZ_ALLOCATE(BrzUpvalue*, function->upvalue_count);
    for (int i = 0; i < function->upvalue_count; i++) {
        upvalues[i] = NULL;
    }

    BrzClosure* closure = ALLOCATE_OBJ(BrzClosure, OBJ_CLOSURE);
    closure->function      = function;
    closure->upvalues      = upvalues;
    closure->upvalue_count = function->upvalue_count;
    return closure;
}

/* ===========================================================================
 * Upvalues
 * =========================================================================== */

BrzUpvalue* brz_upvalue_new(BrzValue* slot) {
    BrzUpvalue* upvalue = ALLOCATE_OBJ(BrzUpvalue, OBJ_UPVALUE);
    upvalue->location = slot;
    upvalue->closed   = BRZ_NULL;
    upvalue->next     = NULL;
    return upvalue;
}

/* ===========================================================================
 * Funções nativas
 * =========================================================================== */

BrzNative* brz_native_new(BrzNativeFn function, BrzString* name, int arity) {
    BrzNative* native = ALLOCATE_OBJ(BrzNative, OBJ_NATIVE);
    native->function = function;
    native->name     = name;
    native->arity    = arity;
    return native;
}

/* ===========================================================================
 * Classes
 * =========================================================================== */

BrzClass* brz_class_new(BrzString* name) {
    BrzClass* klass = ALLOCATE_OBJ(BrzClass, OBJ_CLASS);
    klass->name       = name;
    klass->superclass = NULL;
    brz_table_init(&klass->methods);
    return klass;
}

/* ===========================================================================
 * Instâncias
 * =========================================================================== */

BrzInstance* brz_instance_new(BrzClass* klass) {
    BrzInstance* instance = ALLOCATE_OBJ(BrzInstance, OBJ_INSTANCE);
    instance->klass = klass;
    brz_table_init(&instance->fields);
    return instance;
}

/* ===========================================================================
 * Bound Methods
 * =========================================================================== */

BrzBoundMethod* brz_bound_method_new(BrzValue receiver, BrzClosure* method) {
    BrzBoundMethod* bound = ALLOCATE_OBJ(BrzBoundMethod, OBJ_BOUND_METHOD);
    bound->receiver = receiver;
    bound->method   = method;
    return bound;
}

/* ===========================================================================
 * Listas
 * =========================================================================== */

BrzList* brz_list_new(void) {
    BrzList* list = ALLOCATE_OBJ(BrzList, OBJ_LIST);
    brz_value_array_init(&list->items);
    return list;
}

/* ===========================================================================
 * Impressão de objetos
 * =========================================================================== */

void brz_object_print(BrzValue value) {
    switch (brz_obj_type(value)) {
        case OBJ_STRING:
            printf("%s", BRZ_AS_CSTRING(value));
            break;

        case OBJ_FUNCTION: {
            BrzFunction* fn = BRZ_AS_FUNCTION(value);
            if (fn->name == NULL) {
                printf("<script>");
            } else {
                printf("<funcao %s>", fn->name->chars);
            }
            break;
        }

        case OBJ_CLOSURE: {
            BrzFunction* fn = BRZ_AS_CLOSURE(value)->function;
            if (fn->name == NULL) {
                printf("<script>");
            } else {
                printf("<funcao %s>", fn->name->chars);
            }
            break;
        }

        case OBJ_UPVALUE:
            printf("<upvalue>");
            break;

        case OBJ_NATIVE:
            printf("<nativa %s>", BRZ_AS_NATIVE(value)->name->chars);
            break;

        case OBJ_CLASS:
            printf("<classe %s>", BRZ_AS_CLASS(value)->name->chars);
            break;

        case OBJ_INSTANCE:
            printf("<instancia %s>",
                   BRZ_AS_INSTANCE(value)->klass->name->chars);
            break;

        case OBJ_BOUND_METHOD: {
            BrzFunction* fn = BRZ_AS_BOUND_METHOD(value)->method->function;
            printf("<metodo %s>", fn->name ? fn->name->chars : "?");
            break;
        }

        case OBJ_LIST: {
            BrzList* list = BRZ_AS_LIST(value);
            printf("[");
            for (int i = 0; i < list->items.count; i++) {
                if (i > 0) printf(", ");
                brz_value_print(list->items.values[i]);
            }
            printf("]");
            break;
        }
    }
}

/* ===========================================================================
 * Conversão de objeto para string C
 * =========================================================================== */

char* brz_object_to_cstring(BrzValue value) {
    char buffer[256];

    switch (brz_obj_type(value)) {
        case OBJ_STRING: {
            BrzString* s = BRZ_AS_STRING(value);
            char* result = BRZ_ALLOCATE(char, s->length + 1);
            memcpy(result, s->chars, (size_t)s->length + 1);
            return result;
        }

        case OBJ_FUNCTION: {
            BrzFunction* fn = BRZ_AS_FUNCTION(value);
            snprintf(buffer, sizeof(buffer), "<funcao %s>",
                     fn->name ? fn->name->chars : "script");
            break;
        }

        case OBJ_CLOSURE: {
            BrzFunction* fn = BRZ_AS_CLOSURE(value)->function;
            snprintf(buffer, sizeof(buffer), "<funcao %s>",
                     fn->name ? fn->name->chars : "script");
            break;
        }

        default:
            snprintf(buffer, sizeof(buffer), "<objeto>");
            break;
    }

    size_t len = strlen(buffer);
    char* result = BRZ_ALLOCATE(char, len + 1);
    memcpy(result, buffer, len + 1);
    return result;
}
