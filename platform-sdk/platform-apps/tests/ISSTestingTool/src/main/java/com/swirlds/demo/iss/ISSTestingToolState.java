/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

package com.swirlds.demo.iss;
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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.ParseException;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.platform.test.fixtures.state.FakeStateLifecycles;
import com.swirlds.state.merkle.singleton.StringLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * State for the ISSTestingTool.
 */
@ConstructableIgnored
public class ISSTestingToolState extends PlatformMerkleStateRoot {

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    static {
        FakeStateLifecycles.registerMerkleStateRootClassIds();
    }

    private static final long CLASS_ID = 0xf059378c7764ef47L;

    // 0 is PLATFORM_STATE, 1 is ROSTERS, 2 is ROSTER_STATE
    private static final int RUNNING_SUM_INDEX = 3;
    private static final int GENESIS_TIMESTAMP_INDEX = 4;
    private static final int PLANNED_ISS_LIST_INDEX = 5;
    private static final int PLANNED_LOG_ERROR_LIST_INDEX = 6;

    /**
     * The true "state" of this app. Each transaction is just an integer that gets added to this value.
     */
    private long runningSum = 0;

    /**
     * The timestamp of the first event after genesis.
     */
    private Instant genesisTimestamp;

    /**
     * A list of ISS incidents that will be triggered at a predetermined consensus time
     */
    private List<PlannedIss> plannedIssList = new LinkedList<>();

    /**
     * A list of errors that will be logged at a predetermined consensus time
     */
    private List<PlannedLogError> plannedLogErrorList = new LinkedList<>();

    public ISSTestingToolState(@NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        super(versionFactory);
    }

    public void initState(InitTrigger trigger, Platform platform) {
        // since the test occurrences are relative to the genesis timestamp, the data only needs to be parsed at genesis
        if (trigger == InitTrigger.GENESIS) {
            final ISSTestingToolConfig testingToolConfig =
                    platform.getContext().getConfiguration().getConfigData(ISSTestingToolConfig.class);

            this.plannedIssList = testingToolConfig.getPlannedISSs();
            this.plannedLogErrorList = testingToolConfig.getPlannedLogErrors();
            writeObjectByChildIndex(PLANNED_ISS_LIST_INDEX, plannedIssList);
            writeObjectByChildIndex(PLANNED_LOG_ERROR_LIST_INDEX, plannedLogErrorList);
        } else {
            final StringLeaf runningSumLeaf = getChild(RUNNING_SUM_INDEX);
            if (runningSumLeaf != null) {
                runningSum = Long.parseLong(runningSumLeaf.getLabel());
            }
            final StringLeaf genesisTimestampLeaf = getChild(GENESIS_TIMESTAMP_INDEX);
            if (genesisTimestampLeaf != null) {
                genesisTimestamp = Instant.parse(genesisTimestampLeaf.getLabel());
            }
            plannedIssList = readObjectByChildIndex(PLANNED_ISS_LIST_INDEX, PlannedIss::new);
            plannedLogErrorList = readObjectByChildIndex(PLANNED_LOG_ERROR_LIST_INDEX, PlannedLogError::new);
        }
    }

