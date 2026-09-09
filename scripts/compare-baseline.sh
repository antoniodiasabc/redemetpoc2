#!/bin/bash
# Compara endpoint atual com baseline salvo
# Uso: ./compare-baseline.sh /api/v1/opmet/messages
ENDPOINT=$1
name=$(echo $ENDPOINT | tr '/' '_' | tr -d ' ')
curl -s "http://localhost${ENDPOINT}" > /tmp/current.json
if diff <(cat "baseline/${name}.json" | python3 -m json.tool 2>/dev/null || cat "baseline/${name}.json") \
        <(cat /tmp/current.json | python3 -m json.tool 2>/dev/null || cat /tmp/current.json) > /dev/null 2>&1; then
  echo "✅ $ENDPOINT — OK (igual ao baseline)"
else
  echo "⚠️  $ENDPOINT — DIFF detectado:"
  diff <(cat "baseline/${name}.json" | python3 -m json.tool 2>/dev/null || cat "baseline/${name}.json") \
       <(cat /tmp/current.json | python3 -m json.tool 2>/dev/null || cat /tmp/current.json) | head -20
fi
