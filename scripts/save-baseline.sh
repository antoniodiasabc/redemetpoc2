#!/bin/bash
# Salva baseline de todos os endpoints antes de qualquer migração
mkdir -p baseline
ENDPOINTS=(
  "/health" "/sigmet/count" "/firs" "/hsv_metadata" "/cache/stats"
  "/monitoring/stats" "/frames" "/canal16frames" "/convection"
  "/metar_sbsp" "/metar_sboi" "/metar_top20_sb" "/metar_top50_sb"
  "/api/v1/alerts/metar" "/api/v1/opmet/messages"
  "/api/v1/sigwx/current" "/api/v1/sigwx/meta"
  "/api/wind/barbs/fl050" "/api/wind/barbs/fl390"
  "/redemet_sigmets_json" "/redemet_sigmets_all"
  "/api/v1/animation/realcada-images" "/grib2_info"
)
for ep in "${ENDPOINTS[@]}"; do
  name=$(echo $ep | tr '/' '_' | tr -d ' ')
  STATUS=$(curl -s -o "baseline/${name}.json" -w "%{http_code}" "http://localhost${ep}")
  echo "$STATUS $ep"
done
echo "✅ Baseline salvo em ./baseline/"
