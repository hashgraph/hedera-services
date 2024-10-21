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

import static com.hedera.node.app.tss.TssCryptographyManager.computeNodeShares;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssMessage;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.pairings.FakeGroupElement;
import com.hedera.node.app.tss.pairings.PairingPublicKey;
import com.hedera.node.app.tss.pairings.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

public class TssUtils {
    public static TssParticipantDirectory computeTssParticipantDirectory(
            @NonNull final Roster roster, final long maxSharesPerNode) {
        final var computedShares = computeNodeShares(roster.rosterEntries(), maxSharesPerNode);
        final var totalShares =
                computedShares.values().stream().mapToLong(Long::longValue).sum();
        final var threshold = (int) (totalShares + 2) / 2;

        final var builder = TssParticipantDirectory.createBuilder().withThreshold(threshold);
        for (var rosterEntry : roster.rosterEntries()) {
            final int numSharesPerThisNode =
                    computedShares.get(rosterEntry.nodeId()).intValue();
            // FUTURE: Use the actual public key from the node
            final var pairingPublicKey = new PairingPublicKey(
                    new FakeGroupElement(BigInteger.valueOf(10L)), SignatureSchema.create(new byte[] {1}));
            builder.withParticipant((int) rosterEntry.nodeId(), numSharesPerThisNode, pairingPublicKey);
        }
        // FUTURE: Use the actual signature schema
        return builder.build(SignatureSchema.create(new byte[] {1}));
    }

    /**
     * Validate TSS messages using the TSS library. If the message is valid, add it to the list of valid TSS messages.
     * @param tssMessages list of TSS messages to validate
     * @param tssParticipantDirectory the participant directory
     * @return list of valid TSS messages
     */
    public static List<TssMessageTransactionBody> validateTssMessages(
            @NonNull final List<TssMessageTransactionBody> tssMessages,
            @NonNull final TssParticipantDirectory tssParticipantDirectory,
            @NonNull final TssLibrary tssLibrary) {
        final var validTssMessages = new LinkedList<TssMessageTransactionBody>();
        for (final var op : tssMessages) {
            final var isValid = tssLibrary.verifyTssMessage(
                    tssParticipantDirectory, new TssMessage(op.tssMessage().toByteArray()));
            if (isValid) {
                validTssMessages.add(op);
            }
        }
        return validTssMessages;
    }

    public static List<TssMessage> getTssMessages(List<TssMessageTransactionBody> validTssOps) {
        return validTssOps.stream()
                .map(TssMessageTransactionBody::tssMessage)
                .map(k -> new TssMessage(k.toByteArray()))
                .toList();
    }
}
