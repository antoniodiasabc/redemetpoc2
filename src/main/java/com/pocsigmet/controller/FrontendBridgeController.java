package com.pocsigmet.controller;

import com.pocsigmet.OpmetWebSocketHandler;
import com.pocsigmet.RedisMetarCacheService;
import com.pocsigmet.RedemetSigmetClient;
import com.pocsigmet.service.MetarService;
import com.pocsigmet.service.RedemetService;
import com.pocsigmet.service.SigwxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@CrossOrigin(origins = "*")
public class FrontendBridgeController {

    @Autowired private RedemetService redemetService;
    @Autowired private MetarService metarService;
    @Autowired private SigwxService sigwxService;
    @Autowired private RedemetSigmetClient redemetSigmetClient;
    @Autowired private com.pocsigmet.NeighborSigmetClient neighborSigmetClient;

    // ── SIGMETs ──────────────────────────────────────────────────────────────

    @GetMapping("/redemet_sigmets_json")
    public ResponseEntity<String> sigmetsJson() {
        return json(redemetService.getSigmetsJson());
    }

    @GetMapping("/redemet_sigmets_all")
    public ResponseEntity<String> sigmetsAll() {
        try { return json(redemetSigmetClient.getSigmetsAllJson()); }
        catch (Exception e) { return json("[]"); }
    }

    @GetMapping("/redemet_sigmets_vizinhos")
    public ResponseEntity<String> sigmetsVizinhos() {
        try { return json(neighborSigmetClient.getNeighborSigmetsJson()); }
        catch (Exception e) { return json("[]"); }
    }

    @GetMapping("/redemet_airmets_json")
    public ResponseEntity<String> airmetsJson() {
        return json(redemetService.getAirmetsJson());
    }

    @PostMapping("/sigmet")
    public ResponseEntity<String> postSigmet(@RequestBody(required = false) String body) {
        return json("{\"status\":\"success\",\"message\":\"SIGMET recebido\"}");
    }

    @PostMapping("/create_sigmet")
    public ResponseEntity<String> createSigmet(@RequestBody(required = false) String body) {
        String id = "SIGMET-" + System.currentTimeMillis();
        return json("{\"id\":\"" + id + "\",\"status\":\"created\"}");
    }

    // ── METARs ───────────────────────────────────────────────────────────────

    @GetMapping("/metar_top200_sb")
    public ResponseEntity<String> metarTop200() {
        try { return json(metarService.getMetarTopSB(200)); }
        catch (Exception e) { return json("[]"); }
    }

    @GetMapping("/avisos_aerodromos_count")
    public ResponseEntity<String> avisosCount() {
        try (redis.clients.jedis.Jedis jedis = RedisMetarCacheService.getJedis()) {
            String raw = jedis != null ? jedis.get("cache:avisos_aerodromo_raw") : null;
            int count = 0;
            if (raw != null && !raw.isBlank()) {
                java.util.Set<String> unique = new java.util.HashSet<>();
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("(AD WRNG|WS WRNG)\\s+(\\d+)");
                for (String part : raw.split("=")) {
                    java.util.regex.Matcher m = p.matcher(part);
                    if (m.find()) unique.add(m.group(1) + "_" + m.group(2));
                }
                count = unique.size();
            }
            return json("{\"count\":" + count + "}");
        } catch (Exception e) { return json("{\"count\":0}"); }
    }

    @GetMapping("/api/v1/alerts/metar")
    public ResponseEntity<String> alertsMetar() {
        try {
            // delega para o MetarService que já tem a lógica Redis
            return json(metarService.getMetarAlerts());
        } catch (Exception e) { return json("[]"); }
    }

    // ── Frames / imagens ─────────────────────────────────────────────────────

    @GetMapping("/canal16frames")
    public ResponseEntity<String> canal16frames() {
        File[] files = new File("data").listFiles((d, n) -> n.startsWith("canal16_202") && n.endsWith(".jpg"));
        if (files == null || files.length == 0) return json("[]");
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        int start = Math.max(0, files.length - 8);
        StringBuilder sb = new StringBuilder("[");
        for (int i = start; i < files.length; i++) {
            if (i > start) sb.append(",");
            sb.append("\"").append(files[i].getName()).append("\"");
        }
        return json(sb.append("]").toString());
    }

