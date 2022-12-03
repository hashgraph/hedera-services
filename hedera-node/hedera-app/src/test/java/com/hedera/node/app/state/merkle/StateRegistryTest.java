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

import com.hedera.node.app.spi.state.*;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.merkle.map.MerkleMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StateRegistryTest {
    private static final String TEST_SERVICE_NAME = "My Fancy Service";
    private ServiceStateNode serviceMerkle;
    private ConstructableRegistry constructableRegistry;
    private final SoftwareVersion currentVersion = new BasicSoftwareVersion(2);
    private final SoftwareVersion previousVersion = new BasicSoftwareVersion(1);
    private final Parser<String> parser = (in, ver) -> null;
    private final Writer<String> writer = (val, out) -> {};

    @BeforeEach
    void setUp() {
        serviceMerkle = new ServiceStateNode(TEST_SERVICE_NAME);
        constructableRegistry = ConstructableRegistry.getInstance();
        constructableRegistry.reset();
    }

    @Nested
    @DisplayName("Constructor Tests")
    final class ConstructorTest {
        @Test
        @DisplayName("Constructor requires a non-null ConstructableRegistry")
        void nullRegistryThrows() {
            //noinspection ConstantConditions
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new StateRegistryImpl(
                                    null, TEST_SERVICE_NAME, currentVersion, previousVersion));
        }

        @Test
        @DisplayName("Constructor requires a non-null service name")
        void nullServiceNodeThrows() {
            //noinspection ConstantConditions
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new StateRegistryImpl(
                                    constructableRegistry, null, currentVersion, previousVersion));
        }

        @Test
        @DisplayName("Constructor requires a non-null current version")
        void nullCurrentVersionThrows() {
            //noinspection ConstantConditions
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new StateRegistryImpl(
                                    constructableRegistry,
                                    TEST_SERVICE_NAME,
                                    null,
                                    previousVersion));
        }

        @Test
        @DisplayName("Constructor accepts a null existing version")
        void nullPreviousVersion() {
            final var reg =
                    new StateRegistryImpl(
                            constructableRegistry, TEST_SERVICE_NAME, currentVersion, null);
            assertNull(reg.getExistingVersion());
        }

        @Test
        @DisplayName("Current and existing versions are returned after construction")
        void checkVersions() {
            final var reg =
                    new StateRegistryImpl(
                            constructableRegistry,
                            TEST_SERVICE_NAME,
                            currentVersion,
                            previousVersion);
            assertEquals(currentVersion, reg.getCurrentVersion());
            assertSame(previousVersion, reg.getExistingVersion());
        }
    }

    @Nested
    @DisplayName("Registration Tests")
    final class RegistrationTest {
        private StateRegistryImpl reg;

        @BeforeEach
        void setUp() {
            this.reg =
                    new StateRegistryImpl(
                            constructableRegistry,
                            TEST_SERVICE_NAME,
                            currentVersion,
                            previousVersion);
        }

        @Test
        @DisplayName("Register a state that does not exist")
        void noSuchState() {
            final var stateKey = "Some New StateKey";
            reg.register(stateKey)
                    .keyParser(parser)
                    .valueParser(parser)
                    .keyWriter(writer)
                    .valueWriter(writer)
                    .memory()
                    .complete();

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(serviceMerkle);
            assertNotNull(serviceMerkle.find(stateKey));
        }

        @Test
        @DisplayName("Register a state that exists in a previous release")
        void stateExistsAndHasNotChanged() {
            final var stateKey = "Some Existing StateKey";
            reg.register(stateKey)
                    .keyParser(parser)
                    .valueParser(parser)
                    .keyWriter(writer)
                    .valueWriter(writer)
                    .memory()
                    .complete();

            // Preload the service merkle with something that exits before we start migration
            serviceMerkle.put(stateKey, new MerkleMap<>());

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(serviceMerkle);
            assertNotNull(serviceMerkle.find(stateKey));
        }

        @Test
        @DisplayName("Register a state twice")
        void registerTwice() {
            final var stateKey = "Some StateKey";
            for (int i = 0; i < 2; i++) {
                reg.register(stateKey)
                        .keyParser(parser)
                        .valueParser(parser)
                        .keyWriter(writer)
                        .valueWriter(writer)
                        .memory()
                        .complete();
            }

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(serviceMerkle);
            assertNotNull(serviceMerkle.find(stateKey));
        }

        @ParameterizedTest(name = "with bad name {0}")
        @ValueSource(strings = {"", "\uD83D\uDE08", "Valid Other Than Punctuation!"})
        @DisplayName("Register with a syntactically invalid state key")
        void badStateKeys(final String badStateKey) {
            assertThrows(IllegalArgumentException.class, () -> reg.register(badStateKey));
        }

        @ParameterizedTest(name = "with good name {0}")
        @ValueSource(strings = {" ", "1", "1 2 3", "A", "a", "Az 3"})
        @DisplayName("Register with a syntactically valid state key")
        void goodStateKeys(final String goodStateKey) {
            reg.register(goodStateKey)
                    .keyParser(parser)
                    .valueParser(parser)
                    .keyWriter(writer)
                    .valueWriter(writer)
                    .memory()
                    .complete();

            // Preload the service merkle with something that exits before we start migration
            serviceMerkle.put(goodStateKey, new MerkleMap<>());

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(serviceMerkle);
            assertNotNull(serviceMerkle.find(goodStateKey));
        }

        @Test
        @DisplayName("Fail to register a key parser")
        void missingKeyParser() {
            final var builder =
                    reg.register("My State Key")
                            .valueParser(parser)
                            .keyWriter(writer)
                            .valueWriter(writer)
                            .memory();

            assertThrows(IllegalStateException.class, builder::complete);
        }

        @Test
        @DisplayName("Fail to register a key writer")
        void missingKeyWriter() {
            final var builder =
                    reg.register("My State Key")
                            .keyParser(parser)
                            .valueParser(parser)
                            .valueWriter(writer)
                            .memory();

            assertThrows(IllegalStateException.class, builder::complete);
        }

        @Test
        @DisplayName("Fail to register a value parser")
        void missingValueParser() {
            final var builder =
                    reg.register("My State Key")
                            .keyParser(parser)
                            .keyWriter(writer)
                            .valueWriter(writer)
                            .memory();

            assertThrows(IllegalStateException.class, builder::complete);
        }

        @Test
        @DisplayName("Fail to register a value writer")
        void missingValueWriter() {
            final var builder =
                    reg.register("My State Key")
                            .keyParser(parser)
                            .valueParser(parser)
                            .keyWriter(writer)
                            .memory();

            assertThrows(IllegalStateException.class, builder::complete);
        }

        @Test
        @DisplayName("Fail to select either in-memory or on-disk")
        void missingInMemoryAndOnDisk() {
            final var builder =
                    reg.register("My State Key")
                            .keyParser(parser)
                            .valueParser(parser)
                            .keyWriter(writer)
                            .valueWriter(writer);

            assertThrows(IllegalStateException.class, builder::complete);
        }

        @Test
        @DisplayName("Register for in-memory")
        void inMemory() {
            final var stateKey = "My State Key";
            reg.register(stateKey)
                    .keyParser(parser)
                    .valueParser(parser)
                    .keyWriter(writer)
                    .valueWriter(writer)
                    .memory()
                    .complete();

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(serviceMerkle);
            assertNotNull(serviceMerkle.find(stateKey));
        }

        @Test
        @DisplayName("Register for on-disk")
        @Disabled("Disabled until onDisk implementation is done")
        void onDisk() {
            final var stateKey = "My State Key";
            reg.register(stateKey)
                    .keyParser(parser)
                    .valueParser(parser)
                    .keyWriter(writer)
                    .valueWriter(writer)
                    .disk()
                    .complete();

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(serviceMerkle);
            assertNotNull(serviceMerkle.find(stateKey));
        }

        @Test
        @DisplayName("Register a migration but with nothing to migrate from")
        void migrate() {
            final var stateKey = "My State Key";
            reg.register(stateKey)
                    .keyParser(parser)
                    .valueParser(parser)
                    .keyWriter(writer)
                    .valueWriter(writer)
                    .memory()
                    .onMigrate(
                            (newState, oldStates) -> {
                                assertNotNull(newState);
                                assertNotNull(oldStates);
                                assertTrue(oldStates.isEmpty());
                            })
                    .complete();

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(serviceMerkle);
            assertNotNull(serviceMerkle.find(stateKey));
        }

        @Test
        @DisplayName("Register a migration with one state to migrate from")
        void migrateFromOneState() {
            final var stateKey = "NewState";
            reg.register(stateKey)
                    .keyParser(parser)
                    .valueParser(parser)
                    .keyWriter(writer)
                    .valueWriter(writer)
                    .memory()
                    .migrateFrom("OldState", parser, parser, writer, writer)
                    .onMigrate(
                            (newState, oldStates) -> {
                                assertNotNull(newState);
                                assertNotNull(oldStates);
                                assertEquals(1, oldStates.size());
                                assertTrue(oldStates.containsKey("OldState"));
                                assertNotNull(oldStates.get("OldState"));
                            })
                    .complete();

            // Let's put some "old" state in there
            serviceMerkle.put("OldState", new MerkleMap<>());

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(serviceMerkle);
            assertNotNull(serviceMerkle.find(stateKey));
        }

        @Test
        @DisplayName("Register a migration but with many states to migrate from")
        void migrateFromManyStates() {
            final var stateKey = "NewState";
            reg.register(stateKey)
                    .keyParser(parser)
                    .valueParser(parser)
                    .keyWriter(writer)
                    .valueWriter(writer)
                    .memory()
                    .migrateFrom("OldState1", parser, parser, writer, writer)
                    .migrateFrom("OldState2", parser, parser, writer, writer)
                    .migrateFrom("OldState3", parser, parser, writer, writer)
                    .onMigrate(
                            (newState, oldStates) -> {
                                assertNotNull(newState);
                                assertNotNull(oldStates);
                                assertEquals(3, oldStates.size());
                                assertTrue(oldStates.containsKey("OldState1"));
                                assertNotNull(oldStates.get("OldState1"));
                                assertTrue(oldStates.containsKey("OldState2"));
                                assertNotNull(oldStates.get("OldState2"));
                                assertTrue(oldStates.containsKey("OldState3"));
                                assertNotNull(oldStates.get("OldState3"));
                            })
                    .complete();

            // Let's put some "old" state in there
            serviceMerkle.put("OldState1", new MerkleMap<>());
            serviceMerkle.put("OldState2", new MerkleMap<>());
            serviceMerkle.put("OldState3", new MerkleMap<>());

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(serviceMerkle);
            assertNotNull(serviceMerkle.find(stateKey));
        }

        @Test
        @DisplayName("Register a migration with one state to migrate from which doesn't exist")
        void migrateFromNonExistingState() {
            final var stateKey = "NewState";
            reg.register(stateKey)
                    .keyParser(parser)
                    .valueParser(parser)
                    .keyWriter(writer)
                    .valueWriter(writer)
                    .memory()
                    .migrateFrom("OldState", parser, parser, writer, writer)
                    .onMigrate(
                            (newState, oldStates) -> {
                                assertNotNull(newState);
                                assertNotNull(oldStates);
                                assertTrue(oldStates.isEmpty());
                            })
                    .complete();

            // NOTE: We ARE NOT adding the OldState to the serviceMerkle, so it
            // doesn't exist in the old state.

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(serviceMerkle);
            assertNotNull(serviceMerkle.find(stateKey));
        }

        @Test
        @DisplayName(
                "Register a migration but with many states to migrate from, some of which don't"
                        + " exist")
        void migrateFromMissingStates() {
            final var stateKey = "NewState";
            reg.register(stateKey)
                    .keyParser(parser)
                    .valueParser(parser)
                    .keyWriter(writer)
                    .valueWriter(writer)
                    .memory()
                    .migrateFrom("OldState1", parser, parser, writer, writer)
                    .migrateFrom("OldState2", parser, parser, writer, writer)
                    .migrateFrom("OldState3", parser, parser, writer, writer)
                    .onMigrate(
                            (newState, oldStates) -> {
                                assertNotNull(newState);
                                assertNotNull(oldStates);
                                assertEquals(1, oldStates.size());
                                assertFalse(oldStates.containsKey("OldState1"));
                                assertNull(oldStates.get("OldState1"));
                                assertTrue(oldStates.containsKey("OldState2"));
                                assertNotNull(oldStates.get("OldState2"));
                                assertFalse(oldStates.containsKey("OldState3"));
                                assertNull(oldStates.get("OldState3"));
                            })
                    .complete();

            // Let's put some "old" state in there, but SKIP OldState1 and OldState3
            serviceMerkle.put("OldState2", new MerkleMap<>());

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(serviceMerkle);
            assertNotNull(serviceMerkle.find(stateKey));
        }
    }

    @Nested
    @DisplayName("Removal Tests")
    final class RemovalTest {
        private StateRegistryImpl reg;

        @BeforeEach
        void setUp() {
            this.reg =
                    new StateRegistryImpl(
                            constructableRegistry,
                            TEST_SERVICE_NAME,
                            currentVersion,
                            previousVersion);
        }

        @Test
        @DisplayName("Remove with a null state key")
        void removeNull() {
            //noinspection ConstantConditions
            assertThrows(
                    NullPointerException.class,
                    () -> reg.remove(null, parser, parser, writer, writer));
        }

        @ParameterizedTest(name = "with bad name {0}")
        @ValueSource(strings = {"", "\uD83D\uDE08", "Valid Other Than Punctuation!"})
        @DisplayName("Remove with a variety of illegal state keys")
        void badStateKey(final String badKey) {
            //noinspection ConstantConditions
            assertThrows(
                    IllegalArgumentException.class,
                    () -> reg.remove(badKey, parser, parser, writer, writer));
        }

        @ParameterizedTest(name = "with good name {0}")
        @ValueSource(strings = {" ", "1", "1 2 3", "A", "a", "Az 3"})
        void goodStateKey(final String goodKey) {
            // Won't throw on a good name, and won't exist when we're done
            reg.remove(goodKey, parser, parser, writer, writer);
            reg.migrate(serviceMerkle);
            assertNull(serviceMerkle.find(goodKey));
        }

        @Test
        @DisplayName("Remove a state that does not exist")
        void removeUnknownKey() {
            reg.remove("MyKey", parser, parser, writer, writer);
            reg.migrate(serviceMerkle);
            assertNull(serviceMerkle.find("MyKey"));
        }

        @Test
        @DisplayName("Remove a state from a previous saved state")
        void remove() {
            final var stateKey = "Old Key";
            reg.remove(stateKey, parser, parser, writer, writer);
            serviceMerkle.put(stateKey, new MerkleMap<>());
            reg.migrate(serviceMerkle);
            assertNull(serviceMerkle.find(stateKey));
        }

        @Test
        @DisplayName("Remove a state that was just registered")
        void removeNewKey() {
            final var stateKey = "My State Key";
            reg.register(stateKey)
                    .keyParser(parser)
                    .valueParser(parser)
                    .keyWriter(writer)
                    .valueWriter(writer)
                    .disk()
                    .complete();

            reg.remove(stateKey, parser, parser, writer, writer);
            reg.migrate(serviceMerkle);
            assertNull(serviceMerkle.find(stateKey));
        }

        @Test
        @DisplayName("Remove a state twice")
        void removeTwice() {
            final var stateKey = "Old Key";
            reg.remove(stateKey, parser, parser, writer, writer);
            reg.remove(stateKey, parser, parser, writer, writer);
            serviceMerkle.put(stateKey, new MerkleMap<>());
            reg.migrate(serviceMerkle);
            assertNull(serviceMerkle.find(stateKey));
        }

        @Test
        @DisplayName("Remove a state without specifying the key parser")
        void missingKeyParser() {
            //noinspection ConstantConditions
            assertThrows(
                    NullPointerException.class,
                    () -> reg.remove("Key", null, parser, writer, writer));
        }

        @Test
        @DisplayName("Remove a state without specifying the key writer")
        void missingKeyWriter() {
            //noinspection ConstantConditions
            assertThrows(
                    NullPointerException.class,
                    () -> reg.remove("Key", parser, parser, null, writer));
        }

        @Test
        @DisplayName("Remove a state without specifying the value parser")
        void missingValueParser() {
            //noinspection ConstantConditions
            assertThrows(
                    NullPointerException.class,
                    () -> reg.remove("Key", parser, null, writer, writer));
        }

        @Test
        @DisplayName("Remove a state without specifying the value writer")
        void missingValueWriter() {
            //noinspection ConstantConditions
            assertThrows(
                    NullPointerException.class,
                    () -> reg.remove("Key", parser, parser, writer, null));
        }
    }

    /** Various tests for serialization and deserialization */
    @Nested
    @DisplayName("Serdes")
    final class SerdesTest extends MerkleTestBase {

        /** Tests for serialization and deserialization */
        @Test
        @DisplayName("Serialization and deserialization")
        void serializesAndDeserializes(@TempDir Path tempDir)
                throws IOException, ConstructableRegistryException {
            // Pretend this is run #1 of the application, and it is some older version.
            // We will create a registry, register our state, "migrate" to create things,
            // get the InMemoryState for the state, put some stuff into it, and then write
            // to "disk".
            final var oldStateKey = "String Based State";
            final var oldReg =
                    new StateRegistryImpl(
                            constructableRegistry,
                            TEST_SERVICE_NAME,
                            currentVersion,
                            previousVersion);
            oldReg.register(oldStateKey)
                    .keyParser(MerkleTestBase::parseString)
                    .valueParser(MerkleTestBase::parseString)
                    .keyWriter(MerkleTestBase::writeString)
                    .valueWriter(MerkleTestBase::writeString)
                    .memory()
                    .complete();
            oldReg.migrate(serviceMerkle);

            final MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> oldMerkleMap =
                    Objects.requireNonNull(serviceMerkle.find(oldStateKey));
            final var oldState =
                    new InMemoryState<>(
                            oldStateKey,
                            oldMerkleMap,
                            StateUtils.computeClassId(serviceMerkle.getServiceName(), oldStateKey),
                            MerkleTestBase::parseString,
                            MerkleTestBase::parseString,
                            MerkleTestBase::writeString,
                            MerkleTestBase::writeString);

            oldState.put("A", "Apple");
            oldState.put("B", "Banana");
            oldState.commit();

            final var stateFile = writeTree(serviceMerkle, tempDir);

            // Now, pretend we are starting up the app on a new version. We are going
            // to parse the state file using the same string parsers and writers that
            // we used to write the values. But this time, we're going to go through
            // some kind of migration and switch from a "string" based state to an
            // "int" based state, and in the migration we'll count characters from the
            // old state as the values in the new state.
            final var newStateKey = "1234567890 Based State";
            // TODO Shouldn't onMigrate fail to get called if the current and previous versions are
            // the same?
            final var newReg =
                    new StateRegistryImpl(
                            constructableRegistry,
                            TEST_SERVICE_NAME,
                            currentVersion,
                            previousVersion);
            newReg.register(newStateKey)
                    .keyParser(MerkleTestBase::parseString)
                    .valueParser(MerkleTestBase::parseInteger)
                    .keyWriter(MerkleTestBase::writeString)
                    .valueWriter(MerkleTestBase::writeInteger)
                    .memory()
                    .migrateFrom(
                            oldStateKey,
                            MerkleTestBase::parseString,
                            MerkleTestBase::parseString,
                            MerkleTestBase::writeString,
                            MerkleTestBase::writeString)
                    .onMigrate(
                            (newState, oldStates) -> {
                                // TODO I have a problem with generics here, and it is the fault of
                                // map. Use my own interface?
                                final ReadableState<String, String> old =
                                        oldStates.get(oldStateKey);
                                // TODO Oh no! The State objects need a way to traverse everything
                                // in them. I don't have a way!
                                newState.put("A", old.get("A").orElse("").length());
                                newState.put("B", old.get("B").orElse("").length());
                            })
                    .complete();
            newReg.remove(
                    oldStateKey,
                    MerkleTestBase::parseString,
                    MerkleTestBase::parseString,
                    MerkleTestBase::writeString,
                    MerkleTestBase::writeString);

            // Parse the data file
            serviceMerkle = parseTree(stateFile, tempDir);
            // Do the migration
            newReg.migrate(serviceMerkle);
            // Everything set in "newState" must have been committed
            final MerkleMap<InMemoryKey<String>, InMemoryValue<String, Integer>> newMerkleMap =
                    Objects.requireNonNull(serviceMerkle.find(newStateKey));
            final var newState =
                    new InMemoryState<>(
                            oldStateKey,
                            newMerkleMap,
                            StateUtils.computeClassId(serviceMerkle.getServiceName(), newStateKey),
                            MerkleTestBase::parseString,
                            MerkleTestBase::parseInteger,
                            MerkleTestBase::writeString,
                            MerkleTestBase::writeInteger);
            assertEquals("Apple".length(), newState.get("A").orElse(0));
            assertEquals("Banana".length(), newState.get("B").orElse(0));
        }
    }
}
