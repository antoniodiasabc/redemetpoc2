package com.pocsigmet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Service
public class SigwxService {

    private static final Logger logger = LoggerFactory.getLogger(SigwxService.class);
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss'Z'");
    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("dd/MM HH'Z'");
    private static final java.util.regex.Pattern FILE_PATTERN =
        java.util.regex.Pattern.compile("(egrr|kkci)_iwxxm_forecasts_\\d{4}-\\d{2}-\\d{2}T\\d{6}Z\\.xml");

    public static class CycleEntry {
        public final String filename;
        public final String label;
        public final boolean available;
        public final boolean current;
        public CycleEntry(String filename, String label, boolean available, boolean current) {
            this.filename = filename; this.label = label;
            this.available = available; this.current = current;
        }
    }

    @Value("${app.sigwx.path:/mnt/c/Users/aodias/sigwx}")
    private String sigwxPath;

    public void setSigwxPath(String path) { this.sigwxPath = path; }

    /** Retorna o arquivo mais próximo do UTC atual (próximo ciclo de 3h >= agora), egrr ou kkci */
    public File resolveCurrentFile() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        int h = now.getHour();
        int nextH = ((h / 3) + 1) * 3;
        ZonedDateTime candidate = now.withMinute(0).withSecond(0).withNano(0).withHour(0).plusHours(nextH);

