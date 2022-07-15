/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.contracts.execution.HederaMessageCallProcessor.INVALID_TRANSFER;
import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.SUCCESS_RESULT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FEE_SUBMITTED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Defines the lifecycle for a <b>single execution</b> of a precompiled contract executed via {@link
 * HTSPrecompiledContract}. Implementations may change the world state; unless reverted, their
 * execution will be externalized in the record stream via a "synthetic" {@link TransactionBody} and
 * "child" {@link TransactionRecord}.
 *
 * <p>Just as with {@link HTSPrecompiledContract} itself, implementations will generally be
 * <b>stateful</b> and <b>not thread-safe</b>; their correctness will depend on the integrity of the
 * internal state they keep in-between calls to the various lifecycle methods.
 *
 * <p>The precompile lifecycle methods are used by the {@link HTSPrecompiledContract} executor as
 * below.
 *
 * <ol>
 *   <li>First, the executor calls {@link Precompile#body(Bytes, UnaryOperator)} to get a synthetic
 *       transaction body that captures the input to the precompile.
 *   <li>Second, the executor computes the effective gas for the precompile by consulting {@link
 *       Precompile#getMinimumFeeInTinybars(Timestamp)} for the minimum fee that should be charged;
 *       and {@link Precompile#addImplicitCostsIn(TxnAccessor)} to incorporate any hidden costs
 *       implied by the {@link TxnAccessor} created from the synthetic transaction.
 *   <li>Third, the executor invokes {@link Precompile#run(MessageFrame)}, which is expected to have
 *       side-effects on the {@link com.hedera.services.store.contracts.WorldLedgers} in the
 *       provided message frame.
 *   <li>Finally, if {@code run()} completes without an exception, the executor calls {@link
 *       Precompile#getSuccessResultFor(ExpirableTxnRecord.Builder)} to get the {@link Bytes} to
 *       return to the EVM; otherwise, {@link Precompile#getFailureResultFor(ResponseCodeEnum)} is
 *       called for the same purpose. The executor customizes the final record using some additional
 *       bits of information from {@link Precompile#getCustomFees()} and {@link
 *       Precompile#shouldAddTraceabilityFieldsToRecord()}.
 * </ol>
 */
public interface Precompile {
    // Construct the synthetic transaction
    TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver);

    // Customize fee charging
    long getMinimumFeeInTinybars(Timestamp consensusTime);

    default void addImplicitCostsIn(final TxnAccessor accessor) {
        // Most transaction types can compute their full Hedera fee from just an initial transaction
        // body; but
        // for a token transfer, we may need to recompute to charge for the extra work implied by
        // custom fees
    }

    // Change the world state through the given frame
    void run(MessageFrame frame);

    long getGasRequirement(long blockTimestamp);

    default void customizeTrackingLedgers(final WorldLedgers worldLedgers) {
        // No-op
    }

    default void handleSentHbars(MessageFrame frame) {
        if (!Objects.equals(Wei.ZERO, frame.getValue())) {
            frame.setRevertReason(INVALID_TRANSFER);
            frame.setState(REVERT);

            throw new InvalidTransactionException(INVALID_FEE_SUBMITTED);
        }
    }

    default List<FcAssessedCustomFee> getCustomFees() {
        return Collections.emptyList();
    }

    default Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        return SUCCESS_RESULT;
    }

    default Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return EncodingFacade.resultFrom(status);
    }

    default boolean shouldAddTraceabilityFieldsToRecord() {
        return true;
    }
}
