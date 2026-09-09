public class MetarFiltroUmidade implements MetarFiltro {
    public boolean aceita(String palavra) { return palavra.equals("umidade"); }
    public java.util.function.Predicate<String> buildPredicate(String palavra, int val) {
        return metar -> {
            // extrai T e Td do campo TT/TD ex: 25/18 ou M02/M05
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(M?\\d{2})/(M?\\d{2})").matcher(metar);
            while (m.find()) {
                try {
                    int t  = parseTemp(m.group(1));
                    int td = parseTemp(m.group(2));
                    int ur = 100 - 5 * (t - td);
                    if (ur < val) return true;
                } catch (Exception ignored) {}
            }
            return false;
        };
    }
    private static int parseTemp(String s) {
        return s.startsWith("M") ? -Integer.parseInt(s.substring(1)) : Integer.parseInt(s);
    }
}
