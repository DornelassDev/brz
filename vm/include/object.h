/*
 * BRZ Virtual Machine — Objetos Heap
 *
 * Todos os dados que não cabem em um BrzValue inline (strings,
 * funções, closures, classes, instâncias, listas) vivem como
 * objetos alocados na heap, rastreados pelo garbage collector.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#ifndef BRZ_OBJECT_H
#define BRZ_OBJECT_H

#include "common.h"
#include "value.h"
#include "chunk.h"
#include "table.h"

/* ---------------------------------------------------------------------------
 * Tipos de objeto
 * --------------------------------------------------------------------------- */

typedef enum {
    OBJ_STRING,
    OBJ_FUNCTION,
    OBJ_CLOSURE,
    OBJ_UPVALUE,
    OBJ_NATIVE,
    OBJ_CLASS,
    OBJ_INSTANCE,
    OBJ_BOUND_METHOD,
    OBJ_LIST,
} BrzObjectType;

/* ---------------------------------------------------------------------------
 * Cabeçalho base de todo objeto heap
 *
 * Cada struct de objeto começa com este header, permitindo cast
 * seguro entre BrzObject* e o tipo específico (BrzString*, etc).
 * --------------------------------------------------------------------------- */

struct BrzObject {
    BrzObjectType   type;       /* Discriminante do tipo.                  */
    bool            marked;     /* Flag de marcação do GC.                 */
    struct BrzObject* next;     /* Lista intrusiva de todos os objetos.    */
};

/* ---------------------------------------------------------------------------
 * String (imutável, interned)
 *
 * Strings são internadas: duas strings com mesmo conteúdo apontam
 * para o mesmo objeto. Isso permite comparação por ponteiro (O(1)).
 * --------------------------------------------------------------------------- */

struct BrzString {
    BrzObject   obj;
    int         length;     /* Comprimento em bytes (sem '\0').        */
    uint32_t    hash;       /* Hash FNV-1a pré-computado.              */
    char        chars[];    /* Flexible array member (conteúdo + \0).  */
};

/* ---------------------------------------------------------------------------
 * Função
 * --------------------------------------------------------------------------- */

typedef struct {
    BrzObject   obj;
    int         arity;          /* Número de parâmetros.                */
    int         upvalue_count;  /* Número de upvalues capturados.       */
    BrzChunk    chunk;          /* Bytecode do corpo da função.         */
    BrzString*  name;           /* Nome (NULL para o script top-level). */
} BrzFunction;

/* ---------------------------------------------------------------------------
 * Upvalue (variável capturada por closure)
 * --------------------------------------------------------------------------- */

typedef struct BrzUpvalue {
    BrzObject           obj;
    BrzValue*           location;   /* Ponteiro para o slot na pilha.   */
    BrzValue            closed;     /* Valor quando a variável sai do escopo. */
    struct BrzUpvalue*  next;       /* Lista de upvalues abertos.       */
} BrzUpvalue;

/* ---------------------------------------------------------------------------
 * Closure (função + upvalues capturados)
 * --------------------------------------------------------------------------- */

typedef struct {
    BrzObject       obj;
    BrzFunction*    function;
    BrzUpvalue**    upvalues;       /* Array de ponteiros para upvalues. */
    int             upvalue_count;
} BrzClosure;

/* ---------------------------------------------------------------------------
 * Função nativa (built-in implementada em C)
 * --------------------------------------------------------------------------- */

typedef BrzValue (*BrzNativeFn)(int arg_count, BrzValue* args);

typedef struct {
    BrzObject   obj;
    BrzNativeFn function;
    BrzString*  name;
    int         arity;  /* -1 = variádica */
} BrzNative;

/* ---------------------------------------------------------------------------
 * Classe
 * --------------------------------------------------------------------------- */

typedef struct BrzClass {
    BrzObject       obj;
    BrzString*      name;
    BrzTable methods;    /* Tabela de métodos. */
    struct BrzClass* superclass;
} BrzClass;

/* ---------------------------------------------------------------------------
 * Instância
 * --------------------------------------------------------------------------- */

typedef struct {
    BrzObject       obj;
    BrzClass*       klass;
    BrzTable fields;    /* Campos da instância. */
} BrzInstance;

/* ---------------------------------------------------------------------------
 * Método vinculado (bound method)
 * --------------------------------------------------------------------------- */

typedef struct {
    BrzObject   obj;
    BrzValue    receiver;   /* A instância 'este'. */
    BrzClosure* method;
} BrzBoundMethod;

