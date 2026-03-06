/*
 * BRZ Virtual Machine — Execução de Bytecodes
 *
 * Este é o coração da VM: o dispatch loop que busca, decodifica
 * e executa cada instrução. Inclui chamada de funções, acesso a
 * propriedades, invocação de métodos e criação de closures.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#include "vm.h"
#include "compiler.h"
#include "memory.h"
#include "object.h"

#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <time.h>

/* ===========================================================================
 * Instância global da VM
 * =========================================================================== */

BrzVM brz_vm;

/* ===========================================================================
 * Operações de pilha
 * =========================================================================== */

void brz_vm_push(BrzValue value) {
    *brz_vm.stack_top = value;
    brz_vm.stack_top++;
}

BrzValue brz_vm_pop(void) {
    brz_vm.stack_top--;
    return *brz_vm.stack_top;
}

static BrzValue peek(int distance) {
    return brz_vm.stack_top[-1 - distance];
}

/* ===========================================================================
 * Erros de runtime
 * =========================================================================== */

static void reset_stack(void) {
    brz_vm.stack_top     = brz_vm.stack;
    brz_vm.frame_count   = 0;
    brz_vm.open_upvalues = NULL;
}

static void runtime_error(const char* format, ...) {
    va_list args;
    va_start(args, format);
    fprintf(stderr, "\033[31m");
    vfprintf(stderr, format, args);
    va_end(args);
    fprintf(stderr, "\033[0m\n");

    /* Stack trace. */
    for (int i = brz_vm.frame_count - 1; i >= 0; i--) {
        CallFrame*   frame = &brz_vm.frames[i];
        BrzFunction* fn    = frame->closure->function;
        size_t offset = (size_t)(frame->ip - fn->chunk.code - 1);
        int line = fn->chunk.lines[offset];

        fprintf(stderr, "  \033[90m[linha %d] em ", line);
        if (fn->name == NULL) {
            fprintf(stderr, "<script>\033[0m\n");
        } else {
            fprintf(stderr, "%s()\033[0m\n", fn->name->chars);
        }
    }

    reset_stack();
}

/* ===========================================================================
 * Funções nativas (built-ins)
 * =========================================================================== */

/* relogio() — retorna tempo em segundos. */
static BrzValue native_clock(int arg_count, BrzValue* args) {
    (void)arg_count; (void)args;
    return BRZ_DOUBLE((double)clock() / CLOCKS_PER_SEC);
}

/* tipo(valor) — retorna o tipo como string. */
static BrzValue native_tipo(int arg_count, BrzValue* args) {
    (void)arg_count;
    const char* name;
    switch (args[0].type) {
        case VAL_BOOL:   name = "booleano"; break;
        case VAL_NULL:   name = "nulo";     break;
        case VAL_INT:    name = "inteiro";  break;
        case VAL_DOUBLE: name = "decimal";  break;
        case VAL_OBJECT:
            switch (BRZ_AS_OBJECT(args[0])->type) {
                case OBJ_STRING:       name = "texto"; break;
                case OBJ_FUNCTION:
                case OBJ_CLOSURE:
                case OBJ_NATIVE:       name = "funcao"; break;
                case OBJ_CLASS:        name = "classe"; break;
                case OBJ_INSTANCE:     name = "instancia"; break;
                case OBJ_LIST:         name = "lista"; break;
                case OBJ_BOUND_METHOD: name = "metodo"; break;
                default:               name = "objeto"; break;
            }
            break;
        default: name = "desconhecido"; break;
    }
    return BRZ_OBJECT(brz_string_copy(name, (int)strlen(name)));
}

/* tamanho(valor) — retorna comprimento de string ou lista. */
static BrzValue native_tamanho(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (BRZ_IS_STRING(args[0])) {
        return BRZ_INT(BRZ_AS_STRING(args[0])->length);
    }
    if (BRZ_IS_LIST(args[0])) {
        return BRZ_INT(BRZ_AS_LIST(args[0])->items.count);
    }
    return BRZ_INT(0);
}

/* texto(valor) — converte para string. */
static BrzValue native_texto(int arg_count, BrzValue* args) {
    (void)arg_count;
    char* str = brz_value_to_cstring(args[0]);
    BrzString* result = brz_string_copy(str, (int)strlen(str));
    free(str);
    return BRZ_OBJECT(result);
}

/* inteiro(valor) — converte para inteiro. */
static BrzValue native_inteiro(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (BRZ_IS_INT(args[0]))    return args[0];
    if (BRZ_IS_DOUBLE(args[0])) return BRZ_INT((int64_t)BRZ_AS_DOUBLE(args[0]));
    if (BRZ_IS_STRING(args[0])) {
        int64_t v = strtoll(BRZ_AS_CSTRING(args[0]), NULL, 10);
        return BRZ_INT(v);
    }
    return BRZ_NULL;
}

/* decimal(valor) — converte para double. */
static BrzValue native_decimal(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (BRZ_IS_DOUBLE(args[0])) return args[0];
    if (BRZ_IS_INT(args[0]))    return BRZ_DOUBLE((double)BRZ_AS_INT(args[0]));
    if (BRZ_IS_STRING(args[0])) {
        double v = strtod(BRZ_AS_CSTRING(args[0]), NULL);
        return BRZ_DOUBLE(v);
    }
    return BRZ_NULL;
}

/* lerTexto() — lê linha de stdin. */
static BrzValue native_ler_texto(int arg_count, BrzValue* args) {
    (void)arg_count; (void)args;
    char buffer[4096];
    if (fgets(buffer, sizeof(buffer), stdin) == NULL) {
        return BRZ_OBJECT(brz_string_copy("", 0));
    }
    /* Remove \n final. */
    int len = (int)strlen(buffer);
    if (len > 0 && buffer[len - 1] == '\n') len--;
    return BRZ_OBJECT(brz_string_copy(buffer, len));
}

