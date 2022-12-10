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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.Executors;

public class TestUtils {
    private static final Random RANDOM = new Random(9239992);
    private static final Set<String> WORDS;

    static {
        final var words = new HashSet<String>();
        try (final var input = TestUtils.class.getResourceAsStream("/utils/wordlist.txt")) {
            assert input != null : "Missing utils/wordlist.txt";
            final var reader = new BufferedReader(new InputStreamReader(input));
            String word;
            while ((word = reader.readLine()) != null) {
                words.add(word);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load word list!", e);
        }

        WORDS = Collections.unmodifiableSet(words);
    }

    public static Random random() {
        return RANDOM;
    }

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
        return new DefaultMetrics(
                Executors.newSingleThreadScheduledExecutor(), new DefaultMetricsFactory());
    }

    public static Set<String> words() {
        return WORDS;
    }

    public static List<String> randomWords(final int n) {
        final var randomWords = new ArrayList<>(WORDS);
        Collections.shuffle(randomWords, RANDOM);
        return randomWords.subList(0, n);
    }

    /**
     * Given an alphabet of all possible characters, generate a pseudo-random string of the given
     * length.
     *
     * @param alphabet The alphabet to use. Cannot be null or blank.
     * @param length The length of the string. Must be non-negative
     * @return A random string created by the given alphabet and of the requested length.
     */
    @NonNull
    public static String randomString(@NonNull final String alphabet, final int length) {
        assert !alphabet.isBlank();
        assert length >= 0;

        final var buf = new byte[length];
        for (int i = 0; i < length; i++) {
            buf[i] = (byte) alphabet.charAt(RANDOM.nextInt(alphabet.length()));
        }

        return new String(buf);
    }
}
