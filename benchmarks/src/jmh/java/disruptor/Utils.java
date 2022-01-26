package disruptor;

public class Utils {
    public static long fastModulo(long n, long d) {
        return n & (d-1);
    }
}
