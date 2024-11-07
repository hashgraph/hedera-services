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

package com.hedera.services.bdd.spec.utilops;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.services.bdd.junit.hedera.NodeSelector.byNodeId;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doingContextual;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.ByteString;
import com.hedera.hapi.services.auxiliary.tss.legacy.TssMessageTransactionBody;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.api.TssMessage;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssLibrary;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.utilops.tss.RekeyScenarioOp;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

/**
 * Factory for spec operations that support exercising TSS, especially in embedded mode.
 */
public class TssVerbs {
    private TssVerbs() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns an operation that instructs the embedded {@link TssBaseService} to ignoring TSS signature requests.
     *
     * @return the operation that will ignore TSS signature requests
     */
    public static SpecOperation startIgnoringTssSignatureRequests() {
        return doingContextual(
                spec -> spec.repeatableEmbeddedHederaOrThrow().tssBaseService().startIgnoringRequests());
    }

    /**
     * Returns an operation that instructs the embedded {@link TssBaseService} to stop ignoring TSS signature requests.
     *
     * @return the operation that will stop ignoring TSS signature requests
     */
    public static SpecOperation stopIgnoringTssSignatureRequests() {
        return doingContextual(
                spec -> spec.repeatableEmbeddedHederaOrThrow().tssBaseService().stopIgnoringRequests());
    }

    /**
     * Returns an operation that simulates a re-keying scenario in the context of a repeatable embedded test.
     * @param dabEdits the edits to make before creating the candidate roster
     * @param nodeStakes the node stakes to have in place at the stake period boundary
     * @param tssMessageSims the TSS message simulations to apply
     * @return the operation that will simulate the re-keying scenario
     */
    public static RekeyScenarioOp rekeyingScenario(
            @NonNull final RekeyScenarioOp.DabEdits dabEdits,
            @NonNull final LongUnaryOperator nodeStakes,
            @NonNull final LongFunction<RekeyScenarioOp.TssMessageSim> tssMessageSims) {
        return new RekeyScenarioOp(dabEdits, nodeStakes, tssMessageSims);
    }

    /**
     * Returns an operation that simulates the synchronous submission and execution of a given TSS message
     * from the given node id in the context of a repeatable embedded test.
     *
     * @param nodeId the node id
     * @param sourceRosterHash the source roster hash
     * @param targetRosterHash the target roster hash
     * @param tssMessage the TSS message
     * @return the operation that will submit the TSS message
     */
    public static SpecOperation submitTssMessage(
            final long nodeId,
            @NonNull final Bytes sourceRosterHash,
            @NonNull final Bytes targetRosterHash,
            @NonNull final TssMessage tssMessage) {
        requireNonNull(tssMessage);
        return doingContextual(spec -> submitRepeatable(
                nodeId,
                spec,
                b -> b.setTssMessage(TssMessageTransactionBody.newBuilder()
                        .setSourceRosterHash(fromPbj(sourceRosterHash))
                        .setTargetRosterHash(fromPbj(targetRosterHash))
                        .setShareIndex(FakeTssLibrary.getShareIndex(tssMessage))
                        .setTssMessage(ByteString.copyFrom(tssMessage.bytes())))));
    }

    private static void submitRepeatable(
            final long nodeId,
            @NonNull final HapiSpec spec,
            @NonNull final Consumer<TransactionBody.Builder> bodySpec) {
        final var nodeAccountId = fromPbj(
                spec.targetNetworkOrThrow().getRequiredNode(byNodeId(nodeId)).getAccountId());
        final var builder = builderWith(
                spec.consensusTime(),
                nodeAccountId,
                spec.startupProperties().getDurationFromSecs("hedera.transaction.maxValidDuration"));
        bodySpec.accept(builder);
        spec.repeatableEmbeddedHederaOrThrow()
                .submit(
                        Transaction.newBuilder()
                                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                                        .setBodyBytes(builder.build().toByteString())
                                        .setSigMap(SignatureMap.getDefaultInstance())
                                        .build()
                                        .toByteString())
                                .build(),
                        nodeAccountId);
    }

    private static TransactionBody.Builder builderWith(
            @NonNull final Instant validStartTime,
            @NonNull final AccountID selfAccountId,
            @NonNull final Duration validDuration) {
        return TransactionBody.newBuilder()
                .setNodeAccountID(selfAccountId)
                .setTransactionValidDuration(validDuration)
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder()
                                .setSeconds(validStartTime.getEpochSecond())
                                .setNanos(validStartTime.getNano()))
                        .setAccountID(selfAccountId));
    }
}
