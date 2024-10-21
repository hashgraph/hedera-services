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

package com.swirlds.platform;

import static com.swirlds.platform.state.address.AddressBookInitializer.CONFIG_ADDRESS_BOOK_HEADER;
import static com.swirlds.platform.state.address.AddressBookInitializer.CONFIG_ADDRESS_BOOK_USED;
import static com.swirlds.platform.state.address.AddressBookInitializer.STATE_ADDRESS_BOOK_HEADER;
import static com.swirlds.platform.state.address.AddressBookInitializer.STATE_ADDRESS_BOOK_NULL;
import static com.swirlds.platform.state.address.AddressBookInitializer.STATE_ADDRESS_BOOK_USED;
import static com.swirlds.platform.state.address.AddressBookInitializer.USED_ADDRESS_BOOK_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.config.AddressBookConfig_;
import com.swirlds.platform.roster.RosterAddressBookBuilder;
import com.swirlds.platform.roster.RosterRetriever;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.state.address.AddressBookInitializer;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomRosterBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.stubbing.OngoingStubbing;

class AddressBookInitializerTest {

    @TempDir
    Path testDirectory;

    @Test
    @DisplayName("Force the use of the config address book")
    void forceUseOfConfigAddressBook() throws IOException {
        final Randotron randotron = Randotron.create();
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook(randotron);
        final SignedState signedState = getMockSignedState7WeightRandomAddressBook(randotron);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                NodeId.of(0),
                getMockSoftwareVersion(2),
                // no software upgrade
                false,
                signedState,
                configAddressBook,
                getPlatformContext(true));
        final AddressBook inititializedAddressBook = initializer.getCurrentAddressBook();
        assertEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the config address book.");
        assertEquals(
                RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                initializer.getPreviousAddressBook(),
                "The previous address book must equal the state address book.");
        assertAddressBookFileContent(
                initializer,
                configAddressBook,
                RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                inititializedAddressBook);
        assertTrue(initializer.hasAddressBookChanged());
    }

    @Test
    @DisplayName("Genesis. Config.txt Initializes Address Book.")
    void noStateLoadedFromDisk() throws IOException {
        final Randotron randotron = Randotron.create();
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook(randotron);
        // initial state has no address books set.
        final SignedState signedState = getMockSignedState(10, null, null, true);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                NodeId.of(0),
                getMockSoftwareVersion(2),
                // no software upgrade
                false,
                signedState,
                configAddressBook,
                getPlatformContext(false));
        final AddressBook inititializedAddressBook = initializer.getCurrentAddressBook();
        assertEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the expected address book.");
        assertNull(initializer.getPreviousAddressBook(), "The previous address book should be null.");
        assertAddressBookFileContent(
                initializer,
                configAddressBook,
                signedState.getRoster() == null
                        ? null
                        : RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                inititializedAddressBook);
        assertTrue(initializer.hasAddressBookChanged());
    }

    @Test
    @DisplayName("No state loaded from disk. Genesis State set 0 weight.")
    void noStateLoadedFromDiskGenesisStateSetZeroWeight() throws IOException {
        final Randotron randotron = Randotron.create();
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook(randotron);
        // genesis state address book is set to null to test the code path where it may be null.
        final SignedState signedState = getMockSignedState(10, null, null, true);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                NodeId.of(0),
                getMockSoftwareVersion(2),
                // no software upgrade
                false,
                signedState,
                configAddressBook,
                getPlatformContext(false));
        final AddressBook inititializedAddressBook = initializer.getCurrentAddressBook();
        assertEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the config address book.");
        assertNull(initializer.getPreviousAddressBook(), "The previous address book should be null.");
        assertAddressBookFileContent(
                initializer,
                configAddressBook,
                signedState.getRoster() == null
                        ? null
                        : RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                inititializedAddressBook);
        assertTrue(initializer.hasAddressBookChanged());
    }

    @Test
    @DisplayName("No state loaded from disk. Genesis State modifies address book entries.")
    void noStateLoadedFromDiskGenesisStateChangedAddressBook() throws IOException {
        final Randotron randotron = Randotron.create();
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook(randotron);
        final SignedState signedState = getMockSignedState(7, configAddressBook, null, true);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                NodeId.of(0),
                getMockSoftwareVersion(2),
                // no software upgrade
                false,
                signedState,
                configAddressBook,
                getPlatformContext(false));
        final AddressBook inititializedAddressBook = initializer.getCurrentAddressBook();
        assertEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the config address book.");
        assertNull(initializer.getPreviousAddressBook(), "The previous address book should be null.");
        assertAddressBookFileContent(
                initializer,
                configAddressBook,
                RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                inititializedAddressBook);
        // Even when the genesis state has the correct address book, we always adopt the config.txt address book and
        // indicate an address book change.
        assertTrue(initializer.hasAddressBookChanged());
    }

    @Test
    @DisplayName("Current software version is equal to state software version.")
    void currentVersionEqualsStateVersion() throws IOException {
        final Randotron randotron = Randotron.create();
        clearTestDirectory();
        // start state with previous address book
        final SignedState signedState =
                getMockSignedState(2, getRandomAddressBook(randotron), getRandomAddressBook(randotron), false);
        final AddressBook configAddressBook =
                copyWithWeightChanges(RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()), 10);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                NodeId.of(0),
                getMockSoftwareVersion(2),
                // no software upgrade
                false,
                signedState,
                configAddressBook,
                getPlatformContext(false));
        final AddressBook inititializedAddressBook = initializer.getCurrentAddressBook();
        assertEquals(
                RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                inititializedAddressBook,
                "The initial address book must equal the state address book.");
        assertEquals(
                null,
                initializer.getPreviousAddressBook(),
                "When there is no upgrade, the address book should not change");
        assertAddressBookFileContent(
                initializer,
                configAddressBook,
                RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                inititializedAddressBook);
        // The addressBooks remain unchanged when there is no software upgrade.
        assertFalse(initializer.hasAddressBookChanged());
    }

    @Test
    @DisplayName("Version upgrade, SwirldState set 0 weight.")
    void versionUpgradeSwirldStateZeroWeight() throws IOException {
        final Randotron randotron = Randotron.create();
        clearTestDirectory();
        final SignedState signedState =
                getMockSignedState(0, getRandomAddressBook(randotron), getRandomAddressBook(randotron), false);
        final AddressBook configAddressBook =
                copyWithWeightChanges(RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()), 10);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                NodeId.of(0),
                getMockSoftwareVersion(3),
                // software upgrade
                true,
                signedState,
                configAddressBook,
                getPlatformContext(false));
        final AddressBook inititializedAddressBook = initializer.getCurrentAddressBook();
        assertEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the config address book.");
        assertEquals(
                RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                initializer.getPreviousAddressBook(),
                "The previous address book must equal the state address book.");
        assertAddressBookFileContent(
                initializer,
                configAddressBook,
                RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                inititializedAddressBook);
        assertTrue(initializer.hasAddressBookChanged());
    }

    @Test
    @DisplayName("Version upgrade, Swirld State modified the address book.")
    void versionUpgradeSwirldStateModifiedAddressBook() throws IOException {
        final Randotron randotron = Randotron.create();
        clearTestDirectory();
        final SignedState signedState =
                getMockSignedState(2, getRandomAddressBook(randotron), getRandomAddressBook(randotron), false);
        final AddressBook configAddressBook =
                copyWithWeightChanges(RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()), 3);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                NodeId.of(0),
                getMockSoftwareVersion(3),
                // software upgrade
                true,
                signedState,
                configAddressBook,
                getPlatformContext(false));
        final AddressBook inititializedAddressBook = initializer.getCurrentAddressBook();
        assertEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the config address book.");
        assertEquals(
                RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                initializer.getPreviousAddressBook(),
                "The previous address book must equal the state address book.");
        assertAddressBookFileContent(
                initializer,
                configAddressBook,
                RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                inititializedAddressBook);
        assertTrue(initializer.hasAddressBookChanged());
    }

    @Test
    @DisplayName("Version upgrade, Swirld State updates weight successfully.")
    void versionUpgradeSwirldStateWeightUpdateWorks() throws IOException {
        final Randotron randotron = Randotron.create();
        clearTestDirectory();
        final SignedState signedState = getMockSignedState7WeightRandomAddressBook(randotron);
        final AddressBook configAddressBook =
                copyWithWeightChanges(RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()), 5);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                NodeId.of(0),
                getMockSoftwareVersion(3),
                // software upgrade
                true,
                signedState,
                configAddressBook,
                getPlatformContext(false));
        final AddressBook inititializedAddressBook = initializer.getCurrentAddressBook();
        assertNotEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must not equal the config address book.");
        assertNotEquals(
                RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                inititializedAddressBook,
                "The initial address book must not equal the state address book.");
        assertEquals(
                RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                initializer.getPreviousAddressBook(),
                "The previous address book must equal the state address book.");
        assertAddressBookFileContent(
                initializer,
                configAddressBook,
                RosterAddressBookBuilder.buildAddressBook(signedState.getRoster()),
                inititializedAddressBook);
        assertTrue(initializer.hasAddressBookChanged());
    }

    /**
     * Copies the address book while setting weight per address to the given weight value.
     *
     * @param addressBook The address book to copy
     * @param weightValue The new weight value per address.
     * @return the copy of the input address book with the given weight value per address.
     */
    private AddressBook copyWithWeightChanges(AddressBook addressBook, int weightValue) {
        final AddressBook newAddressBook = new AddressBook();
        for (Address address : addressBook) {
            newAddressBook.add(address.copySetWeight(weightValue));
        }
        return newAddressBook;
    }

    /**
     * Creates a mock SoftwareVersion matching the input version number.
     *
     * @param version the integer software version.
     * @return the SoftwareVersion matching the input version.
     */
    private SoftwareVersion getMockSoftwareVersion(int version) {
        final SoftwareVersion softwareVersion = mock(SoftwareVersion.class);
        when(softwareVersion.getVersion()).thenReturn(version);
        final AtomicReference<SoftwareVersion> softVersion = new AtomicReference<>();
        when(softwareVersion.compareTo(argThat(sv -> {
                    softVersion.set(sv);
                    return true;
                })))
                .thenAnswer(i -> {
                    SoftwareVersion other = softVersion.get();
                    if (other == null) {
                        return 1;
                    } else {
                        return Integer.compare(softwareVersion.getVersion(), other.getVersion());
                    }
                });
        return softwareVersion;
    }

    /**
     * Creates a mock signed state and a SwirldState that sets all addresses to have weight = 7.
     *
     * @return The mock SignedState.
     */
    private SignedState getMockSignedState7WeightRandomAddressBook(@NonNull final Randotron randotron) {
        return getMockSignedState(7, getRandomAddressBook(randotron), getRandomAddressBook(randotron), false);
    }

    /**
     * Creates a mock signed state and a SwirldState that sets all addresses to the given weightValue.
     *
     * @param weightValue         The weight value that the SwirldState should set all addresses to in its updateWeight
     *                            method.
     * @param currentAddressBook  The address book that should be returned by {@link SignedState#getRoster()} and
     *                            {@link PlatformStateAccessor#getAddressBook()}
     * @param previousAddressBook The address book that should be returned by
     *                            {@link PlatformStateAccessor#getPreviousAddressBook()}
     * @param fromGenesis         Whether the state should be from genesis or not.
     * @return The mock SignedState and SwirldState configured to set all addresses with given weightValue.
     */
    private SignedState getMockSignedState(
            final int weightValue,
            @Nullable final AddressBook currentAddressBook,
            @Nullable final AddressBook previousAddressBook,
            boolean fromGenesis) {
        final SignedState signedState = mock(SignedState.class);
        final SoftwareVersion softwareVersion = getMockSoftwareVersion(2);
        final SwirldState swirldState = getMockSwirldStateSupplier(weightValue).get();
        when(signedState.getSwirldState()).thenReturn(swirldState);
        final PlatformStateAccessor platformState = mock(PlatformStateAccessor.class);
        when(platformState.getCreationSoftwareVersion()).thenReturn(softwareVersion);
        when(platformState.getAddressBook()).thenReturn(currentAddressBook);
        when(platformState.getPreviousAddressBook()).thenReturn(previousAddressBook);
        final MerkleRoot state = mock(MerkleRoot.class);
        when(state.getReadablePlatformState()).thenReturn(platformState);
        when(signedState.getState()).thenReturn(state);
        when(signedState.isGenesisState()).thenReturn(fromGenesis);
        final Roster currentRoster =
                currentAddressBook == null ? null : RosterRetriever.buildRoster(currentAddressBook);
        when(signedState.getRoster()).thenReturn(currentRoster);
        return signedState;
    }

    /**
     * Creates a mock swirld state with the given scenario.
     *
     * @param scenario The scenario to load.
     * @return A SwirldState which behaves according to the input scenario.
     */
    private Supplier<SwirldState> getMockSwirldStateSupplier(int scenario) {

        final AtomicReference<AddressBook> configAddressBook = new AtomicReference<>();
        final SwirldState swirldState = mock(SwirldState.class);

        final OngoingStubbing<AddressBook> stub = when(swirldState.updateWeight(
                argThat(confAB -> {
                    configAddressBook.set(confAB);
                    return true;
                }),
                argThat(context -> true)));

        switch (scenario) {
            case 0:
                stub.thenAnswer(foo -> copyWithWeightChanges(configAddressBook.get(), 0));
                break;
            case 1:
                stub.thenAnswer(foo -> configAddressBook.get());
                break;
            case 2:
                stub.thenAnswer(foo -> configAddressBook
                        .get()
                        .add(configAddressBook
                                .get()
                                .getAddress(configAddressBook.get().getNodeId(0))
                                .copySetNodeId(configAddressBook.get().getNextAvailableNodeId())));
                break;
            case 7:
                stub.thenAnswer(foo -> copyWithWeightChanges(configAddressBook.get(), 7));
                break;
            default:
                stub.thenAnswer(foo -> copyWithWeightChanges(configAddressBook.get(), 10));
        }

        return () -> swirldState;
    }

    /**
     * Creates an address book where each address has weight equal to its node ID.
     *
     * @return the address book created.
     */
    @NonNull
    private AddressBook getRandomAddressBook(@NonNull final Random random) {
        return RosterAddressBookBuilder.buildAddressBook(
                RandomRosterBuilder.create(random).withSize(5).build());
    }

    /**
     * removes all files and subdirectories from the test directory.
     *
     * @throws IOException if any IOException is created by deleting files or directories.
     */
    private void clearTestDirectory() throws IOException {
        for (File file : testDirectory.toFile().listFiles()) {
            if (file.isDirectory()) {
                FileUtils.deleteDirectory(file.toPath());
            } else {
                file.delete();
            }
        }
    }

    /**
     * Validates the state of the recorded address book file according to the excepted input states.
     *
     * @param initializer       The AddressBookInitializer constructed.
     * @param configAddressBook The configuration address book.
     * @param stateAddressBook  The state recorded address book.  May be null if there is no state, or it was unusable.
     * @param usedAddressBook   The address book returned by the initializer.
     * @throws IOException if any IOExceptions occur while reading the recorded file on disk.
     */
    private void assertAddressBookFileContent(
            @NonNull final AddressBookInitializer initializer,
            @NonNull final AddressBook configAddressBook,
            @NonNull final AddressBook stateAddressBook,
            @NonNull final AddressBook usedAddressBook)
            throws IOException {
        Objects.requireNonNull(initializer, "The initializer must not be null.");
        Objects.requireNonNull(configAddressBook, "The configAddressBook must not be null.");
        Objects.requireNonNull(configAddressBook, "The initializedAddressBook must not be null.");
        final Path addressBookDirectory = initializer.getPathToAddressBookDirectory();
        assertEquals(
                2,
                Arrays.stream(addressBookDirectory.toFile().listFiles()).count(),
                "There should be exactly one file in the test directory.");
        final File[] files = addressBookDirectory.toFile().listFiles();
        final File debugFile;
        final File usedFile;
        if (files[0].toString().contains("debug")) {
            debugFile = files[0];
            usedFile = files[1];
        } else {
            debugFile = files[1];
            usedFile = files[0];
        }
        final String debugFileContent = Files.readString(debugFile.toPath());
        final String usedFileContent = Files.readString(usedFile.toPath());

        // check used AddressBook content
        final String usedAddressBookText = usedAddressBook.toConfigText();
        assertEquals(
                usedAddressBookText,
                usedFileContent,
                "The used file content is not the same as the used address book.");

        // check debug AddressBook content
        final String configText = CONFIG_ADDRESS_BOOK_HEADER + "\n" + configAddressBook.toConfigText();
        assertTrue(
                debugFileContent.contains(configText),
                "The configAddressBook content is not:\n" + configText + "\n\n debugFileContent:\n" + debugFileContent);

        // check stateAddressBook content
        final String stateAddressBookText =
                (stateAddressBook == null ? STATE_ADDRESS_BOOK_NULL : stateAddressBook.toConfigText());
        final String stateText = STATE_ADDRESS_BOOK_HEADER + "\n" + stateAddressBookText;

        assertTrue(
                debugFileContent.contains(stateText),
                "The stateAddressBook content is not:\n" + stateText + "\n\n debugFileContent:\n" + debugFileContent);

        // check usedAddressBook content
        String usedText = USED_ADDRESS_BOOK_HEADER + "\n";
        if (usedAddressBook.equals(configAddressBook)) {
            usedText += CONFIG_ADDRESS_BOOK_USED;
        } else if (usedAddressBook.equals(stateAddressBook)) {
            usedText += STATE_ADDRESS_BOOK_USED;
        } else {
            usedText += usedAddressBook.toConfigText();
        }
        assertTrue(
                debugFileContent.contains(usedText),
                "The usedAddressBook content is not:\n" + usedText + "\n\n debugFileContent:\n" + debugFileContent);
    }

    /**
     * Creates an PlatformContext object with a configuration able to return AddressBookConfiguration with the given
     * forceUseOfConfigAddressBook value.
     *
     * @param forceUseOfConfigAddressBook the value to use for the forceUseOfConfigAddressBook property.
     * @return the PlatformContext object.
     */
    private PlatformContext getPlatformContext(boolean forceUseOfConfigAddressBook) {
        return TestPlatformContextBuilder.create()
                .withConfiguration(new TestConfigBuilder()
                        .withValue(AddressBookConfig_.FORCE_USE_OF_CONFIG_ADDRESS_BOOK, forceUseOfConfigAddressBook)
                        .withValue(AddressBookConfig_.ADDRESS_BOOK_DIRECTORY, testDirectory.toString())
                        .withValue(AddressBookConfig_.MAX_RECORDED_ADDRESS_BOOK_FILES, 50)
                        .getOrCreateConfig())
                .build();
    }
}
