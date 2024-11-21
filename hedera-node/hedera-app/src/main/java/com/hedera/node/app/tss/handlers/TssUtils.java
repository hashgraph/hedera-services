/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss.handlers;

import static java.util.Objects.requireNonNull;

import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.cryptography.bls.GroupAssignment;
import com.hedera.cryptography.bls.SignatureSchema;
import com.hedera.cryptography.pairings.api.Curve;
import com.hedera.cryptography.tss.api.TssMessage;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.node.app.tss.api.FakeGroupElement;
import com.hedera.node.app.tss.api.TssLibrary;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TssUtils {
    public static final SignatureSchema SIGNATURE_SCHEMA =
            SignatureSchema.create(Curve.ALT_BN128, GroupAssignment.SHORT_SIGNATURES);
    /**
     * Compute the TSS participant directory from the roster.
     *
     * @param roster the roster
     * @param maxSharesPerNode the maximum number of shares per node
     * @param selfNodeId the node ID of the current node
     * @return the TSS participant directory
     */
    public static TssParticipantDirectory computeParticipantDirectory(
            @NonNull final Roster roster, final long maxSharesPerNode, final int selfNodeId) {
        final var computedShares = computeNodeShares(roster.rosterEntries(), maxSharesPerNode);
        final var totalShares =
                computedShares.values().stream().mapToLong(Long::longValue).sum();
        final var threshold = getThresholdForTssMessages(totalShares);

        final var builder = TssParticipantDirectory.createBuilder().withThreshold(threshold);
        for (var rosterEntry : roster.rosterEntries()) {
            final int numSharesPerThisNode =
                    computedShares.get(rosterEntry.nodeId()).intValue();
            // FUTURE: Use the actual public key from the node
            final var pairingPublicKey =
                    new BlsPublicKey(new FakeGroupElement(BigInteger.valueOf(10L)), SIGNATURE_SCHEMA);
            builder.withParticipant((int) rosterEntry.nodeId(), numSharesPerThisNode, pairingPublicKey);
        }
        // FUTURE: Use the actual signature schema
        return builder.build();
    }

    /**
     * Compute the threshold of consensus weight needed for submitting a {@link com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody}
     * If more than 1/2 the consensus weight has been received, then the threshold is met
     *
     * @param totalShares the total number of shares
     * @return the threshold for TSS messages
     */
    public static int getThresholdForTssMessages(final long totalShares) {
        return (int) (totalShares + 2) / 2;
    }

    /**
     * Validate TSS messages using the TSS library. If the message is valid, add it to the list of valid TSS messages.
     *
     * @param tssMessages             list of TSS messages to validate
     * @param tssParticipantDirectory the participant directory
     * @return list of valid TSS messages
     */
    public static List<TssMessageTransactionBody> validateTssMessages(
            @NonNull final List<TssMessageTransactionBody> tssMessages,
            @NonNull final TssParticipantDirectory tssParticipantDirectory,
            @NonNull final TssLibrary tssLibrary) {
        final var validTssMessages = new LinkedList<TssMessageTransactionBody>();
        for (final var op : tssMessages) {
            final var isValid = tssLibrary.verifyTssMessage(tssParticipantDirectory, op.tssMessage());
            if (isValid) {
                validTssMessages.add(op);
            }
        }
        return validTssMessages;
    }

    /**
     * Get the TSS messages from the list of valid TSS Message bodies.
     *
     * @param validTssOps             list of valid TSS message bodies
     * @param tssParticipantDirectory
     * @param tssLibrary
     * @return list of TSS messages
     */
    public static List<TssMessage> getTssMessages(
            List<TssMessageTransactionBody> validTssOps,
            final TssParticipantDirectory tssParticipantDirectory,
            final TssLibrary tssLibrary) {
        return validTssOps.stream()
                .map(TssMessageTransactionBody::tssMessage)
                .map(k -> tssLibrary.getTssMessageFromBytes(k, tssParticipantDirectory))
                .toList();
    }

    /**
     * Compute the number of shares each node should have based on the weight of the node.
     *
     * @param rosterEntries    the list of roster entries
     * @param maxSharesPerNode the maximum number of shares per node
     * @return a map of node ID to the number of shares
     */
    public static Map<Long, Long> computeNodeShares(
            @NonNull final List<RosterEntry> rosterEntries, final long maxSharesPerNode) {
        final var weights = new LinkedHashMap<Long, Long>();
        rosterEntries.forEach(entry -> weights.put(entry.nodeId(), entry.weight()));
        return computeSharesFromWeights(weights, maxSharesPerNode);
    }

    /**
     * Compute the number of shares each node should have based on the weight of the node.
     * @param weights the map of node ID to weight
     * @param maxShares the maximum number of shares
     * @return a map of node ID to the number of shares
     */
    public static Map<Long, Long> computeSharesFromWeights(
            @NonNull final Map<Long, Long> weights, final long maxShares) {
        requireNonNull(weights);
        final var maxWeight =
                weights.values().stream().mapToLong(Long::longValue).max().orElse(0);
        final var shares = new LinkedHashMap<Long, Long>();
        weights.forEach((nodeId, weight) -> {
            final var numShares = ((maxShares * weight + maxWeight - 1) / maxWeight);
            shares.put(nodeId, numShares);
        });
        return shares;
    }
}
