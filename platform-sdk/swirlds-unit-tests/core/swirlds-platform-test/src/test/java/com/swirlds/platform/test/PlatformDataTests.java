/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.utility.CompareTo.isGreaterThan;
import static com.swirlds.platform.test.event.source.EventSourceFactory.newStandardEventSources;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.fixtures.config.TestConfigBuilder;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.LegacyPlatformState;
import com.swirlds.platform.state.MinGenInfo;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.test.event.generator.GraphGenerator;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.event.source.EventSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PlatformData Tests")
class PlatformDataTests {

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        new TestConfigBuilder().getOrCreateConfig();
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    private static PlatformData generateRandomPlatformData(final Random random) {
        final int randomBound = 10_000;

        final List<MinGenInfo> minGenInfo = new LinkedList<>();
        final int minGenInfoSize = random.nextInt(100) + 1;
        for (int i = 0; i < minGenInfoSize; i++) {
            minGenInfo.add(new MinGenInfo(random.nextLong(randomBound), random.nextLong(randomBound)));
        }

        final List<EventSource<?>> eventSources = newStandardEventSources(List.of(1L, 2L, 3L, 4L, 5L));
        final GraphGenerator<?> generator = new StandardGraphGenerator(0, eventSources);

        final int eventsSize = random.nextInt(100) + 1;
        final EventImpl[] events = new EventImpl[eventsSize];
        for (int i = 0; i < eventsSize; i++) {
            events[i] = generator.generateEvent();
        }

        return new PlatformData()
                .setRound(random.nextLong(randomBound))
                .setNumEventsCons(random.nextLong(randomBound))
                .setHashEventsCons(RandomUtils.randomHash(random))
                .setEvents(events)
                .setConsensusTimestamp(Instant.ofEpochSecond(random.nextInt(randomBound)))
                .setMinGenInfo(minGenInfo)
                .setCreationSoftwareVersion(new BasicSoftwareVersion(random.nextInt(randomBound)))
                .setEpochHash(RandomUtils.randomHash(random));
    }

    @Test
    @DisplayName("Serialization Test")
    void serializationTest() throws IOException {
        final Random random = getRandomPrintSeed();
        final PlatformData platformData = generateRandomPlatformData(random);

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

        out.writeSerializable(platformData, true);

        final SerializableDataInputStream in =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

        final PlatformData deserialized = in.readSerializable();

        CryptographyHolder.get().digestSync(platformData);
        CryptographyHolder.get().digestSync(deserialized);

        assertEquals(platformData.getHash(), deserialized.getHash(), "hash should match");
    }

    @Test
    @DisplayName("Serialization Test")
    void equalityTest() {
        final Random random = getRandomPrintSeed();

        final long seed1 = random.nextLong();
        final long seed2 = random.nextLong();

        final PlatformData platformData1 = generateRandomPlatformData(new Random(seed1));
        final PlatformData platformData1Duplicate = generateRandomPlatformData(new Random(seed1));
        final PlatformData platformData2 = generateRandomPlatformData(new Random(seed2));

        assertEquals(platformData1, platformData1Duplicate, "should be equal");
        assertNotEquals(platformData1, platformData2, "should not be equal");
    }

    @Test
    @DisplayName("Last Timestamp Test")
    void lastTimestampTest() {
        final Random random = getRandomPrintSeed();
        final PlatformData platformData = generateRandomPlatformData(random);

        Instant lastTimestamp = null;
        for (final EventImpl event : platformData.getEvents()) {
            if (lastTimestamp == null || isGreaterThan(event.getLastTransTime(), lastTimestamp)) {
                lastTimestamp = event.getLastTransTime();
            }
        }

        assertEquals(lastTimestamp, platformData.getLastTransactionTimestamp(), "last timestamp incorrectly computed");
    }

    @Test
    @DisplayName("Update Epoch Hash Test")
    void updateEpochHashTest() {
        final Random random = getRandomPrintSeed();
        final PlatformData platformData = generateRandomPlatformData(random);
        final Hash hash = RandomUtils.randomHash(random);

        platformData.setEpochHash(null);
        platformData.setNextEpochHash(null);
        assertDoesNotThrow(platformData::updateEpochHash);
        assertNull(platformData.getEpochHash(), "epoch hash should not change");
        assertNull(platformData.getNextEpochHash(), "next epoch hash should not change");

        platformData.setEpochHash(hash);
        platformData.setNextEpochHash(null);
        assertDoesNotThrow(platformData::updateEpochHash);
        assertEquals(hash, platformData.getEpochHash(), "epoch hash should not change");
        assertNull(platformData.getNextEpochHash(), "next epoch hash should not change");

        platformData.setEpochHash(null);
        platformData.setNextEpochHash(hash);
        assertDoesNotThrow(platformData::updateEpochHash);
        assertEquals(hash, platformData.getEpochHash(), "epoch hash should be updated");
        assertNull(platformData.getNextEpochHash(), "next epoch hash should be set to null");

        platformData.setEpochHash(RandomUtils.randomHash(random));
        platformData.setNextEpochHash(hash);
        assertDoesNotThrow(platformData::updateEpochHash);
        assertEquals(hash, platformData.getEpochHash(), "epoch hash should be updated");
        assertNull(platformData.getNextEpochHash(), "next epoch hash should be set to null");
    }

    // FUTURE WORK: this can be removed after LegacyPlatformState is deleted
    @Test
    @DisplayName("Migration Test")
    void migrationTest() {
        final Random random = getRandomPrintSeed();
        final PlatformData platformData = generateRandomPlatformData(random);
        platformData.setEpochHash(null);

        final LegacyPlatformState legacyState = new LegacyPlatformState();
        legacyState.setRound(platformData.getRound());
        legacyState.setNumEventsCons(platformData.getNumEventsCons());
        legacyState.setHashEventsCons(platformData.getHashEventsCons());
        legacyState.setEvents(platformData.getEvents());
        legacyState.setConsensusTimestamp(platformData.getConsensusTimestamp());
        legacyState.setLastTransactionTimestamp(platformData.getLastTransactionTimestamp());
        legacyState.setMinGenInfo(platformData.getMinGenInfo());
        legacyState.setCreationSoftwareVersion(platformData.getCreationSoftwareVersion());

        final PlatformData migratedData =
                ((PlatformState) legacyState.migrate(legacyState.getVersion())).getPlatformData();

        assertEquals(platformData, migratedData, "should be equal after migration");
    }
}
