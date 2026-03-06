# =============================================================================
# BRZ Virtual Machine — Build System
# Copyright (c) 2026 DornelassDev
# =============================================================================

CC        := cc
CFLAGS    := -std=c99 -Wall -Wextra -Werror -pedantic -Iinclude
LDFLAGS   := -lm

# Diretórios
SRC_DIR   := src
INC_DIR   := include
BUILD_DIR := build
BIN_DIR   := bin

# Fontes e objetos
SOURCES   := $(wildcard $(SRC_DIR)/*.c)
OBJECTS   := $(SOURCES:$(SRC_DIR)/%.c=$(BUILD_DIR)/%.o)
TARGET    := $(BIN_DIR)/brz

# Build padrão: release
.PHONY: all clean debug release install test

all: release

# Release: otimizado
release: CFLAGS += -O2 -DNDEBUG
release: $(TARGET)

# Debug: com símbolos e sanitizers
debug: CFLAGS += -g -O0 -fsanitize=address,undefined
debug: LDFLAGS += -fsanitize=address,undefined
debug: $(TARGET)

# Linkagem
$(TARGET): $(OBJECTS) | $(BIN_DIR)
	@echo "  LINK  $@"
	@$(CC) $(OBJECTS) -o $@ $(LDFLAGS)
	@echo ""
	@echo "  Build concluído: $@"
	@echo ""

# Compilação
$(BUILD_DIR)/%.o: $(SRC_DIR)/%.c | $(BUILD_DIR)
	@echo "  CC    $<"
	@$(CC) $(CFLAGS) -c $< -o $@

# Diretórios
$(BUILD_DIR):
	@mkdir -p $@

$(BIN_DIR):
	@mkdir -p $@

# Limpeza
clean:
	@rm -rf $(BUILD_DIR) $(BIN_DIR)
	@echo "  Limpo."

# Instalação global
install: release
	@cp $(TARGET) /usr/local/bin/brz
	@echo "  Instalado em /usr/local/bin/brz"

# Teste rápido
test: release
	@echo "=== Testes BRZ VM ==="
	@$(TARGET) -c 'mostrar("ola mundo")'
	@echo "--- OK ---"
