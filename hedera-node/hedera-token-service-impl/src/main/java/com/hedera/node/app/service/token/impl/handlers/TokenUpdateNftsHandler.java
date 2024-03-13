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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_SERIAL_NUMBERS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_METADATA_KEY;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.token.TokenUpdateNftsTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides the state transition for an NFT collection update.
 */
@Singleton
public class TokenUpdateNftsHandler implements TransactionHandler {
    private final TokenAttributesValidator validator;

    @Inject
    public TokenUpdateNftsHandler(@NonNull final TokenAttributesValidator validator) {
        this.validator = validator;
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.tokenUpdateNftsOrThrow();
        validateTruePreCheck(op.hasToken(), INVALID_TOKEN_ID);
        validateTrue(!op.serialNumbers().isEmpty(), MISSING_SERIAL_NUMBERS);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenUpdateNftsOrThrow();
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var token = tokenStore.get(op.tokenOrElse(TokenID.DEFAULT));
        if (token == null) throw new PreCheckException(INVALID_TOKEN_ID);
        if (token.hasMetadataKey()) {
            context.requireKey(token.metadataKeyOrThrow());
        }
    }

    @Override
    public void handle(@NonNull HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txnBody = context.body();
        final var op = txnBody.tokenUpdateNftsOrThrow();
        final var tokenId = op.tokenOrThrow();

        // Ensure that the token has metadataKey
        final var tokenStore = context.readableStore(ReadableTokenStore.class);
        final var token = getIfUsable(tokenId, tokenStore);
        validateTrue(token.hasMetadataKey(), TOKEN_HAS_NO_METADATA_KEY);

        validateSemantics(context, op);
        final var nftStore = context.writableStore(WritableNftStore.class);

        // Wrap in Set to de-duplicate serial numbers
        final var nftSerialNums = new LinkedHashSet<>(op.serialNumbers());
        validateTrue(nftSerialNums.size() <= nftStore.sizeOfState(), INVALID_NFT_ID);
        updateNftMetadata(nftSerialNums, nftStore, tokenId, op);
    }

    private void updateNftMetadata(
            @NonNull final Set<Long> nftSerialNums,
            @NonNull final WritableNftStore nftStore,
            @NonNull final TokenID tokenNftId,
            @NonNull final TokenUpdateNftsTransactionBody op) {
        // Validate that the list of NFTs provided in txnBody exist in state
        // and update the metadata for each NFT
        for (final Long nftSerialNumber : nftSerialNums) {
            validateTrue(nftSerialNumber > 0, INVALID_TOKEN_NFT_SERIAL_NUMBER);
            final Nft nft = nftStore.get(tokenNftId, nftSerialNumber);
            validateTrue(nft != null, INVALID_NFT_ID);
            if (op.hasMetadata()) {
                // Update the metadata for the NFT(s)
                var updatedNft =
                        nft.copyBuilder().metadata(op.metadataOrThrow()).build();
                nftStore.put(updatedNft);
            }
        }
    }

    /** The total price should be N * $0.001, where N is the number of NFTs in the transaction body
     * * @param feeContext the {@link FeeContext} with all information needed for the calculation
     * @return the total Fee
     */
    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        final var op = feeContext.body();
        final var serials = op.tokenUpdateNftsOrThrow().serialNumbers();
        final var feeCalculator = feeContext.feeCalculator(SubType.TOKEN_NON_FUNGIBLE_UNIQUE);
        feeCalculator.resetUsage();
        return feeCalculator.addBytesPerTransaction(serials.size()).calculate();
    }

    private void validateSemantics(
            @NonNull final HandleContext context, @NonNull final TokenUpdateNftsTransactionBody op) {
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        // validate metadata
        if (op.hasMetadata()) {
            validator.validateTokenMetadata(op.metadataOrThrow(), tokensConfig);
        }
        validateTrue(op.serialNumbers().size() <= tokensConfig.nftsMaxBatchSizeUpdate(), BATCH_SIZE_LIMIT_EXCEEDED);
    }
}
