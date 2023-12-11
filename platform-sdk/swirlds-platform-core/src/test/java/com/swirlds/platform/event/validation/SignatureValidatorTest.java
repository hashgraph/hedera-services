/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.time.Time;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SignatureValidatorTest {
    @Test
    void testMultipleAddressBooks() {
        final AddressBook full = new RandomAddressBookGenerator(RandomUtils.getRandomPrintSeed())
                .setSize(10)
                .build();
        final NodeId removedNodeId = full.getNodeId(0);
        final AddressBook reduced = full.remove(removedNodeId);
        final SignatureVerifier signatureVerifier = (data, signature, publicKey) -> true;
        final SoftwareVersion currentSoftwareVersion = new BasicSoftwareVersion(1);
        final SignatureValidator validator =
                new SignatureValidator(reduced, full, currentSoftwareVersion, signatureVerifier, Time.getCurrent());
        for (final Address address : full) {
            assertTrue(
                    validator.isEventValid(createEvent(address.getNodeId(), currentSoftwareVersion)),
                    "if an event has the current software version "
                            + "and is signed by a node in the current address book, it should be valid");
        }
        for (final Address address : reduced) {
            assertTrue(
                    validator.isEventValid(createEvent(address.getNodeId())),
                    "if an event has an earlier software version "
                            + "and is signed by a node in previous address book, it should be valid");
        }
        assertFalse(
                validator.isEventValid(createEvent(removedNodeId)),
                "the event should be invalid since it is not from the current software version "
                        + "and from a node not in the previous address book.");
        for (final AddressBook ab : List.of(full, reduced)) {
            assertFalse(
                    validator.isEventValid(createEvent(ab.getNextNodeId())),
                    "if an event is signed by a node NOT in any address book, it should not be valid");
        }
    }

    private static @NonNull GossipEvent createEvent(@NonNull final NodeId id) {
        return createEvent(id, null);
    }

    private static @NonNull GossipEvent createEvent(
            @NonNull final NodeId id, @Nullable final SoftwareVersion softwareVersion) {
        final GossipEvent event = Mockito.mock(GossipEvent.class);
        final BaseEventHashedData hd = Mockito.mock(BaseEventHashedData.class);
        final BaseEventUnhashedData ud = Mockito.mock(BaseEventUnhashedData.class);
        final Hash hash = Mockito.mock(Hash.class);
        Mockito.when(hd.getCreatorId()).thenReturn(id);
        Mockito.when(hd.getHash()).thenReturn(hash);
        Mockito.when(hd.getSoftwareVersion()).thenReturn(softwareVersion);
        Mockito.when(event.getHashedData()).thenReturn(hd);
        Mockito.when(event.getUnhashedData()).thenReturn(ud);

        return event;
    }
}
