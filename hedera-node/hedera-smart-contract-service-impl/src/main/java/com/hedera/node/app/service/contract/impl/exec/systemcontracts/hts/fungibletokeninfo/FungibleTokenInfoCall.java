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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.fungibletokeninfo;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.TokenTupleUtils.fungibleTokenInfoTupleFor;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.fungibletokeninfo.FungibleTokenInfoTranslator.FUNGIBLE_TOKEN_INFO;
import static com.swirlds.common.utility.CommonUtils.hex;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractNonRevertibleTokenViewCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.config.data.LedgerConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class FungibleTokenInfoCall extends AbstractNonRevertibleTokenViewCall {
    private final Configuration configuration;
    private final boolean isStaticCall;

    public FungibleTokenInfoCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            final boolean isStaticCall,
            @Nullable final Token token,
            @NonNull final Configuration configuration) {
        super(enhancement, token);
        this.configuration = requireNonNull(configuration);
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
        requireNonNull(status);
        requireNonNull(token);

        final var ledgerConfig = configuration.getConfigData(LedgerConfig.class);
        final var ledgerId = hex(ledgerConfig.id().toByteArray());
        // @Future remove to revert #9073 after modularization is completed
        if (isStaticCall && status != SUCCESS) {
            return revertResult(status, 0);
        }
        return successResult(
                FUNGIBLE_TOKEN_INFO
                        .getOutputs()
                        .encodeElements(status.protoOrdinal(), fungibleTokenInfoTupleFor(token, ledgerId)),
                gasRequirement);
    }
}
