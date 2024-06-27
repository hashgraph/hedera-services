/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.spi.fixtures.state.TestSchema;
import com.swirlds.base.state.MutabilityException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.merkle.StateUtils;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableQueueState;
import com.swirlds.state.spi.WritableSingletonState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerkleHederaStateTest extends MerkleTestBase {
    /** The merkle tree we will test with */
    private MerkleHederaState hederaMerkle;

    private final AtomicBoolean onPreHandleCalled = new AtomicBoolean(false);
    private final AtomicBoolean onHandleCalled = new AtomicBoolean(false);
    private final AtomicBoolean onUpdateWeightCalled = new AtomicBoolean(false);

    private final HederaLifecycles lifecycles = new HederaLifecycles() {
        @Override
        public void onPreHandle(@NonNull Event event, @NonNull HederaState state) {
            onPreHandleCalled.set(true);
        }

        @Override
        public void onNewRecoveredState(@NonNull MerkleHederaState recoveredState) {
            // No-op
        }

        @Override
        public void onHandleConsensusRound(
                @NonNull Round round, @NonNull PlatformState platformState, @NonNull HederaState state) {
            onHandleCalled.set(true);
        }

        @Override
        public void onStateInitialized(
                @NonNull HederaState state,
                @NonNull Platform platform,
                @NonNull PlatformState platformState,
                @NonNull InitTrigger trigger,
                @Nullable SoftwareVersion previousVersion) {}

        @Override
        public void onUpdateWeight(
                @NonNull MerkleHederaState state,
                @NonNull AddressBook configAddressBook,
                @NonNull PlatformContext context) {
            onUpdateWeightCalled.set(true);
        }
    };

    /**
     * Start with an empty Merkle Tree, but with the "fruit" map and metadata created and ready to
     * be added.
     */
    @BeforeEach
    void setUp() {
        setupFruitMerkleMap();
        hederaMerkle = new MerkleHederaState(lifecycles);
    }

    /** Looks for a merkle node with the given label */
    MerkleNode getNodeForLabel(String label) {
        return getNodeForLabel(hederaMerkle, label);
    }

    @Nested
    @DisplayName("Service Registration Tests")
    final class RegistrationTest {
        @Test
        @DisplayName("Adding a null service metadata will throw an NPE")
        void addingNullServiceMetaDataThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(() -> hederaMerkle.putServiceStateIfAbsent(null, () -> fruitMerkleMap))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Adding a null service node will throw an NPE")
        void addingNullServiceNodeThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(() -> hederaMerkle.putServiceStateIfAbsent(fruitMetadata, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Adding a service node that is not Labeled throws IAE")
        void addingWrongKindOfNodeThrows() {
            assertThatThrownBy(() ->
                            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> Mockito.mock(MerkleNode.class)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Adding a service node without a label throws IAE")
        void addingNodeWithNoLabelThrows() {
            final var fruitNodeNoLabel = Mockito.mock(MerkleMap.class);
            Mockito.when(fruitNodeNoLabel.getLabel()).thenReturn(null);
            assertThatThrownBy(() -> hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitNodeNoLabel))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Adding a service node with a label that doesn't match service name and state key throws IAE")
        void addingBadServiceNodeNameThrows() {
            fruitMerkleMap.setLabel("Some Random Label");
            assertThatThrownBy(() -> hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Adding a service")
        void addingService() {
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
        }

        @Test
        @DisplayName("Adding a service with VirtualMap")
        void addingVirtualMapService() {
            // Given a virtual map
            setupFruitVirtualMap();

            // When added to the merkle tree
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitVirtualMap);

            // Then we can see it is on the tree
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitVirtualMap);
        }

        @Test
        @DisplayName("Adding a service with a Singleton node")
        void addingSingletonService() {
            // Given a singleton node
            setupSingletonCountry();

            // When added to the merkle tree
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);

            // Then we can see it is on the tree
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(countryLabel)).isSameAs(countrySingleton);
        }

        @Test
        @DisplayName("Adding a service with a Queue node")
        void addingQueueService() {
            // Given a queue node
            setupSteamQueue();

            // When added to the merkle tree
            hederaMerkle.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // Then we can see it is on the tree
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(steamLabel)).isSameAs(steamQueue);
        }

        @Test
        @DisplayName("Adding a service to a MerkleHederaState that has other node types on it")
        void addingServiceWhenNonServiceNodeChildrenExist() {
            hederaMerkle.setChild(0, Mockito.mock(MerkleNode.class));
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(2);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
        }

        @Test
        @DisplayName("Adding the same service twice is idempotent")
        void addingServiceTwiceIsIdempotent() {
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
        }

        @Test
        @DisplayName("Adding the same service twice with two different nodes causes the original node to remain")
        void addingServiceTwiceWithDifferentNodesDoesNotReplaceFirstNode() {
            // Given an empty merkle tree, when I add the same metadata twice but with different
            // nodes,
            final var map2 = createMerkleMap(fruitMerkleMap.getLabel());
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> map2);

            // Then the original node is kept and the second node ignored
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
        }

        @Test
        @DisplayName("Adding the same service node twice with two different metadata replaces the" + " metadata")
        void addingServiceTwiceWithDifferentMetadata() {
            // Given an empty merkle tree, when I add the same node twice but with different
            // metadata,
            final var fruitMetadata2 = new StateMetadata<>(
                    FIRST_SERVICE,
                    new TestSchema(1),
                    StateDefinition.inMemory(FRUIT_STATE_KEY, STRING_CODEC, LONG_CODEC, FRUIT_PROTO_FIELD));

            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata2, () -> fruitMerkleMap);

            // Then the original node is kept and the second node ignored
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);

            // NOTE: I don't have a good way to test that the metadata is intact...
        }

        @Test
        @DisplayName("Adding non-VirtualMap merkle node with on-disk metadata throws")
        void merkleMapWithOnDiskThrows() {
            setupFruitVirtualMap();
            assertThatThrownBy(() -> hederaMerkle.putServiceStateIfAbsent(fruitVirtualMetadata, () -> fruitMerkleMap))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Mismatch");
        }
    }

    @Nested
    @DisplayName("Remove Tests")
    final class RemoveTest {
        @Test
        @DisplayName("You cannot remove with a null service name")
        void usingNullServiceNameToRemoveThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(() -> hederaMerkle.removeServiceState(null, FRUIT_STATE_KEY))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("You cannot remove with a null state key")
        void usingNullStateKeyToRemoveThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(() -> hederaMerkle.removeServiceState(FIRST_SERVICE, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Removing an unknown service name does nothing")
        void removeWithUnknownServiceName() {
            // Given a tree with a random node, and a service node
            hederaMerkle.setChild(0, Mockito.mock(MerkleNode.class));
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            final var numChildren = hederaMerkle.getNumberOfChildren();

            // When you try to remove an unknown service
            hederaMerkle.removeServiceState(UNKNOWN_SERVICE, FRUIT_STATE_KEY);

            // It has no effect on anything
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(numChildren);
        }

        @Test
        @DisplayName("Removing an unknown state key does nothing")
        void removeWithUnknownStateKey() {
            // Given a tree with a random node, and a service node
            hederaMerkle.setChild(0, Mockito.mock(MerkleNode.class));
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            final var numChildren = hederaMerkle.getNumberOfChildren();

            // When you try to remove an unknown state key
            hederaMerkle.removeServiceState(FIRST_SERVICE, UNKNOWN_STATE_KEY);

            // It has no effect on anything
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(numChildren);
        }

        @Test
        @DisplayName("Calling `remove` removes the right service")
        void remove() {
            // Put a bunch of stuff into the state
            final var map = new HashMap<String, MerkleNode>();
            for (int i = 0; i < 10; i++) {
                final var serviceName = "Service_" + i;
                final var label = StateUtils.computeLabel(serviceName, FRUIT_STATE_KEY);
                final var md = new StateMetadata<>(
                        serviceName,
                        new TestSchema(1),
                        StateDefinition.inMemory(FRUIT_STATE_KEY, STRING_CODEC, STRING_CODEC, FRUIT_PROTO_FIELD));

                final var node = createMerkleMap(label);
                map.put(serviceName, node);
                hederaMerkle.putServiceStateIfAbsent(md, () -> node);
            }

            // Randomize the order in which they should be removed
            final List<String> serviceNames = new ArrayList<>(map.keySet());
            Collections.shuffle(serviceNames, random());

            // Remove the services
            final Set<String> removedServiceNames = new HashSet<>();
            for (final var serviceName : serviceNames) {
                removedServiceNames.add(serviceName);
                map.remove(serviceName);
                hederaMerkle.removeServiceState(serviceName, FRUIT_STATE_KEY);

                // Verify everything OTHER THAN the removed service node is still present
                for (final var entry : map.entrySet()) {
                    final var label = StateUtils.computeLabel(entry.getKey(), FRUIT_STATE_KEY);
                    assertThat(getNodeForLabel(label)).isSameAs(entry.getValue());
                }

                // Verify NONE OF THE REMOVED SERVICES have a node still present
                for (final var removedKey : removedServiceNames) {
                    final var label = StateUtils.computeLabel(removedKey, FRUIT_STATE_KEY);
                    assertThat(getNodeForLabel(label)).isNull();
                }
            }
        }
    }

    @Nested
    @DisplayName("ReadableStates Tests")
    final class ReadableStatesTest {

        @BeforeEach
        void setUp() {
            setupAnimalMerkleMap();
            setupSingletonCountry();
            setupSteamQueue();

            add(fruitMerkleMap, fruitMetadata, A_KEY, APPLE);
            add(fruitMerkleMap, fruitMetadata, B_KEY, BANANA);
            add(animalMerkleMap, animalMetadata, C_KEY, CUTTLEFISH);
            add(animalMerkleMap, animalMetadata, D_KEY, DOG);
            add(animalMerkleMap, animalMetadata, F_KEY, FOX);
            countrySingleton.setValue(GHANA);
            steamQueue.add(ART);
        }

        @Test
        @DisplayName("Getting ReadableStates on an unknown service returns an empty entity")
        void unknownServiceNameUsingReadableStates() {
            final var states = hederaMerkle.getReadableStates(UNKNOWN_SERVICE);
            assertThat(states).isNotNull();
            assertThat(states.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Reading an unknown state on ReadableStates should throw IAE")
        void unknownState() {
            // Given a HederaState with the fruit and animal and country states
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            hederaMerkle.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // When we get the ReadableStates
            final var states = hederaMerkle.getReadableStates(FIRST_SERVICE);

            // Then query it for an unknown state and get an IAE
            assertThatThrownBy(() -> states.get(UNKNOWN_STATE_KEY)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Read a virtual map")
        void readVirtualMap() {
            // Given a HederaState with the fruit virtual map
            setupFruitVirtualMap();
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitVirtualMap);

            // When we get the ReadableStates
            final var states = hederaMerkle.getReadableStates(FIRST_SERVICE);

            // Then it isn't null
            assertThat(states.get(FRUIT_STATE_KEY)).isNotNull();
        }

        @Test
        @DisplayName("Try to read a state that is MISSING from the merkle tree")
        void readMissingState() {
            // Given a HederaState with the fruit merkle map, which somehow has
            // lost the merkle node (this should NEVER HAPPEN in real life!)
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            hederaMerkle.setChild(0, null);

            // When we get the ReadableStates
            final var states = hederaMerkle.getReadableStates(FIRST_SERVICE);

            // Then try to read the state and find it is missing!
            assertThatThrownBy(() -> states.get(FRUIT_STATE_KEY)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Contains is true for all states in stateKeys and false for unknown ones")
        void contains() {
            // Given a HederaState with the fruit and animal and country and steam states
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            hederaMerkle.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // When we get the ReadableStates and the state keys
            final var states = hederaMerkle.getReadableStates(FIRST_SERVICE);
            final var stateKeys = states.stateKeys();

            // Then we find "contains" is true for every state in stateKeys
            assertThat(stateKeys).hasSize(4);
            for (final var stateKey : stateKeys) {
                assertThat(states.contains(stateKey)).isTrue();
            }

            // And we find other nonsense states are false for contains
            assertThat(states.contains(UNKNOWN_STATE_KEY)).isFalse();
        }

        @Test
        @DisplayName("Getting the same readable state twice returns the same instance")
        void getReturnsSameInstanceIfCalledTwice() {
            // Given a HederaState with the fruit and the ReadableStates for it
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            final var states = hederaMerkle.getReadableStates(FIRST_SERVICE);

            // When we call get twice
            final var kvState1 = states.get(FRUIT_STATE_KEY);
            final var kvState2 = states.get(FRUIT_STATE_KEY);

            // Then we must find both variables are the same instance
            assertThat(kvState1).isSameAs(kvState2);
        }

        @Test
        @DisplayName("Getting ReadableStates on a known service returns an object with all the state")
        void knownServiceNameUsingReadableStates() {
            // Given a HederaState with the fruit and animal and country states
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            hederaMerkle.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // When we get the ReadableStates
            final var states = hederaMerkle.getReadableStates(FIRST_SERVICE);

            // Then query it, we find the data we expected to find
            assertThat(states).isNotNull();
            assertThat(states.isEmpty()).isFalse();
            assertThat(states.size()).isEqualTo(4); // animal and fruit and country and steam

            final ReadableKVState<String, String> fruitState = states.get(FRUIT_STATE_KEY);
            assertFruitState(fruitState);

            final ReadableKVState<String, String> animalState = states.get(ANIMAL_STATE_KEY);
            assertAnimalState(animalState);

            final ReadableSingletonState<String> countryState = states.getSingleton(COUNTRY_STATE_KEY);
            assertCountryState(countryState);

            final ReadableQueueState<String> steamState = states.getQueue(STEAM_STATE_KEY);
            assertSteamState(steamState);

            // And the states we got back CANNOT be cast to WritableState
            assertThatThrownBy(
                            () -> { //noinspection rawtypes
                                final var ignored = (WritableKVState) fruitState;
                            })
                    .isInstanceOf(ClassCastException.class);

            assertThatThrownBy(
                            () -> { //noinspection rawtypes
                                final var ignored = (WritableKVState) animalState;
                            })
                    .isInstanceOf(ClassCastException.class);

            assertThatThrownBy(
                            () -> { //noinspection rawtypes
                                final var ignored = (WritableSingletonState) countryState;
                            })
                    .isInstanceOf(ClassCastException.class);

            assertThatThrownBy(
                            () -> { //noinspection rawtypes
                                final var ignored = (WritableQueueState) steamState;
                            })
                    .isInstanceOf(ClassCastException.class);
        }

        private static void assertFruitState(ReadableKVState<String, String> fruitState) {
            assertThat(fruitState).isNotNull();
            assertThat(fruitState.get(A_KEY)).isSameAs(APPLE);
            assertThat(fruitState.get(B_KEY)).isSameAs(BANANA);
            assertThat(fruitState.get(C_KEY)).isNull();
            assertThat(fruitState.get(D_KEY)).isNull();
            assertThat(fruitState.get(E_KEY)).isNull();
            assertThat(fruitState.get(F_KEY)).isNull();
            assertThat(fruitState.get(G_KEY)).isNull();
        }

        private void assertAnimalState(ReadableKVState<String, String> animalState) {
            assertThat(animalState).isNotNull();
            assertThat(animalState.get(A_KEY)).isNull();
            assertThat(animalState.get(B_KEY)).isNull();
            assertThat(animalState.get(C_KEY)).isSameAs(CUTTLEFISH);
            assertThat(animalState.get(D_KEY)).isSameAs(DOG);
            assertThat(animalState.get(E_KEY)).isNull();
            assertThat(animalState.get(F_KEY)).isSameAs(FOX);
            assertThat(animalState.get(G_KEY)).isNull();
        }

        private void assertCountryState(ReadableSingletonState<String> countryState) {
            assertThat(countryState.getStateKey()).isEqualTo(COUNTRY_STATE_KEY);
            assertThat(countryState.get()).isEqualTo(GHANA);
        }

        private void assertSteamState(ReadableQueueState<String> steamState) {
            assertThat(steamState.getStateKey()).isEqualTo(STEAM_STATE_KEY);
            assertThat(steamState.peek()).isEqualTo(ART);
        }
    }

    @Nested
    @DisplayName("WritableStates Tests")
    final class WritableStatesTest {

        @BeforeEach
        void setUp() {
            setupAnimalMerkleMap();
            setupSingletonCountry();
            setupSteamQueue();

            add(fruitMerkleMap, fruitMetadata, A_KEY, APPLE);
            add(fruitMerkleMap, fruitMetadata, B_KEY, BANANA);
            add(animalMerkleMap, animalMetadata, C_KEY, CUTTLEFISH);
            add(animalMerkleMap, animalMetadata, D_KEY, DOG);
            add(animalMerkleMap, animalMetadata, F_KEY, FOX);
            countrySingleton.setValue(FRANCE);
            steamQueue.add(ART);
        }

        @Test
        @DisplayName("Getting WritableStates on an unknown service returns an empty entity")
        void unknownServiceNameUsingWritableStates() {
            final var states = hederaMerkle.getWritableStates(UNKNOWN_SERVICE);
            assertThat(states).isNotNull();
            assertThat(states.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Reading an unknown state on WritableState should throw IAE")
        void unknownState() {
            // Given a HederaState with the fruit and animal and country states
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            hederaMerkle.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // When we get the WritableStates
            final var states = hederaMerkle.getWritableStates(FIRST_SERVICE);

            // Then query it for an unknown state and get an IAE
            assertThatThrownBy(() -> states.get(UNKNOWN_STATE_KEY)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Try to read a state that is MISSING from the merkle tree")
        void readMissingState() {
            // Given a HederaState with the fruit virtual map, which somehow has
            // lost the merkle node (this should NEVER HAPPEN in real life!)
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            hederaMerkle.setChild(0, null);

            // When we get the WritableStates
            final var states = hederaMerkle.getWritableStates(FIRST_SERVICE);

            // Then try to read the state and find it is missing!
            assertThatThrownBy(() -> states.get(FRUIT_STATE_KEY)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Read a virtual map")
        void readVirtualMap() {
            // Given a HederaState with the fruit virtual map
            setupFruitVirtualMap();
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitVirtualMap);

            // When we get the WritableStates
            final var states = hederaMerkle.getWritableStates(FIRST_SERVICE);

            // Then it isn't null
            assertThat(states.get(FRUIT_STATE_KEY)).isNotNull();
        }

        @Test
        @DisplayName("Contains is true for all states in stateKeys and false for unknown ones")
        void contains() {
            // Given a HederaState with the fruit and animal and country states
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            hederaMerkle.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // When we get the WritableStates and the state keys
            final var states = hederaMerkle.getWritableStates(FIRST_SERVICE);
            final var stateKeys = states.stateKeys();

            // Then we find "contains" is true for every state in stateKeys
            assertThat(stateKeys).hasSize(4);
            for (final var stateKey : stateKeys) {
                assertThat(states.contains(stateKey)).isTrue();
            }

            // And we find other nonsense states are false for contains
            assertThat(states.contains(UNKNOWN_STATE_KEY)).isFalse();
        }

        @Test
        @DisplayName("Getting the same writable state twice returns the same instance")
        void getReturnsSameInstanceIfCalledTwice() {
            // Given a HederaState with the fruit and the WritableStates for it
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            final var states = hederaMerkle.getWritableStates(FIRST_SERVICE);

            // When we call get twice
            final var kvState1 = states.get(FRUIT_STATE_KEY);
            final var kvState2 = states.get(FRUIT_STATE_KEY);

            // Then we must find both variables are the same instance
            assertThat(kvState1).isSameAs(kvState2);
        }

        @Test
        @DisplayName("Getting WritableStates on a known service returns an object with all the state")
        void knownServiceNameUsingWritableStates() {
            // Given a HederaState with the fruit and animal and country states
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            hederaMerkle.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // When we get the WritableStates
            final var states = hederaMerkle.getWritableStates(FIRST_SERVICE);

            // We find the data we expected to find
            assertThat(states).isNotNull();
            assertThat(states.isEmpty()).isFalse();
            assertThat(states.size()).isEqualTo(4);

            final WritableKVState<String, String> fruitStates = states.get(FRUIT_STATE_KEY);
            assertThat(fruitStates).isNotNull();

            final var animalStates = states.get(ANIMAL_STATE_KEY);
            assertThat(animalStates).isNotNull();

            final var countryState = states.getSingleton(COUNTRY_STATE_KEY);
            assertThat(countryState).isNotNull();

            final var steamState = states.getQueue(STEAM_STATE_KEY);
            assertThat(steamState).isNotNull();

            // And the states we got back are writable
            fruitStates.put(C_KEY, CHERRY);
            assertThat(fruitStates.get(C_KEY)).isSameAs(CHERRY);
            countryState.put(ESTONIA);
            assertThat(countryState.get()).isEqualTo(ESTONIA);
        }
    }

    @Nested
    @DisplayName("Handling Pre-Handle Tests")
    final class PreHandleTest {
        @Test
        @DisplayName("The onPreHandle handler is called when a pre-handle happens")
        void onPreHandleCalled() {
            assertThat(onPreHandleCalled).isFalse();
            hederaMerkle.preHandle(Mockito.mock(Event.class));
            assertThat(onPreHandleCalled).isTrue();
        }
    }

    @Nested
    @DisplayName("Handling Consensus Rounds Tests")
    final class ConsensusRoundTest {
        @Test
        @DisplayName("Notifications are sent to onHandleConsensusRound when handleConsensusRound is called")
        void handleConsensusRoundCallback() {
            final var round = Mockito.mock(Round.class);
            final var platformState = Mockito.mock(PlatformState.class);
            final var state = new MerkleHederaState(lifecycles);

            state.handleConsensusRound(round, platformState);
            assertThat(onHandleCalled).isTrue();
        }
    }

    @Nested
    @DisplayName("Copy Tests")
    final class CopyTest {
        @Test
        @DisplayName("When a copy is made, the original loses the onConsensusRoundCallback, and the copy gains it")
        void originalLosesConsensusRoundCallbackAfterCopy() {
            final var copy = hederaMerkle.copy();

            // The original no longer has the listener
            final var round = Mockito.mock(Round.class);
            final var platformState = Mockito.mock(PlatformState.class);
            assertThrows(MutabilityException.class, () -> hederaMerkle.handleConsensusRound(round, platformState));

            // But the copy does
            copy.handleConsensusRound(round, platformState);
            assertThat(onHandleCalled).isTrue();
        }

        @Test
        @DisplayName("Cannot call copy on original after copy")
        void callCopyTwiceOnOriginalThrows() {
            hederaMerkle.copy();
            assertThatThrownBy(hederaMerkle::copy).isInstanceOf(MutabilityException.class);
        }

        @Test
        @DisplayName("Cannot call putServiceStateIfAbsent on original after copy")
        void addServiceOnOriginalAfterCopyThrows() {
            setupAnimalMerkleMap();
            hederaMerkle.copy();
            assertThatThrownBy(() -> hederaMerkle.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap))
                    .isInstanceOf(MutabilityException.class);
        }

        @Test
        @DisplayName("Cannot call removeServiceState on original after copy")
        void removeServiceOnOriginalAfterCopyThrows() {
            setupAnimalMerkleMap();
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            hederaMerkle.copy();
            assertThatThrownBy(() -> hederaMerkle.removeServiceState(FIRST_SERVICE, ANIMAL_STATE_KEY))
                    .isInstanceOf(MutabilityException.class);
        }

        @Test
        @DisplayName("Cannot call createWritableStates on original after copy")
        void createWritableStatesOnOriginalAfterCopyThrows() {
            hederaMerkle.copy();
            assertThatThrownBy(() -> hederaMerkle.getWritableStates(FRUIT_STATE_KEY))
                    .isInstanceOf(MutabilityException.class);
        }
    }

    @Nested
    @DisplayName("Handling updateWeight Tests")
    final class UpdateWeightTest {
        @Test
        @DisplayName("The onUpdateWeight handler is called when a updateWeight is called")
        void onUpdateWeightCalled() {
            assertThat(onUpdateWeightCalled).isFalse();
            hederaMerkle.updateWeight(Mockito.mock(AddressBook.class), Mockito.mock(PlatformContext.class));
            assertThat(onUpdateWeightCalled).isTrue();
        }
    }
}