    @GetMapping("/frames")
    public ResponseEntity<String> frames() {
        File[] files = new File("data").listFiles((d, n) -> n.startsWith("canal16_202") && n.endsWith(".jpg"));
        if (files == null) files = new File[0];
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        StringBuilder sb = new StringBuilder("{\"frames\":[");
        for (int i = 0; i < files.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(files[i].getName()).append("\"");
        }
        return json(sb.append("]}").toString());
    }

    // ── Animação realçada ────────────────────────────────────────────────────

    @GetMapping("/api/v1/animation/realcada-images")
    public ResponseEntity<String> realcadaImages(@RequestParam(defaultValue = "12") int count) {
        count = Math.min(50, Math.max(1, count));
        File[] files = new File("data").listFiles((d, n) -> n.startsWith("realcada_") && n.endsWith(".png") && !n.equals("realcada_latest.png"));
        if (files == null || files.length == 0)
            return json("{\"images\":[],\"count\":0}");
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        int limit = Math.min(count, files.length);
        File[] recent = Arrays.copyOf(files, limit);
        Arrays.sort(recent, (a, b) -> a.getName().compareTo(b.getName()));
        StringBuilder sb = new StringBuilder("{\"images\":[");
        for (int i = 0; i < recent.length; i++) {
            if (i > 0) sb.append(",");
            String name = recent[i].getName();
            String ts = "";
            try {
                String d = name.substring(9, 21);
                ts = d.substring(0,4)+"-"+d.substring(4,6)+"-"+d.substring(6,8)+"T"+d.substring(8,10)+":"+d.substring(10,12)+":00Z";
            } catch (Exception ignored) {}
            sb.append("{\"url\":\"/data/").append(name).append("\",\"timestamp\":\"").append(ts).append("\"}");
        }
        sb.append("],\"count\":").append(recent.length).append("}");
        return json(sb.toString());
    }

    // ── OPMET ────────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/opmet/messages")
    public ResponseEntity<String> opmetMessages() {
        boolean connected = OpmetWebSocketHandler.isConnected();
        int sessions = OpmetWebSocketHandler.getSessionCount();
        long lastMsg = OpmetWebSocketHandler.getLastMessageTime();
        String[] msgs = OpmetWebSocketHandler.getRecentMessages();
        StringBuilder sb = new StringBuilder("{")
            .append("\"connected\":").append(connected).append(",")
            .append("\"sessions\":").append(sessions).append(",")
            .append("\"lastMessageTime\":").append(lastMsg).append(",")
            .append("\"messageCount\":").append(msgs.length).append(",")
            .append("\"recentMessages\":[");
        for (int i = 0; i < msgs.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(msgs[i].replace("\"", "\\\"").replace("\n", "\\n")).append("\"");
        }
        sb.append("],\"status\":\"").append(connected ? "online" : "offline").append("\"}");
        return json(sb.toString());
    }

    private static final CopyOnWriteArrayList<SseEmitter> sseEmitters = new CopyOnWriteArrayList<>();

