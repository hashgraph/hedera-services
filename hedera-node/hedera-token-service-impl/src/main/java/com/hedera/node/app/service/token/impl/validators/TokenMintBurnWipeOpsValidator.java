/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.validators;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;

public class TokenMintBurnWipeOpsValidator {
    private ConfigProvider configProvider;
    @Inject
    public TokenMintBurnWipeOpsValidator(@NonNull final ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    public void pureChecks( final int nftCount,
                            final long fungibleCount,
                            final ResponseCodeEnum invalidTokenAmountError) throws PreCheckException {
        validateFalsePreCheck(nftCount > 0 && fungibleCount > 0, INVALID_TRANSACTION_BODY);
        validateFalsePreCheck(fungibleCount < 0 , invalidTokenAmountError);
    }
    /**
     * Validate the token operations mint/wipe/burn given the attributes of the transaction.
     *
     * @param nftCount The number of unique nfts to mint/wipe/burn.
     * @param fungibleCount The number of fungible common token to mint/wipe/burn.
     * @param invalidTokenAmountResponse Respective response code for invalid token amount in
     *     mint/wipe/burn operations.
     * @param metaDataList either metadata of the nfts being minted or serialNumber list of the
     *     burn/wipe operations.
     * @param batchSizeCheck validation method to check if the batch size of requested nfts is
     *     valid.
     * @param nftMetaDataCheck validation method to check if the metadata of minted nft is valid.
     * @return The validity of the token operation.
     */
    public ResponseCodeEnum validateTokenOpsWith(
            final int nftCount,
            final long fungibleCount,
            final ResponseCodeEnum invalidTokenAmountResponse,
            final List<ByteString> metaDataList,
            final IntFunction<ResponseCodeEnum> batchSizeCheck,
            Function<byte[], ResponseCodeEnum> nftMetaDataCheck) {
        final var tokensConfig = configProvider.getConfiguration().getConfigData(TokensConfig.class);
        final var nftsAreEnabled = tokensConfig.nftsAreEnabled();

        validateCounts(nftCount, fungibleCount, nftsAreEnabled, invalidTokenAmountResponse, batchSizeCheck);

        if (fungibleCount <= 0 && nftCount > 0) {
            return validateMetaData(metaDataList, nftMetaDataCheck);
        }
        return OK;
    }

    private ResponseCodeEnum validateCounts(
            final int nftCount,
            final long fungibleCount,
            final boolean areNftEnabled,
            final ResponseCodeEnum invalidTokenAmount,
            final IntFunction<ResponseCodeEnum> batchSizeCheck) {
        if (nftCount > 0 && !areNftEnabled) {
            return NOT_SUPPORTED;
        }

        boolean bothPresent = (fungibleCount > 0 && nftCount > 0);
        if (bothPresent) {
            return INVALID_TRANSACTION_BODY;
        }

        if (fungibleCount < 0) {
            return invalidTokenAmount;
        }

        if (fungibleCount <= 0 && nftCount > 0) {
            return batchSizeCheck.apply(nftCount);
        }
        return OK;
    }

    private ResponseCodeEnum validateMetaData(
            final List<ByteString> metaDataList, Function<byte[], ResponseCodeEnum> nftMetaDataCheck) {
        for (var bytes : metaDataList) {
            var validity = nftMetaDataCheck.apply(bytes.toByteArray());
            if (validity != OK) {
                return validity;
            }
        }
        return OK;
    }

}
