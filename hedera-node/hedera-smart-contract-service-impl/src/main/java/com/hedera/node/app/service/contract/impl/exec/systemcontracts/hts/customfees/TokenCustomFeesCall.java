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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.customfees;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.TokenTupleUtils.feesTupleFor;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.customfees.TokenCustomFeesTranslator.TOKEN_CUSTOM_FEES;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractNonRevertibleTokenViewCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class TokenCustomFeesCall extends AbstractNonRevertibleTokenViewCall {
    private final boolean isStaticCall;

    public TokenCustomFeesCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            final boolean isStaticCall,
            @Nullable final Token token) {
        super(enhancement, token);
        this.isStaticCall = isStaticCall;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected @NonNull FullResult resultOfViewingToken(@NonNull final Token token) {
        requireNonNull(token);
        // TODO - gas calculation

        return fullResultsFor(SUCCESS, 0L, token);
    }

    @Override
    protected @NonNull FullResult viewCallResultWith(
            @NonNull final ResponseCodeEnum status, final long gasRequirement) {
        return fullResultsFor(status, gasRequirement, Token.DEFAULT);
    }

    private @NonNull FullResult fullResultsFor(
            @NonNull final ResponseCodeEnum status, final long gasRequirement, @NonNull final Token token) {
        // @Future remove to revert #9071 after modularization is completed
        if (isStaticCall) {
            return successResult(
                    TOKEN_CUSTOM_FEES.getOutputs().encode(feesTupleFor(SUCCESS.protoOrdinal(), token)), gasRequirement);
        }
        return successResult(
                TOKEN_CUSTOM_FEES.getOutputs().encode(feesTupleFor(status.protoOrdinal(), token)), gasRequirement);
    }
}
