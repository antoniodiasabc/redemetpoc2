#!/bin/bash
# Faz backup do PocSigmetApplication.java antes de migrar
# Uso: ./backup-before-migrate.sh "fase1-health"
LABEL=${1:-"backup"}
DATE=$(date +%Y%m%d_%H%M)
SRC="src/main/java/com/pocsigmet/PocSigmetApplication.java"
BAK="${SRC}.bak_${DATE}_${LABEL}"
cp "$SRC" "$BAK"
echo "✅ Backup: $BAK"
