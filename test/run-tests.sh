#!/usr/bin/env bash
set -euo pipefail

# Acessar a raiz do projeto
cd "$(dirname "$0")/.."

# Compilar o projeto
echo "=== Compilando Ka ==="
mkdir -p out
javac -d out tool/GenerateAst.java Ka/*.java Ka/ast/*.java Ka/tac/*.java test/tac/*.java

echo "=== Executando testes TAC ==="
java -cp out ka.tac.TacInfrastructureTest
java -cp out ka.TacBuilderTest
java -cp out ka.tac.TacCliTest
java -cp out ka.tac.TacCorpusTest

# Executar os testes
echo "=== Executando ShellTestRunner ==="
shelltest test/shelltest/*.t
