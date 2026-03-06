/*
 * BRZ Virtual Machine — Debug / Disassembler
 *
 * Implementa desmontagem de bytecodes para depuração.
 * Cada opcode é impresso com seus operandos formatados.
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#include "debug.h"
#include "object.h"
#include "value.h"

#include <stdio.h>

/* ===========================================================================
 * Helpers de instrução
 * =========================================================================== */

static int simple_instruction(const char* name, int offset) {
    printf("%s\n", name);
    return offset + 1;
}

static int byte_instruction(const char* name, BrzChunk* chunk, int offset) {
    uint8_t slot = chunk->code[offset + 1];
    printf("%-24s %4d\n", name, slot);
    return offset + 2;
}

static int constant_instruction(const char* name, BrzChunk* chunk, int offset) {
    uint8_t index = chunk->code[offset + 1];
    printf("%-24s %4d '", name, index);
    brz_value_print(chunk->constants.values[index]);
    printf("'\n");
    return offset + 2;
}

static int constant_long_instruction(const char* name, BrzChunk* chunk, int offset) {
    uint32_t index = (uint32_t)(chunk->code[offset + 1] << 16) |
                     (uint32_t)(chunk->code[offset + 2] << 8)  |
                     (uint32_t)(chunk->code[offset + 3]);
    printf("%-24s %4d '", name, index);
    brz_value_print(chunk->constants.values[index]);
    printf("'\n");
    return offset + 4;
}

static int jump_instruction(const char* name, int sign, BrzChunk* chunk, int offset) {
    uint16_t jump = (uint16_t)(chunk->code[offset + 1] << 8);
    jump |= chunk->code[offset + 2];
    printf("%-24s %4d -> %d\n", name, offset, offset + 3 + sign * jump);
    return offset + 3;
}

static int invoke_instruction(const char* name, BrzChunk* chunk, int offset) {
    uint8_t constant = chunk->code[offset + 1];
    uint8_t arg_count = chunk->code[offset + 2];
    printf("%-24s (%d args) %4d '", name, arg_count, constant);
    brz_value_print(chunk->constants.values[constant]);
    printf("'\n");
    return offset + 3;
}

static int closure_instruction(const char* name, BrzChunk* chunk, int offset) {
    offset++;
    uint8_t constant = chunk->code[offset++];
    printf("%-24s %4d ", name, constant);
    brz_value_print(chunk->constants.values[constant]);
    printf("\n");

    BrzFunction* function = BRZ_AS_FUNCTION(chunk->constants.values[constant]);
    for (int j = 0; j < function->upvalue_count; j++) {
        int is_local = chunk->code[offset++];
        int index = chunk->code[offset++];
        printf("      |                     %s %d\n",
               is_local ? "local" : "upvalue", index);
    }

    return offset;
}

/* ===========================================================================
 * Desmontagem de instrução
 * =========================================================================== */

