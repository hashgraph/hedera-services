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

import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;

/**
 * This class contains validations to be done in handle for Token Mint and Token Burn operations
 */
public class TokenSupplyChangeOpsValidator {
    private final ConfigProvider configProvider;

    @Inject
    public TokenSupplyChangeOpsValidator(@NonNull final ConfigProvider configProvider) {
        this.configProvider = requireNonNull(configProvider);
    }

    /**
     * Validate the token operations mint/wipe/burn given the attributes of the transaction.
     *
     * @param fungibleCount The number of fungible common token to mint/wipe/burn.
     * @param metaDataList either metadata of the nfts being minted or serialNumber list of the
     *     burn/wipe operations.
     */
    public void validate(final long fungibleCount, final List<Bytes> metaDataList) {
        final var nftCount = metaDataList.size();
        final var tokensConfig = configProvider.getConfiguration().getConfigData(TokensConfig.class);

        // Get needed configurations
        final var maxNftMintBatchSize = tokensConfig.nftsMaxBatchSizeMint();
        final var nftsAreEnabled = tokensConfig.nftsAreEnabled();
        final var maxNftMetadataBytes = tokensConfig.nftsMaxMetadataBytes();
        // validate nft count and fungible count are valid
        validateCounts(nftCount, fungibleCount, nftsAreEnabled, maxNftMintBatchSize);
        // validate metadata length if only nft count is set
        if (fungibleCount <= 0 && nftCount > 0) {
            validateMetaData(metaDataList, maxNftMetadataBytes);
        }
    }

    /**
     * Validate the fungible amount and metadata size for token operations mint/burn.
     * @param nftCount  The number of nfts to mint/burn.
     * @param fungibleCount The amount of fungible common token to mint/burn.
     * @param nftsAreEnabled Whether nfts are enabled based on config.
     * @param maxBatchSize The max batch size for nft mint based on config.
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
