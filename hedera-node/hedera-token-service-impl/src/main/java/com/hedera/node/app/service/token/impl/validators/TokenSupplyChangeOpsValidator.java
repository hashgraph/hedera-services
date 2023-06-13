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

package com.hedera.node.app.service.token.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.inject.Inject;

/**
 * This class contains validations to be done in handle for Token Mint and
 * Token Burn operations in handle
 */
public class TokenSupplyChangeOpsValidator {
    private final ConfigProvider configProvider;

    @Inject
    public TokenSupplyChangeOpsValidator(@NonNull final ConfigProvider configProvider) {
        this.configProvider = requireNonNull(configProvider);
    }

    /**
     * Validate the transaction data for a token mint operation
     *
     * @param fungibleCount the number of fungible tokens to mint
     * @param metaDataList the list of metadata for the NFTs to mint
     * @throws HandleException if the transaction data is invalid
     */
    public void validateMint(final long fungibleCount, final List<Bytes> metaDataList) {
        final var numNfts = metaDataList.size();
        validateCommon(fungibleCount, numNfts, TokensConfig::nftsMaxBatchSizeMint);

        final var tokensConfig = configProvider.getConfiguration().getConfigData(TokensConfig.class);
        final var maxNftMetadataBytes = tokensConfig.nftsMaxMetadataBytes();
        if (fungibleCount <= 0 && numNfts > 0) {
            validateMetaData(metaDataList, maxNftMetadataBytes);
        }
    }

    /**
     * Validate the transaction data for a token mint operation
     *
     * @param fungibleCount the number of fungible tokens to burn
     * @param nftSerialNums the list of NFT serial numbers to burn
     * @throws HandleException if the transaction data is invalid
     */
    public void validateBurn(final long fungibleCount, final List<Long> nftSerialNums) {
        validateCommon(fungibleCount, nftSerialNums.size(), TokensConfig::nftsMaxBatchSizeBurn);
    }

    @SuppressWarnings("unused")
    // @future('6389'): This method will be used when token wipe is implemented
    public void validateWipe(final long fungibleCount, final List<Long> nftSerialNums) {
        validateCommon(fungibleCount, nftSerialNums.size(), TokensConfig::nftsMaxBatchSizeWipe);
    }

    /**
     * Perform common validation checks for token operations mint, wipe, and burn given the attributes of the transaction.
     *
     * @param fungibleCount The number of fungible common token to mint/wipe/burn.
     * @param nftCount the number of NFTs the operation will be performed on.
     * @param batchSizeGetter The function to get the corresponding batch size for the token operation.
     */
    private void validateCommon(
            final long fungibleCount, final int nftCount, @NonNull final ToIntFunction<TokensConfig> batchSizeGetter) {
        final var tokensConfig = configProvider.getConfiguration().getConfigData(TokensConfig.class);

        // Get needed configurations
        final var nftsAreEnabled = tokensConfig.nftsAreEnabled();
        final var maxNftBatchOpSize = batchSizeGetter.applyAsInt(tokensConfig);
        // validate nft count and fungible count are valid
        validateCounts(nftCount, fungibleCount, nftsAreEnabled, maxNftBatchOpSize);
    }

    /**
     * Validate the fungible amount and metadata size for a token mint or burn operation.
     * @param nftCount  The number of nfts to mint/burn.
     * @param fungibleCount The amount of fungible common token to mint/burn.
     * @param nftsAreEnabled Whether nfts are enabled (based on config).
     * @param maxBatchSize The max batch size for the nft operation (based on config).
     */
    private void validateCounts(
            final int nftCount, final long fungibleCount, final boolean nftsAreEnabled, final long maxBatchSize) {
        if (nftCount > 0) {
            validateTrue(nftsAreEnabled, NOT_SUPPORTED);
        }
        if (fungibleCount <= 0 && nftCount > 0) {
            validateTrue(nftCount <= maxBatchSize, BATCH_SIZE_LIMIT_EXCEEDED);
        }
    }

    /**
     * Validate the metadata size for token operations mint.
     * @param metaDataList The metadata list of the nfts to mint.
     * @param maxNftMetadataBytes The max metadata size for nft mint based on config.
     */
    private void validateMetaData(final List<Bytes> metaDataList, final int maxNftMetadataBytes) {
        for (var bytes : metaDataList) {
            validateTrue(bytes.toByteArray().length <= maxNftMetadataBytes, METADATA_TOO_LONG);
        }
    }
}
