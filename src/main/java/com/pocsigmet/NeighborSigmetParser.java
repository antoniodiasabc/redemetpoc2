package com.pocsigmet;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.*;

/**
 * Parser de SIGMETs para FIRs vizinhas ao Brasil.
 * Suporta 5 formatos: WI, X OF LINE, X OF lat/lon, SFC/FL, ENTIRE FIR.
 * Não modifica nenhuma classe existente.
 */
@org.springframework.stereotype.Component
public class NeighborSigmetParser {

    private static final Logger log = LoggerFactory.getLogger(NeighborSigmetParser.class);

    // Mapa: identificador de consulta REDEMET -> identificador no texto da mensagem
    private static final Map<String, String> FIR_TEXT_MAP = new HashMap<>();
    static {
        FIR_TEXT_MAP.put("SPIM", "SPIM");   // Peru Lima
        FIR_TEXT_MAP.put("SVZM", "SVZM");   // Venezuela Maiquetia
        FIR_TEXT_MAP.put("SKED", "SKED");   // Colombia Bogota
        FIR_TEXT_MAP.put("SKEC", "SKEC");   // Colombia Barranquilla
        FIR_TEXT_MAP.put("SEFG", "SEFG");   // Equador Guayaquil
        FIR_TEXT_MAP.put("SLLF", "SLLF");   // Bolivia
        FIR_TEXT_MAP.put("SGFA", "SGFA");   // Paraguai
        FIR_TEXT_MAP.put("SARR", "SARC");   // Argentina Resistencia (ACC=SARC)
        FIR_TEXT_MAP.put("SACF", "SACC");   // Argentina Cordoba (ACC=SACC)
        FIR_TEXT_MAP.put("SAMF", "SAMF");   // Argentina Mendoza
        FIR_TEXT_MAP.put("SAEF", "SAEF");   // Argentina Ezeiza
        FIR_TEXT_MAP.put("SAVF", "SAVC");   // Argentina Comodoro (ACC=SAVC)
        FIR_TEXT_MAP.put("SUEO", "SUEO");   // Uruguai
        FIR_TEXT_MAP.put("SCFZ", "SCFZ");   // Chile Antofagasta
        FIR_TEXT_MAP.put("SCEZ", "SCEZ");   // Chile Santiago
        FIR_TEXT_MAP.put("SCTZ", "SCTZ");   // Chile Puerto Montt
        FIR_TEXT_MAP.put("SCCZ", "SCCZ");   // Chile Punta Arenas
        FIR_TEXT_MAP.put("SCIZ", "SCIZ");   // Chile Isla de Pascua
        FIR_TEXT_MAP.put("SYGC", "SYGC");   // Guiana
        FIR_TEXT_MAP.put("SOOO", "SOOO");   // Suriname
        FIR_TEXT_MAP.put("SMPM", "SMPM");   // Guiana Francesa
        FIR_TEXT_MAP.put("GOOO", "GOOO");   // Dakar Oceanic
        FIR_TEXT_MAP.put("TTZP", "TTZP");   // Trinidad e Tobago Piarco
        FIR_TEXT_MAP.put("FAJO", "FAJO");   // Johannesburg Oceanic
    }

    // União de todas as FIRs brasileiras — usada para subtrair de polígonos vizinhos
    private static Geometry BRAZIL_FIRS = null;
    private static final Object BRAZIL_LOCK = new Object();

    private static Map<String, List<List<Double>>> FIR_POLYGONS = null;
    private static final Object FIR_LOCK = new Object();

