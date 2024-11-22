/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.legacy.LogMarker.DEMO_INFO;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.state.address.AddressBookInitializer.CONFIG_ADDRESS_BOOK_HEADER;
import static com.swirlds.platform.state.address.AddressBookInitializer.CONFIG_ADDRESS_BOOK_USED;
import static com.swirlds.platform.state.address.AddressBookInitializer.STATE_ADDRESS_BOOK_HEADER;
import static com.swirlds.platform.state.address.AddressBookInitializer.STATE_ADDRESS_BOOK_USED;
import static com.swirlds.platform.state.address.AddressBookInitializer.USED_ADDRESS_BOOK_HEADER;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.ByteUtils;
import com.swirlds.common.utility.StackTrace;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.roster.RosterRetriever;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.MerkleStateLifecycles;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.address.AddressBookUtils;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the AddressBookTestingTool.
 */
@ConstructableIgnored
public class AddressBookTestingToolState extends PlatformMerkleStateRoot {

    private static final Logger logger = LogManager.getLogger(AddressBookTestingToolState.class);

    /** the suffix for the debug address book */
    private static final String DEBUG = "debug";

    /** the suffix for the test address book */
    private AddressBookTestingToolConfig testingToolConfig;
    /** the address book configuration */
    private AddressBookConfig addressBookConfig;
    /** flag indicating if weighting behavior has been logged. */
    private static final AtomicBoolean logWeightingBehavior = new AtomicBoolean(true);

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private static final long CLASS_ID = 0xf052378c7364ef47L;

    private NodeId selfId;

    /** false until the test scenario has been validated, true afterwards. */
    private final AtomicBoolean validationPerformed = new AtomicBoolean(false);

    private Platform platform = null;

    private PlatformContext context = null;

    /**
     * The true "state" of this app. Each transaction is just an integer that gets added to this value.
     */
    private long runningSum = 0;

    /**
     * The number of rounds handled by this app. Is incremented each time
     * {@link #handleConsensusRound(Round, PlatformStateModifier)} is called. Note that this may not actually equal the round
     * number, since we don't call {@link #handleConsensusRound(Round, PlatformStateModifier)} for rounds with no events.
     *
     * <p>
     * Affects the hash of this node.
     */
    private long roundsHandled = 0;

    /**
     * If not zero and we are handling the first round after genesis, configure a freeze this duration later.
     * <p>
     * Does not affect the hash of this node (although actions may be taken based on this info that DO affect the
     * hash).
     */
    private Duration freezeAfterGenesis = null;

    public AddressBookTestingToolState(
            @NonNull final MerkleStateLifecycles lifecycles,
            @NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        super(lifecycles, versionFactory);
        logger.info(STARTUP.getMarker(), "New State Constructed.");
    }