    <T extends SelfSerializable> List<T> readObjectByChildIndex(final int index, final Supplier<T> factory) {
        final StringLeaf stringValue = getChild(index);
        if (stringValue != null) {
            try {
                final SerializableDataInputStream in = new SerializableDataInputStream(
                        new ByteArrayInputStream(stringValue.getLabel().getBytes(StandardCharsets.UTF_8)));
                return in.readSerializableList(1024, false, factory);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }

    <T extends SelfSerializable> void writeObjectByChildIndex(final int index, final List<T> list) {
        try {
            final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);
            out.writeSerializableList(list, false, true);
            setChild(index, new StringLeaf(byteOut.toString(StandardCharsets.UTF_8)));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public void preHandle(
            @NonNull final Event event,
            @NonNull
            final Consumer<ScopedSystemTransaction<StateSignatureTransaction>>
                    stateSignatureTransactionCallback) {
        event.forEachTransaction(transaction -> {
            // We are not interested in pre-handling any system transactions, as they are
            // specific for the platform only.We also don't want to consume deprecated
            // EventTransaction.STATE_SIGNATURE_TRANSACTION system transactions in the
            // callback,since it's intended to be used only for the new form of encoded system
            // transactions in Bytes.Thus, we can directly skip the current
            // iteration, if it processes a deprecated system transaction with the
            // EventTransaction.STATE_SIGNATURE_TRANSACTION type.
            if (transaction.isSystem()) {
                return;
            }

            // We should consume in the callback the new form of system transactions in Bytes
            if (areTransactionBytesSystemOnes(transaction)) {
                consumeSystemTransaction(transaction, event, stateSignatureTransactionCallback);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(
            @NonNull final Round round,
            @NonNull final PlatformStateModifier platformState,
            @NonNull
            final Consumer<ScopedSystemTransaction<StateSignatureTransaction>>
                    stateSignatureTransactionCallback) {
        throwIfImmutable();
        final Iterator<ConsensusEvent> eventIterator = round.iterator();

        while (eventIterator.hasNext()) {
            final var event = eventIterator.next();
            captureTimestamp(event);
            event.consensusTransactionIterator().forEachRemaining(transaction -> {
                // We are not interested in handling any system transactions, as they are specific
                // for the platform only.We also don't want to consume deprecated
                // EventTransaction.STATE_SIGNATURE_TRANSACTION system transactions in the
                // callback,since it's intended to be used only for the new form of encoded system
                // transactions in Bytes.Thus, we can directly skip the current
                // iteration, if it processes a deprecated system transaction with the
                // EventTransaction.STATE_SIGNATURE_TRANSACTION type.
                if (transaction.isSystem()) {
                    return;
                }

                // We should consume in the callback the new form of system transactions in Bytes
                if (areTransactionBytesSystemOnes(transaction)) {
                    consumeSystemTransaction(transaction, event, stateSignatureTransactionCallback);
                } else {
                    handleTransaction(transaction);
                }
            });
            if (!eventIterator.hasNext()) {
                final Instant currentTimestamp = event.getConsensusTimestamp();
                final Duration elapsedSinceGenesis = Duration.between(genesisTimestamp, currentTimestamp);

                final PlannedIss plannedIss =
                        shouldTriggerIncident(elapsedSinceGenesis, currentTimestamp, plannedIssList);

                if (plannedIss != null) {
                    triggerISS(round, plannedIss, elapsedSinceGenesis, currentTimestamp);
                    // Record the consensus time at which this ISS was provoked
                    scratchPad.set(
                            IssTestingToolScratchpad.PROVOKED_ISS,
                            new SerializableLong(currentTimestamp.toEpochMilli()));
                }

                final PlannedLogError plannedLogError =
                        shouldTriggerIncident(elapsedSinceGenesis, currentTimestamp, plannedLogErrorList);
                if (plannedLogError != null) {
                    triggerLogError(plannedLogError, elapsedSinceGenesis);
                }
            }
        }
    }


    /**
     * Save the event's timestamp, if needed.
     */
    void captureTimestamp(@NonNull final ConsensusEvent event) {
        if (genesisTimestamp == null) {
            genesisTimestamp = event.getConsensusTimestamp();
            setChild(GENESIS_TIMESTAMP_INDEX, new StringLeaf(genesisTimestamp.toString()));
        }
    }

    /**
     * Apply a transaction to the state.
     *
     * @param transaction the transaction to apply
     */
    private void handleTransaction(final ConsensusTransaction transaction) {
        final int delta =
                ByteUtils.byteArrayToInt(transaction.getApplicationTransaction().toByteArray(), 0);
        runningSum += delta;
        setChild(RUNNING_SUM_INDEX, new StringLeaf(Long.toString(runningSum)));
    }

    /**
     * Checks if the transaction bytes are system ones. The test creates application transactions
     * with max length of 4. System transactions will be always bigger than that.
     *
     * @param transaction the consensus transaction to check
     * @return true if the transaction bytes are system ones, false otherwise
     */
    private boolean areTransactionBytesSystemOnes(final Transaction transaction) {
        return transaction.getApplicationTransaction().length() > 4;
    }

    private void consumeSystemTransaction(
            final Transaction transaction,
            final Event event,
            final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        try {
            final var stateSignatureTransaction =
                    StateSignatureTransaction.PROTOBUF.parse(transaction.getApplicationTransaction());
            stateSignatureTransactionCallback.accept(new ScopedSystemTransaction<>(
                    event.getCreatorId(), event.getSoftwareVersion(), stateSignatureTransaction));
        } catch (final ParseException e) {
            logger.error("Failed to parse StateSignatureTransaction", e);
        }
    }

    /**
     * Iterate over a list of planned incidents, and return the first one that should be triggered. If no incident from
     * the list should be triggered, return null
     *
     * @param elapsedSinceGenesis the amount of time that has elapsed since genesis
     * @param currentTimestamp    the current consensus timestamp
     * @param plannedIncidentList the list of planned incidents to iterate over
     * @param <T>                 the type of incident in the list
     * @return the first incident that should be triggered, or null if no incident should be triggered
     */
    @Nullable
    private <T extends PlannedIncident> T shouldTriggerIncident(
            @NonNull final Duration elapsedSinceGenesis,
            @NonNull final Instant currentTimestamp,
            @NonNull final List<T> plannedIncidentList) {

        Objects.requireNonNull(elapsedSinceGenesis);
        Objects.requireNonNull(currentTimestamp);
        Objects.requireNonNull(plannedIncidentList);

        final Iterator<T> plannedIncidentIterator = plannedIncidentList.listIterator();
        while (plannedIncidentIterator.hasNext()) {
            final T plannedIncident = plannedIncidentIterator.next();

            if (isLessThan(elapsedSinceGenesis, plannedIncident.getTimeAfterGenesis())) {
                // The next planned incident is for some time in the future, so return null
                return null;
            }

            // If we reach this point then we are ready to trigger the incident.
            // Once triggered, the same incident is not triggered again.
            plannedIncidentIterator.remove();

            if (isGreaterThan(
                    elapsedSinceGenesis, plannedIncident.getTimeAfterGenesis().plus(INCIDENT_WINDOW))) {

                // Consensus time has skipped forward, possibly because this node was restarted.
                // We are outside the allowable window for the scheduled incident, so do not trigger this one.
                logger.info(
                        STARTUP.getMarker(),
                        "Planned {} skipped at {}. Planned time after genesis: {}. "
                                + "Elapsed time since genesis at skip: {}",
                        plannedIncident.getDescriptor(),
                        currentTimestamp,
                        plannedIncident.getTimeAfterGenesis(),
                        elapsedSinceGenesis);

                continue;
            }

            final SerializableLong issLong = scratchPad.get(IssTestingToolScratchpad.PROVOKED_ISS);
            if (issLong != null) {
                final Instant lastProvokedIssTime = Instant.ofEpochMilli(issLong.getValue());
                if (lastProvokedIssTime.equals(currentTimestamp)) {
                    logger.info(
                            STARTUP.getMarker(),
                            "Planned {} skipped at {} because this ISS was already invoked (likely before a restart).",
                            plannedIncident.getDescriptor(),
                            currentTimestamp);
                }
                continue;
            }

            return plannedIncident;
        }

        return null;
    }

    void incrementRunningSum(long delta) {
        runningSum += delta;
        setChild(RUNNING_SUM_INDEX, new StringLeaf(Long.toString(runningSum)));
    }

    Instant getGenesisTimestamp() {
        return genesisTimestamp;
    }

    List<PlannedIss> getPlannedIssList() {
        return plannedIssList;
    }

    List<PlannedLogError> getPlannedLogErrorList() {
        return plannedLogErrorList;
    }

    /**
     * Copy constructor.
     */
    private ISSTestingToolState(final ISSTestingToolState that) {
        super(that);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized ISSTestingToolState copy() {
        throwIfImmutable();
        setImmutable(true);
        return new ISSTestingToolState(this);
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
}