/* lerNumero() — lê inteiro de stdin. */
static BrzValue native_ler_numero(int arg_count, BrzValue* args) {
    (void)arg_count; (void)args;
    char buffer[256];
    if (fgets(buffer, sizeof(buffer), stdin) == NULL) {
        return BRZ_INT(0);
    }
    return BRZ_INT(strtoll(buffer, NULL, 10));
}

/* lerDecimal() — lê double de stdin. */
static BrzValue native_ler_decimal(int arg_count, BrzValue* args) {
    (void)arg_count; (void)args;
    char buffer[256];
    if (fgets(buffer, sizeof(buffer), stdin) == NULL) {
        return BRZ_DOUBLE(0.0);
    }
    return BRZ_DOUBLE(strtod(buffer, NULL));
}

/* adicionar(lista, valor) — adiciona valor ao final da lista. */
static BrzValue native_adicionar(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (!BRZ_IS_LIST(args[0])) return BRZ_NULL;
    BrzList* list = BRZ_AS_LIST(args[0]);
    brz_value_array_write(&list->items, args[1]);
    return BRZ_NULL;
}

/* remover(lista, indice) — remove elemento pelo índice. */
static BrzValue native_remover(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (!BRZ_IS_LIST(args[0]) || !BRZ_IS_INT(args[1])) return BRZ_NULL;
    BrzList* list = BRZ_AS_LIST(args[0]);
    int idx = (int)BRZ_AS_INT(args[1]);
    if (idx < 0 || idx >= list->items.count) return BRZ_NULL;

    BrzValue removed = list->items.values[idx];
    for (int i = idx; i < list->items.count - 1; i++) {
        list->items.values[i] = list->items.values[i + 1];
    }
    list->items.count--;
    return removed;
}

/* raiz(n) — raiz quadrada. */
static BrzValue native_raiz(int arg_count, BrzValue* args) {
    (void)arg_count;
    double v = BRZ_IS_INT(args[0]) ? (double)BRZ_AS_INT(args[0])
                                    : BRZ_AS_DOUBLE(args[0]);
    return BRZ_DOUBLE(sqrt(v));
}

/* abs(n) — valor absoluto. */
static BrzValue native_abs(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (BRZ_IS_INT(args[0])) {
        int64_t v = BRZ_AS_INT(args[0]);
        return BRZ_INT(v < 0 ? -v : v);
    }
    double v = BRZ_AS_DOUBLE(args[0]);
    return BRZ_DOUBLE(fabs(v));
}

/* contem(texto, subtexto) — verifica se string contém substring. */
static BrzValue native_contem(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (!BRZ_IS_STRING(args[0]) || !BRZ_IS_STRING(args[1])) return BRZ_BOOL(false);
    const char* haystack = BRZ_AS_CSTRING(args[0]);
    const char* needle   = BRZ_AS_CSTRING(args[1]);
    return BRZ_BOOL(strstr(haystack, needle) != NULL);
}

/* minusculo(texto) — converte string para minúsculas. */
static BrzValue native_minusculo(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (!BRZ_IS_STRING(args[0])) return args[0];
    BrzString* src = BRZ_AS_STRING(args[0]);
    char* buf = BRZ_ALLOCATE(char, src->length + 1);
    for (int i = 0; i < src->length; i++) {
        buf[i] = (char)tolower((unsigned char)src->chars[i]);
    }
    buf[src->length] = '\0';
    BrzString* result = brz_string_take(buf, src->length);
    return BRZ_OBJECT(result);
}

/* maiusculo(texto) — converte string para maiúsculas. */
static BrzValue native_maiusculo(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (!BRZ_IS_STRING(args[0])) return args[0];
    BrzString* src = BRZ_AS_STRING(args[0]);
    char* buf = BRZ_ALLOCATE(char, src->length + 1);
    for (int i = 0; i < src->length; i++) {
        buf[i] = (char)toupper((unsigned char)src->chars[i]);
    }
    buf[src->length] = '\0';
    BrzString* result = brz_string_take(buf, src->length);
    return BRZ_OBJECT(result);
}

/* fatiar(texto, inicio, fim) — extrai substring (índices baseados em 0). */
static BrzValue native_fatiar(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (!BRZ_IS_STRING(args[0])) return BRZ_OBJECT(brz_string_copy("", 0));
    BrzString* src = BRZ_AS_STRING(args[0]);
    int start = BRZ_IS_INT(args[1]) ? (int)BRZ_AS_INT(args[1]) : 0;
    int end   = BRZ_IS_INT(args[2]) ? (int)BRZ_AS_INT(args[2]) : src->length;
    if (start < 0) start += src->length;
    if (end < 0)   end   += src->length;
    if (start < 0) start = 0;
    if (end > src->length) end = src->length;
    if (start >= end) return BRZ_OBJECT(brz_string_copy("", 0));
    return BRZ_OBJECT(brz_string_copy(src->chars + start, end - start));
}

