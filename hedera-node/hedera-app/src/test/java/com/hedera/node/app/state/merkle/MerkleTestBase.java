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
package com.hedera.node.app.state.merkle;

import com.hedera.node.app.spi.fixtures.state.TestBase;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.hedera.node.app.state.merkle.memory.InMemoryKey;
import com.hedera.node.app.state.merkle.memory.InMemoryValue;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

public class MerkleTestBase extends TestBase {
    public static final String FIRST_SERVICE = "First-Service";
    public static final String SECOND_SERVICE = "Second-Service";
    public static final String UNKNOWN_SERVICE = "Bogus-Service";
    public static final long A_LONG_KEY = 0L;
    public static final long B_LONG_KEY = 1L;
    public static final long C_LONG_KEY = 2L;
    public static final long D_LONG_KEY = 3L;
    public static final long E_LONG_KEY = 4L;
    public static final long F_LONG_KEY = 5L;
    public static final long G_LONG_KEY = 6L;

    // The "FRUIT" Map is part of FIRST_SERVICE
    protected String fruitLabel;
    protected StateMetadata<String, String> fruitMetadata;
    protected MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> fruitMerkleMap;
    protected VirtualMap<OnDiskKey<String>, OnDiskValue<String>> fruitVirtualMap;

    // The "ANIMAL" map is part of FIRST_SERVICE
    protected String animalLabel;
    protected StateMetadata<String, String> animalMetadata;
    protected MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> animalMerkleMap;

    // The "SPACE" map is part of SECOND_SERVICE and uses the long-based keys
    protected String spaceLabel;
    protected StateMetadata<Long, String> spaceMetadata;
    protected MerkleMap<InMemoryKey<Long>, InMemoryValue<Long, Long>> spaceMerkleMap;

    // The "STEAM" map is part of SECOND_SERVICE
    protected String steamLabel;
    protected StateMetadata<String, String> steamMetadata;
    protected MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> steamMerkleMap;

    // The "COUNTRY" map is part of SECOND_SERVICE
    protected String countryLabel;
    protected StateMetadata<String, String> countryMetadata;
    protected MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> countryMerkleMap;

    @BeforeEach
    void setUp() {
        fruitLabel = StateUtils.computeLabel(FIRST_SERVICE, FRUIT_STATE_KEY);
        fruitMerkleMap = createMerkleMap(fruitLabel);
        fruitVirtualMap = createVirtualMap(fruitLabel);
        fruitMetadata =
                new StateMetadata<>(
                        FIRST_SERVICE,
                        FRUIT_STATE_KEY,
                        MerkleHederaStateTest::parseString,
                        MerkleHederaStateTest::parseString,
                        MerkleHederaStateTest::writeString,
                        MerkleHederaStateTest::writeString,
                        MerkleHederaStateTest::measureString);

        animalLabel = StateUtils.computeLabel(FIRST_SERVICE, ANIMAL_STATE_KEY);
        animalMerkleMap = createMerkleMap(animalLabel);
        animalMetadata =
                new StateMetadata<>(
                        FIRST_SERVICE,
                        ANIMAL_STATE_KEY,
                        MerkleHederaStateTest::parseString,
                        MerkleHederaStateTest::parseString,
                        MerkleHederaStateTest::writeString,
                        MerkleHederaStateTest::writeString,
                        MerkleHederaStateTest::measureString);

        spaceLabel = StateUtils.computeLabel(SECOND_SERVICE, SPACE_STATE_KEY);
        spaceMerkleMap = createMerkleMap(spaceLabel);
        spaceMetadata =
                new StateMetadata<>(
                        SECOND_SERVICE,
                        SPACE_STATE_KEY,
                        MerkleHederaStateTest::parseLong,
                        MerkleHederaStateTest::parseString,
                        MerkleHederaStateTest::writeLong,
                        MerkleHederaStateTest::writeString,
                        MerkleHederaStateTest::measureString);

        steamLabel = StateUtils.computeLabel(SECOND_SERVICE, STEAM_STATE_KEY);
        steamMerkleMap = createMerkleMap(steamLabel);
        steamMetadata =
                new StateMetadata<>(
                        SECOND_SERVICE,
                        STEAM_STATE_KEY,
                        MerkleHederaStateTest::parseString,
                        MerkleHederaStateTest::parseString,
                        MerkleHederaStateTest::writeString,
                        MerkleHederaStateTest::writeString,
                        MerkleHederaStateTest::measureString);

        countryLabel = StateUtils.computeLabel(SECOND_SERVICE, COUNTRY_STATE_KEY);
        countryMerkleMap = createMerkleMap(countryLabel);
        countryMetadata =
                new StateMetadata<>(
                        SECOND_SERVICE,
                        COUNTRY_STATE_KEY,
                        MerkleHederaStateTest::parseString,
                        MerkleHederaStateTest::parseString,
                        MerkleHederaStateTest::writeString,
                        MerkleHederaStateTest::writeString,
                        MerkleHederaStateTest::measureString);
    }

    protected <K, V> MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>> createMerkleMap(String label) {
        final var map = new MerkleMap<InMemoryKey<K>, InMemoryValue<K, V>>();
        map.setLabel(label);
        return map;
    }

    @SuppressWarnings("unchecked")
    protected VirtualMap<OnDiskKey<String>, OnDiskValue<String>> createVirtualMap(String label) {
        final var builder = Mockito.mock(VirtualDataSourceBuilder.class);
        return new VirtualMap<OnDiskKey<String>, OnDiskValue<String>>(label, builder);
    }

    protected void add(
            MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> map,
            StateMetadata<String, String> md,
            String key,
            String value) {
        final var k = new InMemoryKey<>(key);
        map.put(k, new InMemoryValue<>(md, k, value));
    }

    protected MerkleNode getNodeForLabel(
            MerkleHederaState hederaMerkle, String serviceName, String stateKey) {
        return getNodeForLabel(hederaMerkle, StateUtils.computeLabel(serviceName, stateKey));
    }

    protected MerkleNode getNodeForLabel(MerkleHederaState hederaMerkle, String label) {
        // This is not idea, as it requires white-box testing -- knowing the
        // internal details of the MerkleHederaState. But lacking a getter
        // (which I don't want to add), this is what I'm left with!
        for (int i = 0, n = hederaMerkle.getNumberOfChildren(); i < n; i++) {
            final MerkleNode child = hederaMerkle.getChild(i);
            if (child instanceof MerkleMap<?, ?> m && label.equals(m.getLabel())) {
                return m;
            } else if (child instanceof VirtualMap<?, ?> v && label.equals(v.getLabel())) {
                return v;
            }
        }

        return null;
    }

    public static void writeString(String value, DataOutput output) throws IOException {
        if (value == null) {
            output.writeInt(0);
        } else {
            final var bytes = value.getBytes(StandardCharsets.UTF_8);
            output.writeInt(bytes.length);
            output.write(bytes);
        }
    }

    public static String parseString(DataInput input) throws IOException {
        final var len = input.readInt();
        final var bytes = new byte[len];
        input.readFully(bytes);
        return len == 0 ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public static int measureString(DataInput input) throws IOException {
        return input.readInt();
    }

    public static void writeInteger(Integer value, DataOutput output) throws IOException {
        output.writeInt(value);
    }

    public static int parseInteger(DataInput input) throws IOException {
        return input.readInt();
    }

    public static void writeLong(Long value, DataOutput output) throws IOException {
        output.writeLong(value);
    }

    public static long parseLong(DataInput input) throws IOException {
        return input.readLong();
    }

    public static int measureLong(DataInput input) throws IOException {
        return 8;
    }
}
