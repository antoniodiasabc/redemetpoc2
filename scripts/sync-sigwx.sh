#!/bin/bash
# Monitora /mnt/c/Users/aodias/sigwx e copia arquivos novos para os containers
SOURCE="/mnt/c/Users/aodias/sigwx"
CONTAINERS=("redemetpoc2_bk07042026-app-1" "redemetpoc2_bk07042026-app-2")
DEST="/sigwx"

inotifywait -m -e close_write,moved_to "$SOURCE" --format '%f' 2>/dev/null | while read FILE; do
    for C in "${CONTAINERS[@]}"; do
        docker cp "$SOURCE/$FILE" "$C:$DEST/$FILE" 2>/dev/null && \
            echo "$(date '+%H:%M:%S') copiado $FILE → $C"
    done
done
