#!/bin/bash
# Rollback para um backup específico
# Uso: ./rollback.sh PocSigmetApplication.java.bak_20260810_1430_fase1-health
BAK=$1
SRC="src/main/java/com/pocsigmet/PocSigmetApplication.java"
if [ -z "$BAK" ]; then
  echo "Backups disponíveis:"
  ls ${SRC}.bak_* 2>/dev/null || echo "Nenhum backup encontrado"
  exit 1
fi
cp "$BAK" "$SRC"
echo "✅ Rollback feito: $BAK"
echo "🔄 Rebuilding..."
./rebuild.sh
