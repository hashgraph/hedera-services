/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.platform.event;

import com.hedera.node.app.service.mono.context.properties.SerializableSemVers;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.recovery.internal.EventStreamSingleFileIterator;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class EventMigrationTest {

    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("");
        StaticSoftwareVersion.setSoftwareVersion(Set.of(SerializableSemVers.CLASS_ID));
    }

    /**
     * Tests the migration of events as we are switching events to protobuf. The main thing we are testing is that the
     * hashes of old events can still be calculated when the code changes. This is done by calculating the hashes of the
     * events that are read and matching them to the parent descriptors inside the events. The parents of most events
     * will be present in the file, except for a few events at the beginning of the file.
     * <p>
     * The file being read is from mainnet written by the SDK 0.46.3.
     * <p>
     * Even though this could be considered a platform test, it needs to be in the services module because the event
     * contains a {@link com.hedera.node.app.service.mono.context.properties.SerializableSemVers} which is a services
     * class
     */
    @Test
    public void migration() throws URISyntaxException, IOException {
        final Set<Hash> eventHashes = new HashSet<>();
        final Set<Hash> parentHashes = new HashSet<>();
        int numEvents = 0;

        try (final EventStreamSingleFileIterator iterator = new EventStreamSingleFileIterator(
                new File(this.getClass()
                                .getClassLoader()
                                .getResource("eventFiles/sdk0.46.3/2024-03-05T00_10_55.002129867Z.events")
                                .toURI())
                        .toPath(),
                false)) {
            while (iterator.hasNext()) {
                final BaseEventHashedData hashedData = iterator.next().getBaseEventHashedData();
                numEvents++;
                CryptographyHolder.get().digestSync(hashedData);
                eventHashes.add(hashedData.getHash());
                Stream.of(hashedData.getSelfParentHash(), hashedData.getOtherParentHash())
                        .filter(Objects::nonNull)
                        .forEach(parentHashes::add);
            }
        }

        Assertions.assertEquals(2417, numEvents, "this file is expected to have 2417 events but has " + numEvents);
        Assertions.assertEquals(
                2417,
                eventHashes.size(),
                "we expected to have 2417 hashes (one for each event) but have " + eventHashes.size());
        eventHashes.removeAll(parentHashes);
        Assertions.assertEquals(
                9,
                eventHashes.size(),
                "the hashes of most parents are expected to match the hashes of events."
                        + " Number of unmatched hashes: " + eventHashes.size());
    }
}