/* substituir(texto, antigo, novo) — substitui TODAS as ocorrências. */
static BrzValue native_substituir(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (!BRZ_IS_STRING(args[0]) || !BRZ_IS_STRING(args[1]) || !BRZ_IS_STRING(args[2]))
        return args[0];
    const char* src     = BRZ_AS_CSTRING(args[0]);
    const char* old_str = BRZ_AS_CSTRING(args[1]);
    const char* new_str = BRZ_AS_CSTRING(args[2]);
    int old_len = BRZ_AS_STRING(args[1])->length;
    int new_len = BRZ_AS_STRING(args[2])->length;
    if (old_len == 0) return args[0];

    /* Conta ocorrências. */
    int count = 0;
    const char* tmp = src;
    while ((tmp = strstr(tmp, old_str)) != NULL) { count++; tmp += old_len; }
    if (count == 0) return args[0];

    int src_len = BRZ_AS_STRING(args[0])->length;
    int result_len = src_len + count * (new_len - old_len);
    char* buf = BRZ_ALLOCATE(char, result_len + 1);
    char* dst = buf;
    const char* p = src;
    while (*p) {
        const char* found = strstr(p, old_str);
        if (!found) {
            strcpy(dst, p);
            dst += strlen(p);
            break;
        }
        memcpy(dst, p, (size_t)(found - p));
        dst += found - p;
        memcpy(dst, new_str, (size_t)new_len);
        dst += new_len;
        p = found + old_len;
    }
    *dst = '\0';
    BrzString* result = brz_string_take(buf, result_len);
    return BRZ_OBJECT(result);
}

/* dividir(texto, separador) — divide string em lista. */
static BrzValue native_dividir(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (!BRZ_IS_STRING(args[0]) || !BRZ_IS_STRING(args[1])) {
        return BRZ_OBJECT(brz_list_new());
    }
    const char* src = BRZ_AS_CSTRING(args[0]);
    const char* sep = BRZ_AS_CSTRING(args[1]);
    int sep_len = BRZ_AS_STRING(args[1])->length;
    BrzList* list = brz_list_new();

    /* Protege da coleta de lixo. */
    brz_vm_push(BRZ_OBJECT(list));

    if (sep_len == 0) {
        /* Separador vazio: divide por caractere. */
        BrzString* s = BRZ_AS_STRING(args[0]);
        for (int i = 0; i < s->length; i++) {
            BrzValue ch = BRZ_OBJECT(brz_string_copy(s->chars + i, 1));
            brz_value_array_write(&list->items, ch);
        }
    } else {
        const char* p = src;
        while (*p) {
            const char* found = strstr(p, sep);
            if (!found) {
                brz_value_array_write(&list->items,
                    BRZ_OBJECT(brz_string_copy(p, (int)strlen(p))));
                break;
            }
            brz_value_array_write(&list->items,
                BRZ_OBJECT(brz_string_copy(p, (int)(found - p))));
            p = found + sep_len;
        }
    }

    brz_vm_pop(); /* Remove proteção GC. */
    return BRZ_OBJECT(list);
}

/* juntar(lista, separador) — junta lista de strings com separador. */
static BrzValue native_juntar(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (!BRZ_IS_LIST(args[0]) || !BRZ_IS_STRING(args[1])) {
        return BRZ_OBJECT(brz_string_copy("", 0));
    }
    BrzList* list = BRZ_AS_LIST(args[0]);
    const char* sep = BRZ_AS_CSTRING(args[1]);
    int sep_len = BRZ_AS_STRING(args[1])->length;

    if (list->items.count == 0)
        return BRZ_OBJECT(brz_string_copy("", 0));

    /* Calcula tamanho total. */
    int total = 0;
    for (int i = 0; i < list->items.count; i++) {
        if (BRZ_IS_STRING(list->items.values[i])) {
            total += BRZ_AS_STRING(list->items.values[i])->length;
        }
        if (i > 0) total += sep_len;
    }

    char* buf = BRZ_ALLOCATE(char, total + 1);
    char* dst = buf;
    for (int i = 0; i < list->items.count; i++) {
        if (i > 0) {
            memcpy(dst, sep, (size_t)sep_len);
            dst += sep_len;
        }
        if (BRZ_IS_STRING(list->items.values[i])) {
            BrzString* s = BRZ_AS_STRING(list->items.values[i]);
            memcpy(dst, s->chars, (size_t)s->length);
            dst += s->length;
        }
    }
    *dst = '\0';
    return BRZ_OBJECT(brz_string_take(buf, total));
}

/* encontrar(texto, subtexto) — retorna índice da primeira ocorrência ou -1. */
static BrzValue native_encontrar(int arg_count, BrzValue* args) {
    (void)arg_count;
    if (!BRZ_IS_STRING(args[0]) || !BRZ_IS_STRING(args[1])) return BRZ_INT(-1);
    const char* haystack = BRZ_AS_CSTRING(args[0]);
    const char* needle   = BRZ_AS_CSTRING(args[1]);
    const char* found = strstr(haystack, needle);
    if (!found) return BRZ_INT(-1);
    return BRZ_INT((int64_t)(found - haystack));
}

/* aleatorio() — retorna decimal aleatório entre 0 e 1. */
static BrzValue native_aleatorio(int arg_count, BrzValue* args) {
    (void)arg_count; (void)args;
    return BRZ_DOUBLE((double)rand() / (double)RAND_MAX);
}

/* ===========================================================================
 * Helpers de chamada
 * =========================================================================== */

static bool call(BrzClosure* closure, int arg_count) {
    if (arg_count != closure->function->arity) {
        runtime_error("Esperado %d argumento(s), recebeu %d.",
                      closure->function->arity, arg_count);
        return false;
    }

    if (brz_vm.frame_count == BRZ_FRAMES_MAX) {
        runtime_error("Stack overflow — muitas chamadas aninhadas.");
        return false;
    }

    CallFrame* frame = &brz_vm.frames[brz_vm.frame_count++];
    frame->closure = closure;
    frame->ip      = closure->function->chunk.code;
    frame->slots   = brz_vm.stack_top - arg_count - 1;
    return true;
}

