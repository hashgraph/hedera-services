/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.decimals;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;

import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractRevertibleTokenViewCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Implements the token redirect {@code decimals()} call of the HTS system contract.
 */
public class DecimalsCall extends AbstractRevertibleTokenViewCall {
    private static final int MAX_REPORTABLE_DECIMALS = 0xFF;

    public DecimalsCall(
            @NonNull HederaWorldUpdater.Enhancement enhancement,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @Nullable final Token token) {
        super(gasCalculator, enhancement, token);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected @NonNull FullResult resultOfViewingToken(@NonNull final Token token) {
        if (token.tokenType() != TokenType.FUNGIBLE_COMMON) {
            return revertResult(INVALID_TOKEN_ID, gasCalculator.viewGasRequirement());
        } else {
            final var decimals = Math.min(MAX_REPORTABLE_DECIMALS, token.decimals());
            return successResult(
                    DecimalsTranslator.DECIMALS.getOutputs().encodeElements(decimals),
                    gasCalculator.viewGasRequirement());
        }
    }
}
