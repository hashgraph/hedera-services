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

import static org.junit.jupiter.api.Assertions.*;

import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import utils.TestUtils;

class HederaStateTest {
    @Test
    @DisplayName("Adding a null ServiceStateNode will throw an NPE")
    void addingNullServiceStateNodeThrows() {
        final var state = new HederaStateImpl();
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> state.addServiceStateNode(null));
    }

    @Test
    @DisplayName("Adding a ServiceStateNode")
    void addingServiceStateNode() {
        final var state = new HederaStateImpl();
        final var ssn = new ServiceStateNode("test state");
        state.addServiceStateNode(ssn);

        final var opt = state.getServiceStateNode("test state");
        assertTrue(opt.isPresent());
        assertEquals(ssn, opt.get());
    }

    @Test
    @DisplayName("Adding a ServiceStateNode to a HederaStateImpl that has other node types on it")
    void addingServiceStateNodeWhenNonServiceStateNodeChildrenExist() {
        final var state = new HederaStateImpl();
        state.setChild(0, Mockito.mock(MerkleNode.class));
        final var ssn = new ServiceStateNode("test state");
        state.addServiceStateNode(ssn);

        final var opt = state.getServiceStateNode("test state");
        assertTrue(opt.isPresent());
        assertEquals(ssn, opt.get());
    }

    @Test
    @DisplayName("Adding the same ServiceStateNode twice is idempotent")
    void addingServiceStateNodeTwiceIsIdempotent() {
        final var state = new HederaStateImpl();
        final var ssn = new ServiceStateNode("test state");
        state.addServiceStateNode(ssn);
        state.addServiceStateNode(ssn);

        final var opt = state.getServiceStateNode("test state");
        assertTrue(opt.isPresent());
        assertEquals(ssn, opt.get());
    }

    @Test
    @DisplayName("You cannot look for a ServiceStateNode with a null service name")
    void usingNullServiceNameToGetThrows() {
        final var state = new HederaStateImpl();
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> state.getServiceStateNode(null));
    }

    @Test
    @DisplayName("Getting an unknown service name returns an empty optional")
    void getWithUnknownServiceName() {
        final var state = new HederaStateImpl();
        state.setChild(0, Mockito.mock(MerkleNode.class));
        final var ssn = new ServiceStateNode("test state");
        state.addServiceStateNode(ssn);

        assertTrue(state.getServiceStateNode("a bogus name").isEmpty());
    }

    @Test
    @DisplayName("You cannot remove with a null service name")
    void usingNullServiceNameToRemoveThrows() {
        final var state = new HederaStateImpl();
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> state.removeServiceStateNode(null));
    }

    @Test
    @DisplayName("Removing an unknown service name does nothing")
    void removeWithUnknownServiceName() {
        final var state = new HederaStateImpl();
        state.setChild(0, Mockito.mock(MerkleNode.class));
        final var ssn = new ServiceStateNode("test state");
        state.addServiceStateNode(ssn);

        state.removeServiceStateNode("unknown");

        final var opt = state.getServiceStateNode("test state");
        assertTrue(opt.isPresent());
        assertEquals(ssn, opt.get());
    }

    @Test
    @DisplayName("Calling `remove` removes the right ServiceStateNode")
    void remove() {
        // Put a bunch of stuff into the state
        final var state = new HederaStateImpl();
        final var map = new HashMap<String, MerkleNode>();
        for (int i = 0; i < 10; i++) {
            final var serviceName = "Service " + i;
            final var ssn = new ServiceStateNode(serviceName);
            map.put(serviceName, ssn);
            state.addServiceStateNode(ssn);
        }

        // Randomize the order in which they should be removed
        List<String> serviceNames = new ArrayList<>(map.keySet());
        Collections.shuffle(serviceNames, TestUtils.random());

        // Remove the services
        Set<String> removedServiceNames = new HashSet<>();
        for (final var key : serviceNames) {
            removedServiceNames.add(key);
            map.remove(key);
            state.removeServiceStateNode(key);

            for (final var entry : map.entrySet()) {
                final var opt = state.getServiceStateNode(entry.getKey());
                assertTrue(opt.isPresent());
                assertSame(entry.getValue(), opt.get());
            }

            for (final var removedKey : removedServiceNames) {
                assertTrue(state.getServiceStateNode(removedKey).isEmpty());
            }
        }
    }

    @Test
    @DisplayName(
            "Notifications are sent to onHandleConsensusRound whenever handleConsensusRound is"
                    + " called")
    void handleConsensusRoundCallback() {
        final var state = new HederaStateImpl();
        final var round = Mockito.mock(Round.class);
        final var dualState = Mockito.mock(SwirldDualState.class);
        final var called = new AtomicBoolean();
        state.setOnHandleConsensusRound(
                (r, d) -> {
                    assertSame(round, r);
                    assertSame(dualState, d);
                    called.set(true);
                });

        state.handleConsensusRound(round, dualState);
        assertTrue(called.get());
    }

    @Test
    @DisplayName(
            "Notifications are sent to onHandleConsensusRound whenever handleConsensusRound is"
                    + " called")
    void unsetConsensusRoundCallback() {
        final var state = new HederaStateImpl();
        final var called = new AtomicBoolean();
        state.setOnHandleConsensusRound((r, d) -> fail());
        state.setOnHandleConsensusRound(null);

        final var round = Mockito.mock(Round.class);
        final var dualState = Mockito.mock(SwirldDualState.class);
        state.handleConsensusRound(round, dualState);
        assertFalse(called.get());
    }

    @Test
    @DisplayName("Copy and Original have the same services")
    void copy() {
        final var original = new HederaStateImpl();
        original.setChild(0, Mockito.mock(MerkleNode.class));
        final var s1 = new ServiceStateNode("s1");
        s1.put("s1k1", new StringLeaf("s1v1"));
        final var s2 = new ServiceStateNode("s2");
        s1.put("s2k1", new StringLeaf("s2v1"));
        s1.put("s2k2", new StringLeaf("s2v2"));
        final var s3 = new ServiceStateNode("s3");
        s1.put("s3k1", new StringLeaf("s3v1"));
        s1.put("s3k2", new StringLeaf("s3v2"));
        s1.put("s3k3", new StringLeaf("s3v3"));
        final var s4 = new ServiceStateNode("s4");
        s1.put("s4k1", new StringLeaf("s4v1"));
        s1.put("s4k2", new StringLeaf("s4v2"));
        s1.put("s4k3", new StringLeaf("s4v3"));
        s1.put("s4k4", new StringLeaf("s4v4"));
        original.addServiceStateNode(s1);
        original.addServiceStateNode(s2);
        original.addServiceStateNode(s3);
        original.addServiceStateNode(s4);
        original.removeServiceStateNode("s3");

        final List<ServiceStateNode> services = Arrays.asList(s1, s2, s4);

        final var copy = original.copy();
        assertTrue(copy.getServiceStateNode("s3").isEmpty());
        for (final var originalService : services) {
            final var opt = copy.getServiceStateNode(originalService.getServiceName());
            assertTrue(opt.isPresent());

            final var copiedService = opt.get();
            assertEquals(copiedService.getServiceName(), originalService.getServiceName());
            assertEquals(
                    copiedService.getNumberOfChildren(), originalService.getNumberOfChildren());

            for (int i = 0; i < originalService.getNumberOfChildren(); i++) {
                final var child = originalService.getChild(i);
                if (child instanceof ServiceStateNode ssn) {
                    assertEquals(copiedService.find(ssn.getServiceName()), ssn);
                }
            }
        }
    }

    @Test
    @DisplayName(
            "When a copy is made, the original loses the onConsensusRoundCallback, and the copy"
                    + " gains it")
    void originalLosesConsensusRoundCallbackAfterCopy() {
        final var original = new HederaStateImpl();
        final var called = new AtomicBoolean();
        original.setOnHandleConsensusRound((r, d) -> called.set(true));

        final var copy = original.copy();

        // The original no longer has the listener
        final var round = Mockito.mock(Round.class);
        final var dualState = Mockito.mock(SwirldDualState.class);
        original.handleConsensusRound(round, dualState);
        assertFalse(called.get());

        // But the copy does
        copy.handleConsensusRound(round, dualState);
        assertTrue(called.get());
    }

    @Test
    @DisplayName("Cannot call copy on original after copy")
    void callCopyTwiceOnOriginalThrows() {
        final var original = new HederaStateImpl();
        original.copy();
        assertThrows(MutabilityException.class, original::copy);
    }

    @Test
    @DisplayName("Cannot call setOnHandleConsensusRound on original after copy")
    void setCallbackOnOriginalAfterCopyThrows() {
        final var original = new HederaStateImpl();
        original.copy();
        assertThrows(
                MutabilityException.class, () -> original.setOnHandleConsensusRound((r, s) -> {}));
    }

    @Test
    @DisplayName("Cannot call addServiceStateNode on original after copy")
    void addServiceOnOriginalAfterCopyThrows() {
        final var original = new HederaStateImpl();
        original.copy();
        final var ssn = new ServiceStateNode("nope");
        assertThrows(MutabilityException.class, () -> original.addServiceStateNode(ssn));
    }

    @Test
    @DisplayName("Cannot call removeServiceStateNode on original after copy")
    void removeServiceOnOriginalAfterCopyThrows() {
        final var original = new HederaStateImpl();
        final var ssn = new ServiceStateNode("nope");
        original.addServiceStateNode(ssn);
        original.copy();
        assertThrows(MutabilityException.class, () -> original.removeServiceStateNode("nope"));
    }

    @Test
    @DisplayName("Cannot call createWritableStates on original after copy")
    void createWritableStatesOnOriginalAfterCopyThrows() {
        final var original = new HederaStateImpl();
        original.copy();
        assertThrows(MutabilityException.class, () -> original.createWritableStates("nope"));
    }
}