    /**
     * Copy constructor.
     */
    private AddressBookTestingToolState(@NonNull final AddressBookTestingToolState that) {
        super(that);
        Objects.requireNonNull(that, "the address book testing tool state to copy cannot be null");
        this.testingToolConfig = that.testingToolConfig;
        this.addressBookConfig = that.addressBookConfig;
        this.runningSum = that.runningSum;
        this.selfId = that.selfId;
        this.platform = that.platform;
        this.context = that.context;
        this.validationPerformed.set(that.validationPerformed.get());
        this.roundsHandled = that.roundsHandled;
        this.freezeAfterGenesis = that.freezeAfterGenesis;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized AddressBookTestingToolState copy() {
        throwIfImmutable();
        setImmutable(true);
        return new AddressBookTestingToolState(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(
            @NonNull final Platform platform,
            @NonNull final InitTrigger trigger,
            @Nullable final SoftwareVersion previousSoftwareVersion) {
        Objects.requireNonNull(platform, "the platform cannot be null");
        Objects.requireNonNull(trigger, "the init trigger cannot be null");
        addressBookConfig = platform.getContext().getConfiguration().getConfigData(AddressBookConfig.class);
        testingToolConfig = platform.getContext().getConfiguration().getConfigData(AddressBookTestingToolConfig.class);
        this.freezeAfterGenesis = testingToolConfig.freezeAfterGenesis();

        this.platform = platform;
        this.context = platform.getContext();

        logger.info(STARTUP.getMarker(), "init called in State.");
        throwIfImmutable();

        this.selfId = platform.getSelfId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(@NonNull final Round round, @NonNull final PlatformStateModifier platformState) {
        Objects.requireNonNull(round, "the round cannot be null");
        Objects.requireNonNull(platformState, "the platform state cannot be null");
        throwIfImmutable();

        if (roundsHandled == 0 && !freezeAfterGenesis.equals(Duration.ZERO)) {
            // This is the first round after genesis.
            logger.info(
                    STARTUP.getMarker(),
                    "Setting freeze time to {} seconds after genesis.",
                    freezeAfterGenesis.getSeconds());
            platformState.setFreezeTime(round.getConsensusTimestamp().plus(freezeAfterGenesis));
        }

        roundsHandled++;

        final Iterator<ConsensusEvent> eventIterator = round.iterator();

        while (eventIterator.hasNext()) {
            final ConsensusEvent event = eventIterator.next();
            event.consensusTransactionIterator().forEachRemaining(this::handleTransaction);
        }

        if (!validationPerformed.getAndSet(true)) {
            String testScenario = testingToolConfig.testScenario();
            if (validateTestScenario()) {
                logger.info(STARTUP.getMarker(), "Test scenario {}: finished without errors.", testScenario);
            } else {
                logger.error(EXCEPTION.getMarker(), "Test scenario {}: validation failed with errors.", testScenario);
            }
        }
    }

    /**
     * Apply a transaction to the state.
     *
     * @param transaction the transaction to apply
     */
    private void handleTransaction(@NonNull final ConsensusTransaction transaction) {
        if (transaction.isSystem()) {
            return;
        }
        final int delta =
                ByteUtils.byteArrayToInt(transaction.getApplicationTransaction().toByteArray(), 0);
        runningSum += delta;
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

    @Override
    public int getMinimumSupportedVersion() {
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
        logger.info(DEMO_INFO.getMarker(), "updateWeight called in State. Weighting Behavior: {}", weightingBehavior);
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
        for (final Address address : addressBook) {
            addressBook.updateWeight(address.getNodeId(), 10);
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
        for (final Address address : addressBook) {
            addressBook.updateWeight(address.getNodeId(), address.getNodeId().id());
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
                case UPGRADE_ADD_NODE:
                    return softwareUpgradeAddNodeWeightingBehavior1(testScenario);
                case UPGRADE_REMOVE_NODE:
                    return softwareUpgradeRemoveNodeWeightingBehavior1(testScenario);
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

    private boolean softwareUpgradeRemoveNodeWeightingBehavior1(@NonNull final AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(false, testScenario, 2, 1)) {
            return false;
        }

        final AddressBook platformAddressBook = RosterUtils.buildAddressBook(platform.getRoster());
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook stateAddressBook = getStateAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsRoster(platformAddressBook, configAddressBook, true)
                && equalsAsRoster(platformAddressBook, stateAddressBook, false)
                && equalsAsRoster(platformAddressBook, usedAddressBook, true)
                && equalsAsRoster(platformAddressBook, updatedAddressBook, true)
                && removedNodeFromAddressBook(platformAddressBook, stateAddressBook);
    }

    private boolean softwareUpgradeAddNodeWeightingBehavior1(@NonNull final AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(false, testScenario, 2, 1)) {
            return false;
        }

        final AddressBook platformAddressBook = RosterUtils.buildAddressBook(platform.getRoster());
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook stateAddressBook = getStateAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        if (equalsAsRosterWithoutCert(stateAddressBook, configAddressBook)) {
            // This is a new node.
            return equalsAsRoster(platformAddressBook, configAddressBook, true)
                    // genesis state uses the config address book.
                    && equalsAsRoster(platformAddressBook, stateAddressBook, true)
                    && equalsAsRoster(platformAddressBook, usedAddressBook, true)
                    && equalsAsRoster(platformAddressBook, updatedAddressBook, true);
        } else {
            // This is an existing node.

            // The equality to config text is due to a limitation of testing capability.
            // Currently all nodes have to start with the same config.txt file that matches the updated weight.
            return equalsAsRoster(platformAddressBook, configAddressBook, true)
                    && equalsAsRoster(platformAddressBook, stateAddressBook, false)
                    && equalsAsRoster(platformAddressBook, usedAddressBook, true)
                    && equalsAsRoster(platformAddressBook, updatedAddressBook, true)
                    && addedNodeToAddressBook(platformAddressBook, stateAddressBook);
        }
    }

    private boolean softwareUpgradeForceUseOfConfigAddressBook(@NonNull final AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(true, testScenario, 2, 2)) {
            return false;
        }

        final AddressBook platformAddressBook = RosterUtils.buildAddressBook(platform.getRoster());
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook stateAddressBook = getStateAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsRoster(platformAddressBook, configAddressBook, true)
                && equalsAsRoster(platformAddressBook, stateAddressBook, true)
                && equalsAsRoster(platformAddressBook, usedAddressBook, true)
                && equalsAsRoster(platformAddressBook, updatedAddressBook, false)
                && theConfigurationAddressBookWasUsed();
    }

    private boolean softwareUpgradeWeightingBehavior2(@NonNull final AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(false, testScenario, 2, 2)) {
            return false;
        }

        final AddressBook platformAddressBook = RosterUtils.buildAddressBook(platform.getRoster());
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook stateAddressBook = getStateAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsRoster(platformAddressBook, configAddressBook, false)
                && equalsAsRoster(platformAddressBook, stateAddressBook, false)
                && equalsAsRoster(platformAddressBook, usedAddressBook, true)
                && equalsAsRoster(platformAddressBook, updatedAddressBook, true);
    }

    private boolean noSoftwareUpgradeForceUseOfConfigAddressBook(@NonNull final AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(true, testScenario, 1, 1)) {
            return false;
        }

        final AddressBook platformAddressBook = RosterUtils.buildAddressBook(platform.getRoster());
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook stateAddressBook = getStateAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsRoster(platformAddressBook, configAddressBook, true)
                && equalsAsRoster(platformAddressBook, stateAddressBook, true)
                && equalsAsRoster(platformAddressBook, usedAddressBook, true)
                && equalsAsRoster(platformAddressBook, updatedAddressBook, false)
                && theConfigurationAddressBookWasUsed();
    }

    private boolean noSoftwareUpgradeUseSavedStateAddressBook(AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(false, testScenario, 1, 1)) {
            return false;
        }

        final AddressBook platformAddressBook = RosterUtils.buildAddressBook(platform.getRoster());
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook stateAddressBook = getStateAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsRoster(platformAddressBook, configAddressBook, true)
                && equalsAsRoster(platformAddressBook, stateAddressBook, true)
                && equalsAsRoster(platformAddressBook, usedAddressBook, true)
                && equalsAsRoster(platformAddressBook, updatedAddressBook, false)
                && theStateAddressBookWasUsed();
    }

    private boolean genesisForceUseOfConfigAddressBookFalse(@NonNull final AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(false, testScenario, 1, 1)) {
            return false;
        }

        final AddressBook platformAddressBook = RosterUtils.buildAddressBook(platform.getRoster());
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook stateAddressBook = getStateAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsRoster(platformAddressBook, configAddressBook, true)
                && equalsAsRoster(platformAddressBook, usedAddressBook, true)
                && equalsAsRoster(platformAddressBook, stateAddressBook, true)
                && equalsAsRoster(platformAddressBook, updatedAddressBook, false);
    }

    private boolean genesisForceUseOfConfigAddressBookTrue(@NonNull final AddressBookTestScenario testScenario)
            throws IOException, ParseException {
        if (!checkTestScenarioConditions(true, testScenario, 1, 1)) {
            return false;
        }

        final AddressBook platformAddressBook = RosterUtils.buildAddressBook(platform.getRoster());
        final AddressBook configAddressBook = getConfigAddressBook();
        final AddressBook stateAddressBook = getStateAddressBook();
        final AddressBook usedAddressBook = getUsedAddressBook();
        final AddressBook updatedAddressBook = updateWeight(configAddressBook.copy(), context);

        return equalsAsRoster(platformAddressBook, configAddressBook, true)
                && equalsAsRoster(platformAddressBook, usedAddressBook, true)
                && equalsAsRoster(platformAddressBook, stateAddressBook, true)
                && equalsAsRoster(platformAddressBook, updatedAddressBook, false)
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
     * Remove certificates from a roster.
     * @param roster an input roster
     * @return the same roster as input but with all gossip certs removed
     */
    private Roster removeCerts(@NonNull final Roster roster) {
        return roster.copyBuilder()
                .rosterEntries(roster.rosterEntries().stream()
                        .map(re -> re.copyBuilder()
                                .gossipCaCertificate(Bytes.EMPTY)
                                .build())
                        .toList())
                .build();
    }

    /**
     * Compare two AddressBooks after converting them to Rosters and removing all certificates
     * to avoid mismatches for fields absent from the Roster.
     * @param addressBook1 the first address book
     * @param addressBook2 the second address book
     * @return true if equals, false otherwise
     */
    private boolean equalsAsRosterWithoutCert(
            @NonNull final AddressBook addressBook1, @NonNull final AddressBook addressBook2) {
        final Roster roster1 = removeCerts(RosterRetriever.buildRoster(addressBook1));
        final Roster roster2 = removeCerts(RosterRetriever.buildRoster(addressBook2));
        return roster1.equals(roster2);
    }

    /**
     * This test compares the equality of two address books against the expected result.
     *
     * @param addressBook1 the first address book
     * @param addressBook2 the second address book
     * @return true if the comparison matches the expected result, false otherwise.
     */
    private boolean equalsAsRoster(
            @NonNull final AddressBook addressBook1,
            @NonNull final AddressBook addressBook2,
            final boolean expectedResult) {
        final boolean pass = equalsAsRosterWithoutCert(addressBook1, addressBook2) == expectedResult;
        if (!pass) {
            if (expectedResult) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "The address books are not equal as Roster. {}",
                        StackTrace.getStackTrace());
            } else {
                logger.error(
                        EXCEPTION.getMarker(), "The address books are equal as Roster. {}", StackTrace.getStackTrace());
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
     * Checks if the new address book contains a proper subset of the node ids in the old address book and that the
     * nextNodeId value of the new address book is the same.
     *
     * @param newAddressBook The new address book
     * @param oldAddressBook The old address book
     * @return true if the new address book has all the nodes in the old address book (ignoring weight differences) plus
     * at least one more and its nextNodeId value is larger.
     */
    private boolean removedNodeFromAddressBook(
            @NonNull final AddressBook newAddressBook, @NonNull final AddressBook oldAddressBook) {
        int missingCount = 0;
        for (final Address address : oldAddressBook) {
            final NodeId nodeId = address.getNodeId();
            if (newAddressBook.contains(nodeId)) {
                continue;
            }
            missingCount++;
        }
        final int newSize = newAddressBook.getSize();
        final int oldSize = oldAddressBook.getSize();
        final boolean atLeastOneNodeRemoved = missingCount > 0;
        if (!atLeastOneNodeRemoved) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "The new address book does not have at least one node removed. {}",
                    StackTrace.getStackTrace());
        }
        final boolean sizesCorrespond = missingCount == oldSize - newSize;
        if (!sizesCorrespond) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "The new address book contains new nodes instead of only having nodes removed. {}",
                    StackTrace.getStackTrace());
        }
        return sizesCorrespond && atLeastOneNodeRemoved;
    }

    /**
     * Checks if the old address book contains a proper subset of node ids in the new address book and that the
     * nextNodeId value of the new address book is larger.
     *
     * @param newAddressBook The new address book
     * @param oldAddressBook The old address book
     * @return true if the new address book has all the nodes in the old address book (ignoring weight differences) plus
     * at least one more and its nextNodeId value is larger.
     */
    private boolean addedNodeToAddressBook(
            @NonNull final AddressBook newAddressBook, @NonNull final AddressBook oldAddressBook) {
        int missingCount = 0;
        for (final Address address : newAddressBook) {
            final NodeId nodeId = address.getNodeId();
            if (oldAddressBook.contains(nodeId)) {
                continue;
            }
            missingCount++;
        }
        final int newSize = newAddressBook.getSize();
        final int oldSize = oldAddressBook.getSize();
        final boolean atLeastOneNodeAdded = missingCount > 0;
        if (!atLeastOneNodeAdded) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "The new address book does not have at least one node added. {}",
                    StackTrace.getStackTrace());
        }
        final boolean sizesCorrespond = missingCount == newSize - oldSize;
        if (!sizesCorrespond) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "The new address book has nodes removed instead of only having nodes added. {}",
                    StackTrace.getStackTrace());
        }
        return sizesCorrespond && atLeastOneNodeAdded;
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
        final int configABHeaderStart = fileContents.indexOf(CONFIG_ADDRESS_BOOK_HEADER);
        final int stateABHeaderStart = fileContents.indexOf(STATE_ADDRESS_BOOK_HEADER);
        final int usedABHeaderStart = fileContents.indexOf(USED_ADDRESS_BOOK_HEADER);

        final int headerStartIndex = fileContents.indexOf(header);
        final int addressBookStartIndex = headerStartIndex + header.length();

        final int addressBookEndIndex;
        if (headerStartIndex == configABHeaderStart) {
            addressBookEndIndex = stateABHeaderStart;
        } else if (headerStartIndex == stateABHeaderStart) {
            addressBookEndIndex = usedABHeaderStart;
        } else {
            addressBookEndIndex = fileContents.length();
        }

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
        return AddressBookUtils.parseAddressBookText(addressBookString);
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
        final String nodeId = "node_%s".formatted(this.selfId);
        final Path addressBookDirectory = Path.of(addressBookConfig.addressBookDirectory());
        File[] files = addressBookDirectory.toFile().listFiles(File::isFile);
        final AtomicReference<Path> lastAddressBookDebugFile = new AtomicReference<>(null);
        for (int i = 0; i < files.length; i++) {
            final String fileName = files[i].getName();
            if (fileName.contains(nodeId) && fileName.endsWith(suffix)) {
                final Path lastAddressBookDebugFilePath = lastAddressBookDebugFile.get();
                if (lastAddressBookDebugFilePath == null
                        || fileName.compareTo(lastAddressBookDebugFilePath
                                        .getFileName()
                                        .toString())
                                > 0) {
                    lastAddressBookDebugFile.set(files[i].toPath());
                }
            }
        }
        return Files.readString(lastAddressBookDebugFile.get());
    }
}
