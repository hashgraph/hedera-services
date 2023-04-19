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

package com.swirlds.demo.addressbook;
/*
 * This file is public domain.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import static com.swirlds.logging.LogMarker.DEMO_INFO;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.AddressBookInitializer.CONFIG_ADDRESS_BOOK_HEADER;
import static com.swirlds.platform.AddressBookInitializer.CONFIG_ADDRESS_BOOK_USED;
import static com.swirlds.platform.AddressBookInitializer.STATE_ADDRESS_BOOK_HEADER;
import static com.swirlds.platform.AddressBookInitializer.STATE_ADDRESS_BOOK_NULL;
import static com.swirlds.platform.AddressBookInitializer.STATE_ADDRESS_BOOK_USED;
import static com.swirlds.platform.AddressBookInitializer.USED_ADDRESS_BOOK_HEADER;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.address.AddressBookUtils;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.utility.ByteUtils;
import com.swirlds.common.utility.StackTrace;
import com.swirlds.platform.Network;
import com.swirlds.platform.config.AddressBookConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the AddressBookTestingTool.
 */
public class AddressBookTestingToolState extends PartialMerkleLeaf implements SwirldState, MerkleLeaf {

    private static final Logger logger = LogManager.getLogger(AddressBookTestingToolState.class);

    /** the suffix for the debug address book */
    private static String DEBUG = "debug";

    /** the suffix for the test address book */
    private static AddressBookTestingToolConfig testingToolConfig;
    /** the address book configuration */
    private static AddressBookConfig addressBookConfig;
    /** flag indicating if weighting behavior has been logged. */
    private static AtomicBoolean logWeightingBehavior = new AtomicBoolean(true);

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private static final long CLASS_ID = 0xf052378c7364ef47L;

    private long selfId;

    /** false until the test scenario has been validated, true afterwards. */
    private final AtomicBoolean validationPerformed = new AtomicBoolean(false);

    private Platform platform = null;

    private PlatformContext context = null;

    /**
     * The true "state" of this app. Each transaction is just an integer that gets added to this value.
     */
    private long runningSum = 0;

    public AddressBookTestingToolState() {
        logger.info(STARTUP.getMarker(), "New State Constructed.");
    }

