/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import static com.swirlds.common.utility.CompareTo.isGreaterThan;
import static com.swirlds.common.utility.CompareTo.isLessThan;
import static com.swirlds.common.utility.NonCryptographicHashing.hash64;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.ParseException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.ByteUtils;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.scratchpad.Scratchpad;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class handles the lifecycle events for the {@link ISSTestingToolState}.
 */
public class ISSTestingToolStateLifecycles implements StateLifecycles<ISSTestingToolState> {

    private static final Logger logger = LogManager.getLogger(ISSTestingToolStateLifecycles.class);

    /**
     * Only trigger an incident if consensus time is within this time window of the scheduled time. If consensus time
     * "skips" forward longer than this window then the scheduled incident will be ignored.
     */
    private static final Duration INCIDENT_WINDOW = Duration.ofSeconds(10);

    private NodeId selfId;
    private Scratchpad<IssTestingToolScratchpad> scratchPad;

    @Override
    public void onStateInitialized(
            @NonNull ISSTestingToolState state,
            @NonNull Platform platform,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        state.throwIfImmutable();

        state.initState(trigger, platform);

        this.selfId = platform.getSelfId();
        this.scratchPad =
                Scratchpad.create(platform.getContext(), selfId, IssTestingToolScratchpad.class, "ISSTestingTool");
    }

    /**
     * Apply a transaction to the state.
     *
     * @param transaction the transaction to apply
     */
    private void handleTransaction(
            @NonNull final ISSTestingToolState state, @NonNull final ConsensusTransaction transaction) {
        if (transaction.isSystem()) {
            return;
        }
        final int delta =
                ByteUtils.byteArrayToInt(transaction.getApplicationTransaction().toByteArray(), 0);
        state.incrementRunningSum(delta);
    }

    @Override
    public void onHandleConsensusRound(
            @NonNull Round round,
            @NonNull ISSTestingToolState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        state.throwIfImmutable();
        final Iterator<ConsensusEvent> eventIterator = round.iterator();

        while (eventIterator.hasNext()) {
            final var event = eventIterator.next();
            state.captureTimestamp(event);
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
                    handleTransaction(state, transaction);
                }
            });
            if (!eventIterator.hasNext()) {
                final Instant currentTimestamp = event.getConsensusTimestamp();
                final Duration elapsedSinceGenesis = Duration.between(state.getGenesisTimestamp(), currentTimestamp);

                final PlannedIss plannedIss =
                        shouldTriggerIncident(elapsedSinceGenesis, currentTimestamp, state.getPlannedIssList());

                if (plannedIss != null) {
                    triggerISS(round, plannedIss, elapsedSinceGenesis, currentTimestamp, state);
                    // Record the consensus time at which this ISS was provoked
                    scratchPad.set(
                            IssTestingToolScratchpad.PROVOKED_ISS,
                            new SerializableLong(currentTimestamp.toEpochMilli()));
                }

                final PlannedLogError plannedLogError =
                        shouldTriggerIncident(elapsedSinceGenesis, currentTimestamp, state.getPlannedLogErrorList());
                if (plannedLogError != null) {
                    triggerLogError(plannedLogError, elapsedSinceGenesis);
                }
            }
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

    /**
     * Trigger an ISS
     *
     * @param round               the current round
     * @param plannedIss          the planned ISS to trigger
     * @param elapsedSinceGenesis the amount of time that has elapsed since genesis
     * @param currentTimestamp    the current consensus timestamp
     */
    private void triggerISS(
            @NonNull final Round round,
            @NonNull final PlannedIss plannedIss,
            @NonNull final Duration elapsedSinceGenesis,
            @NonNull final Instant currentTimestamp,
            @NonNull final ISSTestingToolState state) {

        Objects.requireNonNull(plannedIss);
        Objects.requireNonNull(elapsedSinceGenesis);
        Objects.requireNonNull(currentTimestamp);

        final int hashPartitionIndex = plannedIss.getPartitionOfNode(selfId);
        if (hashPartitionIndex == findLargestPartition(round.getConsensusRoster(), plannedIss)) {
            // If we are in the largest partition then don't bother modifying the state.
            return;
        }

        // Randomly mutate the state. Each node in the same partition will get the same random mutation.
        // Nodes in different partitions will get a different random mutation with high probability.
        final long hashPartitionSeed = hash64(currentTimestamp.toEpochMilli(), hashPartitionIndex);
        final Random random = new Random(hashPartitionSeed);
        state.incrementRunningSum(random.nextInt());

        logger.info(
                STARTUP.getMarker(),
                "ISS intentionally provoked. This ISS was planned to occur at time after genesis {}, "
                        + "and actually occurred at time after genesis {} in round {}. This node ({}) is in partition {} and will "
                        + "agree with the hashes of all other nodes in partition {}. Nodes in other partitions "
                        + "are expected to have divergent hashes.",
                plannedIss.getTimeAfterGenesis(),
                elapsedSinceGenesis,
                round.getRoundNum(),
                selfId,
                hashPartitionIndex,
                hashPartitionIndex);
    }

    /**
     * Determine which hash partition in a planned ISS is the largest (by consensus weight). If there is a tie, returns
     * the smaller partition index.
     *
     * @return the index of the largest hash partition
     */
    private int findLargestPartition(@NonNull final Roster roster, @NonNull final PlannedIss plannedIss) {

        final Map<Integer, Long> partitionWeights = new HashMap<>();
        for (final RosterEntry entry : roster.rosterEntries()) {
            final int partition = plannedIss.getPartitionOfNode(NodeId.of(entry.nodeId()));
            final long newWeight = partitionWeights.getOrDefault(partition, 0L) + entry.weight();
            partitionWeights.put(partition, newWeight);
        }

        int largestPartition = 0;
        long largestPartitionWeight = 0;
        for (int partition = 0; partition < plannedIss.getPartitionCount(); partition++) {
            if (partitionWeights.get(partition) != null && partitionWeights.get(partition) > largestPartitionWeight) {
                largestPartition = partition;
                largestPartitionWeight = partitionWeights.getOrDefault(partition, 0L);
            }
        }

        return largestPartition;
    }

    /**
     * Trigger a log error
     *
     * @param plannedLogError     the planned log error to trigger
     * @param elapsedSinceGenesis the amount of time that has elapsed since genesis
     */
    private void triggerLogError(
            @NonNull final PlannedLogError plannedLogError, @NonNull final Duration elapsedSinceGenesis) {

        Objects.requireNonNull(plannedLogError);
        Objects.requireNonNull(elapsedSinceGenesis);

        if (!plannedLogError.getNodeIds().contains(selfId)) {
            // don't log if this node isn't in the list of nodes that should log
            return;
        }

        logger.error(
                EXCEPTION.getMarker(),
                "This error was scheduled to be logged at time after genesis {}, and actually was logged "
                        + "at time after genesis {}.",
                plannedLogError.getTimeAfterGenesis(),
                elapsedSinceGenesis);
    }

    @Override
    public void onPreHandle(
            @NonNull Event event,
            @NonNull ISSTestingToolState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
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

    @Override
    public boolean onSealConsensusRound(@NonNull Round round, @NonNull ISSTestingToolState state) {
        // no-op
        return true;
    }

    @Override
    public void onUpdateWeight(
            @NonNull ISSTestingToolState state,
            @NonNull AddressBook configAddressBook,
            @NonNull PlatformContext context) {
        // no-op
    }

    @Override
    public void onNewRecoveredState(@NonNull ISSTestingToolState recoveredState) {
        // no-op
    }
}