    static {
        // Heartbeat a cada 25s para manter conexão SSE viva no nginx/proxies
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat"); t.setDaemon(true); return t;
        }).scheduleAtFixedRate(() ->
            sseEmitters.removeIf(emitter -> {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                    return false;
                } catch (Exception e) { emitter.completeWithError(e); return true; }
            }), 25, 25, java.util.concurrent.TimeUnit.SECONDS);

        OpmetWebSocketHandler.registerBroadcastSession(new OpmetWebSocketHandler.OpmetSession() {
            @Override
            public void sendMessage(String message) {
                String text = message;
                if (message.contains("\"message\":\"")) {
                    int s = message.indexOf("\"message\":\"") + 11;
                    int e2 = message.indexOf("\"", s);
                    if (e2 > s) text = message.substring(s, e2);
                }
                final String payload = text;
                sseEmitters.removeIf(emitter -> {
                    try {
                        emitter.send(SseEmitter.event().data(payload, MediaType.TEXT_PLAIN));
                        return false;
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                        return true;
                    }
                });
            }
            @Override
            public String getSessionId() { return "sse-broadcast"; }
        });
    }

    @GetMapping(value = "/api/v1/opmet/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter opmetStream() {
        SseEmitter emitter = new SseEmitter(0L); // sem timeout
        sseEmitters.add(emitter);
        emitter.onCompletion(() -> sseEmitters.remove(emitter));
        emitter.onTimeout(() -> sseEmitters.remove(emitter));
        emitter.onError(e -> sseEmitters.remove(emitter));

        // Envia mensagens recentes ao conectar
        try {
            String[] recent = OpmetWebSocketHandler.getRecentMessages();
            for (String msg : recent) {
                // extrai só o campo "message" se for JSON {"type":"data","message":"..."}
                String text = msg;
                if (msg.contains("\"message\":\"")) {
                    int s = msg.indexOf("\"message\":\"") + 11;
                    int e2 = msg.indexOf("\"", s);
                    if (e2 > s) text = msg.substring(s, e2);
                }
                emitter.send(SseEmitter.event().data(text, MediaType.TEXT_PLAIN));
            }
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @GetMapping("/ws/opmet")
    public ResponseEntity<String> wsOpmet(@RequestParam(required = false) String iwxxm) {
        if (iwxxm != null) OpmetWebSocketHandler.setIwxxmParameter(true);
        String sessionId = "sse-" + System.currentTimeMillis();
        return json("{\"status\":\"connected\",\"sessionId\":\"" + sessionId + "\",\"stream\":\"/api/v1/opmet/stream\"}");
    }

    @GetMapping("/api/v1/opmet/test-message")
    public ResponseEntity<String> opmetTestMessage() {
        OpmetWebSocketHandler.simulateMessageForTesting("SPECI SBSP 241600Z 12005KT 9999 FEW020 25/18 Q1013 NOSIG=");
        OpmetWebSocketHandler.simulateMessageForTesting("SIGMET SBBS A1 VALID 241600/242000 SBBS- SBBS BRASILIA FIR SEV TURB");
        return json("{\"status\":\"success\",\"message\":\"2 mensagens de teste enviadas\"}");
    }

    @GetMapping("/api/v1/opmet/simulate")
    public ResponseEntity<String> opmetSimulate(@org.springframework.web.bind.annotation.RequestParam String msg) {
        OpmetWebSocketHandler.simulateMessageForTesting(msg);
        return json("{\"status\":\"success\"}");
    }

    private static final java.io.File DATA_DIR = new java.io.File("data").getAbsoluteFile();

    private ResponseEntity<?> resolveDataFile(String filename) {
        try {
            java.io.File f = new java.io.File(DATA_DIR, filename).getCanonicalFile();
            if (!f.toPath().startsWith(DATA_DIR.toPath())) return ResponseEntity.badRequest().build();
            return f.exists() ? null : ResponseEntity.notFound().build();
        } catch (Exception e) { return ResponseEntity.badRequest().build(); }
    }

    @GetMapping("/api/v1/sigwx/file")
    public ResponseEntity<String> sigwxFile(@RequestParam(required = false) String name) {
        if (name == null || name.isEmpty()) return ResponseEntity.badRequest().build();
        java.io.File f = sigwxService.resolveFileByName(name);
        if (f == null) {
            boolean valid = name.matches("egrr_iwxxm_forecasts_\\d{4}-\\d{2}-\\d{2}T\\d{6}Z\\.xml");
            return ResponseEntity.status(valid ? 404 : 400).build();
        }
        try { return json(sigwxService.parseToGeoJson(f)); }
        catch (Exception e) { return json("{\"error\":\"" + e.getMessage() + "\"}"); }
    }

    @GetMapping("/api/v1/sigwx/list")
    public ResponseEntity<String> sigwxList() {
        try { return json(sigwxService.cycleWindowToJson(sigwxService.buildCycleWindow())); }
        catch (Exception e) { return json("{\"error\":\"" + e.getMessage() + "\"}"); }
    }

    // ── data/ estático ───────────────────────────────────────────────────────

    @GetMapping("/data/{filename}")
    public ResponseEntity<byte[]> dataFile(@PathVariable String filename) {
        ResponseEntity<?> check = resolveDataFile(filename);
        if (check != null) return check.hasBody() ? ResponseEntity.notFound().build() : ResponseEntity.badRequest().build();
        try {
            java.io.File f = new java.io.File(DATA_DIR, filename).getCanonicalFile();
            String ct = filename.endsWith(".json") ? "application/json"
                : filename.endsWith(".png") ? "image/png"
                : filename.endsWith(".jpg") || filename.endsWith(".jpeg") ? "image/jpeg"
                : "application/octet-stream";
            return ResponseEntity.ok()
                .header("Content-Type", ct)
                .header("Access-Control-Allow-Origin", "*")
                .body(java.nio.file.Files.readAllBytes(f.toPath()));
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    // ── Config pública ───────────────────────────────────────────────────────

    @Value("${CESIUM_TOKEN:}")
    private String cesiumToken;

    @GetMapping("/api/v1/config/cesium-token")
    public ResponseEntity<String> cesiumToken() {
        return ResponseEntity.ok()
            .header("Content-Type", "text/plain")
            .body(cesiumToken);
    }

    // ── Health ───────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return json("{\"status\":\"UP\"}");
    }

    // ── Imagens estáticas ────────────────────────────────────────────────────

    @GetMapping({"/vis", "/canal16", "/realcada", "/cptec", "/raster"})
    public ResponseEntity<byte[]> staticImage(jakarta.servlet.http.HttpServletRequest req) {
        String path = req.getRequestURI();
        File f;
        if ("/canal16".equals(path)) {
            // serve sempre a imagem mais recente por nome (mesma lógica do HSV)
            File[] files = new File("data").listFiles((d, n) -> n.startsWith("canal16_202") && n.endsWith(".jpg"));
            if (files != null && files.length > 0) {
                java.util.Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
                f = files[0];
            } else {
                f = new File("data/canal16_latest.jpg");
            }
        } else {
            java.util.Map<String,String> map = java.util.Map.of(
                "/vis",     "data/vis_latest.png",
                "/realcada","data/realcada_latest.png",
                "/cptec",   "data/cptec_latest.png",
                "/raster",  "data/raster_latest.png"
            );
            f = new File(map.getOrDefault(path, "data/vis_latest.png"));
        }
        if (!f.exists()) return ResponseEntity.status(204).header("Access-Control-Allow-Origin","*").build();
        try {
            String ct = f.getName().endsWith(".jpg") ? "image/jpeg" : "image/png";
            return ResponseEntity.ok()
                .header("Content-Type", ct)
                .header("Access-Control-Allow-Origin", "*")
                .body(java.nio.file.Files.readAllBytes(f.toPath()));
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    // ── Convecção / HSV ──────────────────────────────────────────────────────

    @GetMapping("/convection")
    public ResponseEntity<String> convection() {
        File f = new File("data/convection_hsv.json");
        if (f.exists()) try { return json(new String(java.nio.file.Files.readAllBytes(f.toPath()))); } catch (Exception ignored) {}
        return json("{\"features\":[]}");
    }

    @GetMapping("/hsv_metadata")
    public ResponseEntity<String> hsvMetadata() {
        File f = new File("data/hsv_metadata.json");
        if (f.exists()) try { return json(new String(java.nio.file.Files.readAllBytes(f.toPath()))); } catch (Exception ignored) {}
        return json("{}");
    }

    @PostMapping("/realcada_hsv")
    public ResponseEntity<String> realcadaHsv() {
        if (!new File("data/realcada_latest.png").exists())
            return ResponseEntity.status(404).body("{\"error\":\"Imagem não encontrada\"}");
        CompletableFuture.runAsync(() -> { try { com.pocsigmet.HSVRealcadaHandler.main(new String[]{}); } catch (Exception e) { System.err.println("HSV Realçada: " + e.getMessage()); } });
        return json("{\"status\":\"success\",\"message\":\"Análise HSV iniciada\"}");
    }

    @PostMapping("/hsv_optimized")
    public ResponseEntity<String> hsvOptimized() {
        if (!new File("data/canal16_latest.jpg").exists())
            return ResponseEntity.status(404).body("{\"error\":\"Imagem não encontrada\"}");
        CompletableFuture.runAsync(() -> { try { com.pocsigmet.HSVOptimizedHandler.main(new String[]{}); } catch (Exception e) { System.err.println("HSV Otimizado: " + e.getMessage()); } });
        return json("{\"status\":\"success\",\"message\":\"Análise HSV Otimizado iniciada\"}");
    }

    @GetMapping("/hsv_polygons")
    public ResponseEntity<String> hsvPolygons() {
        File f = new File("data/hsv_polygons.json");
        if (f.exists()) try { return json(new String(java.nio.file.Files.readAllBytes(f.toPath()))); } catch (Exception ignored) {}
        return json("{\"features\":[],\"total\":0}");
    }

    // ── METARs adicionais ────────────────────────────────────────────────────

    @GetMapping("/metar_sbsp")
    public ResponseEntity<String> metarSbsp() {
        try {
            String r = CompletableFuture.supplyAsync(() -> metarService.getMetarSbsp())
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
            return json(r);
        } catch (Exception e) { return json("{\"icao\":\"SBSP\",\"condition\":\"UNKNOWN\"}"); }
    }

    @GetMapping("/metar_sboi")
    public ResponseEntity<String> metarSboi() {
        try {
            String r = CompletableFuture.supplyAsync(() -> metarService.getMetarSboi())
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
            return json(r);
        } catch (Exception e) { return json("{\"icao\":\"SBOI\",\"condition\":\"UNKNOWN\"}"); }
    }

    @GetMapping("/metar_all")
    public ResponseEntity<String> metarAll() {
        try {
            String r = CompletableFuture.supplyAsync(() -> metarService.getMetarAll())
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
            return json(r);
        } catch (Exception e) { return json("[]"); }
    }

    @GetMapping("/metar_top20_sb")
    public ResponseEntity<String> metarTop20() {
        try {
            String r = CompletableFuture.supplyAsync(() -> metarService.getMetarTopSB(20))
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
            return json(r);
        } catch (Exception e) { return json("[]"); }
    }

    @GetMapping("/metar_top50_sb")
    public ResponseEntity<String> metarTop50() {
        try {
            String r = CompletableFuture.supplyAsync(() -> metarService.getMetarTopSB(50))
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
            return json(r);
        } catch (Exception e) { return json("[]"); }
    }

    // ── SIGMETs adicionais ───────────────────────────────────────────────────

    @GetMapping("/redemet_sigmets")
    public ResponseEntity<String> redemetSigmets() {
        try { return json(new com.pocsigmet.RedemetSigmetClient().getSigmets()); } catch (Exception e) { return json("[]"); }
    }

    @GetMapping("/sigmet/count")
    public ResponseEntity<String> sigmetCount() {
        try {
            String j = CompletableFuture.supplyAsync(() -> {
                try { return new com.pocsigmet.RedemetSigmetClient().getSigmetsJson(); }
                catch (Exception e) { return "{}"; }
            }).get(8, java.util.concurrent.TimeUnit.SECONDS);
            int total = j.split("\"sigmetNumber\"").length - 1;
            int sbao  = j.split("\"fir\":\"SBAO\"").length - 1;
            int sbaz  = j.split("\"fir\":\"SBAZ\"").length - 1;
            int sbre  = j.split("\"fir\":\"SBRE\"").length - 1;
            int sbcw  = j.split("\"fir\":\"SBCW\"").length - 1;
            int sbbs  = j.split("\"fir\":\"SBBS\"").length - 1;
            return json(String.format(
                "{\"total\":%d,\"by_fir\":{\"SBAO\":%d,\"SBAZ\":%d,\"SBRE\":%d,\"SBCW\":%d,\"SBBS\":%d},\"timestamp\":\"%s\"}",
                total, sbao, sbaz, sbre, sbcw, sbbs, java.time.LocalDateTime.now()));
        } catch (Exception e) { return json("{\"total\":0,\"error\":\"" + e.getMessage() + "\"}"); }
    }

    @GetMapping("/sigmet_copilot")
    public ResponseEntity<String> sigmetCopilot() {
        try {
            String r = CompletableFuture.supplyAsync(() -> {
                try { return new com.pocsigmet.SevereConvectionSIGMET().processImage2(); }
                catch (Exception e) { return "{\"features\":[]}"; }
            }).get(5, java.util.concurrent.TimeUnit.SECONDS);
            return json(r);
        } catch (Exception e) { return json("{\"features\":[]}"); }
    }

    // ── GeoJSON ──────────────────────────────────────────────────────────────

    @GetMapping("/severe_convection_with_sigmet.geojson")
    public ResponseEntity<String> severeConvectionGeojson() {
        File f = new File("data/severe_convection_with_sigmet.geojson");
        if (f.exists()) try { return json(new String(java.nio.file.Files.readAllBytes(f.toPath()))); } catch (Exception ignored) {}
        return json("{\"type\":\"FeatureCollection\",\"features\":[]}");
    }

    @GetMapping("/risk_polygons.geojson")
    public ResponseEntity<String> riskPolygons() {
        File f = new File("data/config/risk_polygons.geojson");
        if (f.exists()) try { return json(new String(java.nio.file.Files.readAllBytes(f.toPath()))); } catch (Exception ignored) {}
        return json("{\"type\":\"FeatureCollection\",\"features\":[]}");
    }

    @GetMapping("/firs")
    public ResponseEntity<String> firs() {
        try {
            org.springframework.core.io.ClassPathResource res = new org.springframework.core.io.ClassPathResource("firs_brasil_decea_oficial.geojson");
            String json = new String(res.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return json(json);
        } catch (Exception e) {
            return json("{\"type\":\"FeatureCollection\",\"features\":[]}");
        }
    }

    // ── OPMET Live ───────────────────────────────────────────────────────────

    @GetMapping("/opmet_live")
    public ResponseEntity<String> opmetLive() {
        return json("{\"status\":\"success\",\"data\":[]}");
    }

    // ── Imagem transparente ──────────────────────────────────────────────────

    @GetMapping("/make_transparent")
    public ResponseEntity<String> makeTransparent() {
        return json("{\"status\":\"success\",\"message\":\"Processamento iniciado\"}");
    }

    @GetMapping("/transparent_image")
    public ResponseEntity<byte[]> transparentImage() {
        File f = new File("data/realcada_latest_transparent.png");
        if (!f.exists()) f = new File("data/realcada_latest.png");
        if (!f.exists()) return ResponseEntity.status(204).header("Access-Control-Allow-Origin","*").build();
        try {
            return ResponseEntity.ok()
                .header("Content-Type", "image/png")
                .header("Access-Control-Allow-Origin", "*")
                .body(java.nio.file.Files.readAllBytes(f.toPath()));
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    // ── Cache stats ──────────────────────────────────────────────────────────

    @GetMapping("/frame/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> frame(@PathVariable String filename) {
        ResponseEntity<?> check = resolveDataFile(filename);
        if (check != null) return ResponseEntity.badRequest().build();
        try {
            java.io.File f = new java.io.File(DATA_DIR, filename).getCanonicalFile();
            String ct = filename.endsWith(".jpg") || filename.endsWith(".jpeg") ? "image/jpeg" : "image/png";
            return ResponseEntity.ok()
                .header("Content-Type", ct)
                .body(new org.springframework.core.io.FileSystemResource(f));
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    @GetMapping("/cache/stats")
    public ResponseEntity<String> cacheStats() {
        try { return json(new com.pocsigmet.service.MonitoringService().getCacheStats()); }
        catch (Exception e) { return json("{\"error\":\"" + e.getMessage() + "\"}"); }
    }

    // ── Canal16 frame individual ─────────────────────────────────────────────

    @GetMapping("/canal16frame/{filename}")
    public ResponseEntity<byte[]> canal16frame(@PathVariable String filename) {
        try {
            java.io.File f = new java.io.File(DATA_DIR, filename).getCanonicalFile();
            if (!f.toPath().startsWith(DATA_DIR.toPath())) return ResponseEntity.badRequest().build();
            if (!f.exists()) f = new java.io.File(DATA_DIR, "canal16_latest.jpg").getCanonicalFile();
            if (!f.exists()) return ResponseEntity.status(204).header("Access-Control-Allow-Origin","*").build();
            return ResponseEntity.ok()
                .header("Content-Type", "image/jpeg")
                .header("Access-Control-Allow-Origin", "*")
                .body(java.nio.file.Files.readAllBytes(f.toPath()));
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    // ── util ─────────────────────────────────────────────────────────────────

    private ResponseEntity<String> json(String body) {
        return ResponseEntity.ok()
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Access-Control-Allow-Origin", "*")
            .body(body);
    }
}
