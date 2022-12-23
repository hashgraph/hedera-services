/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.fixtures.state;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class TestBase {
    private static final Set<String> WORDS;

    protected static final String UNKNOWN_STATE_KEY = "BOGUS_STATE_KEY";
    protected static final String UNKNOWN_KEY = "BOGUS_KEY";

    protected static final String FRUIT_STATE_KEY = "FRUIT";
    protected static final String ANIMAL_STATE_KEY = "ANIMAL";
    protected static final String SPACE_STATE_KEY = "SPACE";
    protected static final String STEAM_STATE_KEY = "STEAM";
    protected static final String COUNTRY_STATE_KEY = "COUNTRY";

    protected static final String A_KEY = "A";
    protected static final String B_KEY = "B";
    protected static final String C_KEY = "C";
    protected static final String D_KEY = "D";
    protected static final String E_KEY = "E";
    protected static final String F_KEY = "F";
    protected static final String G_KEY = "G";

    protected static final String APPLE = "Apple";
    protected static final String ACAI = "Acai";
    protected static final String BANANA = "Banana";
    protected static final String BLACKBERRY = "BlackBerry";
    protected static final String BLUEBERRY = "BlueBerry";
    protected static final String CHERRY = "Cherry";
    protected static final String DATE = "Date";
    protected static final String EGGPLANT = "Eggplant";
    protected static final String ELDERBERRY = "ElderBerry";
    protected static final String FIG = "Fig";
    protected static final String GRAPE = "Grape";

    protected static final String AARDVARK = "Aardvark";
    protected static final String BEAR = "Bear";
    protected static final String CUTTLEFISH = "Cuttlefish";
    protected static final String DOG = "Dog";
    protected static final String EMU = "Emu";
    protected static final String FOX = "Fox";
    protected static final String GOOSE = "Goose";

    protected static final String ASTRONAUT = "Astronaut";
    protected static final String BLASTOFF = "Blastoff";
    protected static final String COMET = "Comet";
    protected static final String DRACO = "Draco";
    protected static final String EXOPLANET = "Exoplanet";
    protected static final String FORCE = "Force";
    protected static final String GRAVITY = "Gravity";

    protected static final String ART = "Art";
    protected static final String BIOLOGY = "Biology";
    protected static final String CHEMISTRY = "Chemistry";
    protected static final String DISCIPLINE = "Discipline";
    protected static final String ECOLOGY = "Ecology";
    protected static final String FIELDS = "Fields";
    protected static final String GEOMETRY = "Geometry";

    protected static final String AUSTRALIA = "Australia";
    protected static final String BRAZIL = "Brazil";
    protected static final String CHAD = "Chad";
    protected static final String DENMARK = "Denmark";
    protected static final String ESTONIA = "Estonia";
    protected static final String FRANCE = "France";
    protected static final String GHANA = "Ghana";

    static {
        final var words = new HashSet<String>();
        try (final var input = TestBase.class.getResourceAsStream("wordlist.txt")) {
            assert input != null : "Missing wordlist.txt";
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

    private static final Random RAND = new Random(7729311);
    private final Random rand = new Random(9239992);

    public Random random() {
        return rand;
    }

    /**
     * Generates some random bytes
     *
     * @param length The number of bytes to generate.
     * @return Some random bytes.
     */
    public byte[] randomBytes(int length) {
        final byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) rand.nextInt();
        }
        return data;
    }

    public Set<String> words() {
        return WORDS;
    }

    public List<String> randomWords(final int n) {
        final var randomWords = new ArrayList<>(WORDS);
        Collections.shuffle(randomWords, rand);
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
            buf[i] = (byte) alphabet.charAt(RAND.nextInt(alphabet.length()));
        }

        return new String(buf);
    }
}
