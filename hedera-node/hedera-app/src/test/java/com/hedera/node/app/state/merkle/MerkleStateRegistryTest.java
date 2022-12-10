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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.node.app.spi.state.ReadableState;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.SoftwareVersion;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MerkleStateRegistryTest extends MerkleTestBase {
    private @TempDir Path path;
    private MerkleHederaState hederaMerkle;
    private ConstructableRegistry constructableRegistry;
    private final SoftwareVersion currentVersion = new BasicSoftwareVersion(2);
    private final SoftwareVersion previousVersion = new BasicSoftwareVersion(1);

    private MerkleStateRegistry reg; // registry for FIRST_SERVICE
    private MerkleStateRegistry reg2; // registry for SECOND_SERVICE

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        constructableRegistry = ConstructableRegistry.getInstance();
        constructableRegistry.reset();
        hederaMerkle = new MerkleHederaState((tree, ver) -> {}, evt -> {}, (round, dual) -> {});
        this.reg =
                new MerkleStateRegistry(
                        constructableRegistry,
                        path,
                        FIRST_SERVICE,
                        currentVersion,
                        previousVersion);
        this.reg2 =
                new MerkleStateRegistry(
                        constructableRegistry,
                        path,
                        SECOND_SERVICE,
                        currentVersion,
                        previousVersion);
    }

    MerkleNode findFruitNode() {
        return getNodeForLabel(hederaMerkle, FIRST_SERVICE, FRUIT_STATE_KEY);
    }

    MerkleNode findSteamNode() {
        return getNodeForLabel(hederaMerkle, SECOND_SERVICE, STEAM_STATE_KEY);
    }

    MerkleNode findSpaceNode() {
        return getNodeForLabel(hederaMerkle, SECOND_SERVICE, SPACE_STATE_KEY);
    }

    MerkleNode findCountryNode() {
        return getNodeForLabel(hederaMerkle, SECOND_SERVICE, COUNTRY_STATE_KEY);
    }

    @Nested
    @DisplayName("Constructor Tests")
    final class ConstructorTest {
        @Test
        @DisplayName("Constructor requires a non-null ConstructableRegistry")
        void nullRegistryThrows() {
            assertThatThrownBy(
                            () -> {
                                //noinspection DataFlowIssue
                                new MerkleStateRegistry(
                                        null, path, FIRST_SERVICE, currentVersion, previousVersion);
                            })
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Constructor requires a non-null path")
        void nullPathThrows() {
            assertThatThrownBy(
                            () -> {
                                //noinspection DataFlowIssue
                                new MerkleStateRegistry(
                                        constructableRegistry,
                                        null,
                                        FIRST_SERVICE,
                                        currentVersion,
                                        previousVersion);
                            })
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Constructor requires a non-null service name")
        void nullServiceNodeThrows() {
            //noinspection ConstantConditions
            assertThatThrownBy(
                            () -> {
                                //noinspection DataFlowIssue
                                new MerkleStateRegistry(
                                        constructableRegistry,
                                        path,
                                        null,
                                        currentVersion,
                                        previousVersion);
                            })
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Constructor requires a non-null current version")
        void nullCurrentVersionThrows() {
            assertThatThrownBy(
                            () -> {
                                //noinspection DataFlowIssue
                                new MerkleStateRegistry(
                                        constructableRegistry,
                                        path,
                                        FIRST_SERVICE,
                                        null,
                                        previousVersion);
                            })
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Constructor accepts a null existing version")
        void nullPreviousVersion() {
            final var reg =
                    new MerkleStateRegistry(
                            constructableRegistry, path, FIRST_SERVICE, currentVersion, null);
            assertThat(reg.getPreviousVersion()).isNull();
        }

        @Test
        @DisplayName("Current and existing versions are returned after construction")
        void checkVersions() {
            final var reg =
                    new MerkleStateRegistry(
                            constructableRegistry,
                            path,
                            FIRST_SERVICE,
                            currentVersion,
                            previousVersion);
            assertThat(reg.getCurrentVersion()).isSameAs(currentVersion);
            assertThat(reg.getPreviousVersion()).isSameAs(previousVersion);
        }
    }

    @Nested
    @DisplayName("Registration Tests")
    final class RegistrationTest {
        @Test
        @DisplayName("Register a state that does not exist at genesis")
        void noSuchState() {
            // When we register a new state
            reg.register(FRUIT_STATE_KEY)
                    .keyParser(MerkleTestBase::parseString)
                    .valueParser(MerkleTestBase::parseString)
                    .keyWriter(MerkleTestBase::writeString)
                    .valueWriter(MerkleTestBase::writeString)
                    .memory()
                    .complete();

            // Then before we migrate, we should find that nothing has been created yet
            assertThat(findFruitNode()).isNull();

            // When we migrate, then we find the state has been created
            reg.migrate(hederaMerkle);
            assertThat(findFruitNode()).isNotNull();
        }

        @Test
        @DisplayName("Register a state that exists in a previous release and is unchanged")
        void stateExistsAndHasNotChanged() {
            // The lifecycle here is that we register, then load state, then migrate.
            // So first, we register our interest in a service
            reg.register(FRUIT_STATE_KEY)
                    .keyParser(MerkleTestBase::parseString)
                    .valueParser(MerkleTestBase::parseString)
                    .keyWriter(MerkleTestBase::writeString)
                    .valueWriter(MerkleTestBase::writeString)
                    .memory()
                    .complete();

            // And given that this service exists in the saved state (we create it here to simulate
            // loading it)
            hederaMerkle.putServiceStateIfAbsent(fruitMetadata, fruitMerkleMap);

            // When we perform migration, then we find the state is still available
            reg.migrate(hederaMerkle);
            assertThat(findFruitNode()).isNotNull();
        }

        @Test
        @DisplayName("Register a state twice")
        void registerTwice() {
            for (int i = 0; i < 2; i++) {
                reg.register(FRUIT_STATE_KEY)
                        .keyParser(MerkleTestBase::parseString)
                        .valueParser(MerkleTestBase::parseString)
                        .keyWriter(MerkleTestBase::writeString)
                        .valueWriter(MerkleTestBase::writeString)
                        .memory()
                        .complete();
            }

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(hederaMerkle);
            assertThat(findFruitNode()).isNotNull();
        }

        //        @ParameterizedTest(name = "with bad name {0}")
        //        @ValueSource(strings = {"", "\uD83D\uDE08", "Valid Other Than Punctuation!"})
        //        @DisplayName("Register with a syntactically invalid state key")
        //        void badStateKeys(final String badStateKey) {
        //            assertThrows(IllegalArgumentException.class, () -> reg.register(badStateKey));
        //        }
        //
        //        @ParameterizedTest(name = "with good name {0}")
        //        @ValueSource(strings = {" ", "1", "1 2 3", "A", "a", "Az 3"})
        //        @DisplayName("Register with a syntactically valid state key")
        //        void goodStateKeys(final String goodStateKey) {
        //            reg.register(goodStateKey)
        //                    .keyParser(parser)
        //                    .valueParser(parser)
        //                    .keyWriter(writer)
        //                    .valueWriter(writer)
        //                    .memory()
        //                    .complete();
        //
        //            // Preload the service merkle with something that exits before we start
        // migration
        //            hederaMerkle.put(goodStateKey, new MerkleMap<>());
        //
        //            // Finalize the creation and check that the actual merkle stuff exists now
        //            reg.migrate(hederaMerkle);
        //            assertNotNull(hederaMerkle.find(goodStateKey));
        //        }

        @Test
        @DisplayName("Fail to register a key parser")
        void missingKeyParser() {
            final var builder =
                    reg.register(FRUIT_STATE_KEY)
                            .valueParser(MerkleTestBase::parseString)
                            .keyWriter(MerkleTestBase::writeString)
                            .valueWriter(MerkleTestBase::writeString)
                            .memory();

            assertThatThrownBy(builder::complete).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Fail to register a key writer")
        void missingKeyWriter() {
            final var builder =
                    reg.register(FRUIT_STATE_KEY)
                            .keyParser(MerkleTestBase::parseString)
                            .valueParser(MerkleTestBase::parseString)
                            .valueWriter(MerkleTestBase::writeString)
                            .memory();

            assertThatThrownBy(builder::complete).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Fail to register a value parser")
        void missingValueParser() {
            final var builder =
                    reg.register(FRUIT_STATE_KEY)
                            .keyParser(MerkleTestBase::parseString)
                            .keyWriter(MerkleTestBase::writeString)
                            .valueWriter(MerkleTestBase::writeString)
                            .memory();

            assertThatThrownBy(builder::complete).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Fail to register a value writer")
        void missingValueWriter() {
            final var builder =
                    reg.register(FRUIT_STATE_KEY)
                            .keyParser(MerkleTestBase::parseString)
                            .valueParser(MerkleTestBase::parseString)
                            .keyWriter(MerkleTestBase::writeString)
                            .memory();

            assertThatThrownBy(builder::complete).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Fail to select either in-memory or on-disk")
        void missingInMemoryAndOnDisk() {
            final var builder =
                    reg.register(FRUIT_STATE_KEY)
                            .keyParser(MerkleTestBase::parseString)
                            .valueParser(MerkleTestBase::parseString)
                            .keyWriter(MerkleTestBase::writeString)
                            .valueWriter(MerkleTestBase::writeString);

            assertThatThrownBy(builder::complete).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Register for in-memory")
        void inMemory() {
            reg.register(FRUIT_STATE_KEY)
                    .keyParser(MerkleTestBase::parseString)
                    .valueParser(MerkleTestBase::parseString)
                    .keyWriter(MerkleTestBase::writeString)
                    .valueWriter(MerkleTestBase::writeString)
                    .memory()
                    .complete();

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(hederaMerkle);
            assertThat(findFruitNode()).isNotNull();
        }

        @Test
        @DisplayName("Register for on-disk")
        void onDisk() {
            reg.register(FRUIT_STATE_KEY)
                    .keyParser(MerkleTestBase::parseString)
                    .valueParser(MerkleTestBase::parseString)
                    .keyWriter(MerkleTestBase::writeString)
                    .valueWriter(MerkleTestBase::writeString)
                    .disk()
                    .keyLength(MerkleTestBase::measureString)
                    .maxNumOfKeys(100)
                    .complete();

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(hederaMerkle);
            assertThat(findFruitNode()).isNotNull();
        }

        @Test
        @DisplayName("The `keyLength` must be specified for on-disk states")
        void onDiskRequiresKeyLength() {
            assertThatThrownBy(
                            () -> {
                                reg.register(FRUIT_STATE_KEY)
                                        .keyParser(MerkleTestBase::parseString)
                                        .valueParser(MerkleTestBase::parseString)
                                        .keyWriter(MerkleTestBase::writeString)
                                        .valueWriter(MerkleTestBase::writeString)
                                        .disk()
                                        /*.keyLength(MerkleTestBase::measureString)*/
                                        // Omitted, leading to failure!
                                        .maxNumOfKeys(100)
                                        .complete();
                            })
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("The `maxNumOfKeys` must be specified for on-disk states")
        void onDiskRequiresMaxNumOfKeys() {
            assertThatThrownBy(
                            () -> {
                                reg.register(FRUIT_STATE_KEY)
                                        .keyParser(MerkleTestBase::parseString)
                                        .valueParser(MerkleTestBase::parseString)
                                        .keyWriter(MerkleTestBase::writeString)
                                        .valueWriter(MerkleTestBase::writeString)
                                        .disk()
                                        .keyLength(MerkleTestBase::measureString)
                                        /*.maxNumOfKeys(100)*/
                                        // Omitted, leading to failure!
                                        .complete();
                            })
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Migration Tests")
    class MigrateTest {
        @Test
        @DisplayName("Register a migration but with nothing to migrate from")
        void migrate() {
            reg.register(FRUIT_STATE_KEY)
                    .keyParser(MerkleTestBase::parseString)
                    .valueParser(MerkleTestBase::parseString)
                    .keyWriter(MerkleTestBase::writeString)
                    .valueWriter(MerkleTestBase::writeString)
                    .memory()
                    .onMigrate(
                            (oldStates, newState) -> {
                                assertThat(newState).isNotNull();
                                assertThat(oldStates).isNotNull();
                                assertThat(oldStates.isEmpty()).isTrue();
                            })
                    .complete();

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(hederaMerkle);
            assertThat(findFruitNode()).isNotNull();
        }

        /**
         * This is a rich test. The original state has "A" -> "ART", etc. The new state will map the
         * "A" to 0, "B" to 1, etc., so it is changing keys and data types of the keys. It is also
         * replacing the science values with the appropriate "space" themed values. And it is moving
         * from a merkle map to a virtual map. Quite a migration!
         */
        @Test
        @DisplayName("Register a migration with one state to migrate from")
        void migrateFromOneState() {
            final AtomicBoolean called = new AtomicBoolean(false);
            reg2.register(SPACE_STATE_KEY)
                    .keyParser(MerkleTestBase::parseLong)
                    .valueParser(MerkleTestBase::parseString)
                    .keyWriter(MerkleTestBase::writeLong)
                    .valueWriter(MerkleTestBase::writeString)
                    .keyLength(MerkleTestBase::measureLong)
                    .maxNumOfKeys(10)
                    .disk()
                    .addMigrationFrom(
                            STEAM_STATE_KEY,
                            MerkleTestBase::parseString,
                            MerkleTestBase::parseString,
                            MerkleTestBase::writeString,
                            MerkleTestBase::writeString,
                            MerkleTestBase::measureString)
                    .onMigrate(
                            (oldStates, newState) -> {
                                called.set(true);
                                assertThat(newState).isNotNull();
                                assertThat(oldStates).isNotNull();
                                assertThat(oldStates.size()).isEqualTo(1);
                                final ReadableState<String, String> oldState =
                                        oldStates.get(STEAM_STATE_KEY);
                                assertThat(oldState).isNotNull();

                                // A -> 0, B -> 1, etc. And use space themed values.
                                var val = oldState.get(A_KEY).orElse(null);
                                assertThat(val).isEqualTo(ART);
                                newState.put(A_LONG_KEY, ASTRONAUT);

                                val = oldState.get(B_KEY).orElse(null);
                                assertThat(val).isEqualTo(BIOLOGY);
                                newState.put(B_LONG_KEY, BLASTOFF);

                                val = oldState.get(C_KEY).orElse(null);
                                assertThat(val).isEqualTo(CHEMISTRY);
                                newState.put(C_LONG_KEY, COMET);

                                val = oldState.get(D_KEY).orElse(null);
                                assertThat(val).isEqualTo(DISCIPLINE);
                                newState.put(D_LONG_KEY, DRACO);

                                val = oldState.get(E_KEY).orElse(null);
                                assertThat(val).isEqualTo(ECOLOGY);
                                newState.put(E_LONG_KEY, EXOPLANET);

                                val = oldState.get(F_KEY).orElse(null);
                                assertThat(val).isEqualTo(FIELDS);
                                newState.put(F_LONG_KEY, FORCE);

                                val = oldState.get(G_KEY).orElse(null);
                                assertThat(val).isEqualTo(GEOMETRY);
                                newState.put(G_LONG_KEY, GRAVITY);
                            })
                    .complete();

            reg2.remove(
                    STEAM_STATE_KEY,
                    MerkleTestBase::parseString,
                    MerkleTestBase::parseString,
                    MerkleTestBase::writeString,
                    MerkleTestBase::writeString,
                    MerkleTestBase::measureString);

            // Let's put some "old" state into the merkle tree
            hederaMerkle.putServiceStateIfAbsent(steamMetadata, steamMerkleMap);
            add(steamMerkleMap, steamMetadata, A_KEY, ART);
            add(steamMerkleMap, steamMetadata, B_KEY, BIOLOGY);
            add(steamMerkleMap, steamMetadata, C_KEY, CHEMISTRY);
            add(steamMerkleMap, steamMetadata, D_KEY, DISCIPLINE);
            add(steamMerkleMap, steamMetadata, E_KEY, ECOLOGY);
            add(steamMerkleMap, steamMetadata, F_KEY, FIELDS);
            add(steamMerkleMap, steamMetadata, G_KEY, GEOMETRY);

            // Migrate, and check that the actual merkle stuff exists now
            reg2.migrate(hederaMerkle);
            assertThat(called).isTrue(); // Make sure the migrate method was called!
            assertThat(findSteamNode()).isNull();
            assertThat(findSpaceNode()).isNotNull();

            final var states = hederaMerkle.createReadableStates(SECOND_SERVICE);
            assertThat(states).isNotNull();

            final var spaceState = states.get(SPACE_STATE_KEY);
            assertThat(spaceState).isNotNull();

            assertThat(spaceState.get(A_LONG_KEY)).get().isEqualTo(ASTRONAUT);
            assertThat(spaceState.get(B_LONG_KEY)).get().isEqualTo(BLASTOFF);
            assertThat(spaceState.get(C_LONG_KEY)).get().isEqualTo(COMET);
            assertThat(spaceState.get(D_LONG_KEY)).get().isEqualTo(DRACO);
            assertThat(spaceState.get(E_LONG_KEY)).get().isEqualTo(EXOPLANET);
            assertThat(spaceState.get(F_LONG_KEY)).get().isEqualTo(FORCE);
            assertThat(spaceState.get(G_LONG_KEY)).get().isEqualTo(GRAVITY);
        }

        @Test
        @DisplayName("Register a migration but with more than one state to migrate from")
        void migrateFromManyStates() {
            final AtomicBoolean called = new AtomicBoolean(false);
            reg2.register(SPACE_STATE_KEY)
                    .keyParser(MerkleTestBase::parseLong)
                    .valueParser(MerkleTestBase::parseString)
                    .keyWriter(MerkleTestBase::writeLong)
                    .valueWriter(MerkleTestBase::writeString)
                    .keyLength(MerkleTestBase::measureLong)
                    .maxNumOfKeys(10)
                    .disk()
                    .addMigrationFrom(
                            STEAM_STATE_KEY,
                            MerkleTestBase::parseString,
                            MerkleTestBase::parseString,
                            MerkleTestBase::writeString,
                            MerkleTestBase::writeString,
                            MerkleTestBase::measureString)
                    .addMigrationFrom(
                            COUNTRY_STATE_KEY,
                            MerkleTestBase::parseString,
                            MerkleTestBase::parseString,
                            MerkleTestBase::writeString,
                            MerkleTestBase::writeString,
                            MerkleTestBase::measureString)
                    .onMigrate(
                            (oldStates, newState) -> {
                                called.set(true);
                                assertThat(newState).isNotNull();
                                assertThat(oldStates).isNotNull();
                                assertThat(oldStates.size()).isEqualTo(2);
                                final ReadableState<String, String> oldSteamState =
                                        oldStates.get(STEAM_STATE_KEY);
                                assertThat(oldSteamState).isNotNull();
                                final ReadableState<String, String> oldCountryState =
                                        oldStates.get(STEAM_STATE_KEY);
                                assertThat(oldCountryState).isNotNull();
                            })
                    .complete();

            reg2.remove(
                    STEAM_STATE_KEY,
                    MerkleTestBase::parseString,
                    MerkleTestBase::parseString,
                    MerkleTestBase::writeString,
                    MerkleTestBase::writeString,
                    MerkleTestBase::measureString);

            // Let's put some "old" state into the merkle tree
            hederaMerkle.putServiceStateIfAbsent(steamMetadata, steamMerkleMap);
            add(steamMerkleMap, steamMetadata, A_KEY, ART);
            hederaMerkle.putServiceStateIfAbsent(countryMetadata, countryMerkleMap);
            add(countryMerkleMap, countryMetadata, B_KEY, BRAZIL);

            // After migration, only the node I didn't explicitly remove should be gone
            reg2.migrate(hederaMerkle);
            assertThat(called).isTrue(); // Make sure the migrate method was called!
            assertThat(findSteamNode()).isNull();
            assertThat(findSpaceNode()).isNotNull();
            assertThat(findCountryNode()).isNotNull();
        }

        @Test
        @DisplayName("Register a migration with one state to migrate from which doesn't exist")
        void migrateFromNonExistingState() {
            final AtomicBoolean called = new AtomicBoolean(false);
            reg.register(FRUIT_STATE_KEY)
                    .keyParser(MerkleTestBase::parseString)
                    .valueParser(MerkleTestBase::parseString)
                    .keyWriter(MerkleTestBase::writeString)
                    .valueWriter(MerkleTestBase::writeString)
                    .memory()
                    .addMigrationFrom(
                            UNKNOWN_STATE_KEY,
                            MerkleTestBase::parseString,
                            MerkleTestBase::parseString,
                            MerkleTestBase::writeString,
                            MerkleTestBase::writeString,
                            null)
                    .onMigrate(
                            (oldStates, newState) -> {
                                called.set(true);
                                assertThat(newState).isNotNull();
                                assertThat(oldStates).isNotNull();
                                assertThat(oldStates.isEmpty()).isTrue();
                            })
                    .complete();

            // NOTE: We ARE NOT adding the OldState to the hederaMerkle, so it
            // doesn't exist in the old state.

            // Finalize the creation and check that the actual merkle stuff exists now
            reg.migrate(hederaMerkle);
            assertThat(called).isTrue();
            assertThat(findFruitNode()).isNotNull();
        }

        //        @Test
        //        @DisplayName(
        //                "Register a migration but with many states to migrate from, some of which
        // don't"
        //                        + " exist")
        //        void migrateFromMissingStates() {
        //            final var stateKey = "NewState";
        //            reg.register(stateKey)
        //                    .keyParser(parser)
        //                    .valueParser(parser)
        //                    .keyWriter(writer)
        //                    .valueWriter(writer)
        //                    .memory()
        //                    .addMigrationFrom("OldState1", parser, parser, writer, writer)
        //                    .addMigrationFrom("OldState2", parser, parser, writer, writer)
        //                    .addMigrationFrom("OldState3", parser, parser, writer, writer)
        //                    .onMigrate(
        //                            (oldStates, newState) -> {
        //                                assertNotNull(newState);
        //                                assertNotNull(oldStates);
        //                                assertEquals(1, oldStates.size());
        //                                assertNull(oldStates.get("OldState1"));
        //                                assertNotNull(oldStates.get("OldState2"));
        //                                assertNull(oldStates.get("OldState3"));
        //                            })
        //                    .complete();
        //
        //            // Let's put some "old" state in there, but SKIP OldState1 and OldState3
        //            hederaMerkle.put("OldState2", new MerkleMap<>());
        //
        //            // Finalize the creation and check that the actual merkle stuff exists now
        //            reg.migrate(hederaMerkle);
        //            assertNotNull(hederaMerkle.find(stateKey));
        //        }
    }
    //
    ////    @Nested
    ////    @DisplayName("Removal Tests")
    ////    final class RemovalTest {
    ////        private MerkleStateRegistry reg;
    ////
    ////        @BeforeEach
    ////        void setUp() {
    ////            this.reg =
    ////                    new MerkleStateRegistry(
    ////                            constructableRegistry,
    ////                            TEST_SERVICE_NAME,
    ////                            currentVersion,
    ////                            previousVersion);
    ////        }
    ////
    ////        @Test
    ////        @DisplayName("Remove with a null state key")
    ////        void removeNull() {
    ////            //noinspection ConstantConditions
    ////            assertThrows(
    ////                    NullPointerException.class,
    ////                    () -> reg.remove(null, parser, parser, writer, writer));
    ////        }
    ////
    ////        @ParameterizedTest(name = "with bad name {0}")
    ////        @ValueSource(strings = {"", "\uD83D\uDE08", "Valid Other Than Punctuation!"})
    ////        @DisplayName("Remove with a variety of illegal state keys")
    ////        void badStateKey(final String badKey) {
    ////            //noinspection ConstantConditions
    ////            assertThrows(
    ////                    IllegalArgumentException.class,
    ////                    () -> reg.remove(badKey, parser, parser, writer, writer));
    ////        }
    ////
    ////        @ParameterizedTest(name = "with good name {0}")
    ////        @ValueSource(strings = {" ", "1", "1 2 3", "A", "a", "Az 3"})
    ////        void goodStateKey(final String goodKey) {
    ////            // Won't throw on a good name, and won't exist when we're done
    ////            reg.remove(goodKey, parser, parser, writer, writer);
    ////            reg.migrate(hederaMerkle);
    ////            assertNull(hederaMerkle.find(goodKey));
    ////        }
    ////
    ////        @Test
    ////        @DisplayName("Remove a state that does not exist")
    ////        void removeUnknownKey() {
    ////            reg.remove("MyKey", parser, parser, writer, writer);
    ////            reg.migrate(hederaMerkle);
    ////            assertNull(hederaMerkle.find("MyKey"));
    ////        }
    ////
    ////        @Test
    ////        @DisplayName("Remove a state from a previous saved state")
    ////        void remove() {
    ////            final var stateKey = "Old Key";
    ////            reg.remove(stateKey, parser, parser, writer, writer);
    ////            hederaMerkle.put(stateKey, new MerkleMap<>());
    ////            reg.migrate(hederaMerkle);
    ////            assertNull(hederaMerkle.find(stateKey));
    ////        }
    ////
    ////        @Test
    ////        @DisplayName("Remove a state that was just registered")
    ////        void removeNewKey() {
    ////            final var stateKey = "My State Key";
    ////            reg.register(stateKey)
    ////                    .keyParser(parser)
    ////                    .valueParser(parser)
    ////                    .keyWriter(writer)
    ////                    .valueWriter(writer)
    ////                    .disk()
    ////                    .complete();
    ////
    ////            reg.remove(stateKey, parser, parser, writer, writer);
    ////            reg.migrate(hederaMerkle);
    ////            assertNull(hederaMerkle.find(stateKey));
    ////        }
    ////
    ////        @Test
    ////        @DisplayName("Remove a state twice")
    ////        void removeTwice() {
    ////            final var stateKey = "Old Key";
    ////            reg.remove(stateKey, parser, parser, writer, writer);
    ////            reg.remove(stateKey, parser, parser, writer, writer);
    ////            hederaMerkle.put(stateKey, new MerkleMap<>());
    ////            reg.migrate(hederaMerkle);
    ////            assertNull(hederaMerkle.find(stateKey));
    ////        }
    ////
    ////        @Test
    ////        @DisplayName("Remove a state without specifying the key parser")
    ////        void missingKeyParser() {
    ////            //noinspection ConstantConditions
    ////            assertThrows(
    ////                    NullPointerException.class,
    ////                    () -> reg.remove("Key", null, parser, writer, writer));
    ////        }
    ////
    ////        @Test
    ////        @DisplayName("Remove a state without specifying the key writer")
    ////        void missingKeyWriter() {
    ////            //noinspection ConstantConditions
    ////            assertThrows(
    ////                    NullPointerException.class,
    ////                    () -> reg.remove("Key", parser, parser, null, writer));
    ////        }
    ////
    ////        @Test
    ////        @DisplayName("Remove a state without specifying the value parser")
    ////        void missingValueParser() {
    ////            //noinspection ConstantConditions
    ////            assertThrows(
    ////                    NullPointerException.class,
    ////                    () -> reg.remove("Key", parser, null, writer, writer));
    ////        }
    ////
    ////        @Test
    ////        @DisplayName("Remove a state without specifying the value writer")
    ////        void missingValueWriter() {
    ////            //noinspection ConstantConditions
    ////            assertThrows(
    ////                    NullPointerException.class,
    ////                    () -> reg.remove("Key", parser, parser, writer, null));
    ////        }
    ////    }
    ////
    ////    /** Various tests for serialization and deserialization */
    ////    @Nested
    ////    @DisplayName("Serdes")
    ////    final class SerdesTest extends MerkleTestBase {
    ////
    ////        /** Tests for serialization and deserialization */
    ////        @Test
    ////        @DisplayName("Serialization and deserialization")
    ////        void serializesAndDeserializes(@TempDir Path tempDir)
    ////                throws IOException, ConstructableRegistryException {
    ////            // Pretend this is run #1 of the application, and it is some older version.
    ////            // We will create a registry, register our state, "migrate" to create things,
    ////            // get the InMemoryState for the state, put some stuff into it, and then write
    ////            // to "disk".
    ////            final var oldStateKey = "String Based State";
    ////            final var oldReg =
    ////                    new MerkleStateRegistry(
    ////                            constructableRegistry,
    ////                            TEST_SERVICE_NAME,
    ////                            currentVersion,
    ////                            previousVersion);
    ////            oldReg.register(oldStateKey)
    ////                    .keyParser(MerkleTestBase::parseString)
    ////                    .valueParser(MerkleTestBase::parseString)
    ////                    .keyWriter(MerkleTestBase::writeString)
    ////                    .valueWriter(MerkleTestBase::writeString)
    ////                    .memory()
    ////                    .complete();
    ////            oldReg.migrate(hederaMerkle);
    ////
    ////            final MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> oldMerkleMap
    // =
    ////                    Objects.requireNonNull(hederaMerkle.find(oldStateKey));
    ////            final var oldState =
    ////                    new InMemoryState<>(
    ////                            oldStateKey,
    ////                            oldMerkleMap,
    ////                            StateUtils.computeValueClassId(hederaMerkle.getServiceName(),
    // oldStateKey),
    ////                            MerkleTestBase::parseString,
    ////                            MerkleTestBase::parseString,
    ////                            MerkleTestBase::writeString,
    ////                            MerkleTestBase::writeString);
    ////
    ////            oldState.put("A", "Apple");
    ////            oldState.put("B", "Banana");
    ////            oldState.commit();
    ////
    ////            final var stateFile = writeTree(hederaMerkle, tempDir);
    ////
    ////            // Now, pretend we are starting up the app on a new version. We are going
    ////            // to parse the state file using the same string parsers and writers that
    ////            // we used to write the values. But this time, we're going to go through
    ////            // some kind of migration and switch from a "string" based state to an
    ////            // "int" based state, and in the migration we'll count characters from the
    ////            // old state as the values in the new state.
    ////            final var newStateKey = "1234567890 Based State";
    ////            // TODO Shouldn't onMigrate fail to get called if the current and previous
    // versions are
    ////            // the same?
    ////            final var newReg =
    ////                    new MerkleStateRegistry(
    ////                            constructableRegistry,
    ////                            TEST_SERVICE_NAME,
    ////                            currentVersion,
    ////                            previousVersion);
    ////            newReg.register(newStateKey)
    ////                    .keyParser(MerkleTestBase::parseString)
    ////                    .valueParser(MerkleTestBase::parseInteger)
    ////                    .keyWriter(MerkleTestBase::writeString)
    ////                    .valueWriter(MerkleTestBase::writeInteger)
    ////                    .memory()
    ////                    .addMigrationFrom(
    ////                            oldStateKey,
    ////                            MerkleTestBase::parseString,
    ////                            MerkleTestBase::parseString,
    ////                            MerkleTestBase::writeString,
    ////                            MerkleTestBase::writeString)
    ////                    .onMigrate(
    ////                            (oldStates, newState) -> {
    ////                                // TODO I have a problem with generics here, and it is the
    // fault of
    ////                                // map. Use my own interface?
    ////                                final ReadableState<String, String> old =
    ////                                        oldStates.get(oldStateKey);
    ////                                // TODO Oh no! The State objects need a way to traverse
    // everything
    ////                                // in them. I don't have a way!
    ////                                newState.put("A", old.get("A").orElse("").length());
    ////                                newState.put("B", old.get("B").orElse("").length());
    ////                            })
    ////                    .complete();
    ////            newReg.remove(
    ////                    oldStateKey,
    ////                    MerkleTestBase::parseString,
    ////                    MerkleTestBase::parseString,
    ////                    MerkleTestBase::writeString,
    ////                    MerkleTestBase::writeString);
    ////
    ////            // Parse the data file
    ////            hederaMerkle = parseTree(stateFile, tempDir);
    ////            // Do the migration
    ////            newReg.migrate(hederaMerkle);
    ////            // Everything set in "newState" must have been committed
    ////            final MerkleMap<InMemoryKey<String>, InMemoryValue<String, Integer>>
    // newMerkleMap =
    ////                    Objects.requireNonNull(hederaMerkle.find(newStateKey));
    ////            final var newState =
    ////                    new InMemoryState<>(
    ////                            oldStateKey,
    ////                            newMerkleMap,
    ////                            StateUtils.computeValueClassId(hederaMerkle.getServiceName(),
    // newStateKey),
    ////                            MerkleTestBase::parseString,
    ////                            MerkleTestBase::parseInteger,
    ////                            MerkleTestBase::writeString,
    ////                            MerkleTestBase::writeInteger);
    ////            assertEquals("Apple".length(), newState.get("A").orElse(0));
    ////            assertEquals("Banana".length(), newState.get("B").orElse(0));
    ////        }
    ////    }
}