static bool call_value(BrzValue callee, int arg_count) {
    if (BRZ_IS_OBJECT(callee)) {
        switch (BRZ_AS_OBJECT(callee)->type) {
            case OBJ_CLOSURE:
                return call(BRZ_AS_CLOSURE(callee), arg_count);

            case OBJ_NATIVE: {
                BrzNative* native = BRZ_AS_NATIVE(callee);
                if (native->arity != -1 && native->arity != arg_count) {
                    runtime_error("'%s' esperava %d argumento(s), recebeu %d.",
                                  native->name->chars, native->arity, arg_count);
                    return false;
                }
                BrzValue result = native->function(arg_count,
                                                    brz_vm.stack_top - arg_count);
                brz_vm.stack_top -= arg_count + 1;
                brz_vm_push(result);
                return true;
            }

            case OBJ_CLASS: {
                BrzClass* klass = BRZ_AS_CLASS(callee);
                brz_vm.stack_top[-arg_count - 1] = BRZ_OBJECT(
                    brz_instance_new(klass));

                /* Chama inicializador se existir. */
                BrzValue initializer;
                if (brz_table_get(&klass->methods, brz_vm.init_string,
                                  &initializer)) {
                    return call(BRZ_AS_CLOSURE(initializer), arg_count);
                } else if (arg_count != 0) {
                    runtime_error("Esperado 0 argumentos, recebeu %d.", arg_count);
                    return false;
                }
                return true;
            }

            case OBJ_BOUND_METHOD: {
                BrzBoundMethod* bound = BRZ_AS_BOUND_METHOD(callee);
                brz_vm.stack_top[-arg_count - 1] = bound->receiver;
                return call(bound->method, arg_count);
            }

            default:
                break;
        }
    }

    runtime_error("Só é possível chamar funções e classes.");
    return false;
}

static bool invoke_from_class(BrzClass* klass, BrzString* name, int arg_count) {
    BrzValue method;
    if (!brz_table_get(&klass->methods, name, &method)) {
        runtime_error("Método '%s' indefinido.", name->chars);
        return false;
    }
    return call(BRZ_AS_CLOSURE(method), arg_count);
}

static bool invoke(BrzString* name, int arg_count) {
    BrzValue receiver = peek(arg_count);

    if (!BRZ_IS_INSTANCE(receiver)) {
        runtime_error("Só instâncias têm métodos.");
        return false;
    }

    BrzInstance* instance = BRZ_AS_INSTANCE(receiver);

    /* Campo que é uma closure? Chama como função. */
    BrzValue value;
    if (brz_table_get(&instance->fields, name, &value)) {
        brz_vm.stack_top[-arg_count - 1] = value;
        return call_value(value, arg_count);
    }

    return invoke_from_class(instance->klass, name, arg_count);
}

static bool bind_method(BrzClass* klass, BrzString* name) {
    BrzValue method;
    if (!brz_table_get(&klass->methods, name, &method)) {
        runtime_error("Propriedade '%s' indefinida.", name->chars);
        return false;
    }

    BrzBoundMethod* bound = brz_bound_method_new(peek(0),
                                                   BRZ_AS_CLOSURE(method));
    brz_vm_pop();
    brz_vm_push(BRZ_OBJECT(bound));
    return true;
}

/* ===========================================================================
 * Upvalues — captura de variáveis
 * =========================================================================== */

static BrzUpvalue* capture_upvalue(BrzValue* local) {
    BrzUpvalue* prev    = NULL;
    BrzUpvalue* upvalue = brz_vm.open_upvalues;

    while (upvalue != NULL && upvalue->location > local) {
        prev    = upvalue;
        upvalue = upvalue->next;
    }

    if (upvalue != NULL && upvalue->location == local) {
        return upvalue;
    }

    BrzUpvalue* created = brz_upvalue_new(local);
    created->next = upvalue;

    if (prev == NULL) {
        brz_vm.open_upvalues = created;
    } else {
        prev->next = created;
    }

    return created;
}

static void close_upvalues(BrzValue* last) {
    while (brz_vm.open_upvalues != NULL &&
           brz_vm.open_upvalues->location >= last) {
        BrzUpvalue* uv = brz_vm.open_upvalues;
        uv->closed   = *uv->location;
        uv->location = &uv->closed;
        brz_vm.open_upvalues = uv->next;
    }
}

/* ===========================================================================
 * Helpers numéricos
 * =========================================================================== */

/* Promoção automática: se um operando é double, o resultado é double. */
#define NUMERIC_BINARY_OP(value_type, op) \
    do { \
        if (!BRZ_IS_NUMBER(peek(0)) || !BRZ_IS_NUMBER(peek(1))) { \
            runtime_error("Operandos devem ser numéricos."); \
            return INTERPRET_RUNTIME_ERROR; \
        } \
        BrzValue b = brz_vm_pop(); \
        BrzValue a = brz_vm_pop(); \
        if (BRZ_IS_INT(a) && BRZ_IS_INT(b)) { \
            brz_vm_push(value_type(BRZ_AS_INT(a) op BRZ_AS_INT(b))); \
        } else { \
            brz_vm_push(BRZ_DOUBLE(BRZ_AS_NUMBER(a) op BRZ_AS_NUMBER(b))); \
        } \
    } while (false)

#define COMPARISON_OP(op) \
    do { \
        if (!BRZ_IS_NUMBER(peek(0)) || !BRZ_IS_NUMBER(peek(1))) { \
            runtime_error("Operandos devem ser numéricos para comparação."); \
            return INTERPRET_RUNTIME_ERROR; \
        } \
        BrzValue b = brz_vm_pop(); \
        BrzValue a = brz_vm_pop(); \
        brz_vm_push(BRZ_BOOL(BRZ_AS_NUMBER(a) op BRZ_AS_NUMBER(b))); \
    } while (false)

