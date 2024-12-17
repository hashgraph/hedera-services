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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.hashing.DefaultEventHasher;
import com.swirlds.platform.recovery.internal.EventStreamSingleFileIterator;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Disabled("Temporarily disabling until platform refactoring is complete")
public class EventMigrationTest {

    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("");
        StaticSoftwareVersion.setSoftwareVersion(new ServicesSoftwareVersion(SemanticVersion.DEFAULT, 0));
    }

    public static Stream<Arguments> migrationTestArguments() {
        return Stream.of(
                Arguments.of("eventFiles/previewnet-53/2024-08-26T10_38_35.016340634Z.events", 637, 4),
                Arguments.of("eventFiles/testnet-53/2024-09-10T00_00_00.021456201Z.events", 635, 5));
    }

    /**
     * Tests the migration of events as we are switching events to protobuf. The main thing we are testing is that the
     * hashes of old events can still be calculated when the code changes. This is done by calculating the hashes of the
     * events that are read and matching them to the parent descriptors inside the events. The parents of most events
     * will be present in the file, except for a few events at the beginning of the file.
     * <p>
     * Even though this could be considered a platform test, it needs to be in the services module because the event
     * contains a {@link SerializableSemVers} which is a services class
     */
    @ParameterizedTest
    @MethodSource("migrationTestArguments")
    public void migration(
            @NonNull final String fileName, final int numEventsExpected, final int unmatchedHashesExpected)
            throws URISyntaxException, IOException {
        final Set<Hash> eventHashes = new HashSet<>();
        final Set<Hash> parentHashes = new HashSet<>();
        int numEventsFound = 0;

        try (final EventStreamSingleFileIterator iterator = new EventStreamSingleFileIterator(
                new File(this.getClass().getClassLoader().getResource(fileName).toURI()).toPath(), false)) {
            while (iterator.hasNext()) {
                final PlatformEvent platformEvent = iterator.next().getPlatformEvent();
                new DefaultEventHasher().hashEvent(platformEvent);
                numEventsFound++;
                eventHashes.add(platformEvent.getHash());
                platformEvent.getAllParents().stream()
                        .filter(Objects::nonNull)
                        .map(EventDescriptorWrapper::hash)
                        .forEach(parentHashes::add);
            }
        }

        Assertions.assertEquals(
                numEventsExpected,
                numEventsFound,
                "this file is expected to have %d events but has %d".formatted(numEventsExpected, numEventsFound));
        Assertions.assertEquals(
                numEventsExpected,
                eventHashes.size(),
                "we expected to have %d hashes (one for each event) but have %d"
                        .formatted(numEventsExpected, eventHashes.size()));
        eventHashes.removeAll(parentHashes);
        Assertions.assertEquals(
                unmatchedHashesExpected,
                eventHashes.size(),
                "the hashes of most parents are expected to match the hashes of events."
                        + " Number of unmatched hashes: " + eventHashes.size());
    }
}
