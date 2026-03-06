/*
 * BRZ Virtual Machine — Ponto de Entrada
 *
 * Modos de execução:
 *   brz                  →  REPL interativo
 *   brz arquivo.brz      →  Executa arquivo
 *   brz -c "código"      →  Executa inline
 *   brz --versao         →  Mostra versão
 *   brz --ajuda          →  Mostra ajuda
 *
 * Copyright (c) 2026 DornelassDev
 * Licensed under MIT
 */

#include "common.h"
#include "vm.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* ===========================================================================
 * REPL interativo
 * =========================================================================== */

static void repl(void) {
    printf("\033[1;36m");
    printf("  ____  _____  ______  __      ____  __\n");
    printf(" |  _ \\|  __ \\|___  / \\ \\    / /  \\/  |\n");
    printf(" | |_) | |__) |  / /   \\ \\  / /| \\  / |\n");
    printf(" |  _ <|  _  /  / /     \\ \\/ / | |\\/| |\n");
    printf(" | |_) | | \\ \\ / /__     \\  /  | |  | |\n");
    printf(" |____/|_|  \\_\\_____|     \\/   |_|  |_|\n");
    printf("\033[0m\n");
    printf(" BRZ VM v%s — Linguagem de programação em Português\n", BRZ_VERSION_STRING);
    printf(" Digite 'sair()' para encerrar.\n\n");

    char line[4096];

    for (;;) {
        printf("\033[1;32mbrz>\033[0m ");
        fflush(stdout);

        if (!fgets(line, sizeof(line), stdin)) {
            printf("\n");
            break;
        }

        /* Remove \n final. */
        size_t len = strlen(line);
        if (len > 0 && line[len - 1] == '\n') {
            line[len - 1] = '\0';
            len--;
        }

        /* Comandos especiais. */
        if (len == 0) continue;
        if (strcmp(line, "sair()") == 0 || strcmp(line, "sair") == 0) break;
        if (strcmp(line, "ajuda") == 0 || strcmp(line, "ajuda()") == 0) {
            printf("  Comandos:\n");
            printf("    sair()     — encerra o REPL\n");
            printf("    ajuda()    — mostra esta mensagem\n");
            printf("    limpar()   — limpa a tela\n\n");
            continue;
        }
        if (strcmp(line, "limpar()") == 0 || strcmp(line, "limpar") == 0) {
            printf("\033[H\033[2J");
            continue;
        }

        brz_vm_interpret(line);
    }
}

/* ===========================================================================
 * Execução de arquivo
 * =========================================================================== */

static char* read_file(const char* path) {
    FILE* file = fopen(path, "rb");
    if (file == NULL) {
        fprintf(stderr, "\033[31mErro: não foi possível abrir '%s'.\033[0m\n", path);
        exit(74);
    }

    fseek(file, 0L, SEEK_END);
    size_t file_size = (size_t)ftell(file);
    rewind(file);

    char* buffer = (char*)malloc(file_size + 1);
    if (buffer == NULL) {
        fprintf(stderr, "\033[31mErro: sem memória para ler '%s'.\033[0m\n", path);
        fclose(file);
        exit(74);
    }

    size_t bytes_read = fread(buffer, sizeof(char), file_size, file);
    if (bytes_read < file_size) {
        fprintf(stderr, "\033[31mErro: falha ao ler '%s'.\033[0m\n", path);
        free(buffer);
        fclose(file);
        exit(74);
    }

    buffer[bytes_read] = '\0';
    fclose(file);
    return buffer;
}

static void run_file(const char* path) {
    char* source = read_file(path);
    BrzInterpretResult result = brz_vm_interpret(source);
    free(source);

    if (result == INTERPRET_COMPILE_ERROR) exit(65);
    if (result == INTERPRET_RUNTIME_ERROR) exit(70);
}

/* ===========================================================================
 * Ajuda e versão
 * =========================================================================== */

static void show_version(void) {
    printf("BRZ VM v%s\n", BRZ_VERSION_STRING);
    printf("Linguagem de programação em Português\n");
    printf("Copyright (c) 2026 DornelassDev\n");
}

static void show_help(void) {
    printf("Uso: brz [opcoes] [arquivo.brz]\n\n");
    printf("Opções:\n");
    printf("  -c <código>    Executa código inline\n");
    printf("  --versao       Mostra a versão\n");
    printf("  --ajuda        Mostra esta mensagem\n");
    printf("  --debug        Ativa modo debug (trace de bytecodes)\n\n");
    printf("Exemplos:\n");
    printf("  brz                          # REPL interativo\n");
    printf("  brz programa.brz             # Executa arquivo\n");
    printf("  brz -c \"mostrar(42)\"         # Executa inline\n");
}

/* ===========================================================================
 * main
 * =========================================================================== */

int main(int argc, char* argv[]) {
    brz_vm_init();

    if (argc == 1) {
        /* Sem argumentos → REPL. */
        repl();
    } else if (argc == 2) {
        if (strcmp(argv[1], "--versao") == 0 || strcmp(argv[1], "-v") == 0) {
            show_version();
        } else if (strcmp(argv[1], "--ajuda") == 0 || strcmp(argv[1], "-h") == 0) {
            show_help();
        } else {
            run_file(argv[1]);
        }
    } else if (argc == 3 && strcmp(argv[1], "-c") == 0) {
        /* Execução inline. */
        BrzInterpretResult result = brz_vm_interpret(argv[2]);
        if (result == INTERPRET_COMPILE_ERROR) { brz_vm_free(); exit(65); }
        if (result == INTERPRET_RUNTIME_ERROR) { brz_vm_free(); exit(70); }
    } else {
        show_help();
        brz_vm_free();
        return 64;
    }

    brz_vm_free();
    return 0;
}
