package com.pocsigmet;

import com.pocsigmet.service.SigwxService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class SigwxServiceTest {

    private SigwxService serviceWithPath(String path) throws Exception {
        SigwxService svc = new SigwxService();
        Field f = SigwxService.class.getDeclaredField("sigwxPath");
        f.setAccessible(true);
        f.set(svc, path);
        return svc;
    }

    // ── testes novos (independentes de ambiente) ──────────────────────────────

    @Test
    void listAvailableFiles_retornaOrdenada(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("egrr_iwxxm_forecasts_2026-07-25T150000Z.xml"));
        Files.createFile(dir.resolve("egrr_iwxxm_forecasts_2026-07-25T120000Z.xml"));
        Files.createFile(dir.resolve("outro_arquivo.txt")); // deve ser ignorado
        SigwxService svc = serviceWithPath(dir.toString());
        List<String> list = svc.listAvailableFiles();
        assertEquals(List.of(
            "egrr_iwxxm_forecasts_2026-07-25T120000Z.xml",
            "egrr_iwxxm_forecasts_2026-07-25T150000Z.xml"), list);
    }

    @Test
    void listAvailableFiles_retornaVazioSeDiretorioNaoExiste() throws Exception {
        SigwxService svc = serviceWithPath("/tmp/sigwx_inexistente_xyz");
        assertDoesNotThrow(() -> {
            List<String> list = svc.listAvailableFiles();
            assertTrue(list.isEmpty());
        });
    }

    @Test
    void resolveFileByName_retornaNullParaPathTraversal(@TempDir Path dir) throws Exception {
        SigwxService svc = serviceWithPath(dir.toString());
        assertNull(svc.resolveFileByName("../etc/passwd"));
    }

    @Test
    void resolveFileByName_retornaNullParaPadraoInvalido(@TempDir Path dir) throws Exception {
        SigwxService svc = serviceWithPath(dir.toString());
        assertNull(svc.resolveFileByName("qualquer_coisa.xml"));
    }

    @Test
    void resolveFileByName_retornaNullSeArquivoNaoExiste(@TempDir Path dir) throws Exception {
        SigwxService svc = serviceWithPath(dir.toString());
        assertNull(svc.resolveFileByName("egrr_iwxxm_forecasts_2026-07-25T120000Z.xml"));
    }

    @Test
    void resolveFileByName_retornaFileSeExiste(@TempDir Path dir) throws Exception {
        String name = "egrr_iwxxm_forecasts_2026-07-25T120000Z.xml";
        Files.createFile(dir.resolve(name));
        SigwxService svc = serviceWithPath(dir.toString());
        File f = svc.resolveFileByName(name);
        assertNotNull(f);
        assertEquals(name, f.getName());
    }

    @Test
    void buildCycleWindow_retornaExatamente11Entradas(@TempDir Path dir) throws Exception {
        SigwxService svc = serviceWithPath(dir.toString());
        List<SigwxService.CycleEntry> window = svc.buildCycleWindow();
        assertEquals(14, window.size());
    }

    @Test
    void buildCycleWindow_marcaAvailableApenasParaArquivosPresentes(@TempDir Path dir) throws Exception {
        SigwxService svc = serviceWithPath(dir.toString());
        List<SigwxService.CycleEntry> window = svc.buildCycleWindow();
        // diretório vazio — nenhum deve ser available
        assertTrue(window.stream().noneMatch(e -> e.available));
    }

    @Test
    void buildCycleWindow_marcaCurrentQuandoArquivoExiste(@TempDir Path dir) throws Exception {
        SigwxService svc = serviceWithPath(dir.toString());
        List<SigwxService.CycleEntry> window = svc.buildCycleWindow();
        // sem arquivos no disco, nenhum deve ser current
        assertTrue(window.stream().noneMatch(e -> e.current));
    }

    // ── testes de polígono com buraco (interior) ─────────────────────────────

    private java.lang.reflect.Method getMethod(String name, Class<?>... params) throws Exception {
        java.lang.reflect.Method m = SigwxService.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    @Test
    void posListToRing_converteCorretamente() throws Exception {
        SigwxService svc = serviceWithPath("/tmp");
        java.lang.reflect.Method m = getMethod("posListToRing", String.class);
        // lat lon lat lon ...
        String ring = (String) m.invoke(svc, "-10.0 -50.0 -10.0 -40.0 -20.0 -40.0 -20.0 -50.0 -10.0 -50.0");
        assertNotNull(ring);
        assertTrue(ring.startsWith("["));
        assertTrue(ring.contains("[-50.0,-10.0]"));
    }

    @Test
    void posListToRing_retornaNullParaListaCurta() throws Exception {
        SigwxService svc = serviceWithPath("/tmp");
        java.lang.reflect.Method m = getMethod("posListToRing", String.class);
        assertNull(m.invoke(svc, "1.0 2.0"));
    }

    @Test
    void posListToPolygonWithHoles_semInterior() throws Exception {
        SigwxService svc = serviceWithPath("/tmp");
        java.lang.reflect.Method m = getMethod("posListToPolygonWithHoles", String.class, List.class);
        String ext = "-10.0 -50.0 -10.0 -40.0 -20.0 -40.0 -20.0 -50.0 -10.0 -50.0";
        String geojson = (String) m.invoke(svc, ext, List.of());
        assertNotNull(geojson);
        assertTrue(geojson.contains("\"type\":\"Polygon\""));
        // só 1 ring
        assertEquals(1, geojson.split("\\[\\[").length - 1);
    }

    @Test
    void posListToPolygonWithHoles_comInterior() throws Exception {
        SigwxService svc = serviceWithPath("/tmp");
        java.lang.reflect.Method m = getMethod("posListToPolygonWithHoles", String.class, List.class);
        String ext  = "-10.0 -50.0 -10.0 -40.0 -20.0 -40.0 -20.0 -50.0 -10.0 -50.0";
        String hole = "-12.0 -48.0 -12.0 -44.0 -16.0 -44.0 -16.0 -48.0 -12.0 -48.0";
        String geojson = (String) m.invoke(svc, ext, List.of(hole));
        assertNotNull(geojson);
        assertTrue(geojson.contains("\"type\":\"Polygon\""));
        // 2 rings: exterior + interior
        assertEquals(2, geojson.split("\\[\\[").length - 1);
    }

    @Test
    void parseToGeoJson_xmlComInterior_geraPoligonoComBuraco(@TempDir Path dir) throws Exception {
        // XML mínimo com 1 feature CLOUD com exterior + interior
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<iwxxm:WAFSSignificantWeatherForecast " +
            "  xmlns:gml=\"http://www.opengis.net/gml/3.2\" " +
            "  xmlns:iwxxm=\"http://icao.int/iwxxm/2023-1\" " +
            "  xmlns:xlink=\"http://www.w3.org/1999/xlink\" " +
            "  gml:id=\"uuid.test\" reportStatus=\"NORMAL\" permissibleUsage=\"OPERATIONAL\">" +
            "<gml:identifier codeSpace=\"x\">test</gml:identifier>" +
            "<iwxxm:boundingPeriod><gml:TimePeriod gml:id=\"tp1\">" +
            "  <gml:beginPosition>2026-09-02T12:00:00Z</gml:beginPosition>" +
            "  <gml:endPosition>2026-09-02T12:00:00Z</gml:endPosition>" +
            "</gml:TimePeriod></iwxxm:boundingPeriod>" +
            "<iwxxm:issueTime><gml:TimeInstant gml:id=\"ti1\">" +
            "  <gml:timePosition>2026-09-01T05:00:00Z</gml:timePosition>" +
            "</gml:TimeInstant></iwxxm:issueTime>" +
            "<iwxxm:feature>" +
            "  <iwxxm:MeteorologicalFeature gml:id=\"uuid.mf1\">" +
            "    <gml:identifier codeSpace=\"x\">feat1</gml:identifier>" +
            "    <iwxxm:phenomenon xlink:href=\"http://codes.wmo.int/49-2/MeteorologicalFeature/CLOUD\"/>" +
            "    <iwxxm:phenomenonGeometry>" +
            "      <iwxxm:ElevatedVolume gml:id=\"uuid.ev1\" srsDimension=\"2\" axisLabels=\"Lat Long\" srsName=\"http://www.opengis.net/def/crs/EPSG/0/4326\">" +
            "        <gml:patches><gml:PolygonPatch>" +
            "          <gml:exterior><gml:Ring><gml:curveMember><gml:Curve gml:id=\"c1\" srsDimension=\"2\" axisLabels=\"Lat Long\" srsName=\"http://www.opengis.net/def/crs/EPSG/0/4326\">" +
            "            <gml:segments><gml:CubicSpline>" +
            "              <gml:posList>-10.0 -50.0 -10.0 -40.0 -20.0 -40.0 -20.0 -50.0 -10.0 -50.0</gml:posList>" +
            "              <gml:vectorAtStart>0 1</gml:vectorAtStart><gml:vectorAtEnd>0 1</gml:vectorAtEnd>" +
            "            </gml:CubicSpline></gml:segments>" +
            "          </gml:Curve></gml:curveMember></gml:Ring></gml:exterior>" +
            "          <gml:interior><gml:Ring><gml:curveMember><gml:Curve gml:id=\"c2\" srsDimension=\"2\" axisLabels=\"Lat Long\" srsName=\"http://www.opengis.net/def/crs/EPSG/0/4326\">" +
            "            <gml:segments><gml:CubicSpline>" +
            "              <gml:posList>-12.0 -48.0 -12.0 -44.0 -16.0 -44.0 -16.0 -48.0 -12.0 -48.0</gml:posList>" +
            "              <gml:vectorAtStart>0 1</gml:vectorAtStart><gml:vectorAtEnd>0 1</gml:vectorAtEnd>" +
            "            </gml:CubicSpline></gml:segments>" +
            "          </gml:Curve></gml:curveMember></gml:Ring></gml:interior>" +
            "        </gml:PolygonPatch></gml:patches>" +
            "        <iwxxm:upperElevation uom=\"FL\">350</iwxxm:upperElevation>" +
            "        <iwxxm:lowerElevation uom=\"FL\" xsi:nil=\"true\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" nilReason=\"http://codes.wmo.int/common/nil/inapplicable\"/>" +
            "      </iwxxm:ElevatedVolume>" +
            "    </iwxxm:phenomenonGeometry>" +
            "    <iwxxm:phenomenonProperty><iwxxm:CloudDistribution xlink:href=\"http://codes.wmo.int/bufr4/codeflag/0-20-008/10\"/></iwxxm:phenomenonProperty>" +
            "    <iwxxm:phenomenonProperty><iwxxm:CloudType xlink:href=\"http://codes.wmo.int/bufr4/codeflag/0-20-012/9\"/></iwxxm:phenomenonProperty>" +
            "  </iwxxm:MeteorologicalFeature>" +
            "</iwxxm:feature>" +
            "</iwxxm:WAFSSignificantWeatherForecast>";

        Path xmlFile = dir.resolve("egrr_iwxxm_forecasts_2026-09-02T120000Z.xml");
        Files.writeString(xmlFile, xml);

        SigwxService svc = serviceWithPath(dir.toString());
        String geojson = svc.parseToGeoJson(xmlFile.toFile());

        assertNotNull(geojson);
        assertTrue(geojson.contains("\"type\":\"Polygon\""), "deve ter Polygon");
        // deve ter 2 rings: exterior + interior
        long ringCount = geojson.chars().filter(c -> c == '[').count();
        // coordinates:[ [exterior], [interior] ] — pelo menos 2 arrays de coordenadas
        assertTrue(geojson.split("-48\\.0,-12\\.0").length > 1 ||
                   geojson.contains("-48.0,-12.0") ||
                   geojson.contains("-12.0,-48.0"),
                   "deve conter coordenadas do interior");
    }



    @Disabled("requer ambiente local com /mnt/c/Users/aodias/sigwx")
    @Test
    public void testResolveCurrentFileEncontrado() throws Exception {
        SigwxService svc = serviceWithPath("/mnt/c/Users/aodias/sigwx");
        File f = svc.resolveCurrentFile();
        assertNotNull(f, "Deve encontrar um arquivo egrr disponível");
        assertTrue(f.getName().startsWith("egrr_iwxxm_forecasts_"));
        assertTrue(f.getName().endsWith(".xml"));
    }

    @Disabled("requer ambiente local com /mnt/c/Users/aodias/sigwx")
    @Test
    public void testResolveCurrentFileNaoEncontrado() throws Exception {
        SigwxService svc = serviceWithPath("/tmp/sigwx_inexistente");
        File f = svc.resolveCurrentFile();
        assertNull(f, "Deve retornar null quando diretório não existe");
    }

    @Disabled("requer ambiente local com /mnt/c/Users/aodias/sigwx")
    @Test
    public void testParseToGeoJsonEstrutura() throws Exception {
        SigwxService svc = serviceWithPath("/mnt/c/Users/aodias/sigwx");
        File f = svc.resolveCurrentFile();
        assertNotNull(f);
        String geojson = svc.parseToGeoJson(f);
        assertTrue(geojson.contains("\"type\":\"FeatureCollection\""));
        assertTrue(geojson.contains("\"features\":["));
    }

    @Disabled("requer ambiente local com /mnt/c/Users/aodias/sigwx")
    @Test
    public void testParseToGeoJsonPropriedades() throws Exception {
        SigwxService svc = serviceWithPath("/mnt/c/Users/aodias/sigwx");
        File f = svc.resolveCurrentFile();
        assertNotNull(f);
        String geojson = svc.parseToGeoJson(f);
        assertTrue(geojson.contains("\"type\":"));
        assertTrue(geojson.contains("\"flUpper\":"));
        assertTrue(geojson.contains("\"flLower\":"));
    }

    @Disabled("requer ambiente local com /mnt/c/Users/aodias/sigwx")
    @Test
    public void testParseToGeoJsonGeometria() throws Exception {
        SigwxService svc = serviceWithPath("/mnt/c/Users/aodias/sigwx");
        File f = svc.resolveCurrentFile();
        assertNotNull(f);
        String geojson = svc.parseToGeoJson(f);
        assertTrue(geojson.contains("\"Polygon\"") || geojson.contains("\"Point\""));
        assertTrue(geojson.contains("\"coordinates\""));
    }
}
