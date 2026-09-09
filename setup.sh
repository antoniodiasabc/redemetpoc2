#!/bin/bash
# setup.sh — prepara o ambiente após clonar o repositório
set -e

echo "=== Verificando pré-requisitos ==="
command -v java   >/dev/null || { echo "ERRO: Java 11+ não encontrado"; exit 1; }
command -v mvn    >/dev/null || { echo "ERRO: Maven não encontrado"; exit 1; }
command -v docker >/dev/null || { echo "ERRO: Docker não encontrado"; exit 1; }

echo "=== Verificando arquivos obrigatórios ==="

# JARs locais referenciados no pom.xml (não estão no Maven Central)
REQUIRED_LIBS=(
    "libs/cdm-core-5.4.2.jar"
    "libs/grib-5.4.2.jar"
    "libs/guava-32.1.3-jre.jar"
    "libs/jcommander-1.82.jar"
    "libs/protobuf-java-3.21.12.jar"
    "libs/jdom2-2.0.6.jar"
    "libs/joda-time-2.12.5.jar"
    "libs/re2j-1.7.jar"
    "libs/opencv-4.5.1-2.jar"
    "libs/ojdbc8-21.9.0.0.jar"
    "libs/jackson-databind-2.15.2.jar"
    "libs/gson-2.8.9.jar"
    "libs/httpclient-4.5.14.jar"
    "libs/httpcore-4.4.16.jar"
    "libs/jsch-0.1.55.jar"
)

MISSING=0
for f in "${REQUIRED_LIBS[@]}"; do
    [ -f "$f" ] || { echo "  FALTANDO: $f"; MISSING=1; }
done

# Biblioteca nativa OpenCV
[ -f "libs/libopencv_java451.so" ] || { echo "  FALTANDO: libs/libopencv_java451.so"; MISSING=1; }

# Credenciais
[ -f ".env" ] || { echo "  FALTANDO: .env (copie o .env.example e preencha)"; MISSING=1; }

[ $MISSING -eq 1 ] && {
    echo ""
    echo "Copie os arquivos faltando da máquina de origem:"
    echo "  rsync -av origem:/caminho/redemetpoc2/libs/ ./libs/"
    echo "  rsync -av origem:/caminho/redemetpoc2/.env ./"
    exit 1
}

echo "=== Build ==="
mvn clean package -DskipTests -q

echo "=== Subindo containers ==="
docker compose up -d --build

echo ""
echo "Pronto! Acesse http://localhost"
