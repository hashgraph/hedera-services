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

package com.swirlds.platform;

import static com.swirlds.platform.state.address.AddressBookInitializer.CONFIG_ADDRESS_BOOK_HEADER;
import static com.swirlds.platform.state.address.AddressBookInitializer.CONFIG_ADDRESS_BOOK_USED;
import static com.swirlds.platform.state.address.AddressBookInitializer.STATE_ADDRESS_BOOK_HEADER;
import static com.swirlds.platform.state.address.AddressBookInitializer.STATE_ADDRESS_BOOK_USED;
import static com.swirlds.platform.state.address.AddressBookInitializer.USED_ADDRESS_BOOK_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.address.AddressBookInitializer;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
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
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook();
        final SignedState signedState = getMockSignedState7WeightRandomAddressBook();
        final AddressBookInitializer initializer = new AddressBookInitializer(
                new NodeId(0),
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
                signedState.getAddressBook(),
                initializer.getPreviousAddressBook(),
                "The previous address book must equal the state address book.");
        assertAddressBookFileContent(
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
    }

    @Test
    @DisplayName("Genesis. Config.txt Initializes Address Book.")
    void noStateLoadedFromDisk() throws IOException {
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook();
        final SignedState signedState = getMockSignedState(10, configAddressBook, true);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                new NodeId(0),
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
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
    }

    @Test
    @DisplayName("No state loaded from disk. Genesis State set 0 weight.")
    void noStateLoadedFromDiskGenesisStateSetZeroWeight() throws IOException {
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook();
        final SignedState signedState = getMockSignedState(10, configAddressBook, true);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                new NodeId(0),
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
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
    }

    @Test
    @DisplayName("No state loaded from disk. Genesis State modifies address book entries.")
    void noStateLoadedFromDiskGenesisStateChangedAddressBook() throws IOException {
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook();
        final SignedState signedState = getMockSignedState(7, configAddressBook, true);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                new NodeId(0),
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
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
    }

    @Test
    @DisplayName("Current software version is equal to state software version.")
    void currentVersionEqualsStateVersion() throws IOException {
        clearTestDirectory();
        final SignedState signedState = getMockSignedState(2, getRandomAddressBook(), false);
        final AddressBook configAddressBook = copyWithWeightChanges(signedState.getAddressBook(), 10);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                new NodeId(0),
                getMockSoftwareVersion(2),
                // no software upgrade
                false,
                signedState,
                configAddressBook,
                getPlatformContext(false));
        final AddressBook inititializedAddressBook = initializer.getCurrentAddressBook();
        assertEquals(
                signedState.getAddressBook(),
                inititializedAddressBook,
                "The initial address book must equal the state address book.");
        assertEquals(
                null,
                initializer.getPreviousAddressBook(),
                "When there is no upgrade, the address book should not change");
        assertAddressBookFileContent(
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
    }

    @Test
    @DisplayName("Version upgrade, SwirldState set 0 weight.")
    void versionUpgradeSwirldStateZeroWeight() throws IOException {
        clearTestDirectory();
        final SignedState signedState = getMockSignedState(0, getRandomAddressBook(), false);
        final AddressBook configAddressBook = copyWithWeightChanges(signedState.getAddressBook(), 10);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                new NodeId(0),
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
                signedState.getAddressBook(),
                initializer.getPreviousAddressBook(),
                "The previous address book must equal the state address book.");
        assertAddressBookFileContent(
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
    }

    @Test
    @DisplayName("Version upgrade, Swirld State modified the address book.")
    void versionUpgradeSwirldStateModifiedAddressBook() throws IOException {
        clearTestDirectory();
        final SignedState signedState = getMockSignedState(2, getRandomAddressBook(), false);
        final AddressBook configAddressBook = copyWithWeightChanges(signedState.getAddressBook(), 3);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                new NodeId(0),
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
                signedState.getAddressBook(),
                initializer.getPreviousAddressBook(),
                "The previous address book must equal the state address book.");
        assertAddressBookFileContent(
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
    }

    @Test
    @DisplayName("Version upgrade, Swirld State updates weight successfully.")
    void versionUpgradeSwirldStateWeightUpdateWorks() throws IOException {
        clearTestDirectory();
        final SignedState signedState = getMockSignedState7WeightRandomAddressBook();
        final AddressBook configAddressBook = copyWithWeightChanges(signedState.getAddressBook(), 5);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                new NodeId(0),
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
                signedState.getAddressBook(),
                inititializedAddressBook,
                "The initial address book must not equal the state address book.");
        assertEquals(
                signedState.getAddressBook(),
                initializer.getPreviousAddressBook(),
                "The previous address book must equal the state address book.");
        assertAddressBookFileContent(
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
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
    private SignedState getMockSignedState7WeightRandomAddressBook() {
        return getMockSignedState(7, getRandomAddressBook(), false);
    }

    /**
     * Creates a mock signed state and a SwirldState that sets all addresses to the given weightValue.
     *
     * @param weightValue      The weight value that the SwirldState should set all addresses to in its updateWeight
     *                         method.
     * @param stateAddressBook The address book that the SignedState should return in its getAddressBook method.
     * @param fromGenesis      Whether the state should be from genesis or not.
     * @return The mock SignedState and SwirldState configured to set all addresses with given weightValue.
     */
    private SignedState getMockSignedState(
            final int weightValue, @NonNull final AddressBook stateAddressBook, boolean fromGenesis) {
        final SignedState signedState = mock(SignedState.class);
        final SoftwareVersion softwareVersion = getMockSoftwareVersion(2);
        final SwirldState swirldState = getMockSwirldStateSupplier(weightValue).get();
        when(signedState.getAddressBook()).thenReturn(stateAddressBook);
        when(signedState.getSwirldState()).thenReturn(swirldState);
        final PlatformData platformData = mock(PlatformData.class);
        when(platformData.getCreationSoftwareVersion()).thenReturn(softwareVersion);
        final PlatformState platformState = mock(PlatformState.class);
        when(platformState.getPlatformData()).thenReturn(platformData);
        final State state = mock(State.class);
        when(state.getPlatformState()).thenReturn(platformState);
        when(signedState.getState()).thenReturn(state);
        when(signedState.isGenesisState()).thenReturn(fromGenesis);
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
                                .copySetNodeId(configAddressBook.get().getNextNodeId())));
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
    private AddressBook getRandomAddressBook() {
        return new RandomAddressBookGenerator()
                .setSize(5)
                .setCustomWeightGenerator(i -> i.id())
                .build();
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
        final String stateText = STATE_ADDRESS_BOOK_HEADER + "\n" + stateAddressBook.toConfigText();
        assertTrue(
                debugFileContent.contains(stateText),
                "The stateAddressBook content is not:\n" + stateText + "\n\n debugFileContent:\n" + debugFileContent);

        // check usedAddressBook content
        String usedText = USED_ADDRESS_BOOK_HEADER + "\n";
        if (usedAddressBook == configAddressBook) {
            usedText += CONFIG_ADDRESS_BOOK_USED;
        } else if (usedAddressBook == stateAddressBook) {
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
                        .withValue("addressBook.forceUseOfConfigAddressBook", forceUseOfConfigAddressBook)
                        .withValue("addressBook.addressBookDirectory", testDirectory.toString())
                        .withValue("addressBook.maxRecordedAddressBookFiles", 50)
                        .getOrCreateConfig())
                .build();
    }
}
