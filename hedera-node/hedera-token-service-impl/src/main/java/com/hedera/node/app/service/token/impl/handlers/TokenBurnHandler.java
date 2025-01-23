/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_BURN_METADATA;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
import static com.hedera.node.app.hapi.fees.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.node.app.hapi.fees.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hedera.node.app.service.token.impl.validators.TokenSupplyChangeOpsValidator.verifyTokenInstanceAmounts;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.util.TokenHandlerHelper;
import com.hedera.node.app.service.token.impl.validators.TokenSupplyChangeOpsValidator;
import com.hedera.node.app.service.token.records.TokenBurnStreamBuilder;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_BURN}.
 */
@Singleton
public final class TokenBurnHandler extends BaseTokenHandler implements TransactionHandler {
    @NonNull
    private final TokenSupplyChangeOpsValidator validator;

    /**
     * Default constructor for injection.
     * @param validator the {@link TokenSupplyChangeOpsValidator} to use
     */
    @Inject
    public TokenBurnHandler(@NonNull final TokenSupplyChangeOpsValidator validator) {
        this.validator = requireNonNull(validator);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenBurnOrThrow();
        final var tokenId = op.tokenOrElse(TokenID.DEFAULT);
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMetadata = tokenStore.getTokenMeta(tokenId);
        if (tokenMetadata == null) throw new PreCheckException(INVALID_TOKEN_ID);
        // we will fail in handle() if token has no supply key
        if (tokenMetadata.hasSupplyKey()) {
            context.requireKey(tokenMetadata.supplyKey());
        }
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.tokenBurnOrThrow();
        verifyTokenInstanceAmounts(op.amount(), op.serialNumbers(), op.hasToken(), INVALID_TOKEN_BURN_AMOUNT);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var storeFactory = context.storeFactory();
        final var accountStore = storeFactory.writableStore(WritableAccountStore.class);
        final var tokenStore = storeFactory.writableStore(WritableTokenStore.class);
        final var tokenRelStore = storeFactory.writableStore(WritableTokenRelationStore.class);
        final var nftStore = storeFactory.writableStore(WritableNftStore.class);
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);

        final var txn = context.body();
        final var op = txn.tokenBurnOrThrow();
        final var tokenId = op.token();
        final var fungibleBurnCount = op.amount();
        // Wrapping the serial nums this way de-duplicates the serial nums:
        final var nftSerialNums = new ArrayList<>(new LinkedHashSet<>(op.serialNumbers()));
        final var validated =
                validateSemantics(tokenId, fungibleBurnCount, nftSerialNums, tokenStore, tokenRelStore, tokensConfig);
        final var treasuryRel = validated.tokenTreasuryRel();
        final var token = validated.token();

        if (token.hasKycKey()) {
            validateTrue(treasuryRel.kycGranted(), ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN);
        }

        TokenBurnStreamBuilder record = context.savepointStack().getBaseBuilder(TokenBurnStreamBuilder.class);
        if (token.tokenType() == TokenType.FUNGIBLE_COMMON) {
            validateTrue(fungibleBurnCount >= 0, INVALID_TOKEN_BURN_AMOUNT);
            final var newTotalSupply = changeSupply(
                    validated.token(),
                    treasuryRel,
                    -fungibleBurnCount,
                    INVALID_TOKEN_BURN_AMOUNT,
                    accountStore,
                    tokenStore,
                    tokenRelStore,
                    context.expiryValidator());
            record.newTotalSupply(newTotalSupply);
        } else {
            validateTrue(!nftSerialNums.isEmpty(), INVALID_TOKEN_BURN_METADATA);

            // Load and validate the nfts
            for (final Long nftSerial : nftSerialNums) {
                final var nft = nftStore.get(tokenId, nftSerial);
                validateTrue(nft != null, INVALID_NFT_ID);

                final var nftOwner = nft.ownerId();
                validateTrue(treasuryOwnsNft(nftOwner), TREASURY_MUST_OWN_BURNED_NFT);
            }

            // Update counts for accounts and token rels
            final var newTotalSupply = changeSupply(
                    token,
                    treasuryRel,
                    -nftSerialNums.size(),
                    FAIL_INVALID,
                    accountStore,
                    tokenStore,
                    tokenRelStore,
                    context.expiryValidator());

            // Update treasury's NFT count
            final var treasuryAcct = accountStore.get(token.treasuryAccountIdOrThrow());
            final var updatedTreasuryAcct = treasuryAcct
                    .copyBuilder()
                    .numberOwnedNfts(treasuryAcct.numberOwnedNfts() - nftSerialNums.size())
                    .build();
            accountStore.put(updatedTreasuryAcct);

            // Remove the nft objects
            nftSerialNums.forEach(serialNum -> nftStore.remove(tokenId, serialNum));
            record.newTotalSupply(newTotalSupply);
        }
        record.tokenType(token.tokenType());
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        final var op = feeContext.body();
        final var meta = TOKEN_OPS_USAGE_UTILS.tokenBurnUsageFrom(CommonPbjConverters.fromPbj(op));
        return feeContext
                .feeCalculatorFactory()
                .feeCalculator(
                        meta.getSerialNumsCount() > 0
                                ? SubType.TOKEN_NON_FUNGIBLE_UNIQUE
                                : SubType.TOKEN_FUNGIBLE_COMMON)
                .addBytesPerTransaction(meta.getBpt())
                .addNetworkRamByteSeconds(meta.getTransferRecordDb() * USAGE_PROPERTIES.legacyReceiptStorageSecs())
                .calculate();
    }

    private ValidationResult validateSemantics(
            @NonNull final TokenID tokenId,
            final long fungibleBurnCount,
            @NonNull final List<Long> nftSerialNums,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final ReadableTokenRelationStore tokenRelStore,
            @NonNull final TokensConfig tokensConfig) {
        validateTrue(fungibleBurnCount >= 0, INVALID_TOKEN_BURN_AMOUNT);

        validator.validateBurn(fungibleBurnCount, nftSerialNums, tokensConfig);

        final var token = TokenHandlerHelper.getIfUsable(tokenId, tokenStore);
        validateTrue(token.supplyKey() != null, TOKEN_HAS_NO_SUPPLY_KEY);

        final var treasuryAcctId = token.treasuryAccountId();
        final var treasuryRel = TokenHandlerHelper.getIfUsable(treasuryAcctId, tokenId, tokenRelStore);
        return new ValidationResult(token, treasuryRel);
    }

    private boolean treasuryOwnsNft(final AccountID ownerID) {
        return ownerID == null;
    }

    private record ValidationResult(@NonNull Token token, @NonNull TokenRelation tokenTreasuryRel) {}
}