/* ===========================================================================
 * Dispatch loop — O CORAÇÃO DA VM
 *
 * Busca, decodifica e executa cada instrução sequencialmente.
 * Usa computed goto onde disponível (GCC/Clang) ou switch padrão.
 * =========================================================================== */

static BrzInterpretResult run(void) {
    CallFrame* frame = &brz_vm.frames[brz_vm.frame_count - 1];

/* Macros de conveniência para leitura de bytecodes. */
#define READ_BYTE()     (*frame->ip++)
#define READ_SHORT()    (frame->ip += 2, \
    (uint16_t)((frame->ip[-2] << 8) | frame->ip[-1]))
#define READ_CONSTANT() (frame->closure->function->chunk.constants.values[READ_BYTE()])
#define READ_STRING()   BRZ_AS_STRING(READ_CONSTANT())

    for (;;) {

#ifdef BRZ_DEBUG_TRACE
        /* Imprime estado da pilha antes de cada instrução. */
        printf("          ");
        for (BrzValue* slot = brz_vm.stack; slot < brz_vm.stack_top; slot++) {
            printf("[ ");
            brz_value_print(*slot);
            printf(" ]");
        }
        printf("\n");

        extern void brz_debug_instruction(BrzChunk* chunk, int offset);
        brz_debug_instruction(&frame->closure->function->chunk,
            (int)(frame->ip - frame->closure->function->chunk.code));
#endif

        uint8_t instruction = READ_BYTE();

        switch (instruction) {

        /* ---------------------------------------------------------------
         * Constantes e literais
         * --------------------------------------------------------------- */

        case OP_CONSTANT: {
            BrzValue constant = READ_CONSTANT();
            brz_vm_push(constant);
            break;
        }

        case OP_CONSTANT_LONG: {
            uint32_t index = (uint32_t)(READ_BYTE() << 16) |
                             (uint32_t)(READ_BYTE() << 8)  |
                             (uint32_t)(READ_BYTE());
            brz_vm_push(frame->closure->function->chunk.constants.values[index]);
            break;
        }

        case OP_NULL:  brz_vm_push(BRZ_NULL); break;
        case OP_TRUE:  brz_vm_push(BRZ_BOOL(true)); break;
        case OP_FALSE: brz_vm_push(BRZ_BOOL(false)); break;

        /* ---------------------------------------------------------------
         * Aritmética
         * --------------------------------------------------------------- */

        case OP_ADD: {
            if (BRZ_IS_STRING(peek(0)) && BRZ_IS_STRING(peek(1))) {
                BrzString* b = BRZ_AS_STRING(peek(0));
                BrzString* a = BRZ_AS_STRING(peek(1));
                BrzString* result = brz_string_concat(a, b);
                brz_vm_pop();
                brz_vm_pop();
                brz_vm_push(BRZ_OBJECT(result));
            } else if (BRZ_IS_STRING(peek(0)) || BRZ_IS_STRING(peek(1))) {
                /* Auto-converte para string em concatenação mista. */
                char* b_str = brz_value_to_cstring(peek(0));
                char* a_str = brz_value_to_cstring(peek(1));
                int a_len = (int)strlen(a_str);
                int b_len = (int)strlen(b_str);
                char* buf = BRZ_ALLOCATE(char, a_len + b_len + 1);
                memcpy(buf, a_str, (size_t)a_len);
                memcpy(buf + a_len, b_str, (size_t)b_len);
                buf[a_len + b_len] = '\0';
                free(a_str);
                free(b_str);
                BrzString* result = brz_string_take(buf, a_len + b_len);
                brz_vm_pop();
                brz_vm_pop();
                brz_vm_push(BRZ_OBJECT(result));
            } else if (BRZ_IS_NUMBER(peek(0)) && BRZ_IS_NUMBER(peek(1))) {
                BrzValue b = brz_vm_pop();
                BrzValue a = brz_vm_pop();
                if (BRZ_IS_INT(a) && BRZ_IS_INT(b)) {
                    brz_vm_push(BRZ_INT(BRZ_AS_INT(a) + BRZ_AS_INT(b)));
                } else {
                    brz_vm_push(BRZ_DOUBLE(BRZ_AS_NUMBER(a) + BRZ_AS_NUMBER(b)));
                }
            } else {
                runtime_error("Operandos inválidos para '+'.");
                return INTERPRET_RUNTIME_ERROR;
            }
            break;
        }

        case OP_SUBTRACT: NUMERIC_BINARY_OP(BRZ_INT, -); break;
        case OP_MULTIPLY: NUMERIC_BINARY_OP(BRZ_INT, *); break;

        case OP_DIVIDE: {
            if (!BRZ_IS_NUMBER(peek(0)) || !BRZ_IS_NUMBER(peek(1))) {
                runtime_error("Operandos devem ser numéricos.");
                return INTERPRET_RUNTIME_ERROR;
            }
            BrzValue b = brz_vm_pop();
            BrzValue a = brz_vm_pop();
            double db = BRZ_AS_NUMBER(b);
            if (db == 0.0) {
                runtime_error("Divisão por zero.");
                return INTERPRET_RUNTIME_ERROR;
            }
            /* Divisão sempre retorna double. */
            brz_vm_push(BRZ_DOUBLE(BRZ_AS_NUMBER(a) / db));
            break;
        }

        case OP_MODULO: {
            if (!BRZ_IS_NUMBER(peek(0)) || !BRZ_IS_NUMBER(peek(1))) {
                runtime_error("Operandos devem ser numéricos para '%%'.");
                return INTERPRET_RUNTIME_ERROR;
            }
            BrzValue b = brz_vm_pop();
            BrzValue a = brz_vm_pop();
            if (BRZ_IS_INT(a) && BRZ_IS_INT(b)) {
                int64_t bv = BRZ_AS_INT(b);
                if (bv == 0) {
                    runtime_error("Divisão por zero.");
                    return INTERPRET_RUNTIME_ERROR;
                }
                brz_vm_push(BRZ_INT(BRZ_AS_INT(a) % bv));
            } else {
                brz_vm_push(BRZ_DOUBLE(fmod(BRZ_AS_NUMBER(a), BRZ_AS_NUMBER(b))));
            }
            break;
        }

        case OP_POWER: {
            if (!BRZ_IS_NUMBER(peek(0)) || !BRZ_IS_NUMBER(peek(1))) {
                runtime_error("Operandos devem ser numéricos para '**'.");
                return INTERPRET_RUNTIME_ERROR;
            }
            BrzValue b = brz_vm_pop();
            BrzValue a = brz_vm_pop();
            brz_vm_push(BRZ_DOUBLE(pow(BRZ_AS_NUMBER(a), BRZ_AS_NUMBER(b))));
            break;
        }

        case OP_NEGATE: {
            if (BRZ_IS_INT(peek(0))) {
                brz_vm_push(BRZ_INT(-BRZ_AS_INT(brz_vm_pop())));
            } else if (BRZ_IS_DOUBLE(peek(0))) {
                brz_vm_push(BRZ_DOUBLE(-BRZ_AS_DOUBLE(brz_vm_pop())));
            } else {
                runtime_error("Operando deve ser numérico.");
                return INTERPRET_RUNTIME_ERROR;
            }
            break;
        }

        /* ---------------------------------------------------------------
         * Comparação
         * --------------------------------------------------------------- */

        case OP_EQUAL: {
            BrzValue b = brz_vm_pop();
            BrzValue a = brz_vm_pop();
            brz_vm_push(BRZ_BOOL(brz_values_equal(a, b)));
            break;
        }

        case OP_NOT_EQUAL: {
            BrzValue b = brz_vm_pop();
            BrzValue a = brz_vm_pop();
            brz_vm_push(BRZ_BOOL(!brz_values_equal(a, b)));
            break;
        }

        case OP_GREATER:       COMPARISON_OP(>);  break;
        case OP_GREATER_EQUAL: COMPARISON_OP(>=); break;
        case OP_LESS:          COMPARISON_OP(<);  break;
        case OP_LESS_EQUAL:    COMPARISON_OP(<=); break;

        /* ---------------------------------------------------------------
         * Lógica
         * --------------------------------------------------------------- */

        case OP_NOT:
            brz_vm_push(BRZ_BOOL(!brz_value_is_truthy(brz_vm_pop())));
            break;

        /* ---------------------------------------------------------------
         * Variáveis globais
         * --------------------------------------------------------------- */

        case OP_DEFINE_GLOBAL: {
            BrzString* name = READ_STRING();
            brz_table_set(&brz_vm.globals, name, peek(0));
            brz_vm_pop();
            break;
        }

        case OP_GET_GLOBAL: {
            BrzString* name = READ_STRING();
            BrzValue value;
            if (!brz_table_get(&brz_vm.globals, name, &value)) {
                runtime_error("Variável '%s' não definida.", name->chars);
                return INTERPRET_RUNTIME_ERROR;
            }
            brz_vm_push(value);
            break;
        }

        case OP_SET_GLOBAL: {
            BrzString* name = READ_STRING();
            if (brz_table_set(&brz_vm.globals, name, peek(0))) {
                brz_table_delete(&brz_vm.globals, name);
                runtime_error("Variável '%s' não definida.", name->chars);
                return INTERPRET_RUNTIME_ERROR;
            }
            break;
        }

        /* ---------------------------------------------------------------
         * Variáveis locais
         * --------------------------------------------------------------- */

        case OP_GET_LOCAL: {
            uint8_t slot = READ_BYTE();
            brz_vm_push(frame->slots[slot]);
            break;
        }

        case OP_SET_LOCAL: {
            uint8_t slot = READ_BYTE();
            frame->slots[slot] = peek(0);
            break;
        }

        /* ---------------------------------------------------------------
         * Upvalues (closures)
         * --------------------------------------------------------------- */

        case OP_GET_UPVALUE: {
            uint8_t slot = READ_BYTE();
            brz_vm_push(*frame->closure->upvalues[slot]->location);
            break;
        }

        case OP_SET_UPVALUE: {
            uint8_t slot = READ_BYTE();
            *frame->closure->upvalues[slot]->location = peek(0);
            break;
        }

        case OP_CLOSE_UPVALUE:
            close_upvalues(brz_vm.stack_top - 1);
            brz_vm_pop();
            break;

        /* ---------------------------------------------------------------
         * Pilha
         * --------------------------------------------------------------- */

        case OP_POP:
            brz_vm_pop();
            break;

        /* ---------------------------------------------------------------
         * Controle de fluxo
         * --------------------------------------------------------------- */

        case OP_JUMP: {
            uint16_t offset = READ_SHORT();
            frame->ip += offset;
            break;
        }

        case OP_JUMP_IF_FALSE: {
            uint16_t offset = READ_SHORT();
            if (!brz_value_is_truthy(peek(0))) {
                frame->ip += offset;
            }
            break;
        }

        case OP_LOOP: {
            uint16_t offset = READ_SHORT();
            frame->ip -= offset;
            break;
        }

        /* ---------------------------------------------------------------
         * Funções
         * --------------------------------------------------------------- */

        case OP_CALL: {
            int arg_count = READ_BYTE();
            if (!call_value(peek(arg_count), arg_count)) {
                return INTERPRET_RUNTIME_ERROR;
            }
            frame = &brz_vm.frames[brz_vm.frame_count - 1];
            break;
        }

        case OP_CLOSURE: {
            BrzFunction* fn = BRZ_AS_FUNCTION(READ_CONSTANT());
            BrzClosure* closure = brz_closure_new(fn);
            brz_vm_push(BRZ_OBJECT(closure));

            for (int i = 0; i < closure->upvalue_count; i++) {
                uint8_t is_local = READ_BYTE();
                uint8_t index = READ_BYTE();
                if (is_local) {
                    closure->upvalues[i] = capture_upvalue(frame->slots + index);
                } else {
                    closure->upvalues[i] = frame->closure->upvalues[index];
                }
            }
            break;
        }

        case OP_RETURN: {
            BrzValue result = brz_vm_pop();
            close_upvalues(frame->slots);
            brz_vm.frame_count--;

            if (brz_vm.frame_count == 0) {
                brz_vm_pop();
                return INTERPRET_OK;
            }

            brz_vm.stack_top = frame->slots;
            brz_vm_push(result);
            frame = &brz_vm.frames[brz_vm.frame_count - 1];
            break;
        }

        /* ---------------------------------------------------------------
         * Classes e objetos
         * --------------------------------------------------------------- */

        case OP_CLASS:
            brz_vm_push(BRZ_OBJECT(brz_class_new(READ_STRING())));
            break;

        case OP_GET_PROPERTY: {
            if (!BRZ_IS_INSTANCE(peek(0))) {
                runtime_error("Só instâncias têm propriedades.");
                return INTERPRET_RUNTIME_ERROR;
            }

            BrzInstance* instance = BRZ_AS_INSTANCE(peek(0));
            BrzString* name = READ_STRING();

            BrzValue value;
            if (brz_table_get(&instance->fields, name, &value)) {
                brz_vm_pop();
                brz_vm_push(value);
                break;
            }

            if (!bind_method(instance->klass, name)) {
                return INTERPRET_RUNTIME_ERROR;
            }
            break;
        }

        case OP_SET_PROPERTY: {
            if (!BRZ_IS_INSTANCE(peek(1))) {
                runtime_error("Só instâncias têm campos.");
                return INTERPRET_RUNTIME_ERROR;
            }

            BrzInstance* instance = BRZ_AS_INSTANCE(peek(1));
            brz_table_set(&instance->fields, READ_STRING(), peek(0));
            BrzValue value = brz_vm_pop();
            brz_vm_pop();
            brz_vm_push(value);
            break;
        }

        case OP_METHOD:
            brz_table_set(&BRZ_AS_CLASS(peek(1))->methods,
                           READ_STRING(), peek(0));
            brz_vm_pop();
            break;

        case OP_INVOKE: {
            BrzString* method_name = READ_STRING();
            int arg_count = READ_BYTE();
            if (!invoke(method_name, arg_count)) {
                return INTERPRET_RUNTIME_ERROR;
            }
            frame = &brz_vm.frames[brz_vm.frame_count - 1];
            break;
        }

        case OP_INHERIT: {
            BrzValue superclass = peek(1);
            if (!BRZ_IS_CLASS(superclass)) {
                runtime_error("Superclasse deve ser uma classe.");
                return INTERPRET_RUNTIME_ERROR;
            }

            BrzClass* sub = BRZ_AS_CLASS(peek(0));
            sub->superclass = BRZ_AS_CLASS(superclass);
            brz_table_add_all(&BRZ_AS_CLASS(superclass)->methods,
                              &sub->methods);
            brz_vm_pop();
            break;
        }

        case OP_GET_SUPER: {
            BrzString* name = READ_STRING();
            BrzClass* superclass = BRZ_AS_CLASS(brz_vm_pop());
            if (!bind_method(superclass, name)) {
                return INTERPRET_RUNTIME_ERROR;
            }
            break;
        }

        case OP_SUPER_INVOKE: {
            BrzString* method_name = READ_STRING();
            int arg_count = READ_BYTE();
            BrzClass* superclass = BRZ_AS_CLASS(brz_vm_pop());
            if (!invoke_from_class(superclass, method_name, arg_count)) {
                return INTERPRET_RUNTIME_ERROR;
            }
            frame = &brz_vm.frames[brz_vm.frame_count - 1];
            break;
        }

        /* ---------------------------------------------------------------
         * Listas
         * --------------------------------------------------------------- */

        case OP_LIST_NEW: {
            int count = READ_BYTE();
            BrzList* list = brz_list_new();
            brz_vm_push(BRZ_OBJECT(list));

            /* Copia elementos da pilha para a lista. */
            for (int i = count; i > 0; i--) {
                brz_value_array_write(&list->items,
                    brz_vm.stack_top[-1 - i]);
            }

            /* Remove a lista temporária e os elementos. */
            brz_vm.stack_top -= count + 1;
            brz_vm_push(BRZ_OBJECT(list));
            break;
        }

        case OP_INDEX_GET: {
            if (!BRZ_IS_INT(peek(0))) {
                runtime_error("Índice deve ser um inteiro.");
                return INTERPRET_RUNTIME_ERROR;
            }
            int index = (int)BRZ_AS_INT(brz_vm_pop());
            BrzValue target = brz_vm_pop();

            if (BRZ_IS_LIST(target)) {
                BrzList* list = BRZ_AS_LIST(target);
                if (index < 0) index += list->items.count;
                if (index < 0 || index >= list->items.count) {
                    runtime_error("Índice %d fora dos limites (tamanho: %d).",
                                  index, list->items.count);
                    return INTERPRET_RUNTIME_ERROR;
                }
                brz_vm_push(list->items.values[index]);
            } else if (BRZ_IS_STRING(target)) {
                BrzString* str = BRZ_AS_STRING(target);
                if (index < 0) index += str->length;
                if (index < 0 || index >= str->length) {
                    runtime_error("Índice %d fora dos limites (tamanho: %d).",
                                  index, str->length);
                    return INTERPRET_RUNTIME_ERROR;
                }
                brz_vm_push(BRZ_OBJECT(brz_string_copy(&str->chars[index], 1)));
            } else {
                runtime_error("Indexação suportada apenas em listas e strings.");
                return INTERPRET_RUNTIME_ERROR;
            }
            break;
        }

        case OP_INDEX_SET: {
            BrzValue value = brz_vm_pop();
            if (!BRZ_IS_INT(peek(0))) {
                runtime_error("Índice deve ser um inteiro.");
                return INTERPRET_RUNTIME_ERROR;
            }
            int index = (int)BRZ_AS_INT(brz_vm_pop());
            BrzValue target = brz_vm_pop();

            if (!BRZ_IS_LIST(target)) {
                runtime_error("Atribuição por índice suportada apenas em listas.");
                return INTERPRET_RUNTIME_ERROR;
            }

            BrzList* list = BRZ_AS_LIST(target);
            if (index < 0) index += list->items.count;
            if (index < 0 || index >= list->items.count) {
                runtime_error("Índice %d fora dos limites (tamanho: %d).",
                              index, list->items.count);
                return INTERPRET_RUNTIME_ERROR;
            }
            list->items.values[index] = value;
            brz_vm_push(value);
            break;
        }

        /* ---------------------------------------------------------------
         * Built-ins
         * --------------------------------------------------------------- */

        case OP_PRINT: {
            brz_value_print(brz_vm_pop());
            printf("\n");
            break;
        }

        default:
            runtime_error("Opcode desconhecido: %d.", instruction);
            return INTERPRET_RUNTIME_ERROR;

        } /* end switch */
    } /* end for */

