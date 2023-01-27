/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrueOrRevert;

import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.FcAssessedCustomFee;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.Precompile;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * A wrapper around a normal redirect precompile. Delegates each call to the wrapped precompile,
 * except for the @code {run()} method, where it first checks that the targeted token exists in the
 * current ledgers, before delegating. This check is required, since
 * all the ERC precompiles @code {run()} methods execute with the assumption that the passed token
 * exists.
 */
public class RedirectPrecompile implements Precompile {

    private final Precompile wrappedPrecompile;
    private final TokenID tokenID;
    private final WorldLedgers worldLedgers;

    public RedirectPrecompile(
            final Precompile wrappedPrecompile,
            final WorldLedgers worldLedgers,
            final TokenID tokenID) {
        this.wrappedPrecompile = wrappedPrecompile;
        this.worldLedgers = worldLedgers;
        this.tokenID = tokenID;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return wrappedPrecompile.body(input, aliasResolver);
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        return wrappedPrecompile.getMinimumFeeInTinybars(consensusTime);
    }

    @Override
    public void run(final MessageFrame frame) {
        validateTrueOrRevert(
                worldLedgers.tokens().exists(tokenID), ResponseCodeEnum.INVALID_TOKEN_ID);
        wrappedPrecompile.run(frame);
    }

    @Override
    public long getGasRequirement(final long blockTimestamp) {
        return wrappedPrecompile.getGasRequirement(blockTimestamp);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        return wrappedPrecompile.getSuccessResultFor(childRecord);
    }

    @Override
    public Bytes getFailureResultFor(final ResponseCodeEnum status) {
        return wrappedPrecompile.getFailureResultFor(status);
    }

    @Override
    public void customizeTrackingLedgers(final WorldLedgers worldLedgers) {
        wrappedPrecompile.customizeTrackingLedgers(worldLedgers);
    }

    @Override
    public List<FcAssessedCustomFee> getCustomFees() {
        return wrappedPrecompile.getCustomFees();
    }

    @Override
    public void addImplicitCostsIn(final TxnAccessor accessor) {
        wrappedPrecompile.addImplicitCostsIn(accessor);
    }

    @Override
    public boolean shouldAddTraceabilityFieldsToRecord() {
        return wrappedPrecompile.shouldAddTraceabilityFieldsToRecord();
    }
}
