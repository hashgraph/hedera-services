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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.address.AddressBookValidator;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Determines the initial address book to use at platform start and validates it.
 */
public class AddressBookInitializer {

    /** The header before the config address book in the usedAddressBook file. */
    public static final String CONFIG_ADDRESS_BOOK_HEADER = "--- Configuration Address Book ---";
    /** The header before the state address book in the usedAddressBook file. */
    public static final String STATE_ADDRESS_BOOK_HEADER = "--- State Saved Address Book ---";
    /** The header before the used address book in the usedAddressBook file. */
    public static final String USED_ADDRESS_BOOK_HEADER = "--- Used Address Book ---";
    /** The text indicating the config address book was used in the usedAddressBook file. */
    public static final String CONFIG_ADDRESS_BOOK_USED = "The Configuration Address Book Was Used.";
    /** The text indicating the state address book was used in the usedAddressBook file. */
    public static final String STATE_ADDRESS_BOOK_USED = "The State Saved Address Book Was Used.";
    /** The text indicating the state address book was null in the usedAddressBook file. */
    public static final String STATE_ADDRESS_BOOK_NULL = "The State Saved Address Book Was NULL.";
    /** The name of the address book directory to write address books to. */
    private static final String ADDRESS_BOOK_DIRECTORY_NAME = "address_book";
    /** The file name prefix to use when creating address book files. */
    private static final String ADDRESS_BOOK_FILE_PREFIX = "usedAddressBook";
    /** The format of date and time to use when creating address book files. */
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-m-ss").withZone(ZoneId.systemDefault());
    /** For logging info, warn, and error. */
    private static final Logger logger = LogManager.getLogger(AddressBookInitializer.class);
    /** The current version of the application from config.txt. */
    @NonNull
    private final SoftwareVersion currentVersion;
    /** The SwirldState to use at genesis. */
    @NonNull
    private final Supplier<SwirldState> genesisSupplier;
    /** The SignedState loaded from disk. May be null. */
    @Nullable
    private final SignedState loadedSignedState;
    /** The address book in the signed state loaded from disk. May be null. */
    @Nullable
    private final AddressBook loadedAddressBook;
    /** The address book derived from config.txt */
    @NonNull
    private final AddressBook configAddressBook;
    /** The address book determined for use after {@link AddressBookInitializer#initialize()} is called. */
    @NonNull
    private final AddressBook initialAddressBook;
    /** The path to the directory for writing address books. */
    @NonNull
    private final Path pathToAddressBookDirectory;
    /** Indicate that the unmodified config address book must be used. */
    private final boolean useConfigAddressBook;

    /**
     * Constructs an AddressBookInitializer to initialize an address book from the swirld application state, config.txt,
     * and the saved state from disk.
     *
     * @param currentVersion       The current version of the application. Must not be null.
     * @param signedState          The signed state loaded from disk.  May be null.
     * @param genesisSupplier      The swirld application state in genesis start. Must not be null.
     * @param configAddressBook    The address book derived from config.txt. Must not be null.
     * @param parentDirectory      The parent directory of the address book directory. Must not be null.
     * @param useConfigAddressBook Indicates if the unmodified config address book should be used.
     */
    public AddressBookInitializer(
            @NonNull final SoftwareVersion currentVersion,
            @Nullable final SignedState signedState,
            @NonNull final Supplier<SwirldState> genesisSupplier,
            @NonNull final AddressBook configAddressBook,
            @NonNull final String parentDirectory,
            final boolean useConfigAddressBook) {
        this.currentVersion = Objects.requireNonNull(currentVersion, "The currentVersion must not be null.");
        this.genesisSupplier =
                Objects.requireNonNull(genesisSupplier, "The genesis swirldState supplier must not be null.");
        this.configAddressBook = Objects.requireNonNull(configAddressBook, "The configAddressBook must not be null.");
        Objects.requireNonNull(parentDirectory, "The parentDirectory must not be null.");
        this.pathToAddressBookDirectory = Path.of(parentDirectory, ADDRESS_BOOK_DIRECTORY_NAME);
        try {
            Files.createDirectories(pathToAddressBookDirectory);
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "Not able to create directory: {}", pathToAddressBookDirectory, e);
            throw new IllegalStateException("Not able to create directory: " + pathToAddressBookDirectory, e);
        }
        this.loadedSignedState = signedState;
        this.loadedAddressBook = loadedSignedState == null ? null : loadedSignedState.getAddressBook();
        this.useConfigAddressBook = useConfigAddressBook;