        for (int i = 0; i < 8; i++) {
            String ts = FILE_FMT.format(candidate);
            for (String prefix : new String[]{"kkci", "egrr"}) {
                File f = new File(sigwxPath, prefix + "_iwxxm_forecasts_" + ts + ".xml");
                if (f.exists()) { logger.info("SigWx file resolved: {}", f.getName()); return f; }
            }
            candidate = candidate.plusHours(3);
        }
        logger.warn("Nenhum arquivo SigWx encontrado em {}", sigwxPath);
        return null;
    }

    /** Lista arquivos no disco que batem com o padrão. Nunca lança exceção. */
    public List<String> listAvailableFiles() {
        File dir = new File(sigwxPath);
        if (!dir.isDirectory()) return Collections.emptyList();
        String[] files = dir.list((d, n) -> FILE_PATTERN.matcher(n).matches());
        if (files == null) return Collections.emptyList();
        Arrays.sort(files);
        return Arrays.asList(files);
    }

    /** Valida nome e retorna File se existe, null caso contrário. Nunca lança exceção. */
    public File resolveFileByName(String name) {
        if (name == null || !FILE_PATTERN.matcher(name).matches()) return null;
        File f = new File(sigwxPath, name);
        return f.exists() ? f : null;
    }

    /** Monta janela de 6 ciclos (âncora-3h … âncora+12h) com available e current calculados. */
    public List<CycleEntry> buildCycleWindow() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        int anchorH = (now.getHour() / 3) * 3;
        ZonedDateTime anchor = now.withHour(anchorH).withMinute(0).withSecond(0).withNano(0);

        List<String> onDisk = listAvailableFiles();
        File currentFile = resolveCurrentFile();
        String currentName = currentFile != null ? currentFile.getName() : null;

        List<CycleEntry> window = new ArrayList<>(6);
        for (int offset : new int[]{0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36, 39}) {
            ZonedDateTime slot = anchor.plusHours(offset);
            String ts = FILE_FMT.format(slot);
            String label = LABEL_FMT.format(slot);
            // prefere kkci, fallback egrr
            String filename = "egrr_iwxxm_forecasts_" + ts + ".xml";
            for (String prefix : new String[]{"kkci", "egrr"}) {
                String candidate = prefix + "_iwxxm_forecasts_" + ts + ".xml";
                if (onDisk.contains(candidate)) { filename = candidate; break; }
            }
            boolean available = onDisk.contains(filename);
            boolean current = filename.equals(currentName);
            window.add(new CycleEntry(filename, label, available, current));
        }
        return window;
    }

    /** Serializa lista de CycleEntry para JSON. */
    public String cycleWindowToJson(List<CycleEntry> window) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < window.size(); i++) {
            if (i > 0) sb.append(",");
            CycleEntry e = window.get(i);
            sb.append("{\"filename\":\"").append(e.filename).append("\"")
              .append(",\"label\":\"").append(e.label).append("\"")
              .append(",\"available\":").append(e.available)
              .append(",\"current\":").append(e.current).append("}");
        }
        return sb.append("]").toString();
    }

    /** Converte o arquivo XML em GeoJSON FeatureCollection */
    public String parseToGeoJson(File xmlFile) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document doc = dbf.newDocumentBuilder().parse(xmlFile);

        NodeList features = doc.getElementsByTagNameNS("http://icao.int/iwxxm/2023-1", "MeteorologicalFeature");
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        boolean first = true;
        int turbSeq = 1;

        for (int i = 0; i < features.getLength(); i++) {
            Element feat = (Element) features.item(i);

            String tipo = attrHrefLast(feat, "http://icao.int/iwxxm/2023-1", "phenomenon");
            if (tipo == null) continue;

            NodeList flUpEls  = feat.getElementsByTagNameNS("*", "upperElevation");
            NodeList flLowEls = feat.getElementsByTagNameNS("*", "lowerElevation");
            String flUpper = flUpEls.getLength()  > 0 ? flUpEls.item(0).getTextContent().trim()  : null;
            String flLower = flLowEls.getLength() > 0 ? flLowEls.item(0).getTextContent().trim() : null;

            // TROPOPAUSE: FL vem da tag <elevation uom="FL">
            if ("TROPOPAUSE".equals(tipo) && flUpper == null) {
                NodeList elEls = feat.getElementsByTagNameNS("*", "elevation");
                for (int j = 0; j < elEls.getLength(); j++) {
                    Element elEl = (Element) elEls.item(j);
                    if ("FL".equals(elEl.getAttribute("uom"))) {
                        flUpper = elEl.getTextContent().trim();
                        break;
                    }
                }
            }

            boolean hasPolygon = feat.getElementsByTagNameNS("http://www.opengis.net/gml/3.2", "PolygonPatch").getLength() > 0;
            NodeList posLists  = feat.getElementsByTagNameNS("http://www.opengis.net/gml/3.2", "posList");
            NodeList posSingle = feat.getElementsByTagNameNS("http://www.opengis.net/gml/3.2", "pos");

            if ("JETSTREAM".equals(tipo)) {
                // linha principal — extrai coordenadas para calcular bearing local das barbelas
                double[] lineCoords = null;
                if (posLists.getLength() > 0) {
                    String vectorEnd = getTextContent(feat, "vectorAtEnd");
                    double bearing = vectorEnd != null ? vectorToBearing(vectorEnd) : 0;
                    String rawPosList = posLists.item(0).getTextContent().trim();
                    lineCoords = parsePosListToDoubles(rawPosList);
                    String geom = posListToLineString(rawPosList);
                    if (geom != null) {
                        if (!first) sb.append(",");
                        first = false;
                        sb.append("{\"type\":\"Feature\",\"geometry\":").append(geom)
                          .append(",\"properties\":{\"type\":\"JETSTREAM\",\"subtype\":\"line\"")
                          .append(",\"bearing\":").append(String.format("%.1f", bearing))
                          .append("}}");
                    }
                }
                // símbolos de barbela ao longo da linha
                NodeList symbols = feat.getElementsByTagNameNS("http://icao.int/iwxxm/2023-1", "WAFSJetStreamWindSymbol");
                for (int j = 0; j < symbols.getLength(); j++) {
                    Element sym = (Element) symbols.item(j);
                    NodeList symPos = sym.getElementsByTagNameNS("http://www.opengis.net/gml/3.2", "pos");
                    if (symPos.getLength() == 0) continue;
                    String posText = symPos.item(0).getTextContent().trim();
                    String geom = posToPoint(posText);
                    if (geom == null) continue;

                    NodeList elEls = sym.getElementsByTagNameNS("*", "elevation");
                    NodeList wsEls = sym.getElementsByTagNameNS("*", "windSpeed");
                    NodeList flUpS = sym.getElementsByTagNameNS("*", "IsotachUpperElevation");
                    NodeList flLoS = sym.getElementsByTagNameNS("*", "IsotachLowerElevation");
                    String fl      = elEls.getLength() > 0 ? elEls.item(0).getTextContent().trim() : null;
                    String ws      = wsEls.getLength() > 0 ? wsEls.item(0).getTextContent().trim() : null;
                    String isoUp   = flUpS.getLength() > 0 ? flUpS.item(0).getTextContent().trim() : null;
                    String isoLo   = flLoS.getLength() > 0 ? flLoS.item(0).getTextContent().trim() : null;

                    // bearing local: ângulo do segmento da linha mais próximo ao ponto da barbela
                    double barbBearing = 0;
                    if (lineCoords != null) {
                        String[] parts = posText.split("\\s+");
                        if (parts.length >= 2) {
                            double bLat = Double.parseDouble(parts[0]);
                            double bLon = Double.parseDouble(parts[1]);
                            barbBearing = bearingAtNearestSegment(lineCoords, bLat, bLon);
                        }
                    }

                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{\"type\":\"Feature\",\"geometry\":").append(geom)
                      .append(",\"properties\":{\"type\":\"JETSTREAM\",\"subtype\":\"barb\"")
                      .append(",\"windSpeedKt\":").append(ws != null ? ws : "null")
                      .append(",\"fl\":").append(fl != null ? "\"FL" + fl + "\"" : "null")
                      .append(",\"bearing\":").append(String.format("%.1f", barbBearing))
                      .append(",\"flUpper\":").append(isoUp != null ? "\"" + isoUp + "\"" : "null")
                      .append(",\"flLower\":").append(isoLo != null ? "\"" + isoLo + "\"" : "null")
                      .append("}}");
                }
                continue;
            }

            // outros fenômenos
            NodeList wsEls = feat.getElementsByTagNameNS("*", "windSpeed");
            String windSpeedKt = wsEls.getLength() > 0 ? wsEls.item(0).getTextContent().trim() : null;

            // CB: distribuição e tipo
            String cloudDist = attrHrefLast(feat, "http://icao.int/iwxxm/2023-1", "CloudDistribution");
            String cloudType = attrHrefLast(feat, "http://icao.int/iwxxm/2023-1", "CloudType");

            // Vulcão: nome
            String volcanoName = getTextContent(feat, "name");

            // Turbulência/Icing: grau
            String turbDegree = attrHrefLast(feat, "http://icao.int/iwxxm/2023-1", "DegreeOfTurbulence");
            String icingDegree = attrHrefLast(feat, "http://icao.int/iwxxm/2023-1", "DegreeOfIcing");
            String degree = turbDegree != null ? turbDegree : icingDegree;

            String geometry = null;
            String centroid = null;
            if (posLists.getLength() > 0) {
                if (hasPolygon && !"TROPOPAUSE".equals(tipo)) {
                    // extrai exterior + interiores para suporte a polígonos com buracos
                    NodeList extNodes  = feat.getElementsByTagNameNS("http://www.opengis.net/gml/3.2", "exterior");
                    NodeList intrNodes = feat.getElementsByTagNameNS("http://www.opengis.net/gml/3.2", "interior");
                    if (intrNodes.getLength() > 0) {
                        logger.debug("Feature com interior: tipo={} extNodes={} intrNodes={}", tipo, extNodes.getLength(), intrNodes.getLength());
                    }
                    String extPos = null;
                    if (extNodes.getLength() > 0) {
                        NodeList extPosList = ((Element) extNodes.item(0)).getElementsByTagNameNS("http://www.opengis.net/gml/3.2", "posList");
                        if (extPosList.getLength() > 0) extPos = extPosList.item(0).getTextContent().trim();
                    }
                    if (extPos == null) extPos = posLists.item(0).getTextContent().trim();
                    List<String> interiorPos = new java.util.ArrayList<>();
                    for (int k = 0; k < intrNodes.getLength(); k++) {
                        NodeList intPosList = ((Element) intrNodes.item(k)).getElementsByTagNameNS("http://www.opengis.net/gml/3.2", "posList");
                        if (intPosList.getLength() > 0) interiorPos.add(intPosList.item(0).getTextContent().trim());
                    }
                    geometry = posListToPolygonWithHoles(extPos, interiorPos);
                    if ("TURBULENCE".equals(tipo) || "AIRFRAME_ICING".equals(tipo))
                        centroid = calcCentroid(extPos);
                } else {
                    geometry = posListToLineString(posLists.item(0).getTextContent().trim());
                }
            } else if (posSingle.getLength() > 0) {
                geometry = posToPoint(posSingle.item(0).getTextContent().trim());
            }
            if (geometry == null) continue;

            if (!first) sb.append(",");
            first = false;
            sb.append("{\"type\":\"Feature\",\"geometry\":").append(geometry)
              .append(",\"properties\":{")
              .append("\"type\":\"").append(tipo).append("\"")
              .append(",\"flUpper\":").append(flUpper != null ? "\"" + flUpper + "\"" : "null")
              .append(",\"flLower\":").append(flLower != null ? "\"" + flLower + "\"" : "null")
              .append(",\"windSpeedKt\":").append(windSpeedKt != null ? "\"" + windSpeedKt + "\"" : "null")
              .append(",\"cloudDist\":").append(cloudDist != null ? "\"" + cloudDist + "\"" : "null")
              .append(",\"cloudType\":").append(cloudType != null ? "\"" + cloudType + "\"" : "null")
              .append(",\"name\":").append(volcanoName != null ? "\"" + volcanoName + "\"" : "null")
              .append(",\"degree\":").append(degree != null ? "\"" + degree + "\"" : "null")
              .append(",\"centroid\":").append(centroid != null ? centroid : "null")
              .append(",\"seqNum\":").append("TURBULENCE".equals(tipo) ? turbSeq++ : "null")
              .append("}}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String attrHrefLast(Element parent, String ns, String localName) {
        NodeList nl = parent.getElementsByTagNameNS(ns, localName);
        if (nl.getLength() == 0) return null;
        String href = ((Element) nl.item(0)).getAttributeNS("http://www.w3.org/1999/xlink", "href");
        if (href == null || href.isEmpty()) return null;
        return href.substring(href.lastIndexOf('/') + 1);
    }

    private String getTextContent(Element parent, String localName) {
        NodeList nl = parent.getElementsByTagNameNS("*", localName);
        if (nl.getLength() == 0) return null;
        String t = nl.item(0).getTextContent().trim();
        return t.isEmpty() ? null : t;
    }

    /** vectorAtEnd é "dy dx" → bearing em graus */
    private double vectorToBearing(String vector) {
        String[] parts = vector.trim().split("\\s+");
        if (parts.length < 2) return 0;
        double dy = Double.parseDouble(parts[0]);
        double dx = Double.parseDouble(parts[1]);
        return (Math.toDegrees(Math.atan2(dx, dy)) + 360) % 360;
    }

    private String posListToPolygon(String posList) {
        String[] nums = posList.split("\\s+");
        if (nums.length < 4) return null;

        // extrai pares [lon, lat]
        double[] lons = new double[nums.length / 2];
        double[] lats = new double[nums.length / 2];
        for (int i = 0; i < nums.length - 1; i += 2) {
            lats[i / 2] = Double.parseDouble(nums[i]);
            lons[i / 2] = Double.parseDouble(nums[i + 1]);
        }

        // detecta cruzamento do antimeridiano: normaliza lons para ficarem contíguos
        // a partir do primeiro ponto, ajusta cada lon seguinte para ficar dentro de ±180 do anterior
        for (int i = 1; i < lons.length; i++) {
            double diff = lons[i] - lons[i - 1];
            if (diff > 180)  lons[i] -= 360;
            if (diff < -180) lons[i] += 360;
        }

        StringBuilder coords = new StringBuilder("[");
        for (int i = 0; i < lons.length; i++) {
            if (i > 0) coords.append(",");
            coords.append("[").append(lons[i]).append(",").append(lats[i]).append("]");
        }
        coords.append("]");
        return "{\"type\":\"Polygon\",\"coordinates\":[" + coords + "]}";
    }

    private String posListToPolygonWithHoles(String exteriorPosList, List<String> interiorPosLists) {
        String extRing = posListToRing(exteriorPosList);
        if (extRing == null) return null;
        StringBuilder sb = new StringBuilder("{\"type\":\"Polygon\",\"coordinates\":[").append(extRing);
        for (String interior : interiorPosLists) {
            String ring = posListToRing(interior);
            if (ring != null) sb.append(",").append(ring);
        }
        return sb.append("]}").toString();
    }

    private String posListToRing(String posList) {
        String[] nums = posList.split("\\s+");
        if (nums.length < 4) return null;
        double[] lons = new double[nums.length / 2];
        double[] lats = new double[nums.length / 2];
        for (int i = 0; i < nums.length - 1; i += 2) {
            lats[i / 2] = Double.parseDouble(nums[i]);
            lons[i / 2] = Double.parseDouble(nums[i + 1]);
        }
        for (int i = 1; i < lons.length; i++) {
            double diff = lons[i] - lons[i - 1];
            if (diff > 180)  lons[i] -= 360;
            if (diff < -180) lons[i] += 360;
        }
        StringBuilder coords = new StringBuilder("[");
        for (int i = 0; i < lons.length; i++) {
            if (i > 0) coords.append(",");
            coords.append("[").append(lons[i]).append(",").append(lats[i]).append("]");
        }
        return coords.append("]").toString();
    }

    private String posListToLineString(String posList) {
        String[] nums = posList.split("\\s+");
        if (nums.length < 4) return null;

        double[] lons = new double[nums.length / 2];
        double[] lats = new double[nums.length / 2];
        for (int i = 0; i < nums.length - 1; i += 2) {
            lats[i / 2] = Double.parseDouble(nums[i]);
            lons[i / 2] = Double.parseDouble(nums[i + 1]);
        }

        // normaliza antimeridiano
        for (int i = 1; i < lons.length; i++) {
            double diff = lons[i] - lons[i - 1];
            if (diff > 180)  lons[i] -= 360;
            if (diff < -180) lons[i] += 360;
        }

        // interpola Catmull-Rom entre os nós de controle (aprox. CubicSpline)
        // gera pontos suaves entre cada par de nós
        List<double[]> interpolated = interpolateCatmullRom(lons, lats, 10);

        StringBuilder coords = new StringBuilder("[");
        for (int i = 0; i < interpolated.size(); i++) {
            if (i > 0) coords.append(",");
            coords.append("[").append(interpolated.get(i)[0]).append(",").append(interpolated.get(i)[1]).append("]");
        }
        coords.append("]");
        return "{\"type\":\"LineString\",\"coordinates\":" + coords + "}";
    }

    /** Catmull-Rom spline — boa aproximação visual de CubicSpline com os mesmos nós */
    private List<double[]> interpolateCatmullRom(double[] lons, double[] lats, int steps) {
        List<double[]> result = new java.util.ArrayList<>();
        int n = lons.length;
        for (int i = 0; i < n - 1; i++) {
            double p0lon = lons[Math.max(i - 1, 0)],     p0lat = lats[Math.max(i - 1, 0)];
            double p1lon = lons[i],                        p1lat = lats[i];
            double p2lon = lons[i + 1],                    p2lat = lats[i + 1];
            double p3lon = lons[Math.min(i + 2, n - 1)],  p3lat = lats[Math.min(i + 2, n - 1)];
            for (int s = 0; s < steps; s++) {
                double t = (double) s / steps;
                double t2 = t * t, t3 = t2 * t;
                double lon = 0.5 * ((2*p1lon) + (-p0lon+p2lon)*t + (2*p0lon-5*p1lon+4*p2lon-p3lon)*t2 + (-p0lon+3*p1lon-3*p2lon+p3lon)*t3);
                double lat = 0.5 * ((2*p1lat) + (-p0lat+p2lat)*t + (2*p0lat-5*p1lat+4*p2lat-p3lat)*t2 + (-p0lat+3*p1lat-3*p2lat+p3lat)*t3);
                result.add(new double[]{lon, lat});
            }
        }
        result.add(new double[]{lons[n-1], lats[n-1]});
        return result;
    }

    private String posToPoint(String pos) {
        String[] nums = pos.trim().split("\\s+");
        if (nums.length < 2) return null;
        return "{\"type\":\"Point\",\"coordinates\":[" + nums[1] + "," + nums[0] + "]}";
    }

    /** Calcula centroide simples (média lat/lon) de um posList */
    private String calcCentroid(String posList) {
        double[] d = parsePosListToDoubles(posList);
        if (d.length < 2) return null;
        double sumLat = 0, sumLon = 0;
        int n = d.length / 2;
        for (int i = 0; i < d.length; i += 2) { sumLat += d[i]; sumLon += d[i+1]; }
        return "{\"type\":\"Point\",\"coordinates\":[" + String.format("%.4f", sumLon/n) + "," + String.format("%.4f", sumLat/n) + "]}";
    }

    /** Converte posList em array de doubles [lat0,lon0, lat1,lon1, ...] */
    private double[] parsePosListToDoubles(String posList) {
        String[] parts = posList.trim().split("\\s+");
        double[] d = new double[parts.length];
        for (int i = 0; i < parts.length; i++) d[i] = Double.parseDouble(parts[i]);
        return d;
    }

    /** Bearing do segmento da linha mais próximo ao ponto (lat,lon) */
    private double bearingAtNearestSegment(double[] coords, double lat, double lon) {
        double minDist = Double.MAX_VALUE;
        double bestBearing = 0;
        for (int i = 0; i + 3 < coords.length; i += 2) {
            double lat1 = coords[i], lon1 = coords[i+1];
            double lat2 = coords[i+2], lon2 = coords[i+3];
            double midLat = (lat1 + lat2) / 2, midLon = (lon1 + lon2) / 2;
            double d = Math.sqrt(Math.pow(lat - midLat, 2) + Math.pow(lon - midLon, 2));
            if (d < minDist) {
                minDist = d;
                bestBearing = Math.toDegrees(Math.atan2(lon2 - lon1, lat2 - lat1));
            }
        }
        return bestBearing;
    }
}
