public class MetarFiltroPressao implements MetarFiltro {
    public boolean aceita(String palavra) { return palavra.equals("pressao"); }
    public java.util.function.Predicate<String> buildPredicate(String palavra, int val) {
        return metar -> {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("Q(\\d{4})").matcher(metar);
            return m.find() && Integer.parseInt(m.group(1)) <= val;
        };
    }
}
