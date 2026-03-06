/*
 * BRZ Virtual Machine — Representação de Valores
 *
 * Implementa array dinâmico de constantes, impressão, comparação
 * e conversão de valores.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#include "value.h"
#include "object.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

/* ===========================================================================
 * Array dinâmico de valores
 * =========================================================================== */

void brz_value_array_init(BrzValueArray* array) {
    array->count    = 0;
    array->capacity = 0;
    array->values   = NULL;
}

void brz_value_array_write(BrzValueArray* array, BrzValue value) {
    if (array->count + 1 > array->capacity) {
        int old_cap     = array->capacity;
        array->capacity = BRZ_GROW_CAP(old_cap);
        array->values   = BRZ_GROW_ARRAY(BrzValue, array->values,
                                          old_cap, array->capacity);
    }
    array->values[array->count] = value;
    array->count++;
}

void brz_value_array_free(BrzValueArray* array) {
    BRZ_FREE_ARRAY(BrzValue, array->values, array->capacity);
    brz_value_array_init(array);
}

/* ===========================================================================
 * Impressão de valores
 * =========================================================================== */

void brz_value_print(BrzValue value) {
    switch (value.type) {
        case VAL_BOOL:
            printf("%s", BRZ_AS_BOOL(value) ? "verdadeiro" : "falso");
            break;

        case VAL_NULL:
            printf("nulo");
            break;

        case VAL_INT:
            printf("%lld", (long long)BRZ_AS_INT(value));
            break;

        case VAL_DOUBLE: {
            double d = BRZ_AS_DOUBLE(value);
            if (d == floor(d) && !isinf(d)) {
                printf("%.1f", d);
            } else {
                printf("%g", d);
            }
            break;
        }

        case VAL_OBJECT:
            brz_object_print(value);
            break;
    }
}

/* ===========================================================================
 * Conversão para string C
 * =========================================================================== */

char* brz_value_to_cstring(BrzValue value) {
    char buffer[128];

    switch (value.type) {
        case VAL_BOOL:
            snprintf(buffer, sizeof(buffer), "%s",
                     BRZ_AS_BOOL(value) ? "verdadeiro" : "falso");
            break;

        case VAL_NULL:
            snprintf(buffer, sizeof(buffer), "nulo");
            break;

        case VAL_INT:
            snprintf(buffer, sizeof(buffer), "%lld",
                     (long long)BRZ_AS_INT(value));
            break;

        case VAL_DOUBLE: {
            double d = BRZ_AS_DOUBLE(value);
            if (d == floor(d) && !isinf(d)) {
                snprintf(buffer, sizeof(buffer), "%.1f", d);
            } else {
                snprintf(buffer, sizeof(buffer), "%g", d);
            }
            break;
        }

        case VAL_OBJECT:
            return brz_object_to_cstring(value);
    }

    size_t len = strlen(buffer);
    char* result = BRZ_ALLOCATE(char, len + 1);
    memcpy(result, buffer, len + 1);
    return result;
}

/* ===========================================================================
 * Igualdade
 * =========================================================================== */

bool brz_values_equal(BrzValue a, BrzValue b) {
    /* Tipos numéricos são comparáveis entre si. */
    if (BRZ_IS_NUMBER(a) && BRZ_IS_NUMBER(b)) {
        return BRZ_AS_NUMBER(a) == BRZ_AS_NUMBER(b);
    }

    if (a.type != b.type) return false;

    switch (a.type) {
        case VAL_BOOL:   return BRZ_AS_BOOL(a) == BRZ_AS_BOOL(b);
        case VAL_NULL:   return true;
        case VAL_INT:    return BRZ_AS_INT(a) == BRZ_AS_INT(b);
        case VAL_DOUBLE: return BRZ_AS_DOUBLE(a) == BRZ_AS_DOUBLE(b);
        case VAL_OBJECT: return BRZ_AS_OBJECT(a) == BRZ_AS_OBJECT(b);
    }

    return false;
}

/* ===========================================================================
 * Truthiness
 * =========================================================================== */

bool brz_value_is_truthy(BrzValue value) {
    switch (value.type) {
        case VAL_BOOL:   return BRZ_AS_BOOL(value);
        case VAL_NULL:   return false;
        case VAL_INT:    return BRZ_AS_INT(value) != 0;
        case VAL_DOUBLE: return BRZ_AS_DOUBLE(value) != 0.0;
        case VAL_OBJECT: return true;
    }
    return false;
}