    /**
     * Copy constructor.
     */
    private AddressBookTestingToolState(@NonNull final AddressBookTestingToolState that) {
        super(that);
        Objects.requireNonNull(that, "the address book testing tool state to copy cannot be null");
        this.runningSum = that.runningSum;
        this.selfId = that.selfId;
        this.platform = that.platform;
        this.context = that.context;
        this.validationPerformed.set(that.validationPerformed.get());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized AddressBookTestingToolState copy() {
        throwIfImmutable();
        return new AddressBookTestingToolState(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(
            @NonNull final Platform platform,
            @NonNull final SwirldDualState swirldDualState,
            @NonNull final InitTrigger trigger,
            @Nullable final SoftwareVersion previousSoftwareVersion) {
        Objects.requireNonNull(platform, "the platform cannot be null");
        Objects.requireNonNull(swirldDualState, "the swirld dual state cannot be null");
        Objects.requireNonNull(trigger, "the init trigger cannot be null");
        addressBookConfig = platform.getContext().getConfiguration().getConfigData(AddressBookConfig.class);
        testingToolConfig = platform.getContext().getConfiguration().getConfigData(AddressBookTestingToolConfig.class);

        this.platform = platform;
        this.context = platform.getContext();

        logger.info(STARTUP.getMarker(), "init called in State.");
        throwIfImmutable();

        if (trigger == InitTrigger.GENESIS) {
            parseArguments(((PlatformWithDeprecatedMethods) platform).getParameters());
        }

        this.selfId = platform.getSelfId().getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(@NonNull final Round round, @NonNull final SwirldDualState swirldDualState) {
        Objects.requireNonNull(round, "the round cannot be null");
        Objects.requireNonNull(swirldDualState, "the swirld dual state cannot be null");
        throwIfImmutable();

        final Iterator<ConsensusEvent> eventIterator = round.iterator();

        while (eventIterator.hasNext()) {
            final ConsensusEvent event = eventIterator.next();
            event.consensusTransactionIterator().forEachRemaining(this::handleTransaction);
        }

        if (!validationPerformed.getAndSet(true)) {
            if (validateTestScenario()) {
                logger.info(
                        STARTUP.getMarker(),
                        "Test scenario {}: finished without errors.",
                        testingToolConfig.testScenario());
            } else {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Test scenario {}: validation failed with errors.",
                        testingToolConfig.testScenario());
            }
        }
    }

    /**
     * Apply a transaction to the state.
     *
     * @param transaction the transaction to apply
     */
    private void handleTransaction(@NonNull final ConsensusTransaction transaction) {
        final int delta = ByteUtils.byteArrayToInt(transaction.getContents(), 0);
        runningSum += delta;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        Objects.requireNonNull(out, "the serializable data output stream cannot be null");
        out.writeLong(runningSum);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        Objects.requireNonNull(in, "the serializable data input stream cannot be null");
        runningSum = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    private void parseArguments(@NonNull final String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("Expected no arguments. See javadocs for details.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public synchronized AddressBook updateWeight(
            @NonNull final AddressBook addressBook, @NonNull final PlatformContext context) {
        Objects.requireNonNull(addressBook, "the address book cannot be null");
        this.context = Objects.requireNonNull(context, "the platform context cannot be null");
        final int weightingBehavior = context.getConfiguration()
                .getConfigData(AddressBookTestingToolConfig.class)
                .weightingBehavior();
        logger.info("updateWeight called in State. Weighting Behavior: {}", weightingBehavior);
        switch (weightingBehavior) {
            case 1:
                return weightingBehavior1(addressBook);
            case 2:
                return weightingBehavior2(addressBook);
            default:
                logger.info(
                        STARTUP.getMarker(), "Weighting Behavior {}: no change to address book.", weightingBehavior);
                return addressBook;
        }
    }

    /**
     * All nodes received 10 weight.
     *
     * @param addressBook the address book to update.
     * @return the updated address book.
     */
    @NonNull
    private AddressBook weightingBehavior1(@NonNull final AddressBook addressBook) {
        if (logWeightingBehavior.get()) {
            logger.info(STARTUP.getMarker(), "Weighting Behavior 1: updating all nodes to have 10 weight.");
        }
        for (int i = 0; i < addressBook.getSize(); i++) {
            addressBook.updateWeight(i, 10);
        }
        return addressBook;
    }

    /**
     * All nodes received weight equal to their nodeId.
     *
     * @param addressBook the address book to update.
     * @return the updated address book.
     */
    @NonNull
    private AddressBook weightingBehavior2(@NonNull final AddressBook addressBook) {
        if (logWeightingBehavior.get()) {
            logger.info(
                    STARTUP.getMarker(),
                    "Weighting Behavior 2: updating all nodes to have weight equal to their nodeId.");
        }
        for (int i = 0; i < addressBook.getSize(); i++) {
            addressBook.updateWeight(i, i);
        }
        return addressBook;
    }

    private boolean validateTestScenario() {
        if (platform == null) {
            throw new IllegalStateException("platform is null, init has not been called.");
        }
        logWeightingBehavior.set(false);
        final AddressBookTestScenario testScenario = AddressBookTestScenario.valueOf(testingToolConfig.testScenario());
        try {
            logger.info(DEMO_INFO.getMarker(), "Validating test scenario {}.", testScenario);
            switch (testScenario) {
                case GENESIS_FORCE_CONFIG_AB:
                    return genesisForceUseOfConfigAddressBookTrue(testScenario);
                case GENESIS_NORMAL:
                    return genesisForceUseOfConfigAddressBookFalse(testScenario);
                case NO_UPGRADE_USE_SAVED_STATE:
                    return noSoftwareUpgradeUseSavedStateAddressBook(testScenario);
                case NO_UPGRADE_FORCE_CONFIG_AB:
                    return noSoftwareUpgradeForceUseOfConfigAddressBook(testScenario);
                case UPGRADE_WEIGHT_BEHAVIOR_2:
                    return softwareUpgradeWeightingBehavior2(testScenario);
                case UPGRADE_FORCE_CONFIG_AB:
                    return softwareUpgradeForceUseOfConfigAddressBook(testScenario);
                case SKIP_VALIDATION:
                    // fall into default case. No validation performed.
                default:
                    logger.info(DEMO_INFO.getMarker(), "Test Scenario {}: no validation performed.", testScenario);
                    return true;
            }
        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "Exception occurred in Test Scenario {}.", testScenario, e);
            return false;
        }
    }

    private boolean softwareUpgradeForceUseOfConfigAddressBook(@NonNull final AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(true, testScenario, 2, 2)) {
            return false;
        }

        final AddressBook platformAddressBook = platform.getAddressBook();
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook stateAddressBook = getStateAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsConfigText(platformAddressBook, configAddressBook, true)
                && equalsAsConfigText(platformAddressBook, stateAddressBook, true)
                && equalsAsConfigText(platformAddressBook, usedAddressBook, true)
                && equalsAsConfigText(platformAddressBook, updatedAddressBook, false)
                && theConfigurationAddressBookWasUsed();
    }

    private boolean softwareUpgradeWeightingBehavior2(@NonNull final AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(false, testScenario, 2, 2)) {
            return false;
        }

        final AddressBook platformAddressBook = platform.getAddressBook();
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook stateAddressBook = getStateAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsConfigText(platformAddressBook, configAddressBook, false)
                && equalsAsConfigText(platformAddressBook, stateAddressBook, false)
                && equalsAsConfigText(platformAddressBook, usedAddressBook, true)
                && equalsAsConfigText(platformAddressBook, updatedAddressBook, true);
    }

    private boolean noSoftwareUpgradeForceUseOfConfigAddressBook(@NonNull final AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(true, testScenario, 1, 1)) {
            return false;
        }

        final AddressBook platformAddressBook = platform.getAddressBook();
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook stateAddressBook = getStateAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsConfigText(platformAddressBook, configAddressBook, true)
                && equalsAsConfigText(platformAddressBook, stateAddressBook, true)
                && equalsAsConfigText(platformAddressBook, usedAddressBook, true)
                && equalsAsConfigText(platformAddressBook, updatedAddressBook, false)
                && theConfigurationAddressBookWasUsed();
    }

    private boolean noSoftwareUpgradeUseSavedStateAddressBook(AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(false, testScenario, 1, 1)) {
            return false;
        }

        final AddressBook platformAddressBook = platform.getAddressBook();
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook stateAddressBook = getStateAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsConfigText(platformAddressBook, configAddressBook, true)
                && equalsAsConfigText(platformAddressBook, stateAddressBook, true)
                && equalsAsConfigText(platformAddressBook, usedAddressBook, true)
                && equalsAsConfigText(platformAddressBook, updatedAddressBook, false)
                && theStateAddressBookWasUsed();
    }