    @SuppressWarnings("unchecked")
    private static Geometry getBrazilFirs() {
        if (BRAZIL_FIRS != null) return BRAZIL_FIRS;
        synchronized (BRAZIL_LOCK) {
            if (BRAZIL_FIRS != null) return BRAZIL_FIRS;
            try {
                InputStream is = NeighborSigmetParser.class.getClassLoader()
                        .getResourceAsStream("firs_brasil_decea_oficial.geojson");
                if (is == null)
                    is = new java.io.FileInputStream("data/config/firs_brasil_decea_oficial.geojson");
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> geojson = mapper.readValue(is, Map.class);
                List<Map<String, Object>> features = (List<Map<String, Object>>) geojson.get("features");
                List<Geometry> polys = new ArrayList<>();
                for (Map<String, Object> f : features) {
                    try {
                        Map<String, Object> geom = (Map<String, Object>) f.get("geometry");
                        List<List<List<Object>>> rings = (List<List<List<Object>>>) geom.get("coordinates");
                        Coordinate[] cs = rings.get(0).stream()
                                .map(p -> new Coordinate(((Number)p.get(0)).doubleValue(), ((Number)p.get(1)).doubleValue()))
                                .toArray(Coordinate[]::new);
                        if (!cs[0].equals2D(cs[cs.length-1])) { cs = Arrays.copyOf(cs, cs.length+1); cs[cs.length-1] = cs[0]; }
                        polys.add(GF.createPolygon(cs).buffer(0));
                    } catch (Exception ignored) {}
                }
                BRAZIL_FIRS = polys.stream().reduce(Geometry::union).orElse(GF.createEmpty(2)).buffer(0);
                log.info("✅ FIRs Brasil carregadas: {} polígonos, bounds={}", polys.size(), BRAZIL_FIRS.getEnvelopeInternal());
            } catch (Exception e) {
                log.error("❌ Erro ao carregar firs_brasil: {}", e.getMessage());
                BRAZIL_FIRS = GF.createEmpty(2);
            }
            return BRAZIL_FIRS;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<List<Double>>> getFirPolygons() {
        if (FIR_POLYGONS != null) return FIR_POLYGONS;
        synchronized (FIR_LOCK) {
            if (FIR_POLYGONS != null) return FIR_POLYGONS;
            try {
                InputStream is = NeighborSigmetParser.class.getClassLoader()
                        .getResourceAsStream("firs_vizinhos.json");
                if (is == null) {
                    // fallback: ler do filesystem
                    is = new java.io.FileInputStream("data/firs_vizinhos.json");
                }
                ObjectMapper mapper = new ObjectMapper();
                FIR_POLYGONS = mapper.readValue(is, Map.class);
                log.info("✅ FIRs vizinhas carregadas: {}", FIR_POLYGONS.keySet());
            } catch (Exception e) {
                log.error("❌ Erro ao carregar firs_vizinhos.json: {}", e.getMessage());
                FIR_POLYGONS = new HashMap<>();
            }
            return FIR_POLYGONS;
        }
    }

    // ── Parsing de coordenada "S0420 W07758" ou "N0227 W07641" ──────────────
    private static double[] parseCoord(String s) {
        s = s.trim();
        if (!s.matches("^[NS]\\d{4}\\s+[WE]\\d{5}$")) return null;
        String[] parts = s.split("\\s+");
        String latStr = parts[0];
        double lat = Double.parseDouble(latStr.substring(1, 3)) + Double.parseDouble(latStr.substring(3, 5)) / 60.0;
        if (latStr.charAt(0) == 'S') lat = -lat;
        String lonStr = parts[1];
        double lon = Double.parseDouble(lonStr.substring(1, 4)) + Double.parseDouble(lonStr.substring(4, 6)) / 60.0;
        if (lonStr.charAt(0) == 'W') lon = -lon;
        return new double[]{lon, lat};
    }

    private static List<double[]> parseCoordList(String section) {
        List<double[]> result = new ArrayList<>();
        Matcher m = Pattern.compile("[NS]\\d{4}\\s+[WE]\\d{5}").matcher(section);
        while (m.find()) {
            double[] pt = parseCoord(m.group());
            if (pt != null) result.add(pt);
        }
        return result;
    }

    // ── Verificar se SIGMET ainda é válido ───────────────────────────────────
    private static boolean isValid(String endTime) {
        try {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            int endDay  = Integer.parseInt(endTime.substring(0, 2));
            int endHour = Integer.parseInt(endTime.substring(2, 4));
            int endMin  = Integer.parseInt(endTime.substring(4, 6));
            // Construir endDateTime no mês atual; se endDay < curDay assume mês seguinte
            int month = now.getMonthValue(), year = now.getYear();
            if (endDay < now.getDayOfMonth()) {
                month++; if (month > 12) { month = 1; year++; }
            }
            LocalDateTime end = LocalDateTime.of(year, month, endDay, endHour, endMin);
            return now.isBefore(end);
        } catch (Exception e) { return false; }
    }

    private static List<List<Double>> getFirRing(String firQuery) {
        return getFirPolygons().get(firQuery);
    }

    // ── JTS factory ─────────────────────────────────────────────────────────
    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private static Polygon firPolygon(String firQuery) {
        List<List<Double>> ring = getFirRing(firQuery);
        if (ring == null) return null;
        Coordinate[] coords = ring.stream()
                .map(p -> new Coordinate(((Number)p.get(0)).doubleValue(), ((Number)p.get(1)).doubleValue()))
                .toArray(Coordinate[]::new);
        if (!coords[0].equals2D(coords[coords.length - 1])) {
            coords = Arrays.copyOf(coords, coords.length + 1);
            coords[coords.length - 1] = coords[0];
        }
        Polygon p = GF.createPolygon(coords);
        Geometry base = p.isValid() ? p : p.buffer(0);
        try {
            PrecisionModel pm = new PrecisionModel(1e6); // 6 casas decimais
            Geometry a = GeometryPrecisionReducer.reduce(base, pm);
            Geometry b = GeometryPrecisionReducer.reduce(getBrazilFirs(), pm);
            Geometry diff = a.difference(b).buffer(0);
            return largestPolygon(diff, (Polygon) base);
        } catch (Exception e) {
            log.warn("firPolygon difference falhou para {}: {}", firQuery, e.getMessage());
            return (Polygon) base;
        }
    }

    private static Polygon largestPolygon(Geometry g, Polygon fallback) {
        if (g instanceof Polygon) return (Polygon) g;
        if (g instanceof org.locationtech.jts.geom.MultiPolygon && !g.isEmpty()) {
            Polygon largest = null;
            for (int i = 0; i < g.getNumGeometries(); i++) {
                Polygon sub = (Polygon) g.getGeometryN(i);
                if (largest == null || sub.getArea() > largest.getArea()) largest = sub;
            }
            return largest;
        }
        return fallback;
    }

    private static List<double[]> splitFirBySide(String firQuery, List<double[]> linePts, String direction) {
        Polygon fir = firPolygon(firQuery);
        if (fir == null) return null;

        // Se a linha original não intersecta a FIR, retorna a FIR inteira
        LineString originalLine = GF.createLineString(linePts.stream()
                .map(p -> new Coordinate(p[0], p[1])).toArray(Coordinate[]::new));
        if (!originalLine.intersects(fir)) {
            return geometryToCoords(fir);
        }

        Envelope env = fir.getEnvelopeInternal();

        // Para linhas com múltiplos segmentos usa splitFirBySideExtended (cross product + bbox)
        // que é mais preciso para linhas em zigue-zague
        if (linePts.size() > 2) {
            return splitFirBySideExtended(fir, env, linePts, direction, firQuery);
        }

        // Constrói half-plane usando a linha estendida como borda real (não bounding box)
        // Funciona para qualquer orientação de linha
        double[] p1 = linePts.get(0), p2 = linePts.get(linePts.size() - 1);
        double minX = env.getMinX() - 1, maxX = env.getMaxX() + 1;
        double minY = env.getMinY() - 1, maxY = env.getMaxY() + 1;

        // Determina o half-plane baseado na direção e na linha
        Geometry halfPlane;
        try {
            // Estender a linha além do envelope da FIR
            double dx = p2[0] - p1[0], dy = p2[1] - p1[1];
            double len = Math.hypot(dx, dy);
            if (len < 1e-10) return null;
            double diag = Math.hypot(env.getWidth(), env.getHeight()) * 2;
            double f = diag / len;
            double ex1 = p1[0] - dx * f, ey1 = p1[1] - dy * f;
            double ex2 = p2[0] + dx * f, ey2 = p2[1] + dy * f;

            // Vetor perpendicular apontando para o lado correto
            // Normal à direita da linha (dx,dy) é (dy,-dx); à esquerda é (-dy,dx)
            double nx = dy, ny = -dx; // normal à direita
            // Verificar se a normal aponta para o lado correto da direção
            boolean flip = false;
            switch (direction) {
                case "N": flip = (ny < 0); break;
                case "S": flip = (ny > 0); break;
                case "E": flip = (nx < 0); break;
                case "W": flip = (nx > 0); break;
                case "NE": flip = (nx + ny < 0); break;
                case "SW": flip = (nx + ny > 0); break;
                case "NW": flip = (-nx + ny < 0); break;
                case "SE": flip = (nx - ny < 0); break;
            }
            if (flip) { nx = -nx; ny = -ny; }

            // Validação: verifica se um ponto de referência no lado correto está no half-plane
            // Ponto de referência: centroide da linha deslocado na direção correta
            double midX = (p1[0] + p2[0]) / 2, midY = (p1[1] + p2[1]) / 2;
            double refX = midX, refY = midY;
            switch (direction) {
                case "N":  refY = midY + 5; break;
                case "S":  refY = midY - 5; break;
                case "E":  refX = midX + 5; break;
                case "W":  refX = midX - 5; break;
                case "NE": refX = midX + 5; refY = midY + 5; break;
                case "SW": refX = midX - 5; refY = midY - 5; break;
                case "NW": refX = midX - 5; refY = midY + 5; break;
                case "SE": refX = midX + 5; refY = midY - 5; break;
            }
            // O ponto ref deve estar no lado do half-plane: dot(ref - ex1, normal) > 0
            double dotRef = (refX - ex1) * nx + (refY - ey1) * ny;
            if (dotRef < 0) { nx = -nx; ny = -ny; }

            // Construir polígono do half-plane: linha estendida + offset grande no lado correto
            double offset = diag * 2;
            halfPlane = GF.createPolygon(new Coordinate[]{
                new Coordinate(ex1, ey1),
                new Coordinate(ex2, ey2),
                new Coordinate(ex2 + nx * offset, ey2 + ny * offset),
                new Coordinate(ex1 + nx * offset, ey1 + ny * offset),
                new Coordinate(ex1, ey1)
            });
        } catch (Exception e) {
            return splitFirBySideExtended(fir, env, linePts, direction);
        }

        try {
            Geometry result = fir.intersection(halfPlane).buffer(0);
            if (result.isEmpty()) return null;
            return geometryToCoords(result);
        } catch (Exception e) {
            log.warn("splitFirBySide half-plane falhou para {}: {}", firQuery, e.getMessage());
            return null;
        }
    }

    /** Extrai coordenadas do maior polígono de uma geometria (Polygon ou MultiPolygon), simplificado. */
    private static List<double[]> geometryToCoords(Geometry g) {
        Polygon p = largestPolygon(g, null);
        if (p == null) return null;
        Geometry out = p;
        if (p.getCoordinates().length > 200) {
            for (double tol : new double[]{0.02, 0.05, 0.1, 0.2}) {
                Geometry s = org.locationtech.jts.simplify.DouglasPeuckerSimplifier.simplify(p, tol);
                if (s.isValid() && !s.isEmpty() && s.getCoordinates().length >= 4) { out = s; }
                if (out.getCoordinates().length <= 200) break;
            }
        }
        List<double[]> result = new ArrayList<>();
        for (Coordinate c : out.getCoordinates()) result.add(new double[]{c.x, c.y});
        return result;
    }

    private static List<double[]> splitFirBySideExtended(Polygon fir, Envelope env, List<double[]> linePts, String direction) {
        return splitFirBySideExtended(fir, env, linePts, direction, null);
    }

    private static List<double[]> splitFirBySideExtended(Polygon fir, Envelope env, List<double[]> linePts, String direction, String firQuery) {
        // Porta fiel do mountPolygonSigmetLine4 do conversor-iwxxm:
        // 1. LineSegment = primeiro e último ponto da linha (vetor geral)
        // 2. calcLine = cross product do vetor geral em relação a cada vértice da FIR
        // 3. Filtro: calcLine no lado correto E (latOk || lonOk) — bbox da linha com tolerância
        // 4. Adiciona pontos de interseção da linha com a borda da FIR
        // 5. Fecha o polígono via ConvexHull intersectado com a FIR

        Coordinate[] firCoords = fir.getCoordinates();

        // Extremos da linha + tolerância, expandidos no lado correto da direção
        double tol = 0.3;
        double minLon = linePts.stream().mapToDouble(p -> p[0]).min().orElse(0) - tol;
        double maxLon = linePts.stream().mapToDouble(p -> p[0]).max().orElse(0) + tol;
        double minLat = linePts.stream().mapToDouble(p -> p[1]).min().orElse(0) - tol;
        double maxLat = linePts.stream().mapToDouble(p -> p[1]).max().orElse(0) + tol;
        // Expande no lado correto até a borda da FIR
        switch (direction) {
            case "N": case "NE": case "NW": maxLat = env.getMaxY() + 1; break;
            case "S": case "SE": case "SW": minLat = env.getMinY() - 1; break;
        }
        switch (direction) {
            case "E": case "NE": case "SE": maxLon = env.getMaxX() + 1; break;
            case "W": case "NW": case "SW": minLon = env.getMinX() - 1; break;
        }

        double[] lp0 = linePts.get(0), lp1 = linePts.get(linePts.size() - 1);
        boolean wantPositive = wantLeftSide(direction, lp0, lp1);

        List<Coordinate> sideCoords = new ArrayList<>();
        for (Coordinate v : firCoords) {
            double calcLine = (lp1[0]-lp0[0])*(v.y-lp0[1]) - (lp1[1]-lp0[1])*(v.x-lp0[0]);
            boolean onSide = wantPositive ? calcLine > 0 : calcLine < 0;
            if (!onSide) continue;
            boolean latOk = v.x > minLon && v.x < maxLon;
            boolean lonOk = v.y > minLat && v.y < maxLat;
            if (latOk || lonOk) sideCoords.add(v);
        }

        // Adiciona pontos de interseção da linha com a borda da FIR
        LineString firBoundary = fir.getExteriorRing();
        for (int i = 0; i < linePts.size() - 1; i++) {
            double[] s0 = linePts.get(i), s1 = linePts.get(i+1);
            LineString seg = GF.createLineString(new Coordinate[]{
                new Coordinate(s0[0], s0[1]), new Coordinate(s1[0], s1[1])
            });
            Geometry inter = firBoundary.intersection(seg);
            if (!inter.isEmpty()) for (Coordinate c : inter.getCoordinates()) sideCoords.add(c);
        }

        if (sideCoords.size() < 3)
            return firCentroidOnSide(fir, linePts, direction) ? geometryToCoords(fir) : null;

        try {
            Coordinate[] arr = sideCoords.toArray(new Coordinate[0]);
            Geometry hull = GF.createMultiPointFromCoords(arr).convexHull();
            Geometry result = hull.intersection(fir).buffer(0);
            if (result.isEmpty() || result.getArea() < 1e-6)
                return firCentroidOnSide(fir, linePts, direction) ? geometryToCoords(fir) : null;
            return geometryToCoords(result);
        } catch (Exception e) {
            log.warn("splitFirBySideExtended falhou para {}: {}", firQuery, e.getMessage());
            return firCentroidOnSide(fir, linePts, direction) ? geometryToCoords(fir) : null;
        }
    }

    /** Determina se o lado esquerdo do segmento s0->s1 corresponde à direção desejada */
    private static boolean wantLeftSide(String direction, double[] s0, double[] s1) {
        double dx = s1[0] - s0[0], dy = s1[1] - s0[1];
        // Normal esquerda: (-dy, dx) — aponta para o lado esquerdo
        double nx = -dy, ny = dx;
        switch (direction) {
            case "N":  return ny > 0;
            case "S":  return ny < 0;
            case "E":  return nx > 0;
            case "W":  return nx < 0;
            case "NE": return nx + ny > 0;
            case "SW": return nx + ny < 0;
            case "NW": return -nx + ny > 0;
            case "SE": return nx - ny > 0;
            default:   return true;
        }
    }

    /** Verifica se o centroide da FIR está no lado correto da linha */
    private static boolean firCentroidOnSide(Polygon fir, List<double[]> linePts, String direction) {
        Coordinate c = fir.getCentroid().getCoordinate();
        double[] p1 = linePts.get(0), p2 = linePts.get(linePts.size() - 1);
        double cross = (p2[0]-p1[0])*(c.y-p1[1]) - (p2[1]-p1[1])*(c.x-p1[0]);
        boolean leftSide = cross > 0;
        return leftSide == wantLeftSide(direction, p1, p2);
    }

    private static double directionScore(Coordinate c, String direction) {
        switch (direction) {
            case "N":  return  c.y;
            case "S":  return -c.y;
            case "E":  return  c.x;
            case "W":  return -c.x;
            case "NE": return  c.x + c.y;
            case "SW": return -(c.x + c.y);
            case "NW": return -c.x + c.y;
            case "SE": return  c.x - c.y;
            default:   return 0;
        }
    }

    // ── Parser principal ─────────────────────────────────────────────────────

    /**
     * Parseia uma mensagem SIGMET e retorna um Map com as propriedades e geometria GeoJSON,
     * ou null se não for possível parsear ou o SIGMET estiver expirado.
     */
    public Map<String, Object> parse(String raw, String firQuery) {
        String line = raw.replace("\n", " ").replace("\r", " ");
        // GOOO tem duas FIRs: Terrestre e Oceânica — usar polígono correto
        String firGeom = firQuery;
        if ("GOOO".equals(firQuery) && line.contains("DAKAR TERRESTRE")) firGeom = "GOOO_TER";
        String firText = FIR_TEXT_MAP.getOrDefault(firQuery, firQuery);

        // Número do SIGMET (alfanumérico) — tenta com firText, fallback para qualquer identificador
        Matcher mNum = Pattern.compile(firText + "\\s+SIGMET\\s+(\\w+)").matcher(line);
        String number;
        if (mNum.find()) {
            number = mNum.group(1);
        } else {
            Matcher mNumFallback = Pattern.compile("\\w+\\s+SIGMET\\s+(\\w+)").matcher(line);
            number = mNumFallback.find() ? mNumFallback.group(1) : "?";
            if ("?".equals(number)) log.warn("⚠️ Número SIGMET não encontrado para FIR {}: {}", firQuery, line.substring(0, Math.min(60, line.length())));
        }

        // VALID (com ou sem Z)
        Matcher mValid = Pattern.compile("VALID\\s+(\\d{6})Z?/(\\d{6})Z?").matcher(line);
        if (!mValid.find()) { log.debug("SIGMET sem VALID: {}", line.substring(0, Math.min(80, line.length()))); return null; }
        String startTime = mValid.group(1), endTime = mValid.group(2);
        if (!isValid(endTime)) { log.debug("SIGMET expirado: {}/{}", startTime, endTime); return null; }

        // Tipo e cor
        String sigmetType = "SIGMET", color = "#8B008B";
        if (line.contains("EMBD TS") || line.contains("FRQ TS") || line.contains("ISOL TS") || line.contains("OCNL TS")) { sigmetType = "THUNDERSTORM"; color = "#CC00FF"; }
        else if (line.contains("SEV ICE"))  { sigmetType = "ICING";         color = "#7B2FBE"; }
        else if (line.contains("SEV TURB")) { sigmetType = "TURBULENCE";    color = "#9400D3"; }
        else if (line.contains("SEV MTW"))  { sigmetType = "MOUNTAIN_WAVE"; color = "#4B0082"; }
        else if (line.contains("VA CLD"))   { sigmetType = "VOLCANIC_ASH";  color = "#FF4500"; }

        List<double[]> coords = null;
        String method = null;

        // Caso 1: WI
        int wiIdx = line.indexOf("WI ");
        if (wiIdx != -1) {
            int endIdx = -1;
            for (String marker : new String[]{" TOP ", "SFC/FL", "SFC/", "BLW FL", " FL"}) {
                int idx = line.indexOf(marker, wiIdx);
                if (idx != -1 && (endIdx == -1 || idx < endIdx)) endIdx = idx;
            }
            if (endIdx != -1) {
                String section = line.substring(wiIdx + 3, endIdx).replaceAll("\\s*-\\s*", " - ");
                coords = parseCoordList(section);
                method = "WI";
            }
        }

        // Caso 2: X OF LINE
        if (coords == null) {
            Matcher m = Pattern.compile("(N|S|E|W|NE|NW|SE|SW) OF LINE\\s+(.*?)\\s+(?:TOP |SFC/FL|BLW FL|\\bFL\\d)").matcher(line);
            if (m.find()) {
                String direction = m.group(1);
                List<double[]> linePts = parseCoordList(m.group(2));
                if (linePts.size() >= 2) {
                    coords = splitFirBySide(firGeom, linePts, direction);
                    // Clipar pelo bounding box da linha + margem para evitar polígonos gigantes
                    // quando a linha não atravessa toda a FIR (ex: SCIZ que vai até lat -85)
                    if (coords != null) {
                        Polygon fir = firPolygon(firGeom);
                        if (fir != null) {
                            double lineMinX = linePts.stream().mapToDouble(p->p[0]).min().orElse(-180);
                            double lineMaxX = linePts.stream().mapToDouble(p->p[0]).max().orElse(180);
                            double lineMinY = linePts.stream().mapToDouble(p->p[1]).min().orElse(-90);
                            double lineMaxY = linePts.stream().mapToDouble(p->p[1]).max().orElse(90);
                            double margin = Math.max(10, Math.max(lineMaxX - lineMinX, lineMaxY - lineMinY) * 2);
                            Envelope env = fir.getEnvelopeInternal();
                            double clipMinX = Math.max(env.getMinX(), lineMinX - margin);
                            double clipMaxX = Math.min(env.getMaxX(), lineMaxX + margin);
                            double clipMinY = Math.max(env.getMinY(), lineMinY - margin);
                            double clipMaxY = Math.min(env.getMaxY(), lineMaxY + margin);
                            try {
                                Geometry clip = GF.createPolygon(new Coordinate[]{
                                    new Coordinate(clipMinX, clipMinY), new Coordinate(clipMaxX, clipMinY),
                                    new Coordinate(clipMaxX, clipMaxY), new Coordinate(clipMinX, clipMaxY),
                                    new Coordinate(clipMinX, clipMinY)
                                });
                                Geometry clipped = GF.createPolygon(coords.stream()
                                    .map(p -> new Coordinate(p[0], p[1])).toArray(Coordinate[]::new))
                                    .intersection(clip).buffer(0);
                                if (!clipped.isEmpty()) {
                                    coords = new ArrayList<>();
                                    for (Coordinate c : clipped.getCoordinates()) coords.add(new double[]{c.x, c.y});
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    method = direction + " OF LINE";
                }
            }
        }

        // Caso 3: N/S OF <lat> ou E/W OF <lon>
        if (coords == null) {
            Matcher m = Pattern.compile("(N|S) OF ([NS]\\d{4})\\b").matcher(line);
            boolean found = m.find();
            if (!found) {
                m = Pattern.compile("(E|W) OF ([WE]\\d{5})\\b").matcher(line);
                found = m.find();
            }
            if (found) {
                String direction = m.group(1);
                String coordStr = m.group(2);
                List<List<Double>> firRing = getFirRing(firGeom);
                if (firRing != null) {
                    List<double[]> ring = new ArrayList<>();
                    for (List<Double> pt : firRing) ring.add(new double[]{((Number)pt.get(0)).doubleValue(), ((Number)pt.get(1)).doubleValue()});
                    double minX = ring.stream().mapToDouble(p->p[0]).min().orElse(-90);
                    double maxX = ring.stream().mapToDouble(p->p[0]).max().orElse(-30);
                    double minY = ring.stream().mapToDouble(p->p[1]).min().orElse(-60);
                    double maxY = ring.stream().mapToDouble(p->p[1]).max().orElse(20);
                    List<double[]> cutLine;
                    if (direction.equals("N") || direction.equals("S")) {
                        double val = Double.parseDouble(coordStr.substring(1,3)) + Double.parseDouble(coordStr.substring(3,5))/60.0;
                        if (coordStr.charAt(0)=='S') val=-val;
                        cutLine = Arrays.asList(new double[]{minX-1, val}, new double[]{maxX+1, val});
                    } else {
                        double val = Double.parseDouble(coordStr.substring(1,4)) + Double.parseDouble(coordStr.substring(4,6))/60.0;
                        if (coordStr.charAt(0)=='W') val=-val;
                        cutLine = Arrays.asList(new double[]{val, minY-1}, new double[]{val, maxY+1});
                    }
                    coords = splitFirBySide(firGeom, cutLine, direction);
                    method = direction + " OF " + coordStr;
                }
            }
        }

        // Caso 4: SFC/FL coords MOV/STNR
        if (coords == null) {
            Matcher m = Pattern.compile("SFC/FL\\d+\\s+(.*?)\\s+(?:MOV|STNR)").matcher(line);
            if (m.find()) {
                String section = m.group(1).replaceAll("\\s*-\\s*", " - ");
                coords = parseCoordList(section);
                method = "SFC/FL";
            }
        }

        // Caso 5: ENTIRE FIR
        if (coords == null && line.contains("ENTIRE FIR")) {
            List<List<Double>> firRing = getFirRing(firGeom);
            if (firRing != null) {
                coords = new ArrayList<>();
                for (List<Double> pt : firRing) coords.add(new double[]{((Number)pt.get(0)).doubleValue(), ((Number)pt.get(1)).doubleValue()});
                method = "ENTIRE FIR";
            }
        }

        if (coords == null || coords.size() < 3) {
            log.warn("SIGMET sem geometria válida [{}] num={}: {}", firQuery, number, line.substring(0, Math.min(120, line.length())));
            return null;
        }

        // Fechar polígono
        double[] first = coords.get(0), last = coords.get(coords.size()-1);
        if (first[0] != last[0] || first[1] != last[1]) coords.add(first);

        // Garantir que o polígono não invade outras FIRs
        coords = clipToOwnFir(coords, firGeom);

        // Montar GeoJSON Feature
        List<List<Double>> coordList = new ArrayList<>();
        for (double[] pt : coords) coordList.add(Arrays.asList(pt[0], pt[1]));

        Map<String, Object> geometry = new HashMap<>();
        geometry.put("type", "Polygon");
        geometry.put("coordinates", Collections.singletonList(coordList));

        Map<String, Object> properties = new HashMap<>();
        properties.put("fir", firQuery);
        properties.put("sigmetNumber", number);
        properties.put("sigmetType", sigmetType);
        properties.put("color", color);
        properties.put("validPeriod", startTime + "/" + endTime);
        properties.put("method", method);
        properties.put("text", raw.trim());
        properties.put("source", "neighbor");

        Map<String, Object> feature = new HashMap<>();
        feature.put("type", "Feature");
        feature.put("geometry", geometry);
        feature.put("properties", properties);

        log.info("✅ SIGMET vizinho: {} {} {} [{}] método={}", firQuery, number, sigmetType, startTime+"/"+endTime, method);
        return feature;
    }

    /** Clipa o polígono à FIR de origem (já sem Brasil). */
    private static List<double[]> clipToOwnFir(List<double[]> coords, String firQuery) {
        try {
            Polygon ownFir = firPolygon(firQuery);
            if (ownFir == null) return coords;
            List<double[]> closed = new ArrayList<>(coords);
            if (closed.size() >= 3) {
                double[] f = closed.get(0), l = closed.get(closed.size()-1);
                if (f[0] != l[0] || f[1] != l[1]) closed.add(f);
            }
            Coordinate[] cs = closed.stream().map(p -> new Coordinate(p[0], p[1])).toArray(Coordinate[]::new);
            if (cs.length < 4) return coords;
            Geometry input = GF.createPolygon(cs).buffer(0);
            Geometry result = input.intersection(ownFir).buffer(0);
            if (result.isEmpty()) return coords;
            if (result.getCoordinates().length > 200)
                result = org.locationtech.jts.simplify.DouglasPeuckerSimplifier.simplify(result, 0.05);
            List<double[]> out = new ArrayList<>();
            for (Coordinate c : result.getCoordinates()) out.add(new double[]{c.x, c.y});
            return out;
        } catch (Exception e) {
            log.warn("clipToOwnFir falhou para {}: {}", firQuery, e.getMessage());
            return coords;
        }
    }
}
