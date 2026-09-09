import re, json
from shapely.geometry import LineString, Polygon, mapping
from shapely.affinity import scale
from shapely.ops import split
from shapely.validation import make_valid

with open("data/firs_vizinhos.json") as f:
    FIRS = json.load(f)

# Mapa consulta REDEMET -> ID no texto da mensagem
FIR_TEXT_MAP = {
    "SGAS": "SGFA",  # Paraguai: consulta SGAS, texto usa SGFA
}

def parse_coord(s):
    s = s.strip()
    lat = float(s[1:3]) + float(s[3:5]) / 60.0
    if s[0] == 'S': lat = -lat
    lon_s = s.split()[1]
    lon = float(lon_s[1:4]) + float(lon_s[4:6]) / 60.0
    if lon_s[0] == 'W': lon = -lon
    return (lon, lat)

def side_score(part, direction):
    cx, cy = part.centroid.x, part.centroid.y
    return {
        "N": cy, "S": -cy, "E": cx, "W": -cx,
        "NE": cx + cy, "SW": -(cx + cy),
        "NW": -cx + cy, "SE": cx - cy,
    }[direction]

def parse_sigmet(raw, fir_query):
    line = raw.replace("\n", " ")
    fir_text = FIR_TEXT_MAP.get(fir_query, fir_query)

    # número (alfanumérico)
    m = re.search(fir_text + r'\s+SIGMET\s+(\w+)', line)
    number = m.group(1) if m else "?"

    # VALID
    m = re.search(r'VALID\s+(\d{6})/(\d{6})', line)
    if not m: return None, "sem VALID"
    valid = m.group(1) + "/" + m.group(2)

    # Caso WI — polígono explícito
    wi = line.find("WI ")
    if wi != -1:
        end = next((line.find(x) for x in [" TOP ", "SFC/FL", "BLW FL", " FL"] if line.find(x) > wi), -1)
        if end == -1: return None, "sem endIndex"
        section = re.sub(r'\s*-\s*', ' - ', line[wi+3:end])
        coords = [parse_coord(c) for c in section.split(" - ") if re.match(r'^[NS]\d{4}\s+[WE]\d{5}$', c.strip())]
        if len(coords) < 3: return None, f"menos de 3 coords ({len(coords)})"
        if coords[0] != coords[-1]: coords.append(coords[0])
        return {"number": number, "valid": valid, "polygon": coords, "method": "WI"}, None

    # Caso X OF LINE — recortar FIR
    m = re.search(r'(N|S|E|W|NE|NW|SE|SW) OF LINE\s+(.*?)\s+(?:TOP |SFC/FL|BLW FL|\bFL\d)', line)
    if m:
        direction = m.group(1)
        raw_coords = re.split(r'\s*-\s*', m.group(2).strip())
        pts = [parse_coord(c) for c in raw_coords if re.match(r'^[NS]\d{4}\s+[WE]\d{5}$', c.strip())]
        if len(pts) < 2: return None, "OF LINE: menos de 2 pontos"

        fir_ring = FIRS.get(fir_query)
        if not fir_ring: return None, f"FIR {fir_query} não encontrada"
        fir_poly = make_valid(Polygon(fir_ring))

        ext_line = scale(LineString(pts), xfact=10, yfact=10)
        try:
            parts = split(fir_poly, ext_line)
        except Exception as e:
            return None, f"split falhou: {e}"

        best = max(parts.geoms, key=lambda p: side_score(p, direction))
        coords = list(best.exterior.coords)
        return {"number": number, "valid": valid, "polygon": coords, "method": f"{direction} OF LINE"}, None

    return None, "sem WI nem OF LINE"


# ── TESTES ──────────────────────────────────────────────────────────────────

tests = [
    ("SGAS", """WSPY31 SGAS 191640
SGFA SIGMET 5 VALID 191640/192040 SGAS-
SGFA ASUNCION FIR SEV TURB FCST AT 1635Z SW OF LINE S1934 W06101 -
S2639 W05456  TOP FL250/360 MOV STNR NC="""),

    ("SPIM", """WSPR31 SPIM 191650
SPIM SIGMET 10 VALID 191650/191950 SPJC-
SPIM LIMA FIR EMBD TS OBS AT 1620Z WI S0420 W07758 -
S0259 W07530 - S0557 W07530 - S0648 W07628 - S0420 W07758
TOP FL460 STNR NC="""),

    ("SKBO", """WVCO31 SKBO 191740
SKED SIGMET 5 VALID 191745/192310 SKBO-
SKED BOGOTA FIR VA CLD MT VOLCAN PURACE PSN: N0219 W07624 VA CLD OBS AT 1740Z
WI N0227 W07641 - N0220 W07623- N0219 W07623 - N0219 W07643 - N0227 W07641
SFC/FL220 MOV W 15KT="""),

    ("SCTZ", """WSCH31 SCTE 191529
SCTZ SIGMET 04 VALID 191530/191930 SCTE-
SCTZ PUERTO MONTT FIR SEV MTW FCST E OF LINE S4000 W07230 - S4700
W07430 BLW FL120 STNR NC="""),

    ("SUEO", """WSUY31 SUMU 191750
SUEO SIGMET A6 VALID 191800/192200 SUMU-
SUEO MONTEVIDEO FIR SEV TURB FCST WI S3015 W05750 - S3005 W05649 - S3223
W05321 - S3402 W05255 - S3631 W05255 - S3420 W05830 - S3015 W05750 FL240/400
STNR NC="""),
]

for fir, raw in tests:
    result, err = parse_sigmet(raw, fir)
    print(f"\n{'='*60}")
    print(f"FIR: {fir}")
    if err:
        print(f"❌ ERRO: {err}")
    else:
        poly = result['polygon']
        lons = [c[0] for c in poly]
        lats = [c[1] for c in poly]
        print(f"✅ SIGMET {result['number']} | VALID {result['valid']} | método: {result['method']}")
        print(f"   Pontos: {len(poly)}")
        print(f"   bbox: lon[{min(lons):.3f},{max(lons):.3f}] lat[{min(lats):.3f},{max(lats):.3f}]")
        print(f"   Primeiros 4 pontos: {poly[:4]}")