/* ---------------------------------------------------------------------------
 * Lista dinâmica
 * --------------------------------------------------------------------------- */

typedef struct {
    BrzObject       obj;
    BrzValueArray   items;
} BrzList;

/* ---------------------------------------------------------------------------
 * Type checks para objetos
 * --------------------------------------------------------------------------- */

static inline BrzObjectType brz_obj_type(BrzValue value) {
    return BRZ_AS_OBJECT(value)->type;
}

#define BRZ_IS_STRING(v)       (BRZ_IS_OBJECT(v) && brz_obj_type(v) == OBJ_STRING)
#define BRZ_IS_FUNCTION(v)     (BRZ_IS_OBJECT(v) && brz_obj_type(v) == OBJ_FUNCTION)
#define BRZ_IS_CLOSURE(v)      (BRZ_IS_OBJECT(v) && brz_obj_type(v) == OBJ_CLOSURE)
#define BRZ_IS_NATIVE(v)       (BRZ_IS_OBJECT(v) && brz_obj_type(v) == OBJ_NATIVE)
#define BRZ_IS_CLASS(v)        (BRZ_IS_OBJECT(v) && brz_obj_type(v) == OBJ_CLASS)
#define BRZ_IS_INSTANCE(v)     (BRZ_IS_OBJECT(v) && brz_obj_type(v) == OBJ_INSTANCE)
#define BRZ_IS_BOUND_METHOD(v) (BRZ_IS_OBJECT(v) && brz_obj_type(v) == OBJ_BOUND_METHOD)
#define BRZ_IS_LIST(v)         (BRZ_IS_OBJECT(v) && brz_obj_type(v) == OBJ_LIST)

/* ---------------------------------------------------------------------------
 * Cast helpers para objetos
 * --------------------------------------------------------------------------- */

#define BRZ_AS_STRING(v)       ((BrzString*)BRZ_AS_OBJECT(v))
#define BRZ_AS_CSTRING(v)      (((BrzString*)BRZ_AS_OBJECT(v))->chars)
#define BRZ_AS_FUNCTION(v)     ((BrzFunction*)BRZ_AS_OBJECT(v))
#define BRZ_AS_CLOSURE(v)      ((BrzClosure*)BRZ_AS_OBJECT(v))
#define BRZ_AS_NATIVE(v)       ((BrzNative*)BRZ_AS_OBJECT(v))
#define BRZ_AS_NATIVE_FN(v)    (((BrzNative*)BRZ_AS_OBJECT(v))->function)
#define BRZ_AS_CLASS(v)        ((BrzClass*)BRZ_AS_OBJECT(v))
#define BRZ_AS_INSTANCE(v)     ((BrzInstance*)BRZ_AS_OBJECT(v))
#define BRZ_AS_BOUND_METHOD(v) ((BrzBoundMethod*)BRZ_AS_OBJECT(v))
#define BRZ_AS_LIST(v)         ((BrzList*)BRZ_AS_OBJECT(v))

/* ---------------------------------------------------------------------------
 * Funções de criação
 * --------------------------------------------------------------------------- */

/* Cria string internada. Copia os bytes. */
BrzString* brz_string_copy(const char* chars, int length);

/* Toma posse de buffer já alocado. */
BrzString* brz_string_take(char* chars, int length);

/* Concatena duas strings. */
BrzString* brz_string_concat(BrzString* a, BrzString* b);

/* Cria função vazia. */
BrzFunction* brz_function_new(void);

/* Cria closure. */
BrzClosure* brz_closure_new(BrzFunction* function);

/* Cria upvalue. */
BrzUpvalue* brz_upvalue_new(BrzValue* slot);

/* Cria função nativa. */
BrzNative* brz_native_new(BrzNativeFn function, BrzString* name, int arity);

/* Cria classe. */
BrzClass* brz_class_new(BrzString* name);

/* Cria instância. */
BrzInstance* brz_instance_new(BrzClass* klass);

/* Cria bound method. */
BrzBoundMethod* brz_bound_method_new(BrzValue receiver, BrzClosure* method);

/* Cria lista vazia. */
BrzList* brz_list_new(void);

/* ---------------------------------------------------------------------------
 * Operações sobre objetos
 * --------------------------------------------------------------------------- */

/* Imprime representação do objeto. */
void brz_object_print(BrzValue value);

/* Converte objeto para string C (caller faz free). */
char* brz_object_to_cstring(BrzValue value);

#endif /* BRZ_OBJECT_H */
