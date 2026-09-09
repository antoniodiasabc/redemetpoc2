public class MetarFiltroVento implements MetarFiltro {
    public boolean aceita(String palavra) { return palavra.equals("vento"); }
    public java.util.function.Predicate<String> buildPredicate(String palavra, int val) {
        return metar -> {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{3}(\\d{2})(?:G(\\d{2}))?KT").matcher(metar);
            while (m.find()) {
                if (Integer.parseInt(m.group(1)) >= val) return true;
                if (m.group(2) != null && Integer.parseInt(m.group(2)) >= val) return true;
            }
            return false;
        };
    }
}
