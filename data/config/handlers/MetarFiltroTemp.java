public class MetarFiltroTemp implements MetarFiltro {
    public boolean aceita(String palavra) {
        return palavra.startsWith("temp");
    }
    public java.util.function.Predicate<String> buildPredicate(String palavra, int val) {
        // palavra = "temp<10" ou "temp>35" — val já vem parseado pelo buildFiltro
        // operador extraído do contexto via sufixo da palavra original não chega aqui,
        // então usamos convenção: temp<N → palavra="temp<", temp>N → palavra="temp>"
        boolean maior = palavra.contains(">");
        return metar -> {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\b(M?\\d{2})/(M?\\d{2})\\b").matcher(metar);
            if (!m.find()) return false;
            int temp = parseTemp(m.group(1));
            return maior ? temp > val : temp < val;
        };
    }
    private static int parseTemp(String s) {
        return s.startsWith("M") ? -Integer.parseInt(s.substring(1)) : Integer.parseInt(s);
    }
}
