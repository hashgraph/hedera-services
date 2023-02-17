/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.events.Event;
import com.swirlds.merkle.map.MerkleMap;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MerkleHederaStateTest extends MerkleTestBase {
    /** The merkle tree we will test with */
    private MerkleHederaState hederaMerkle;

    private final AtomicBoolean onMigrateCalled = new AtomicBoolean(false);
    private final AtomicBoolean onPreHandleCalled = new AtomicBoolean(false);
    private final AtomicBoolean onHandleCalled = new AtomicBoolean(false);

    /**
     * Start with an empty Merkle Tree, but with the "fruit" map and metadata created and ready to
     * be added.
     */
    @BeforeEach
    void setUp() {
        setupFruitMerkleMap();
        hederaMerkle = new MerkleHederaState(
                tree -> onMigrateCalled.set(true),
                (evt, meta, provider) -> onPreHandleCalled.set(true),
                (round, dual, metadata) -> onHandleCalled.set(true));
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
            assertThatThrownBy(() -> hederaMerkle.putServiceStateIfAbsent(null, fruitMerkleMap))
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
            assertThatThrownBy(
                            () -> hederaMerkle.putServiceStateIfAbsent(fruitMetadata, Mockito.mock(MerkleNode.class)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Adding a service node without a label throws IAE")
        void addingNodeWithNoLabelThrows() {
            final var fruitNodeNoLabel = Mockito.mock(MerkleMap.class);
            Mockito.when(fruitNodeNoLabel.getLabel()).thenReturn(null);
            assertThatThrownBy(() -> hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitNodeNoLabel))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Adding a service node with a label that doesn't match service name and state key" + " throws IAE")
        void addingBadServiceNodeNameThrows() {
            fruitMerkleMap.setLabel("Some Random Label");
            assertThatThrownBy(() -> hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Adding a service")
        void addingService() {
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
        }

        @Test
        @DisplayName("Adding a service with VirtualMap")
        void addingVirtualMapService() {
            // Given a virtual map
            setupFruitVirtualMap();

            // When added to the merkle tree
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitVirtualMap);

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
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, countrySingleton);

            // Then we can see it is on the tree
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(countryLabel)).isSameAs(countrySingleton);
        }

        @Test
        @DisplayName("Adding a service to a MerkleHederaState that has other node types on it")
        void addingServiceWhenNonServiceNodeChildrenExist() {
            hederaMerkle.setChild(0, Mockito.mock(MerkleNode.class));
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(2);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
        }

        @Test
        @DisplayName("Adding the same service twice is idempotent")
        void addingServiceTwiceIsIdempotent() {
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
        }

        @Test
        @DisplayName("Adding the same service twice with two different nodes causes the original node to" + " remain")
        void addingServiceTwiceWithDifferentNodesDoesNotReplaceFirstNode() {
            // Given an empty merkle tree, when I add the same metadata twice but with different
            // nodes,
            final var map2 = createMerkleMap(fruitMerkleMap.getLabel());
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, map2);

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
                    StateDefinition.inMemory(FRUIT_STATE_KEY, STRING_SERDES, LONG_SERDES));

            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata2, fruitMerkleMap);

            // Then the original node is kept and the second node ignored
            assertThat(hederaMerkle.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);

            // NOTE: I don't have a good way to test that the metadata is intact...
        }

        @Test
        @DisplayName("Adding non-VirtualMap merkle node with on-disk metadata throws")
        void merkleMapWithOnDiskThrows() {
            setupFruitVirtualMap();
            assertThatThrownBy(() -> hederaMerkle.putServiceStateIfAbsent(fruitVirtualMetadata, fruitMerkleMap))
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
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
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
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
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
                        StateDefinition.inMemory(FRUIT_STATE_KEY, STRING_SERDES, STRING_SERDES));

                final var node = createMerkleMap(label);
                map.put(serviceName, node);
                hederaMerkle.putServiceStateIfAbsent(md, node);
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

            add(fruitMerkleMap, fruitMetadata, A_KEY, APPLE);
            add(fruitMerkleMap, fruitMetadata, B_KEY, BANANA);
            add(animalMerkleMap, animalMetadata, C_KEY, CUTTLEFISH);
            add(animalMerkleMap, animalMetadata, D_KEY, DOG);
            add(animalMerkleMap, animalMetadata, F_KEY, FOX);
            countrySingleton.setValue(GHANA);
        }

        @Test
        @DisplayName("Getting ReadableStates on an unknown service returns an empty entity")
        void unknownServiceNameUsingReadableStates() {
            final var states = hederaMerkle.createReadableStates(UNKNOWN_SERVICE);
            assertThat(states).isNotNull();
            assertThat(states.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Reading an unknown state on ReadableStates should throw IAE")
        void unknownState() {
            // Given a HederaState with the fruit and animal and country states
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, animalMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, countrySingleton);

            // When we get the ReadableStates
            final var states = hederaMerkle.createReadableStates(FIRST_SERVICE);

            // Then query it for an unknown state and get an IAE
            assertThatThrownBy(() -> states.get(UNKNOWN_STATE_KEY)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Read a virtual map")
        void readVirtualMap() {
            // Given a HederaState with the fruit virtual map
            setupFruitVirtualMap();
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitVirtualMap);

            // When we get the ReadableStates
            final var states = hederaMerkle.createReadableStates(FIRST_SERVICE);

            // Then it isn't null
            assertThat(states.get(FRUIT_STATE_KEY)).isNotNull();
        }

        @Test
        @DisplayName("Try to read a state that is MISSING from the merkle tree")
        void readMissingState() {
            // Given a HederaState with the fruit merkle map, which somehow has
            // lost the merkle node (this should NEVER HAPPEN in real life!)
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            hederaMerkle.setChild(0, null);

            // When we get the ReadableStates
            final var states = hederaMerkle.createReadableStates(FIRST_SERVICE);

            // Then try to read the state and find it is missing!
            assertThatThrownBy(() -> states.get(FRUIT_STATE_KEY)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Contains is true for all states in stateKeys and false for unknown ones")
        void contains() {
            // Given a HederaState with the fruit and animal and country states
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, animalMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, countrySingleton);

            // When we get the ReadableStates and the state keys
            final var states = hederaMerkle.createReadableStates(FIRST_SERVICE);
            final var stateKeys = states.stateKeys();

            // Then we find "contains" is true for every state in stateKeys
            assertThat(stateKeys).hasSize(3);
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
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            final var states = hederaMerkle.createReadableStates(FIRST_SERVICE);

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
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, animalMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, countrySingleton);

            // When we get the ReadableStates
            final var states = hederaMerkle.createReadableStates(FIRST_SERVICE);

            // Then query it, we find the data we expected to find
            assertThat(states).isNotNull();
            assertThat(states.isEmpty()).isFalse();
            assertThat(states.size()).isEqualTo(3); // animal and fruit and country

            final ReadableKVState<String, String> fruitStates = states.get(FRUIT_STATE_KEY);
            assertThat(fruitStates).isNotNull();
            assertThat(fruitStates.get(A_KEY)).isSameAs(APPLE);
            assertThat(fruitStates.get(B_KEY)).isSameAs(BANANA);
            assertThat(fruitStates.get(C_KEY)).isNull();
            assertThat(fruitStates.get(D_KEY)).isNull();
            assertThat(fruitStates.get(E_KEY)).isNull();
            assertThat(fruitStates.get(F_KEY)).isNull();
            assertThat(fruitStates.get(G_KEY)).isNull();

            final ReadableKVState<String, String> animalStates = states.get(ANIMAL_STATE_KEY);
            assertThat(animalStates).isNotNull();
            assertThat(animalStates.get(A_KEY)).isNull();
            assertThat(animalStates.get(B_KEY)).isNull();
            assertThat(animalStates.get(C_KEY)).isSameAs(CUTTLEFISH);
            assertThat(animalStates.get(D_KEY)).isSameAs(DOG);
            assertThat(animalStates.get(E_KEY)).isNull();
            assertThat(animalStates.get(F_KEY)).isSameAs(FOX);
            assertThat(animalStates.get(G_KEY)).isNull();

            final ReadableSingletonState<String> countryState = states.getSingleton(COUNTRY_STATE_KEY);
            assertThat(countryState.getStateKey()).isEqualTo(COUNTRY_STATE_KEY);
            assertThat(countryState.get()).isEqualTo(GHANA);

            // And the states we got back CANNOT be cast to WritableState
            assertThatThrownBy(
                            () -> { //noinspection rawtypes
                                final var ignored = (WritableKVState) fruitStates;
                            })
                    .isInstanceOf(ClassCastException.class);

            assertThatThrownBy(
                            () -> { //noinspection rawtypes
                                final var ignored = (WritableKVState) animalStates;
                            })
                    .isInstanceOf(ClassCastException.class);

            assertThatThrownBy(
                            () -> { //noinspection rawtypes
                                final var ignored = (WritableSingletonState) countryState;
                            })
                    .isInstanceOf(ClassCastException.class);
        }
    }

    @Nested
    @DisplayName("WritableStates Tests")
    final class WritableStatesTest {

        @BeforeEach
        void setUp() {
            setupAnimalMerkleMap();
            setupSingletonCountry();

            add(fruitMerkleMap, fruitMetadata, A_KEY, APPLE);
            add(fruitMerkleMap, fruitMetadata, B_KEY, BANANA);
            add(animalMerkleMap, animalMetadata, C_KEY, CUTTLEFISH);
            add(animalMerkleMap, animalMetadata, D_KEY, DOG);
            add(animalMerkleMap, animalMetadata, F_KEY, FOX);
            countrySingleton.setValue(FRANCE);
        }

        @Test
        @DisplayName("Getting WritableStates on an unknown service returns an empty entity")
        void unknownServiceNameUsingWritableStates() {
            final var states = hederaMerkle.createWritableStates(UNKNOWN_SERVICE);
            assertThat(states).isNotNull();
            assertThat(states.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Reading an unknown state on WritableState should throw IAE")
        void unknownState() {
            // Given a HederaState with the fruit and animal and country states
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, animalMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, countrySingleton);

            // When we get the WritableStates
            final var states = hederaMerkle.createWritableStates(FIRST_SERVICE);

            // Then query it for an unknown state and get an IAE
            assertThatThrownBy(() -> states.get(UNKNOWN_STATE_KEY)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Try to read a state that is MISSING from the merkle tree")
        void readMissingState() {
            // Given a HederaState with the fruit virtual map, which somehow has
            // lost the merkle node (this should NEVER HAPPEN in real life!)
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            hederaMerkle.setChild(0, null);

            // When we get the WritableStates
            final var states = hederaMerkle.createWritableStates(FIRST_SERVICE);

            // Then try to read the state and find it is missing!
            assertThatThrownBy(() -> states.get(FRUIT_STATE_KEY)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Read a virtual map")
        void readVirtualMap() {
            // Given a HederaState with the fruit virtual map
            setupFruitVirtualMap();
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitVirtualMap);

            // When we get the WritableStates
            final var states = hederaMerkle.createWritableStates(FIRST_SERVICE);

            // Then it isn't null
            assertThat(states.get(FRUIT_STATE_KEY)).isNotNull();
        }

        @Test
        @DisplayName("Contains is true for all states in stateKeys and false for unknown ones")
        void contains() {
            // Given a HederaState with the fruit and animal and country states
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, animalMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, countrySingleton);

            // When we get the WritableStates and the state keys
            final var states = hederaMerkle.createWritableStates(FIRST_SERVICE);
            final var stateKeys = states.stateKeys();

            // Then we find "contains" is true for every state in stateKeys
            assertThat(stateKeys).hasSize(3);
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
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            final var states = hederaMerkle.createWritableStates(FIRST_SERVICE);

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
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, animalMerkleMap);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, countrySingleton);

            // When we get the WritableStates
            final var states = hederaMerkle.createWritableStates(FIRST_SERVICE);

            // We find the data we expected to find
            assertThat(states).isNotNull();
            assertThat(states.isEmpty()).isFalse();
            assertThat(states.size()).isEqualTo(3);

            final WritableKVState<String, String> fruitStates = states.get(FRUIT_STATE_KEY);
            assertThat(fruitStates).isNotNull();

            final var animalStates = states.get(ANIMAL_STATE_KEY);
            assertThat(animalStates).isNotNull();

            final var countryState = states.getSingleton(COUNTRY_STATE_KEY);
            assertThat(countryState).isNotNull();

            // And the states we got back are writable
            fruitStates.put(C_KEY, CHERRY);
            assertThat(fruitStates.get(C_KEY)).isSameAs(CHERRY);
            countryState.put(ESTONIA);
            assertThat(countryState.get()).isEqualTo(ESTONIA);
        }
    }

    @Nested
    @DisplayName("Handling Migrate Tests")
    final class MigrationTest {
        @Test
        @DisplayName("The onMigrate handler is called when a migration happens")
        void onMigrateCalled() {
            assertThat(onMigrateCalled).isFalse();
            hederaMerkle.migrate(1);
            assertThat(onMigrateCalled).isTrue();
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
        @DisplayName("Notifications are sent to onHandleConsensusRound when handleConsensusRound is" + " called")
        void handleConsensusRoundCallback() {
            final var round = Mockito.mock(Round.class);
            final var dualState = Mockito.mock(SwirldDualState.class);
            final var state = new MerkleHederaState(
                    tree -> onMigrateCalled.set(true),
                    (evt, meta, provider) -> onPreHandleCalled.set(true),
                    (r, d, m) -> {
                        assertThat(round).isSameAs(r);
                        assertThat(dualState).isSameAs(d);
                        onHandleCalled.set(true);
                    });

            state.handleConsensusRound(round, dualState);
            assertThat(onHandleCalled).isTrue();
        }
    }

    @Nested
    @DisplayName("Copy Tests")
    final class CopyTest {
        @Test
        @DisplayName("When a copy is made, the original loses the onConsensusRoundCallback, and the copy" + " gains it")
        void originalLosesConsensusRoundCallbackAfterCopy() {
            final var copy = hederaMerkle.copy();

            // The original no longer has the listener
            final var round = Mockito.mock(Round.class);
            final var dualState = Mockito.mock(SwirldDualState.class);
            assertThrows(MutabilityException.class, () -> hederaMerkle.handleConsensusRound(round, dualState));

            // But the copy does
            copy.handleConsensusRound(round, dualState);
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
            assertThatThrownBy(() -> hederaMerkle.putServiceStateIfAbsent(animalMetadata, animalMerkleMap))
                    .isInstanceOf(MutabilityException.class);
        }

        @Test
        @DisplayName("Cannot call removeServiceState on original after copy")
        void removeServiceOnOriginalAfterCopyThrows() {
            setupAnimalMerkleMap();
            hederaMerkle.putServiceStateIfAbsent(animalMetadata, animalMerkleMap);
            hederaMerkle.copy();
            assertThatThrownBy(() -> hederaMerkle.removeServiceState(FIRST_SERVICE, ANIMAL_STATE_KEY))
                    .isInstanceOf(MutabilityException.class);
        }

        @Test
        @DisplayName("Cannot call createWritableStates on original after copy")
        void createWritableStatesOnOriginalAfterCopyThrows() {
            hederaMerkle.copy();
            assertThatThrownBy(() -> hederaMerkle.createWritableStates(FRUIT_STATE_KEY))
                    .isInstanceOf(MutabilityException.class);
        }
    }
}
