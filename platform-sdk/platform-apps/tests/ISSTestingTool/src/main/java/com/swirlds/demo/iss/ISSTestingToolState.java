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

import static com.swirlds.common.utility.CompareTo.isGreaterThan;
import static com.swirlds.common.utility.CompareTo.isLessThan;
import static com.swirlds.common.utility.NonCryptographicHashing.hash64;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.ByteUtils;
import com.swirlds.platform.scratchpad.Scratchpad;
import com.swirlds.platform.state.MerkleStateLifecycles;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the ISSTestingTool.
 */
@ConstructableIgnored
public class ISSTestingToolState extends MerkleStateRoot {

    private static final Logger logger = LogManager.getLogger(ISSTestingToolState.class);

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    static {
        FakeMerkleStateLifecycles.registerMerkleStateRootClassIds();
    }

    private static final long CLASS_ID = 0xf059378c7764ef47L;

    /**
     * Only trigger an incident if consensus time is within this time window of the scheduled time. If consensus time
     * "skips" forward longer than this window then the scheduled incident will be ignored.
     */
    private static final Duration INCIDENT_WINDOW = Duration.ofSeconds(10);

    private NodeId selfId;

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

    private boolean immutable;

    private Scratchpad<IssTestingToolScratchpad> scratchPad;

    public ISSTestingToolState(
            @NonNull final MerkleStateLifecycles lifecycles,
            @NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        super(lifecycles, versionFactory);
    }

    /**
     * Copy constructor.
     */
    private ISSTestingToolState(final ISSTestingToolState that) {
        super(that);
        this.runningSum = that.runningSum;
        this.plannedIssList = new LinkedList<>(that.plannedIssList);
        this.plannedLogErrorList = new LinkedList<>(that.plannedLogErrorList);
        this.genesisTimestamp = that.genesisTimestamp;
        this.selfId = that.selfId;
        this.scratchPad = that.scratchPad;
        that.immutable = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isImmutable() {
        return immutable;
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
    public void init(
            @NonNull final Platform platform,
            @NonNull final InitTrigger trigger,
            @Nullable final SoftwareVersion previousSoftwareVersion) {
        super.init(platform, trigger, previousSoftwareVersion);

        throwIfImmutable();

        // since the test occurrences are relative to the genesis timestamp, the data only needs to be parsed at genesis
        if (trigger == InitTrigger.GENESIS) {
            final ISSTestingToolConfig testingToolConfig =
                    platform.getContext().getConfiguration().getConfigData(ISSTestingToolConfig.class);

            this.plannedIssList = testingToolConfig.getPlannedISSs();
            this.plannedLogErrorList = testingToolConfig.getPlannedLogErrors();
        }

        this.selfId = platform.getSelfId();
        this.scratchPad =
                Scratchpad.create(platform.getContext(), selfId, IssTestingToolScratchpad.class, "ISSTestingTool");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(final Round round, final PlatformStateModifier platformState) {
        throwIfImmutable();
        final Iterator<ConsensusEvent> eventIterator = round.iterator();

        while (eventIterator.hasNext()) {
            final ConsensusEvent event = eventIterator.next();
            captureTimestamp(event);
            event.consensusTransactionIterator().forEachRemaining(this::handleTransaction);
            if (!eventIterator.hasNext()) {
                final Instant currentTimestamp = event.getConsensusTimestamp();
                final Duration elapsedSinceGenesis = Duration.between(genesisTimestamp, currentTimestamp);

                final PlannedIss plannedIss =
                        shouldTriggerIncident(elapsedSinceGenesis, currentTimestamp, plannedIssList);

                if (plannedIss != null) {
                    triggerISS(round.getConsensusRoster(), plannedIss, elapsedSinceGenesis, currentTimestamp);
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
    private void captureTimestamp(final ConsensusEvent event) {
        if (genesisTimestamp == null) {
            genesisTimestamp = event.getConsensusTimestamp();
        }
    }

    /**
     * Apply a transaction to the state.
     *
     * @param transaction the transaction to apply
     */
    private void handleTransaction(final ConsensusTransaction transaction) {
        if (transaction.isSystem()) {
            return;
        }
        final int delta =
                ByteUtils.byteArrayToInt(transaction.getApplicationTransaction().toByteArray(), 0);
        runningSum += delta;
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
            if (partitionWeights.get(partition) > largestPartitionWeight) {
                largestPartition = partition;
                largestPartitionWeight = partitionWeights.getOrDefault(partition, 0L);
            }
        }

        return largestPartition;
    }

    /**
     * Trigger an ISS
     *
     * @param roster         the address book for this round
     * @param plannedIss          the planned ISS to trigger
     * @param elapsedSinceGenesis the amount of time that has elapsed since genesis
     * @param currentTimestamp    the current consensus timestamp
     */
    private void triggerISS(
            @NonNull final Roster roster,
            @NonNull final PlannedIss plannedIss,
            @NonNull final Duration elapsedSinceGenesis,
            @NonNull final Instant currentTimestamp) {

        Objects.requireNonNull(plannedIss);
        Objects.requireNonNull(elapsedSinceGenesis);
        Objects.requireNonNull(currentTimestamp);

        final int hashPartitionIndex = plannedIss.getPartitionOfNode(selfId);
        if (hashPartitionIndex == findLargestPartition(roster, plannedIss)) {
            // If we are in the largest partition then don't bother modifying the state.
            return;
        }

        // Randomly mutate the state. Each node in the same partition will get the same random mutation.
        // Nodes in different partitions will get a different random mutation with high probability.
        final long hashPartitionSeed = hash64(currentTimestamp.toEpochMilli(), hashPartitionIndex);
        final Random random = new Random(hashPartitionSeed);
        runningSum += random.nextInt();

        logger.info(
                STARTUP.getMarker(),
                "ISS intentionally provoked. This ISS was planned to occur at time after genesis {}, "
                        + "and actually occurred at time after genesis {}. This node ({}) is in partition {} and will "
                        + "agree with the hashes of all other nodes in partition {}. Nodes in other partitions "
                        + "are expected to have divergent hashes.",
                plannedIss.getTimeAfterGenesis(),
                elapsedSinceGenesis,
                selfId,
                hashPartitionIndex,
                hashPartitionIndex);
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
