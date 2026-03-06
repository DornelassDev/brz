/*
 * BRZ Virtual Machine — Representação de Valores
 *
 * Um BrzValue é a unidade fundamental de dados da VM. Usa tagged union
 * para representar inteiros, decimais, booleanos, nulo e objetos heap.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#ifndef BRZ_VALUE_H
#define BRZ_VALUE_H

#include "common.h"

/* Forward declaration — objetos vivem na heap (object.h). */
typedef struct BrzObject BrzObject;
typedef struct BrzString BrzString;

/* ---------------------------------------------------------------------------
 * Tipos de valor
 * --------------------------------------------------------------------------- */

typedef enum {
    VAL_BOOL,
    VAL_NULL,
    VAL_INT,
    VAL_DOUBLE,
    VAL_OBJECT,
} BrzValueType;

/* ---------------------------------------------------------------------------
 * Valor tagged union
 * --------------------------------------------------------------------------- */

typedef struct {
    BrzValueType type;
    union {
        bool    boolean;
        int64_t integer;
        double  decimal;
        BrzObject* object;
    } as;
} BrzValue;

/* ---------------------------------------------------------------------------
 * Construtores — criam BrzValue a partir de valores C
 * --------------------------------------------------------------------------- */

#define BRZ_BOOL(v)    ((BrzValue){ VAL_BOOL,   { .boolean = (v) } })
#define BRZ_NULL       ((BrzValue){ VAL_NULL,    { .integer = 0   } })
#define BRZ_INT(v)     ((BrzValue){ VAL_INT,     { .integer = (v) } })
#define BRZ_DOUBLE(v)  ((BrzValue){ VAL_DOUBLE,  { .decimal = (v) } })
#define BRZ_OBJECT(o)  ((BrzValue){ VAL_OBJECT,  { .object  = (BrzObject*)(o) } })

/* ---------------------------------------------------------------------------
 * Extractors — obtêm o valor C de um BrzValue
 * --------------------------------------------------------------------------- */

#define BRZ_AS_BOOL(v)    ((v).as.boolean)
#define BRZ_AS_INT(v)     ((v).as.integer)
#define BRZ_AS_DOUBLE(v)  ((v).as.decimal)
#define BRZ_AS_OBJECT(v)  ((v).as.object)

/* ---------------------------------------------------------------------------
 * Type checks
 * --------------------------------------------------------------------------- */

#define BRZ_IS_BOOL(v)    ((v).type == VAL_BOOL)
#define BRZ_IS_NULL(v)    ((v).type == VAL_NULL)
#define BRZ_IS_INT(v)     ((v).type == VAL_INT)
#define BRZ_IS_DOUBLE(v)  ((v).type == VAL_DOUBLE)
#define BRZ_IS_OBJECT(v)  ((v).type == VAL_OBJECT)

/* Checa se é numérico (int ou double). */
#define BRZ_IS_NUMBER(v)  (BRZ_IS_INT(v) || BRZ_IS_DOUBLE(v))

/* Extrai valor numérico como double, independente do tipo. */
#define BRZ_AS_NUMBER(v) \
    (BRZ_IS_INT(v) ? (double)BRZ_AS_INT(v) : BRZ_AS_DOUBLE(v))

/* ---------------------------------------------------------------------------
 * Array dinâmico de valores (pool de constantes)
 * --------------------------------------------------------------------------- */

typedef struct {
    int      count;
    int      capacity;
    BrzValue* values;
} BrzValueArray;

void brz_value_array_init(BrzValueArray* array);
void brz_value_array_write(BrzValueArray* array, BrzValue value);
void brz_value_array_free(BrzValueArray* array);

/* ---------------------------------------------------------------------------
 * Operações sobre valores
 * --------------------------------------------------------------------------- */

/* Imprime um valor no stdout. */
void brz_value_print(BrzValue value);

/* Formata um valor como string C (caller faz free). */
char* brz_value_to_cstring(BrzValue value);

/* Compara igualdade entre dois valores. */
bool brz_values_equal(BrzValue a, BrzValue b);

/* Retorna true se o valor é "truthy" (não-falso/não-nulo). */
bool brz_value_is_truthy(BrzValue value);

#endif /* BRZ_VALUE_H */
