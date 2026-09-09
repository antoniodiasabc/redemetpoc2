public class MetarFiltroVis implements MetarFiltro {
    public boolean aceita(String palavra) { return palavra.equals("vis"); }
    public java.util.function.Predicate<String> buildPredicate(String palavra, int val) {
        return metar -> {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{4})\\b").matcher(metar);
            while (m.find()) { int v = Integer.parseInt(m.group(1)); if (v >= 100 && v <= 9999 && v < val) return true; }
            return false;
        };
    }
}
