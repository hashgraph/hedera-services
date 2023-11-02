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

package com.hedera.node.app.state.merkle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.spi.fixtures.state.TestSchema;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableQueueState;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableQueueState;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.throttle.HandleThrottleParser;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsBuilder;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.constructable.RuntimeConstructable;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SerializationTest extends MerkleTestBase {
    private Path dir;
    private Configuration config;
    private NetworkInfo networkInfo;
    private HandleThrottleParser handleThrottling;

    @BeforeEach
    void setUp() throws IOException {
        setupConstructableRegistry();

        this.dir = TemporaryFileBuilder.buildTemporaryDirectory();
        this.config = mock(Configuration.class);
        this.networkInfo = mock(NetworkInfo.class);
        this.handleThrottling = mock(HandleThrottleParser.class);
        final var hederaConfig = mock(HederaConfig.class);
        lenient().when(config.getConfigData(HederaConfig.class)).thenReturn(hederaConfig);
    }

    Schema createV1Schema() {
        return new TestSchema(1) {
            @NonNull
            @Override
            @SuppressWarnings("rawtypes")
            public Set<StateDefinition> statesToCreate() {
                final var fruitDef = StateDefinition.inMemory(FRUIT_STATE_KEY, STRING_CODEC, STRING_CODEC);
                final var animalDef = StateDefinition.onDisk(ANIMAL_STATE_KEY, STRING_CODEC, STRING_CODEC, 100);
                final var countryDef = StateDefinition.singleton(COUNTRY_STATE_KEY, STRING_CODEC);
                final var steamDef = StateDefinition.queue(STEAM_STATE_KEY, STRING_CODEC);
                return Set.of(fruitDef, animalDef, countryDef, steamDef);
            }

            @Override
            public void migrate(@NonNull final MigrationContext ctx) {
                final var newStates = ctx.newStates();
                final WritableKVState<String, String> fruit = newStates.get(FRUIT_STATE_KEY);
                fruit.put(A_KEY, APPLE);
                fruit.put(B_KEY, BANANA);
                fruit.put(C_KEY, CHERRY);
                fruit.put(D_KEY, DATE);
                fruit.put(E_KEY, EGGPLANT);
                fruit.put(F_KEY, FIG);
                fruit.put(G_KEY, GRAPE);

                final WritableKVState<String, String> animals = newStates.get(ANIMAL_STATE_KEY);
                animals.put(A_KEY, AARDVARK);
                animals.put(B_KEY, BEAR);
                animals.put(C_KEY, CUTTLEFISH);
                animals.put(D_KEY, DOG);
                animals.put(E_KEY, EMU);
                animals.put(F_KEY, FOX);
                animals.put(G_KEY, GOOSE);

                final WritableSingletonState<String> country = newStates.getSingleton(COUNTRY_STATE_KEY);
                country.put(CHAD);

                final WritableQueueState<String> steam = newStates.getQueue(STEAM_STATE_KEY);
                steam.add(ART);
                steam.add(BIOLOGY);
                steam.add(CHEMISTRY);
                steam.add(DISCIPLINE);
                steam.add(ECOLOGY);
                steam.add(FIELDS);
                steam.add(GEOMETRY);
            }
        };
    }

    /**
     * In this test scenario, we have a genesis setup where we create FRUIT and ANIMALS and COUNTRY,
     * save them to disk, and then load them back in, and verify everything was loaded correctly.
     */
    @Test
    void simpleReadAndWrite() throws IOException, ConstructableRegistryException {
        // Given a merkle tree with some fruit and animals and country
        final var v1 = version(1, 0, 0);
        final var originalTree = new MerkleHederaState(
                (tree, state) -> {}, (evt, meta, provider) -> {}, (state, platform, dual, trigger, version) -> {});
        final var originalRegistry =
                new MerkleSchemaRegistry(registry, FIRST_SERVICE, mock(GenesisRecordsBuilder.class));
        final var schemaV1 = createV1Schema();
        originalRegistry.register(schemaV1);
        originalRegistry.migrate(originalTree, null, v1, config, networkInfo, handleThrottling);

        // When we serialize it to bytes and deserialize it back into a tree
        originalTree.copy(); // make a fast copy because we can only write to disk an immutable copy
        CRYPTO.digestTreeSync(originalTree);
        final var serializedBytes = writeTree(originalTree, dir);
        final var newRegistry = new MerkleSchemaRegistry(registry, FIRST_SERVICE, mock(GenesisRecordsBuilder.class));
        newRegistry.register(schemaV1);

        // Register the MerkleHederaState so, when found in serialized bytes, it will register with
        // our migration callback, etc. (normally done by the Hedera main method)
        final Supplier<RuntimeConstructable> constructor = () -> new MerkleHederaState(
                (tree, state) ->
                        newRegistry.migrate((MerkleHederaState) state, v1, v1, config, networkInfo, handleThrottling),
                (event, meta, provider) -> {},
                (state, platform, dualState, trigger, version) -> {});
        final var pair = new ClassConstructorPair(MerkleHederaState.class, constructor);
        registry.registerConstructable(pair);

        final MerkleHederaState loadedTree = parseTree(serializedBytes, dir);
        newRegistry.migrate(
                loadedTree, schemaV1.getVersion(), schemaV1.getVersion(), config, networkInfo, handleThrottling);
        loadedTree.migrate(1);

        // Then, we should be able to see all our original states again
        final var states = loadedTree.createReadableStates(FIRST_SERVICE);
        final ReadableKVState<String, String> fruitState = states.get(FRUIT_STATE_KEY);
        assertThat(fruitState.get(A_KEY)).isEqualTo(APPLE);
        assertThat(fruitState.get(B_KEY)).isEqualTo(BANANA);
        assertThat(fruitState.get(C_KEY)).isEqualTo(CHERRY);
        assertThat(fruitState.get(D_KEY)).isEqualTo(DATE);
        assertThat(fruitState.get(E_KEY)).isEqualTo(EGGPLANT);
        assertThat(fruitState.get(F_KEY)).isEqualTo(FIG);
        assertThat(fruitState.get(G_KEY)).isEqualTo(GRAPE);

        final ReadableKVState<String, String> animalState = states.get(ANIMAL_STATE_KEY);
        assertThat(animalState.get(A_KEY)).isEqualTo(AARDVARK);
        assertThat(animalState.get(B_KEY)).isEqualTo(BEAR);
        assertThat(animalState.get(C_KEY)).isEqualTo(CUTTLEFISH);
        assertThat(animalState.get(D_KEY)).isEqualTo(DOG);
        assertThat(animalState.get(E_KEY)).isEqualTo(EMU);
        assertThat(animalState.get(F_KEY)).isEqualTo(FOX);
        assertThat(animalState.get(G_KEY)).isEqualTo(GOOSE);

        final ReadableSingletonState<String> countryState = states.getSingleton(COUNTRY_STATE_KEY);
        assertThat(countryState.get()).isEqualTo(CHAD);

        final ReadableQueueState<String> steamState = states.getQueue(STEAM_STATE_KEY);
        assertThat(steamState.iterator())
                .toIterable()
                .containsExactly(ART, BIOLOGY, CHEMISTRY, DISCIPLINE, ECOLOGY, FIELDS, GEOMETRY);
    }
}
