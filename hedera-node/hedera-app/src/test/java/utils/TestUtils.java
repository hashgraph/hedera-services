/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package utils;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import java.util.*;
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