        initialAddressBook = initialize();
    }

    /**
     * Returns the address book to use in the platform.
     *
     * @return the address book to use in the platform.
     */
    @NonNull
    public AddressBook getInitialAddressBook() {
        return initialAddressBook;
    }

    /**
     * Determines the address book to use.  If there is no saved platform state, the config address book stake is
     * updated by the swirld application state.  If the configured current version of the application is higher than the
     * save state version, the swirld state is given both the config and previously saved address book to determine
     * stake weights.  If the address book returned by the swirld application is valid, it is the initial address book
     * to use, otherwise the configuration address book is the one to use.  All three address books, the configuration
     * address book, the save state address book, and the new address book to use are recorded in the used address book
     * file.
     *
     * @return the address book to use in the platform.
     */
    @NonNull
    private AddressBook initialize() {
        AddressBook candidateAddressBook;
        if (useConfigAddressBook) {
            // configuration is overriding to force use of configuration address book.
            candidateAddressBook = configAddressBook;
        } else if (loadedSignedState == null || loadedAddressBook == null) {
            logger.info(
                    STARTUP.getMarker(),
                    "The loaded signed state is null. The candidateAddressBook is set to "
                            + "genesisSwirldState.updateStake(configAddressBook, null).");
            final SwirldState genesisState = genesisSupplier.get();
            candidateAddressBook =
                    genesisState.updateStake(configAddressBook.copy()).copy();
            genesisState.release();
        } else {
            final SoftwareVersion loadedSoftwareVersion = loadedSignedState
                    .getState()
                    .getPlatformState()
                    .getPlatformData()
                    .getCreationSoftwareVersion();
            final int versionComparison = currentVersion.compareTo(loadedSoftwareVersion);
            if (versionComparison < 0) {
                throw new IllegalStateException("The currentVersion `" + currentVersion
                        + "` is prior to the stateVersion `" + loadedSoftwareVersion + "`");
            } else if (versionComparison == 0) {
                logger.info(
                        STARTUP.getMarker(),
                        "No Software Upgrade. Continuing with software version {} and "
                                + "using the loaded signed state's address book and stake values.",
                        loadedSoftwareVersion);
                candidateAddressBook = loadedAddressBook;
            } else {
                logger.info(
                        STARTUP.getMarker(),
                        "Software Upgrade from version {} to {}. "
                                + "The address book stake will be updated by the saved state's SwirldState.",
                        loadedSoftwareVersion,
                        currentVersion);
                candidateAddressBook = loadedSignedState
                        .getSwirldState()
                        .updateStake(configAddressBook.copy())
                        .copy();
            }
        }
        candidateAddressBook = checkCandidateAddressBookValidity(candidateAddressBook);
        recordAddressBooks(candidateAddressBook);
        return candidateAddressBook;
    }

    /**
     * Checks if the candidateAddressBook is valid and returns it, otherwise returns the configAddressBook if it has
     * non-zero stake.   If the candidateAddressBook's addresses are out of sync with the configAddressBook or both
     * address books have 0 stake, an IllegalStateException is thrown.
     *
     * @return the valid address book to use.
     */
    @NonNull
    private AddressBook checkCandidateAddressBookValidity(@Nullable final AddressBook candidateAddressBook) {
        if (candidateAddressBook == null) {
            logger.warn(STARTUP.getMarker(), "The candidateAddressBook is null, using configAddressBook instead.");
            return configAddressBook;
        } else if (!AddressBookValidator.hasNonZeroStake(candidateAddressBook)
                || !AddressBookValidator.sameExceptForStake(configAddressBook, candidateAddressBook)) {
            // an error was recorded by the address book validator.  Check the configuration address book for usability.
            if (!AddressBookValidator.hasNonZeroStake(configAddressBook)) {
                throw new IllegalStateException(
                        "The candidateAddressBook is not valid and the configAddressBook has 0 total stake.");
            } else {
                logger.warn(
                        STARTUP.getMarker(), "The candidateAddressBook is not valid, using configAddressBook instead.");
                return configAddressBook;
            }
        }
        return candidateAddressBook;
    }

    /**
     * Records the configuration address book, the state loaded address book, and the usedAddressBook in a timestamped
     * file for diagnostic purposes.  If the path to the address book directory does not resolve or a new file for
     * address books cannot be created, no file is generated.
     *
     * @param usedAddressBook the address book to be returned from the AddressBookInitializer.
     */
    private void recordAddressBooks(@NonNull final AddressBook usedAddressBook) {
        final String date = DATE_TIME_FORMAT.format(Instant.now());
        final String addressBookFileName = ADDRESS_BOOK_FILE_PREFIX + "_v" + currentVersion + "_" + date + ".txt";
        final String addressBookDebugFileName = addressBookFileName + ".debug";
        try {
            final File debugFile = Path.of(this.pathToAddressBookDirectory.toString(), addressBookDebugFileName)
                    .toFile();
            try (final FileWriter out = new FileWriter(debugFile)) {
                out.write(CONFIG_ADDRESS_BOOK_HEADER + "\n");
                out.write(configAddressBook.toConfigText() + "\n\n");
                out.write(STATE_ADDRESS_BOOK_HEADER + "\n");
                final String text =
                        loadedAddressBook == null ? STATE_ADDRESS_BOOK_NULL : loadedAddressBook.toConfigText();
                out.write(text + "\n\n");
                out.write(USED_ADDRESS_BOOK_HEADER + "\n");
                if (usedAddressBook == configAddressBook) {
                    out.write(CONFIG_ADDRESS_BOOK_USED);
                } else if (usedAddressBook == loadedAddressBook) {
                    out.write(STATE_ADDRESS_BOOK_USED);
                } else {
                    out.write(usedAddressBook.toConfigText());
                }
                out.write("\n\n");
            }
            final File usedFile = Path.of(this.pathToAddressBookDirectory.toString(), addressBookFileName)
                    .toFile();
            try (final FileWriter out = new FileWriter(usedFile)) {
                out.write(usedAddressBook.toConfigText());
            }
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "Not able to write address book to file. ", e);
        }
    }

    /**
     * Get the path to the directory where the address books are being recorded.
     *
     * @return the directory where the address books are being recorded.
     */
    @NonNull
    public Path getPathToAddressBookDirectory() {
        return pathToAddressBookDirectory;
    }
}