int brz_debug_instruction(BrzChunk* chunk, int offset) {
    printf("%04d ", offset);

    if (offset > 0 && chunk->lines[offset] == chunk->lines[offset - 1]) {
        printf("   | ");
    } else {
        printf("%4d ", chunk->lines[offset]);
    }

    uint8_t instruction = chunk->code[offset];

    switch (instruction) {
        case OP_CONSTANT:       return constant_instruction("OP_CONSTANT", chunk, offset);
        case OP_CONSTANT_LONG:  return constant_long_instruction("OP_CONSTANT_LONG", chunk, offset);
        case OP_NULL:           return simple_instruction("OP_NULL", offset);
        case OP_TRUE:           return simple_instruction("OP_TRUE", offset);
        case OP_FALSE:          return simple_instruction("OP_FALSE", offset);

        case OP_ADD:            return simple_instruction("OP_ADD", offset);
        case OP_SUBTRACT:       return simple_instruction("OP_SUBTRACT", offset);
        case OP_MULTIPLY:       return simple_instruction("OP_MULTIPLY", offset);
        case OP_DIVIDE:         return simple_instruction("OP_DIVIDE", offset);
        case OP_MODULO:         return simple_instruction("OP_MODULO", offset);
        case OP_NEGATE:         return simple_instruction("OP_NEGATE", offset);
        case OP_POWER:          return simple_instruction("OP_POWER", offset);

        case OP_EQUAL:          return simple_instruction("OP_EQUAL", offset);
        case OP_NOT_EQUAL:      return simple_instruction("OP_NOT_EQUAL", offset);
        case OP_GREATER:        return simple_instruction("OP_GREATER", offset);
        case OP_GREATER_EQUAL:  return simple_instruction("OP_GREATER_EQUAL", offset);
        case OP_LESS:           return simple_instruction("OP_LESS", offset);
        case OP_LESS_EQUAL:     return simple_instruction("OP_LESS_EQUAL", offset);

        case OP_NOT:            return simple_instruction("OP_NOT", offset);

        case OP_DEFINE_GLOBAL:  return constant_instruction("OP_DEFINE_GLOBAL", chunk, offset);
        case OP_GET_GLOBAL:     return constant_instruction("OP_GET_GLOBAL", chunk, offset);
        case OP_SET_GLOBAL:     return constant_instruction("OP_SET_GLOBAL", chunk, offset);
        case OP_GET_LOCAL:      return byte_instruction("OP_GET_LOCAL", chunk, offset);
        case OP_SET_LOCAL:      return byte_instruction("OP_SET_LOCAL", chunk, offset);
        case OP_GET_UPVALUE:    return byte_instruction("OP_GET_UPVALUE", chunk, offset);
        case OP_SET_UPVALUE:    return byte_instruction("OP_SET_UPVALUE", chunk, offset);

        case OP_POP:            return simple_instruction("OP_POP", offset);

        case OP_JUMP:           return jump_instruction("OP_JUMP", 1, chunk, offset);
        case OP_JUMP_IF_FALSE:  return jump_instruction("OP_JUMP_IF_FALSE", 1, chunk, offset);
        case OP_LOOP:           return jump_instruction("OP_LOOP", -1, chunk, offset);

        case OP_CALL:           return byte_instruction("OP_CALL", chunk, offset);
        case OP_CLOSURE:        return closure_instruction("OP_CLOSURE", chunk, offset);
        case OP_CLOSE_UPVALUE:  return simple_instruction("OP_CLOSE_UPVALUE", offset);
        case OP_RETURN:         return simple_instruction("OP_RETURN", offset);

        case OP_CLASS:          return constant_instruction("OP_CLASS", chunk, offset);
        case OP_GET_PROPERTY:   return constant_instruction("OP_GET_PROPERTY", chunk, offset);
        case OP_SET_PROPERTY:   return constant_instruction("OP_SET_PROPERTY", chunk, offset);
        case OP_METHOD:         return constant_instruction("OP_METHOD", chunk, offset);
        case OP_INVOKE:         return invoke_instruction("OP_INVOKE", chunk, offset);
        case OP_INHERIT:        return simple_instruction("OP_INHERIT", offset);
        case OP_GET_SUPER:      return constant_instruction("OP_GET_SUPER", chunk, offset);
        case OP_SUPER_INVOKE:   return invoke_instruction("OP_SUPER_INVOKE", chunk, offset);

        case OP_LIST_NEW:       return byte_instruction("OP_LIST_NEW", chunk, offset);
        case OP_INDEX_GET:      return simple_instruction("OP_INDEX_GET", offset);
        case OP_INDEX_SET:      return simple_instruction("OP_INDEX_SET", offset);

        case OP_PRINT:          return simple_instruction("OP_PRINT", offset);

        default:
            printf("Opcode desconhecido: %d\n", instruction);
            return offset + 1;
    }
}

/* ===========================================================================
 * Desmontagem de chunk completo
 * =========================================================================== */

void brz_debug_disassemble(BrzChunk* chunk, const char* name) {
    printf("== %s ==\n", name);

    for (int offset = 0; offset < chunk->count; ) {
        offset = brz_debug_instruction(chunk, offset);
    }
}
