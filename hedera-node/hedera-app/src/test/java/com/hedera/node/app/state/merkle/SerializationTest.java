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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.spi.state.WritableStateBase;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.constructable.RuntimeConstructable;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.BasicSoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SerializationTest extends MerkleTestBase {

    protected byte[] writeTree(@NonNull final MerkleNode tree, @NonNull final Path tempDir)
            throws IOException {
        final var byteOutputStream = new ByteArrayOutputStream();
        try (final var out = new MerkleDataOutputStream(byteOutputStream)) {
            out.writeMerkleTree(tempDir, tree);
        }
        return byteOutputStream.toByteArray();
    }

    protected <T extends MerkleNode> T parseTree(
            @NonNull final byte[] state, @NonNull final Path tempDir) throws IOException {
        final var byteInputStream = new ByteArrayInputStream(state);
        try (final var in = new MerkleDataInputStream(byteInputStream)) {
            return in.readMerkleTree(tempDir, 100);
        }
    }

    /**
     * In this test scenario, we have a genesis setup where we create FRUIT and ANIMALS, save them
     * to disk, and then load them back in, and verify everything was loaded correctly.
     */
    @Test
    void simpleReadAndWrite(@TempDir Path path) throws IOException, ConstructableRegistryException {
        final var originalTree =
                new MerkleHederaState((tree, ver) -> {}, evt -> {}, (round, dual) -> {});

        final var originalRegistry =
                new MerkleStateRegistry(
                        registry,
                        path,
                        FIRST_SERVICE,
                        new BasicSoftwareVersion(1),
                        new BasicSoftwareVersion(1));

        originalRegistry
                .register(FRUIT_STATE_KEY)
                .memory()
                .keyParser(SerializationTest::parseString)
                .keyWriter(SerializationTest::writeString)
                .valueParser(SerializationTest::parseString)
                .valueWriter(SerializationTest::writeString)
                .complete();

        originalRegistry
                .register(ANIMAL_STATE_KEY)
                .memory()
                .keyParser(SerializationTest::parseString)
                .keyWriter(SerializationTest::writeString)
                .valueParser(SerializationTest::parseString)
                .valueWriter(SerializationTest::writeString)
                .complete();

        originalRegistry.migrate(originalTree);

        // Construct a full looking tree
        final var writableFruit =
                originalTree.createWritableStates(FIRST_SERVICE).get(FRUIT_STATE_KEY);
        writableFruit.put(A_KEY, APPLE);
        writableFruit.put(B_KEY, BANANA);
        writableFruit.put(C_KEY, CHERRY);
        writableFruit.put(D_KEY, DATE);
        writableFruit.put(E_KEY, EGGPLANT);
        writableFruit.put(F_KEY, FIG);
        writableFruit.put(G_KEY, GRAPE);
        ((WritableStateBase<?, ?>) writableFruit).commit();

        final var writableAnimals =
                originalTree.createWritableStates(FIRST_SERVICE).get(ANIMAL_STATE_KEY);
        writableAnimals.put(A_KEY, AARDVARK);
        writableAnimals.put(B_KEY, BEAR);
        writableAnimals.put(C_KEY, CUTTLEFISH);
        writableAnimals.put(D_KEY, DOG);
        writableAnimals.put(E_KEY, EMU);
        writableAnimals.put(F_KEY, FOX);
        writableAnimals.put(G_KEY, GOOSE);
        ((WritableStateBase<?, ?>) writableAnimals).commit();

        // Now serialize it all!
        final var serializedBytes = writeTree(originalTree, path);

        // Now, create a new registry, for the new version that we are loading
        final var loadedRegistry =
                new MerkleStateRegistry(
                        registry,
                        path,
                        FIRST_SERVICE,
                        new BasicSoftwareVersion(2),
                        new BasicSoftwareVersion(1));

        loadedRegistry
                .register(FRUIT_STATE_KEY)
                .memory()
                .keyParser(SerializationTest::parseString)
                .keyWriter(SerializationTest::writeString)
                .valueParser(SerializationTest::parseString)
                .valueWriter(SerializationTest::writeString)
                .complete();

        loadedRegistry
                .register(ANIMAL_STATE_KEY)
                .memory()
                .keyParser(SerializationTest::parseString)
                .keyWriter(SerializationTest::writeString)
                .valueParser(SerializationTest::parseString)
                .valueWriter(SerializationTest::writeString)
                .complete();

        // Register the MerkleHederaState so, when found in serialized bytes,
        // it will register with our migration callback, etc.
        final Supplier<RuntimeConstructable> constructor =
                () ->
                        new MerkleHederaState(
                                (tree, ver) -> loadedRegistry.migrate(tree),
                                event -> {},
                                (round, dualState) -> {});
        final var pair = new ClassConstructorPair(MerkleHederaState.class, constructor);
        registry.registerConstructable(pair);

        // We are NOW READY To read from disk!!
        final MerkleHederaState loadedTree = parseTree(serializedBytes, path);

        final var states = loadedTree.createReadableStates(FIRST_SERVICE);
        final var fruitState = states.get(FRUIT_STATE_KEY);
        assertThat(fruitState.get(A_KEY)).get().isEqualTo(APPLE);
        assertThat(fruitState.get(B_KEY)).get().isEqualTo(BANANA);
        assertThat(fruitState.get(C_KEY)).get().isEqualTo(CHERRY);
        assertThat(fruitState.get(D_KEY)).get().isEqualTo(DATE);
        assertThat(fruitState.get(E_KEY)).get().isEqualTo(EGGPLANT);
        assertThat(fruitState.get(F_KEY)).get().isEqualTo(FIG);
        assertThat(fruitState.get(G_KEY)).get().isEqualTo(GRAPE);

        final var animalState = states.get(ANIMAL_STATE_KEY);
        assertThat(animalState.get(A_KEY)).get().isEqualTo(AARDVARK);
        assertThat(animalState.get(B_KEY)).get().isEqualTo(BEAR);
        assertThat(animalState.get(C_KEY)).get().isEqualTo(CUTTLEFISH);
        assertThat(animalState.get(D_KEY)).get().isEqualTo(DOG);
        assertThat(animalState.get(E_KEY)).get().isEqualTo(EMU);
        assertThat(animalState.get(F_KEY)).get().isEqualTo(FOX);
        assertThat(animalState.get(G_KEY)).get().isEqualTo(GOOSE);
    }

    /**
     * In this test scenario, we will have a saved state with all types of FRUIT and ANIMALS saved
     * to the state. Then, we will use the {@link MerkleStateRegistry} to convert FRUIT to STEAM,
     * use FRUIT and ANIMALS to create SCIENCE, and create a fully populated COUNTRY, and to delete
     * FRUIT.
     */
}