#undef READ_BYTE
#undef READ_SHORT
#undef READ_CONSTANT
#undef READ_STRING
}

/* ===========================================================================
 * Registro de nativos
 * =========================================================================== */

void brz_vm_define_native(const char* name, BrzNativeFn function, int arity) {
    BrzString* str = brz_string_copy(name, (int)strlen(name));
    brz_vm_push(BRZ_OBJECT(str));
    brz_vm_push(BRZ_OBJECT(brz_native_new(function, str, arity)));
    brz_table_set(&brz_vm.globals, BRZ_AS_STRING(brz_vm.stack[0]),
                  brz_vm.stack[1]);
    brz_vm_pop();
    brz_vm_pop();
}

static void define_all_natives(void) {
    brz_vm_define_native("relogio",    native_clock,       0);
    brz_vm_define_native("tipo",       native_tipo,        1);
    brz_vm_define_native("tamanho",    native_tamanho,     1);
    brz_vm_define_native("texto",      native_texto,       1);
    brz_vm_define_native("inteiro",    native_inteiro,     1);
    brz_vm_define_native("decimal",    native_decimal,     1);
    brz_vm_define_native("lerTexto",   native_ler_texto,   0);
    brz_vm_define_native("lerNumero",  native_ler_numero,  0);
    brz_vm_define_native("lerDecimal", native_ler_decimal, 0);
    brz_vm_define_native("adicionar",  native_adicionar,   2);
    brz_vm_define_native("remover",    native_remover,     2);
    brz_vm_define_native("raiz",       native_raiz,        1);
    brz_vm_define_native("abs",        native_abs,         1);
    brz_vm_define_native("contem",     native_contem,      2);
    brz_vm_define_native("minusculo",  native_minusculo,   1);
    brz_vm_define_native("maiusculo",  native_maiusculo,   1);
    brz_vm_define_native("fatiar",     native_fatiar,      3);
    brz_vm_define_native("substituir", native_substituir,  3);
    brz_vm_define_native("dividir",    native_dividir,     2);
    brz_vm_define_native("juntar",     native_juntar,      2);
    brz_vm_define_native("encontrar",  native_encontrar,   2);
    brz_vm_define_native("aleatorio",  native_aleatorio,   0);
}

