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
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

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
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.utility.ByteUtils;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the ISSTestingTool.
 */
public class ISSTestingToolState extends PartialMerkleLeaf implements SwirldState2, MerkleLeaf {

    private static final Logger logger = LogManager.getLogger(ISSTestingToolState.class);

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private static final long CLASS_ID = 0xf059378c7764ef47L;

    /**
     * Only trigger an ISS if consensus time is within this time window of a scheduled ISS incident.
     * If consensus time "skips" forward longer than this window then the scheduled ISS will be ignored.
     */
    private static final Duration ISS_WINDOW = Duration.ofSeconds(10);

    private long selfId;

    /**
     * The true "state" of this app. Each transaction is just an integer that gets added to this value.
     */
    private long runningSum = 0;

    /**
     * The timestamp of the first event after genesis.
     */
    private Instant genesisTimestamp;

    private List<PlannedIss> plannedIssList = new LinkedList<>();

    private boolean immutable;

    public ISSTestingToolState() {}

    /**
     * Copy constructor.
     */
    private ISSTestingToolState(final ISSTestingToolState that) {
        super(that);
        this.runningSum = that.runningSum;
        this.plannedIssList = new LinkedList<>(that.plannedIssList);
        this.genesisTimestamp = that.genesisTimestamp;
        this.selfId = that.selfId;
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
        return new ISSTestingToolState(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(
            final Platform platform,
            final SwirldDualState swirldDualState,
            final InitTrigger trigger,
            final SoftwareVersion previousSoftwareVersion) {

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
    public void handleConsensusRound(final Round round, final SwirldDualState swirldDualState) {
        throwIfImmutable();
        final Iterator<ConsensusEvent> eventIterator = round.iterator();

        while (eventIterator.hasNext()) {
            final ConsensusEvent event = eventIterator.next();
            captureTimestamp(event);
            event.consensusTransactionIterator().forEachRemaining(this::handleTransaction);
            if (!eventIterator.hasNext()) {
                maybeTriggerIss(event.getConsensusTimestamp());
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
     * @param transaction
     * 		the transaction to apply
     */
    private void handleTransaction(final ConsensusTransaction transaction) {
        final int delta = ByteUtils.byteArrayToInt(transaction.getContents(), 0);
        runningSum += delta;
    }

    /**
     * Check if it's time to trigger an ISS. If it is, then mutate the state as needed.
     *
     * @param currentTimestamp
     * 		the current consensus time
     */
    private void maybeTriggerIss(final Instant currentTimestamp) {
        if (currentTimestamp == null) {
            return;
        }

        final Duration elapsedSinceGenesis = Duration.between(genesisTimestamp, currentTimestamp);

        final Iterator<PlannedIss> plannedIssIterator = plannedIssList.listIterator();
        while (plannedIssIterator.hasNext()) {
            final PlannedIss plannedIss = plannedIssIterator.next();

            if (isLessThan(elapsedSinceGenesis, plannedIss.getTimeAfterGenesis())) {
                // This planned ISS is for some time in the future.
                break;
            }

            // If we reach this point then we are ready to trigger the ISS.
            // Once triggered, the same ISS is not triggered again.
            plannedIssIterator.remove();

            if (isGreaterThan(
                    elapsedSinceGenesis, plannedIss.getTimeAfterGenesis().plus(ISS_WINDOW))) {
                // Consensus time has skipped forward, possibly because this node was restarted.
                // We are outside the allowable window for the scheduled ISS, so do not trigger this one.
                continue;
            }

            // Randomly mutate the state. Each node in the same partition will get the same random mutation.
            // Nodes in different partitions will get a different random mutation with high probability.
            final int hashPartitionIndex = plannedIss.getPartitionOfNode(selfId);
            final long hashPartitionSeed = hash64(currentTimestamp.toEpochMilli(), hashPartitionIndex);
            final Random random = new Random(hashPartitionSeed);
            runningSum += random.nextInt();

            logger.info(
                    STARTUP.getMarker(),
                    "ISS intentionally provoked. This node ({}) is in partition {} and will agree "
                            + "with the hashes of all other nodes in partition {}. Nodes in other partitions "
                            + "are expected to have divergent hashes.",
                    selfId,
                    hashPartitionIndex,
                    hashPartitionIndex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(runningSum);
        out.writeInstant(genesisTimestamp);
        out.writeSerializableList(plannedIssList, false, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        runningSum = in.readLong();
        genesisTimestamp = in.readInstant();
        plannedIssList = in.readSerializableList(1024, false, PlannedIss::new);
    }

    /**
     * {@inheritDoc}
     */
    private void parseArguments(final String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected 1 or more arguments. See javadocs for details.");
        }

        plannedIssList = new ArrayList<>();
        for (int argIndex = 1; argIndex < args.length; argIndex++) {
            final String issEvent = args[argIndex];
            final Set<Long> uniqueNodeIds = new HashSet<>();

            final String[] timestampAndPartitionsStrings = issEvent.split(":");
            final int elapsedTime = Integer.parseInt(timestampAndPartitionsStrings[0]);
            final String partitionsArg = timestampAndPartitionsStrings[1];
            final String[] partitionStrings = partitionsArg.split("-");
            final List<List<Long>> partitions = new ArrayList<>(partitionStrings.length);
            for (final String partitionString : partitionStrings) {
                final List<Long> nodes = new ArrayList<>();
                final String[] nodeStrings = partitionString.split("\\+");
                for (final String nodeString : nodeStrings) {
                    final long nodeId = Long.parseLong(nodeString);
                    nodes.add(nodeId);
                    if (!uniqueNodeIds.add(nodeId)) {
                        logger.error(
                                EXCEPTION.getMarker(), "Node {} appears more than once in ISS description!", nodeId);
                    }
                }
                partitions.add(nodes);
            }

            plannedIssList.add(new PlannedIss(Duration.ofSeconds(elapsedTime), partitions));
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
}
