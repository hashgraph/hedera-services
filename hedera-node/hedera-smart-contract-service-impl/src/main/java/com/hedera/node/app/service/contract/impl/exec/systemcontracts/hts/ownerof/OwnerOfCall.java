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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractNftViewCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Implements the token redirect {@code ownerOf()} call of the HTS system contract.
 */
public class OwnerOfCall extends AbstractNftViewCall {
    private static final long TREASURY_OWNER_NUM = 0L;

    public OwnerOfCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull HederaWorldUpdater.Enhancement enhancement,
            @Nullable final Token token,
            final long serialNo) {
        super(gasCalculator, enhancement, token, serialNo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected @NonNull PricedResult resultOfViewingNft(@NonNull final Token token, @NonNull final Nft nft) {
        requireNonNull(token);
        requireNonNull(nft);
        final long ownerNum = getOwnerAccountNum(nft, token);
        final var gasRequirement = gasCalculator.viewGasRequirement();
        final var owner = nativeOperations().getAccount(ownerNum);
        if (owner == null) {
            return gasOnly(revertResult(INVALID_ACCOUNT_ID, gasRequirement), INVALID_ACCOUNT_ID, true);
        } else {
            final var output = OwnerOfTranslator.OWNER_OF.getOutputs().encodeElements(headlongAddressOf(owner));
            return gasOnly(successResult(output, gasRequirement), SUCCESS, true);
        }
    }

    @Override
    protected ResponseCodeEnum missingNftStatus() {
        return INVALID_TOKEN_NFT_SERIAL_NUMBER;
    }

    private long getOwnerAccountNum(@NonNull final Nft nft, @NonNull final Token token) {
        final var explicitId = nft.ownerIdOrElse(AccountID.DEFAULT);
        if (explicitId.accountNumOrElse(TREASURY_OWNER_NUM) == TREASURY_OWNER_NUM) {
            return token.treasuryAccountIdOrThrow().accountNumOrThrow();
        } else {
            return explicitId.accountNumOrThrow();
        }
    }
}