/* ===========================================================================
 * API pública
 * =========================================================================== */

void brz_vm_init(void) {
    srand((unsigned int)time(NULL));
    reset_stack();
    brz_vm.objects        = NULL;
    brz_vm.bytes_allocated = 0;
    brz_vm.gc_threshold   = BRZ_GC_INITIAL_THRESHOLD;
    brz_vm.gray_count     = 0;
    brz_vm.gray_capacity  = 0;
    brz_vm.gray_stack     = NULL;
    brz_vm.open_upvalues  = NULL;

    brz_table_init(&brz_vm.globals);
    brz_table_init(&brz_vm.strings);

    /* Cache da string "iniciar" para lookup rápido de construtores. */
    brz_vm.init_string = NULL;
    brz_vm.init_string = brz_string_copy("iniciar", 7);

    define_all_natives();
}

void brz_vm_free(void) {
    brz_table_free(&brz_vm.globals);
    brz_table_free(&brz_vm.strings);
    brz_vm.init_string = NULL;
    brz_gc_free_objects();
}

BrzInterpretResult brz_vm_interpret(const char* source) {
    BrzClosure* closure = brz_compile(source);
    if (closure == NULL) return INTERPRET_COMPILE_ERROR;

    brz_vm_push(BRZ_OBJECT(closure));
    call(closure, 0);

    return run();
}
