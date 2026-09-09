public interface MetarFiltro {
    boolean aceita(String palavra);
    java.util.function.Predicate<String> buildPredicate(String palavra, int val);
}
