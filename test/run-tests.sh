#!/usr/bin/env bash
set -euo pipefail

# Acessar a raiz do projeto
cd "$(dirname "$0")/.."

# Compilar o projeto
echo "=== Compilando Ka ==="
mkdir -p out
javac -d out tool/GenerateAst.java Ka/*.java Ka/ast/*.java

# Executar os testes
echo "=== Executando ShellTestRunner ==="
shelltest test/shelltest/*.t