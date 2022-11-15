package utils;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;

import java.util.Random;
import java.util.concurrent.Executors;

public class TestUtils {
    private static final Random RANDOM = new Random(9239992);

    /**
     * Generates some random bytes
     *
     * @param length The number of bytes to generate.
     * @return Some random bytes.
     */
    public static byte[] randomBytes(int length) {
        final byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) RANDOM.nextInt();
        }
        return data;
    }

    public static Metrics metrics() {
        return new DefaultMetrics(Executors.newSingleThreadScheduledExecutor(), new DefaultMetricsFactory());
    }
}
