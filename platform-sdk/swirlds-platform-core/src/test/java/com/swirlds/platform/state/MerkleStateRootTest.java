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

package com.swirlds.platform.state;

import static com.swirlds.platform.state.MerkleStateRoot.CURRENT_VERSION;
import static com.swirlds.platform.state.service.PbjConverter.toPbjPlatformState;
import static com.swirlds.platform.test.PlatformStateUtils.randomPlatformState;
import static com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static com.swirlds.state.StateChangeListener.StateType.MAP;
import static com.swirlds.state.StateChangeListener.StateType.QUEUE;
import static com.swirlds.state.StateChangeListener.StateType.SINGLETON;
import static com.swirlds.state.merkle.StateUtils.computeLabel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.base.state.MutabilityException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.merkle.crypto.MerkleCryptographyFactory;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.state.service.WritablePlatformStateStore;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles;
import com.swirlds.platform.test.fixtures.state.MerkleTestBase;
import com.swirlds.platform.test.fixtures.state.TestSchema;
import com.swirlds.state.State;
import com.swirlds.state.StateChangeListener;
import com.swirlds.state.merkle.StateMetadata;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableQueueState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerkleStateRootTest extends MerkleTestBase {
    /** The merkle tree we will test with */
    private MerkleStateRoot stateRoot;

    private final AtomicBoolean onPreHandleCalled = new AtomicBoolean(false);
    private final AtomicBoolean onHandleCalled = new AtomicBoolean(false);
    private final AtomicBoolean onUpdateWeightCalled = new AtomicBoolean(false);

    private final MerkleStateLifecycles lifecycles = new MerkleStateLifecycles() {
        @Override
        public List<StateChanges.Builder> initPlatformState(@NonNull final State state) {
            return FAKE_MERKLE_STATE_LIFECYCLES.initPlatformState(state);
        }

        @Override
        public void onSealConsensusRound(@NonNull Round round, @NonNull State state) {
            // No-op
        }

        @Override
        public void onPreHandle(@NonNull Event event, @NonNull State state) {
            onPreHandleCalled.set(true);
        }

        @Override
        public void onNewRecoveredState(@NonNull MerkleStateRoot recoveredState) {
            // No-op
        }

        @Override
        public void onHandleConsensusRound(@NonNull Round round, @NonNull State state) {
            onHandleCalled.set(true);
        }

        @Override
        public void onStateInitialized(
                @NonNull State state,
                @NonNull Platform platform,
                @NonNull InitTrigger trigger,
                @Nullable SoftwareVersion previousVersion) {}

        @Override
        public void onUpdateWeight(
                @NonNull MerkleStateRoot state,
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
        setupConstructableRegistry();
        FakeMerkleStateLifecycles.registerMerkleStateRootClassIds();
        setupFruitMerkleMap();
        stateRoot = new MerkleStateRoot(lifecycles, softwareVersionSupplier);
    }

    /** Looks for a merkle node with the given label */
    MerkleNode getNodeForLabel(String label) {
        return getNodeForLabel(stateRoot, label);
    }

    @Nested
    @DisplayName("Service Registration Tests")
    final class RegistrationTest {
        @Test
        @DisplayName("Adding a null service metadata will throw an NPE")
        void addingNullServiceMetaDataThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(() -> stateRoot.putServiceStateIfAbsent(null, () -> fruitMerkleMap))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Adding a null service node will throw an NPE")
        void addingNullServiceNodeThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(() -> stateRoot.putServiceStateIfAbsent(fruitMetadata, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Adding a service node that is not Labeled throws IAE")
        void addingWrongKindOfNodeThrows() {
            assertThatThrownBy(() ->
                            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> Mockito.mock(MerkleNode.class)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Adding a service node without a label throws IAE")
        void addingNodeWithNoLabelThrows() {
            final var fruitNodeNoLabel = Mockito.mock(MerkleMap.class);
            Mockito.when(fruitNodeNoLabel.getLabel()).thenReturn(null);
            assertThatThrownBy(() -> stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitNodeNoLabel))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Adding a service node with a label that doesn't match service name and state key throws IAE")
        void addingBadServiceNodeNameThrows() {
            fruitMerkleMap.setLabel("Some Random Label");
            assertThatThrownBy(() -> stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Adding a service")
        void addingService() {
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            assertThat(stateRoot.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
        }

        @Test
        @DisplayName("Adding a service with VirtualMap")
        void addingVirtualMapService() {
            // Given a virtual map
            setupFruitVirtualMap();

            // When added to the merkle tree
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitVirtualMap);

            // Then we can see it is on the tree
            assertThat(stateRoot.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitVirtualMap);
        }

        @Test
        @DisplayName("Adding a service with a Singleton node")
        void addingSingletonService() {
            // Given a singleton node
            setupSingletonCountry();

            // When added to the merkle tree
            stateRoot.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);

            // Then we can see it is on the tree
            assertThat(stateRoot.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(countryLabel)).isSameAs(countrySingleton);
        }

        @Test
        @DisplayName("Adding a service with a Queue node")
        void addingQueueService() {
            // Given a queue node
            setupSteamQueue();

            // When added to the merkle tree
            stateRoot.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // Then we can see it is on the tree
            assertThat(stateRoot.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(steamLabel)).isSameAs(steamQueue);
        }

        @Test
        @DisplayName("Adding a service to a MerkleStateRoot that has other node types on it")
        void addingServiceWhenNonServiceNodeChildrenExist() {
            stateRoot.setChild(0, Mockito.mock(MerkleNode.class));
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            assertThat(stateRoot.getNumberOfChildren()).isEqualTo(2);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
        }

        @Test
        @DisplayName("Adding the same service twice is idempotent")
        void addingServiceTwiceIsIdempotent() {
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            assertThat(stateRoot.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
        }

        @Test
        @DisplayName("Adding the same service twice with two different nodes causes the original node to remain")
        void addingServiceTwiceWithDifferentNodesDoesNotReplaceFirstNode() {
            // Given an empty merkle tree, when I add the same metadata twice but with different
            // nodes,
            final var map2 = createMerkleMap(fruitMerkleMap.getLabel());
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> map2);

            // Then the original node is kept and the second node ignored
            assertThat(stateRoot.getNumberOfChildren()).isEqualTo(1);
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
                    StateDefinition.inMemory(FRUIT_STATE_KEY, STRING_CODEC, LONG_CODEC));

            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            stateRoot.putServiceStateIfAbsent(fruitMetadata2, () -> fruitMerkleMap);

            // Then the original node is kept and the second node ignored
            assertThat(stateRoot.getNumberOfChildren()).isEqualTo(1);
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);

            // NOTE: I don't have a good way to test that the metadata is intact...
        }

        @Test
        @DisplayName("Adding non-VirtualMap merkle node with on-disk metadata throws")
        void merkleMapWithOnDiskThrows() {
            setupFruitVirtualMap();
            assertThatThrownBy(() -> stateRoot.putServiceStateIfAbsent(fruitVirtualMetadata, () -> fruitMerkleMap))
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
            assertThatThrownBy(() -> stateRoot.removeServiceState(null, FRUIT_STATE_KEY))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("You cannot remove with a null state key")
        void usingNullStateKeyToRemoveThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(() -> stateRoot.removeServiceState(FIRST_SERVICE, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Removing an unknown service name does nothing")
        void removeWithUnknownServiceName() {
            // Given a tree with a random node, and a service node
            stateRoot.setChild(0, Mockito.mock(MerkleNode.class));
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            final var numChildren = stateRoot.getNumberOfChildren();

            // When you try to remove an unknown service
            stateRoot.removeServiceState(UNKNOWN_SERVICE, FRUIT_STATE_KEY);

            // It has no effect on anything
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
            assertThat(stateRoot.getNumberOfChildren()).isEqualTo(numChildren);
        }

        @Test
        @DisplayName("Removing an unknown state key does nothing")
        void removeWithUnknownStateKey() {
            // Given a tree with a random node, and a service node
            stateRoot.setChild(0, Mockito.mock(MerkleNode.class));
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            final var numChildren = stateRoot.getNumberOfChildren();

            // When you try to remove an unknown state key
            stateRoot.removeServiceState(FIRST_SERVICE, UNKNOWN_STATE_KEY);

            // It has no effect on anything
            assertThat(getNodeForLabel(fruitLabel)).isSameAs(fruitMerkleMap);
            assertThat(stateRoot.getNumberOfChildren()).isEqualTo(numChildren);
        }

        @Test
        @DisplayName("Calling `remove` removes the right service")
        void remove() {
            // Put a bunch of stuff into the state
            final var map = new HashMap<String, MerkleNode>();
            for (int i = 0; i < 10; i++) {
                final var serviceName = "Service_" + i;
                final var label = computeLabel(serviceName, FRUIT_STATE_KEY);
                final var md = new StateMetadata<>(
                        serviceName,
                        new TestSchema(1),
                        StateDefinition.inMemory(FRUIT_STATE_KEY, STRING_CODEC, STRING_CODEC));

                final var node = createMerkleMap(label);
                map.put(serviceName, node);
                stateRoot.putServiceStateIfAbsent(md, () -> node);
            }

            // Randomize the order in which they should be removed
            final List<String> serviceNames = new ArrayList<>(map.keySet());
            Collections.shuffle(serviceNames, random());

            // Remove the services
            final Set<String> removedServiceNames = new HashSet<>();
            for (final var serviceName : serviceNames) {
                removedServiceNames.add(serviceName);
                map.remove(serviceName);
                stateRoot.removeServiceState(serviceName, FRUIT_STATE_KEY);

                // Verify everything OTHER THAN the removed service node is still present
                for (final var entry : map.entrySet()) {
                    final var label = computeLabel(entry.getKey(), FRUIT_STATE_KEY);
                    assertThat(getNodeForLabel(label)).isSameAs(entry.getValue());
                }

                // Verify NONE OF THE REMOVED SERVICES have a node still present
                for (final var removedKey : removedServiceNames) {
                    final var label = computeLabel(removedKey, FRUIT_STATE_KEY);
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
            final var states = stateRoot.getReadableStates(UNKNOWN_SERVICE);
            assertThat(states).isNotNull();
            assertThat(states.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Reading an unknown state on ReadableStates should throw IAE")
        void unknownState() {
            // Given a State with the fruit and animal and country states
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            stateRoot.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            stateRoot.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            stateRoot.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // When we get the ReadableStates
            final var states = stateRoot.getReadableStates(FIRST_SERVICE);

            // Then query it for an unknown state and get an IAE
            assertThatThrownBy(() -> states.get(UNKNOWN_STATE_KEY)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Read a virtual map")
        void readVirtualMap() {
            // Given a State with the fruit virtual map
            setupFruitVirtualMap();
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitVirtualMap);

            // When we get the ReadableStates
            final var states = stateRoot.getReadableStates(FIRST_SERVICE);

            // Then it isn't null
            assertThat(states.get(FRUIT_STATE_KEY)).isNotNull();
        }

        @Test
        @DisplayName("Try to read a state that is MISSING from the merkle tree")
        void readMissingState() {
            // Given a State with the fruit merkle map, which somehow has
            // lost the merkle node (this should NEVER HAPPEN in real life!)
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            stateRoot.setChild(0, null);

            // When we get the ReadableStates
            final var states = stateRoot.getReadableStates(FIRST_SERVICE);

            // Then try to read the state and find it is missing!
            assertThatThrownBy(() -> states.get(FRUIT_STATE_KEY)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Contains is true for all states in stateKeys and false for unknown ones")
        void contains() {
            // Given a State with the fruit and animal and country and steam states
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            stateRoot.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            stateRoot.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            stateRoot.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // When we get the ReadableStates and the state keys
            final var states = stateRoot.getReadableStates(FIRST_SERVICE);
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
            // Given a State with the fruit and the ReadableStates for it
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            final var states = stateRoot.getReadableStates(FIRST_SERVICE);

            // When we call get twice
            final var kvState1 = states.get(FRUIT_STATE_KEY);
            final var kvState2 = states.get(FRUIT_STATE_KEY);

            // Then we must find both variables are the same instance
            assertThat(kvState1).isSameAs(kvState2);
        }

        @Test
        @DisplayName("Getting ReadableStates on a known service returns an object with all the state")
        void knownServiceNameUsingReadableStates() {
            // Given a State with the fruit and animal and country states
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            stateRoot.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            stateRoot.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            stateRoot.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // When we get the ReadableStates
            final var states = stateRoot.getReadableStates(FIRST_SERVICE);

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
            final var states = stateRoot.getWritableStates(UNKNOWN_SERVICE);
            assertThat(states).isNotNull();
            assertThat(states.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Reading an unknown state on WritableState should throw IAE")
        void unknownState() {
            // Given a State with the fruit and animal and country states
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            stateRoot.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            stateRoot.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            stateRoot.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // When we get the WritableStates
            final var states = stateRoot.getWritableStates(FIRST_SERVICE);

            // Then query it for an unknown state and get an IAE
            assertThatThrownBy(() -> states.get(UNKNOWN_STATE_KEY)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Try to read a state that is MISSING from the merkle tree")
        void readMissingState() {
            // Given a State with the fruit virtual map, which somehow has
            // lost the merkle node (this should NEVER HAPPEN in real life!)
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            stateRoot.setChild(0, null);

            // When we get the WritableStates
            final var states = stateRoot.getWritableStates(FIRST_SERVICE);

            // Then try to read the state and find it is missing!
            assertThatThrownBy(() -> states.get(FRUIT_STATE_KEY)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Read a virtual map")
        void readVirtualMap() {
            // Given a State with the fruit virtual map
            setupFruitVirtualMap();
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitVirtualMap);

            // When we get the WritableStates
            final var states = stateRoot.getWritableStates(FIRST_SERVICE);

            // Then it isn't null
            assertThat(states.get(FRUIT_STATE_KEY)).isNotNull();
        }

        @Test
        @DisplayName("Contains is true for all states in stateKeys and false for unknown ones")
        void contains() {
            // Given a State with the fruit and animal and country states
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            stateRoot.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            stateRoot.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            stateRoot.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // When we get the WritableStates and the state keys
            final var states = stateRoot.getWritableStates(FIRST_SERVICE);
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
            // Given a State with the fruit and the WritableStates for it
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            final var states = stateRoot.getWritableStates(FIRST_SERVICE);

            // When we call get twice
            final var kvState1 = states.get(FRUIT_STATE_KEY);
            final var kvState2 = states.get(FRUIT_STATE_KEY);

            // Then we must find both variables are the same instance
            assertThat(kvState1).isSameAs(kvState2);
        }

        @Test
        @DisplayName("Getting WritableStates on a known service returns an object with all the state")
        void knownServiceNameUsingWritableStates() {
            // Given a State with the fruit and animal and country states
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            stateRoot.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            stateRoot.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            stateRoot.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            // When we get the WritableStates
            final var states = stateRoot.getWritableStates(FIRST_SERVICE);

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
            stateRoot.preHandle(Mockito.mock(Event.class));
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
            final var platformState = Mockito.mock(PlatformStateModifier.class);
            final var state = new MerkleStateRoot(lifecycles, softwareVersionSupplier);

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
            final var copy = stateRoot.copy();

            // The original no longer has the listener
            final var round = Mockito.mock(Round.class);
            final var platformState = Mockito.mock(PlatformStateModifier.class);
            assertThrows(MutabilityException.class, () -> stateRoot.handleConsensusRound(round, platformState));

            // But the copy does
            copy.handleConsensusRound(round, platformState);
            assertThat(onHandleCalled).isTrue();
        }

        @Test
        @DisplayName("Cannot call copy on original after copy")
        void callCopyTwiceOnOriginalThrows() {
            stateRoot.copy();
            assertThatThrownBy(stateRoot::copy).isInstanceOf(MutabilityException.class);
        }

        @Test
        @DisplayName("Cannot call putServiceStateIfAbsent on original after copy")
        void addServiceOnOriginalAfterCopyThrows() {
            setupAnimalMerkleMap();
            stateRoot.copy();
            assertThatThrownBy(() -> stateRoot.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap))
                    .isInstanceOf(MutabilityException.class);
        }

        @Test
        @DisplayName("Cannot call removeServiceState on original after copy")
        void removeServiceOnOriginalAfterCopyThrows() {
            setupAnimalMerkleMap();
            stateRoot.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            stateRoot.copy();
            assertThatThrownBy(() -> stateRoot.removeServiceState(FIRST_SERVICE, ANIMAL_STATE_KEY))
                    .isInstanceOf(MutabilityException.class);
        }

        @Test
        @DisplayName("Cannot call createWritableStates on original after copy")
        void createWritableStatesOnOriginalAfterCopyThrows() {
            stateRoot.copy();
            assertThatThrownBy(() -> stateRoot.getWritableStates(FRUIT_STATE_KEY))
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
            stateRoot.updateWeight(Mockito.mock(AddressBook.class), Mockito.mock(PlatformContext.class));
            assertThat(onUpdateWeightCalled).isTrue();
        }
    }

    @Nested
    @DisplayName("with registered listeners")
    class WithRegisteredListeners {
        @Mock
        private StateChangeListener kvListener;

        @Mock
        private StateChangeListener nonKvListener;

        @BeforeEach
        void setUp() {
            given(kvListener.stateTypes()).willReturn(EnumSet.of(MAP));
            given(nonKvListener.stateTypes()).willReturn(EnumSet.of(QUEUE, SINGLETON));
            given(kvListener.stateIdFor(FIRST_SERVICE, FRUIT_STATE_KEY)).willReturn(FRUIT_STATE_ID);
            given(kvListener.stateIdFor(FIRST_SERVICE, ANIMAL_STATE_KEY)).willReturn(ANIMAL_STATE_ID);
            given(nonKvListener.stateIdFor(FIRST_SERVICE, COUNTRY_STATE_KEY)).willReturn(COUNTRY_STATE_ID);
            given(nonKvListener.stateIdFor(FIRST_SERVICE, STEAM_STATE_KEY)).willReturn(STEAM_STATE_ID);

            setupAnimalMerkleMap();
            setupFruitVirtualMap();
            setupSingletonCountry();
            setupSteamQueue();

            add(fruitVirtualMap, fruitVirtualMetadata, C_KEY, CHERRY);
            add(animalMerkleMap, animalMetadata, C_KEY, CUTTLEFISH);
            countrySingleton.setValue(FRANCE);
            steamQueue.add(ART);
        }

        @Test
        void appropriateListenersAreInvokedOnCommit() {
            stateRoot.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            stateRoot.putServiceStateIfAbsent(fruitVirtualMetadata, () -> fruitVirtualMap);
            stateRoot.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            stateRoot.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            stateRoot.registerCommitListener(kvListener);
            stateRoot.registerCommitListener(nonKvListener);

            final var states = stateRoot.getWritableStates(FIRST_SERVICE);
            final var animalState = states.get(ANIMAL_STATE_KEY);
            final var fruitState = states.get(FRUIT_STATE_KEY);
            final var countryState = states.getSingleton(COUNTRY_STATE_KEY);
            final var steamState = states.getQueue(STEAM_STATE_KEY);

            fruitState.put(E_KEY, EGGPLANT);
            fruitState.remove(C_KEY);
            animalState.put(A_KEY, AARDVARK);
            animalState.remove(C_KEY);
            countryState.put(ESTONIA);
            steamState.poll();
            steamState.add(BIOLOGY);

            ((CommittableWritableStates) states).commit();

            verify(kvListener).mapUpdateChange(FRUIT_STATE_ID, E_KEY, EGGPLANT);
            verify(kvListener).mapDeleteChange(FRUIT_STATE_ID, C_KEY);
            verify(kvListener).mapUpdateChange(ANIMAL_STATE_ID, A_KEY, AARDVARK);
            verify(kvListener).mapDeleteChange(ANIMAL_STATE_ID, C_KEY);
            verify(nonKvListener).singletonUpdateChange(COUNTRY_STATE_ID, ESTONIA);
            verify(nonKvListener).queuePushChange(STEAM_STATE_ID, BIOLOGY);
            verify(nonKvListener).queuePopChange(STEAM_STATE_ID);

            verifyNoMoreInteractions(kvListener);
            verifyNoMoreInteractions(nonKvListener);
        }
    }

    @Nested
    @DisplayName("Platform state related tests")
    class PlatformStateTests {

        @Test
        @DisplayName("Platform state should be registered by default")
        void platformStateIsRegisteredByDefault() {
            assertThat(stateRoot.getWritablePlatformState()).isNotNull();
        }

        @Test
        @DisplayName("Test access to the platform state")
        void testAccessToPlatformStateData() {
            PlatformStateModifier randomPlatformState = randomPlatformState(stateRoot.getWritablePlatformState());
            stateRoot.updatePlatformState(randomPlatformState);
            ReadableSingletonState<PlatformState> readableSingletonState = stateRoot
                    .getReadableStates(PlatformStateService.NAME)
                    .getSingleton(V0540PlatformStateSchema.PLATFORM_STATE_KEY);
            WritableSingletonState<PlatformState> writableSingletonState = stateRoot
                    .getWritableStates(PlatformStateService.NAME)
                    .getSingleton(V0540PlatformStateSchema.PLATFORM_STATE_KEY);

            assertThat(readableSingletonState.get()).isEqualTo(toPbjPlatformState(randomPlatformState));
            assertThat(writableSingletonState.get()).isEqualTo(toPbjPlatformState(randomPlatformState));
        }

        @Test
        @DisplayName("Test update of the platform state")
        void testUpdatePlatformStateData() {
            PlatformStateModifier randomPlatformState = randomPlatformState(stateRoot.getWritablePlatformState());
            stateRoot.updatePlatformState(randomPlatformState);
            WritableStates writableStates = stateRoot.getWritableStates(PlatformStateService.NAME);
            WritableSingletonState<PlatformState> writableSingletonState =
                    writableStates.getSingleton(V0540PlatformStateSchema.PLATFORM_STATE_KEY);
            PlatformStateModifier newPlatformState = randomPlatformState(stateRoot.getWritablePlatformState());
            writableSingletonState.put(toPbjPlatformState(newPlatformState));
            ((CommittableWritableStates) writableStates).commit();

            PlatformStateAccessor stateAccessor = stateRoot.getReadablePlatformState();
            assertThat(stateAccessor.getAddressBook()).isEqualTo(newPlatformState.getAddressBook());
            assertThat(stateAccessor.getRound())
                    .isEqualTo(newPlatformState.getSnapshot().round());
        }
    }

    @Nested
    @DisplayName("Migrate test")
    class MigrateTest {
        @Test
        @DisplayName("Migrate fails if the first child is not PlatformState")
        void migrate_fail() {
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            assertThrows(IllegalStateException.class, () -> stateRoot.migrate(CURRENT_VERSION - 1));
        }

        @Test
        @DisplayName("If the version is current, nothing ever happens")
        void migrate_currentVersion() {
            var node1 = mock(MerkleNode.class);
            stateRoot.setChild(0, node1);
            var node2 = mock(MerkleNode.class);
            stateRoot.setChild(1, node2);
            reset(node1, node2);
            assertSame(stateRoot, stateRoot.migrate(CURRENT_VERSION));
            verifyNoMoreInteractions(node1, node2);
        }

        @Test
        @DisplayName("Migrate from the previous version (the platform state is the zeroth child)")
        void migrate_platform_state_zeroth_child() {
            com.swirlds.platform.state.PlatformState platformState = (com.swirlds.platform.state.PlatformState)
                    randomPlatformState(new com.swirlds.platform.state.PlatformState());
            stateRoot.setChild(0, platformState);
            assertNull(stateRoot.getPreV054PlatformState());

            var node1 = mock(MerkleNode.class);
            var node1Copy = mock(MerkleNode.class);
            when(node1.copy()).thenReturn(node1Copy);
            stateRoot.setChild(1, node1);

            var node2 = mock(MerkleNode.class);
            var node2Copy = mock(MerkleNode.class);
            when(node2.copy()).thenReturn(node2Copy);
            stateRoot.setChild(2, node2);

            assertSame(stateRoot, stateRoot.migrate(CURRENT_VERSION - 1));

            // Platform state is not a part of the tree temporarily
            assertEquals(2, stateRoot.getNumberOfChildren());
            verify(node1).release();
            verify(node2).release();

            assertEquals(node1Copy, stateRoot.getChild(0));
            assertEquals(node2Copy, stateRoot.getChild(1));

            // Platform state is temporarily stored in a separate field
            assertEquals(platformState, stateRoot.getPreV054PlatformState());

            assertFalse(stateRoot.isImmutable());

            // MerkleStateRoot registers the platform state as a singleton upon the first request to it
            assertInstanceOf(WritablePlatformStateStore.class, stateRoot.getWritablePlatformState());
            assertInstanceOf(ReadablePlatformStateStore.class, stateRoot.getReadablePlatformState());
            assertEquals(toPbjPlatformState(platformState), toPbjPlatformState(stateRoot.getWritablePlatformState()));
            assertEquals(toPbjPlatformState(platformState), toPbjPlatformState(stateRoot.getReadablePlatformState()));
        }

        @Test
        @DisplayName("Migrate from the previous version (platform state is the last child)")
        void migrate_platform_state_last_child() {
            com.swirlds.platform.state.PlatformState platformState = (com.swirlds.platform.state.PlatformState)
                    randomPlatformState(new com.swirlds.platform.state.PlatformState());

            var node1 = mock(MerkleNode.class);
            var node1Copy = mock(MerkleNode.class);
            when(node1.copy()).thenReturn(node1Copy);
            stateRoot.setChild(0, node1);

            var node2 = mock(MerkleNode.class);
            var node2Copy = mock(MerkleNode.class);
            when(node2.copy()).thenReturn(node2Copy);
            stateRoot.setChild(1, node2);

            stateRoot.setChild(2, platformState);
            assertNull(stateRoot.getPreV054PlatformState());

            assertSame(stateRoot, stateRoot.migrate(CURRENT_VERSION - 1));

            // Platform state is not a part of the tree temporarily
            assertEquals(2, stateRoot.getNumberOfChildren());
            verify(node1).release();
            verify(node2).release();

            assertEquals(node1Copy, stateRoot.getChild(0));
            assertEquals(node2Copy, stateRoot.getChild(1));

            // Platform state is temporarily stored in a separate field
            assertEquals(platformState, stateRoot.getPreV054PlatformState());

            assertFalse(stateRoot.isImmutable());

            // MerkleStateRoot registers the platform state as a singleton upon the first request to it
            assertInstanceOf(WritablePlatformStateStore.class, stateRoot.getWritablePlatformState());
            assertEquals(toPbjPlatformState(platformState), toPbjPlatformState(stateRoot.getWritablePlatformState()));
        }

        @Test
        @DisplayName("Migrate fails if the platform state is absent")
        void migrate_platform_absent() {
            var node1 = mock(MerkleNode.class);
            stateRoot.setChild(0, node1);
            var node2 = mock(MerkleNode.class);
            stateRoot.setChild(1, node2);

            assertThrows(IllegalStateException.class, () -> stateRoot.migrate(CURRENT_VERSION - 1));
        }
    }

    @Nested
    @DisplayName("Hashing test")
    class HashingTest {

        private MerkleCryptography merkleCryptography;

        @BeforeEach
        void setUp() {
            setupAnimalMerkleMap();
            setupSingletonCountry();
            setupSteamQueue();
            setupFruitMerkleMap();

            add(fruitMerkleMap, fruitMetadata, A_KEY, APPLE);
            add(fruitMerkleMap, fruitMetadata, B_KEY, BANANA);
            add(animalMerkleMap, animalMetadata, C_KEY, CUTTLEFISH);
            add(animalMerkleMap, animalMetadata, D_KEY, DOG);
            add(animalMerkleMap, animalMetadata, F_KEY, FOX);
            countrySingleton.setValue(GHANA);
            steamQueue.add(ART);

            // Given a State with the fruit and animal and country states
            stateRoot.putServiceStateIfAbsent(fruitMetadata, () -> fruitMerkleMap);
            stateRoot.putServiceStateIfAbsent(animalMetadata, () -> animalMerkleMap);
            stateRoot.putServiceStateIfAbsent(countryMetadata, () -> countrySingleton);
            stateRoot.putServiceStateIfAbsent(steamMetadata, () -> steamQueue);

            final Platform platform = mock(Platform.class);
            merkleCryptography = MerkleCryptographyFactory.create(
                    ConfigurationBuilder.create()
                            .withConfigDataType(CryptoConfig.class)
                            .build(),
                    CryptographyFactory.create());
            final PlatformContext platformContext = mock(PlatformContext.class);
            when(platform.getContext()).thenReturn(platformContext);
            when(platformContext.getMerkleCryptography()).thenReturn(merkleCryptography);
            stateRoot.init(platform, InitTrigger.GENESIS, mock(SoftwareVersion.class));
        }

        @Test
        @DisplayName("No hash by default")
        void noHashByDefault() {
            assertNull(stateRoot.getHash());
        }

        @Test
        @DisplayName("computeHash is doesn't work on mutable states")
        void calculateHashOnMutable() {
            assertThrows(IllegalStateException.class, stateRoot::computeHash);
        }

        @Test
        @DisplayName("computeHash is doesn't work on destroyed states")
        void calculateHashOnDestroyed() {
            stateRoot.destroyNode();
            assertThrows(IllegalStateException.class, stateRoot::computeHash);
        }

        @Test
        @DisplayName("Hash is computed after computeHash invocation")
        void calculateHash() {
            stateRoot.copy();
            stateRoot.computeHash();
            assertNotNull(stateRoot.getHash());
        }

        @Test
        @DisplayName("computeHash is idempotent")
        void calculateHash_idempotent() {
            stateRoot.copy();
            stateRoot.computeHash();
            Hash hash1 = stateRoot.getHash();
            stateRoot.computeHash();
            Hash hash2 = stateRoot.getHash();
            assertSame(hash1, hash2);
        }
    }
}