    private boolean genesisForceUseOfConfigAddressBookFalse(@NonNull final AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(false, testScenario, 1, 1)) {
            return false;
        }

        final AddressBook platformAddressBook = platform.getAddressBook();
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsConfigText(platformAddressBook, configAddressBook, true)
                && equalsAsConfigText(platformAddressBook, usedAddressBook, true)
                && equalsAsConfigText(platformAddressBook, updatedAddressBook, false)
                && theStateAddressBookWasNull(true);
    }

    private boolean genesisForceUseOfConfigAddressBookTrue(@NonNull final AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(true, testScenario, 1, 1)) {
            return false;
        }

        final AddressBook platformAddressBook = platform.getAddressBook();
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsConfigText(platformAddressBook, configAddressBook, true)
                && equalsAsConfigText(platformAddressBook, usedAddressBook, true)
                && equalsAsConfigText(platformAddressBook, updatedAddressBook, false)
                && theStateAddressBookWasNull(true)
                && theConfigurationAddressBookWasUsed();
    }

    /**
     * Check the test scenario preconditions.
     *
     * @param forceUseConfigAddressBook the expected value of `addressBook.forceUseOfConfigAddressBook`
     * @param testScenario              the expected value of `testingTool.testScenario`
     * @param softwareVersion           the expected value of `testingTool.softwareVersion`
     * @param weightingBehavior         the expected value of `testingTool.weightingBehavior`
     * @return true if the preconditions are met, false otherwise
     */
    private boolean checkTestScenarioConditions(
            final boolean forceUseConfigAddressBook,
            final AddressBookTestScenario testScenario,
            final int softwareVersion,
            final int weightingBehavior) {
        boolean passed = true;
        if (addressBookConfig.forceUseOfConfigAddressBook() != forceUseConfigAddressBook) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "The test scenario requires the setting `addressBook.forceUseOfConfigAddressBook, {}`",
                    forceUseConfigAddressBook);
            passed = false;
        }
        if (!testScenario.toString().equals(testingToolConfig.testScenario())) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "The test scenario requires the setting `testingTool.testScenario, {}`",
                    testScenario);
            passed = false;
        }
        if (testingToolConfig.softwareVersion() != softwareVersion) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "The test scenario requires the setting `testingTool.softwareVersion, {}`",
                    softwareVersion);
            passed = false;
        }
        if (testingToolConfig.weightingBehavior() != weightingBehavior) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "The test scenario requires the setting `testingTool.weightingBehavior, {}`",
                    weightingBehavior);
            passed = false;
        }
        return passed;
    }

    /**
     * This test compares the equality of two address books against the expected result.  The equality comparison is
     * performed by converting the address books to config text and comparing the strings.  The conversion to config
     * text is to avoid needing to load public keys for the addresses and computing accurate isOwnHost values on the
     * addresses.
     *
     * @param addressBook1 the first address book
     * @param addressBook2 the second address book
     * @return true if the comparison matches the expected result, false otherwise.
     */
    private boolean equalsAsConfigText(
            @NonNull final AddressBook addressBook1,
            @NonNull final AddressBook addressBook2,
            final boolean expectedResult) {
        final String addressBook1ConfigText = addressBook1.toConfigText();
        final String addressBook2ConfigText = addressBook2.toConfigText();
        final boolean pass = addressBook1ConfigText.equals(addressBook2ConfigText) == expectedResult;
        if (!pass) {
            if (expectedResult) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "The address books are not equal as config text. {}",
                        StackTrace.getStackTrace());
            } else {
                logger.error(
                        EXCEPTION.getMarker(),
                        "The address books are equal as config text. {}",
                        StackTrace.getStackTrace());
            }
        }
        return pass;
    }

    /**
     * This test passes if expectedResult is true and the state address book was null, or if expectedResult is false and
     * the state address book was not null.
     *
     * @param expectedResult the expected result of the test.
     * @return true if the test passes, false otherwise.
     */
    private boolean theStateAddressBookWasNull(final boolean expectedResult) throws IOException {
        final String fileContents = getLastAddressBookFileEndsWith(DEBUG);
        final String textAfterStateHeader = getTextAfterHeader(fileContents, STATE_ADDRESS_BOOK_HEADER);
        final boolean pass = textAfterStateHeader.contains(STATE_ADDRESS_BOOK_NULL) == expectedResult;
        if (!pass) {
            if (expectedResult) {
                logger.error(
                        EXCEPTION.getMarker(), "The state address book was not null. {}", StackTrace.getStackTrace());
            } else {
                logger.error(EXCEPTION.getMarker(), "The state address book was null. {}", StackTrace.getStackTrace());
            }
        }
        return pass;
    }

    /**
     * Checks if the state address book was used.
     *
     * @return true if the state address book was used, false otherwise.
     */
    private boolean theStateAddressBookWasUsed() throws IOException {
        final String fileContents = getLastAddressBookFileEndsWith(DEBUG);
        final String textAfterUsedHeader = getTextAfterHeader(fileContents, USED_ADDRESS_BOOK_HEADER);
        final boolean pass = textAfterUsedHeader.contains(STATE_ADDRESS_BOOK_USED);
        if (!pass) {
            logger.error(EXCEPTION.getMarker(), "The state address book was not used. {}", StackTrace.getStackTrace());
        }
        return pass;
    }

    /**
     * Checks if the configuration address book was used.
     *
     * @return true if the configuration address book was used, false otherwise.
     */
    private boolean theConfigurationAddressBookWasUsed() throws IOException {
        final String fileContents = getLastAddressBookFileEndsWith(DEBUG);
        final String textAfterUsedHeader = getTextAfterHeader(fileContents, USED_ADDRESS_BOOK_HEADER);
        final boolean pass = textAfterUsedHeader.contains(CONFIG_ADDRESS_BOOK_USED);
        if (!pass) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "The configuration address book was not used. {}",
                    StackTrace.getStackTrace());
        }
        return pass;
    }

    /**
     * Get the address book in the last usedAddressBook file.
     *
     * @return the address book in the last usedAddressBook file.
     */
    @NonNull
    private AddressBook getUsedAddressBook() throws IOException, ParseException {
        final String fileContents = getLastAddressBookFileEndsWith("txt");
        return parseAddressBook(fileContents);
    }

    /**
     * Get the config address book from the last debug addressBook file.
     *
     * @return the config address book from the last debug addressBook file.
     */
    @NonNull
    private AddressBook getConfigAddressBook() throws IOException, ParseException {
        return getDebugAddressBookAfterHeader(CONFIG_ADDRESS_BOOK_HEADER);
    }

    /**
     * Get the state address book from the last debug addressBook file.
     *
     * @return the state address book from the last debug addressBook file.
     */
    @NonNull
    private AddressBook getStateAddressBook() throws IOException, ParseException {
        return getDebugAddressBookAfterHeader(STATE_ADDRESS_BOOK_HEADER);
    }

    /**
     * Get the address book in the last debug addressBook file after the header.
     *
     * @param header the header to find.
     * @return the address book in the last debug addressBook file after the header.
     */
    @NonNull
    AddressBook getDebugAddressBookAfterHeader(@NonNull final String header) throws IOException, ParseException {
        final String fileContents = getLastAddressBookFileEndsWith(DEBUG);
        final String addressBookString = getTextAfterHeader(fileContents, header);
        return parseAddressBook(addressBookString);
    }

    /**
     * Get the text from the fileContents after the header.
     *
     * @param fileContents the file contents.
     * @param header       the header to find.
     * @return the text from the fileContents after the header.
     */
    @NonNull
    String getTextAfterHeader(@NonNull final String fileContents, @NonNull final String header) {
        Objects.requireNonNull(fileContents, "fileContents must not be null");
        Objects.requireNonNull(header, "header must not be null");
        final int headerStartIndex = fileContents.indexOf(header);
        final int addressBookStartIndex = headerStartIndex + header.length();
        final int addressBookEndIndex = fileContents.indexOf("\n\n", addressBookStartIndex);
        return fileContents
                .substring(addressBookStartIndex, addressBookEndIndex)
                .trim();
    }

    /**
     * Parse the address book from the given string.
     *
     * @param addressBookString the address book string.
     * @return the address book.
     * @throws ParseException if unable to parse the address book.
     */
    @NonNull
    private AddressBook parseAddressBook(@NonNull final String addressBookString) throws ParseException {
        Objects.requireNonNull(addressBookString, "addressBookString must not be null");
        return AddressBookUtils.parseAddressBookConfigText(
                addressBookString,
                id -> id,
                ip -> {
                    try {
                        return Network.isOwn(ip);
                    } catch (SocketException e) {
                        logger.error(EXCEPTION.getMarker(), "Unable to determine if {} is own ip address", ip, e);
                        return false;
                    }
                },
                Object::toString);
    }

    /**
     * Get the last address book file that ends with the given suffix.
     *
     * @param suffix the suffix to match.
     * @return the last address book file that ends with the given suffix.
     * @throws IOException if unable to read the file.
     */
    @NonNull
    private String getLastAddressBookFileEndsWith(@NonNull final String suffix) throws IOException {
        final Path addressBookDirectory = Path.of(addressBookConfig.addressBookDirectory());
        File[] files = addressBookDirectory.toFile().listFiles(File::isFile);
        final AtomicReference<Path> lastAddressBookDebugFile = new AtomicReference<>(null);
        for (int i = 0; i < files.length; i++) {
            if (files[i].getName().endsWith(suffix)) {
                String fileName = files[i].getName();
                Path lastAddressBookDebugFilePath = lastAddressBookDebugFile.get();
                if (lastAddressBookDebugFilePath == null) {
                    lastAddressBookDebugFile.set(files[i].toPath());
                } else if (fileName.compareTo(
                                lastAddressBookDebugFilePath.getFileName().toString())
                        > 0) {
                    lastAddressBookDebugFile.set(files[i].toPath());
                }
            }
        }
        return Files.readString(lastAddressBookDebugFile.get());
    }
}
