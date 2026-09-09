public class MetarFiltroTeto implements MetarFiltro {
    public boolean aceita(String palavra) { return palavra.equals("teto"); }
    public java.util.function.Predicate<String> buildPredicate(String palavra, int val) {
        int valFt = (int)(val * 3.28084);
        return metar -> {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:OVC|BKN)(\\d{3})").matcher(metar);
            while (m.find()) { if (Integer.parseInt(m.group(1)) * 100 < valFt) return true; }
            return false;
        };
    }
}
