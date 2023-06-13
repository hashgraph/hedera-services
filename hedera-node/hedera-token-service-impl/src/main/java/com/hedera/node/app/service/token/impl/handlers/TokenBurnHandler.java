/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.util.TokenHandlerHelper;
import com.hedera.node.app.service.token.impl.validators.TokenSupplyChangeOpsValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
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

    @Inject
    public TokenBurnHandler(@NonNull final TokenSupplyChangeOpsValidator validator) {
        // Exists for injection
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
        final var fungibleCount = op.amount();
        final var serialNums = op.serialNumbers();

        validateTruePreCheck(op.hasToken(), INVALID_TOKEN_ID);

        // If a positive fungible amount is present, the NFT serial numbers must be empty
        validateFalsePreCheck(fungibleCount > 0 && !serialNums.isEmpty(), INVALID_TRANSACTION_BODY);

        validateFalsePreCheck(fungibleCount < 0, INVALID_TOKEN_BURN_AMOUNT);

        // Validate the NFT serial numbers
        if (fungibleCount < 1 && !serialNums.isEmpty()) {
            for (final var serialNumber : op.serialNumbers()) {
                validateTruePreCheck(serialNumber > 0, INVALID_NFT_ID);
            }
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var accountStore = context.writableStore(WritableAccountStore.class);
        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var tokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var nftStore = context.writableStore(WritableNftStore.class);
        final var txn = context.body();
        final var op = txn.tokenBurnOrThrow();
        final var tokenId = op.token();
        final var fungibleBurnCount = op.amount();
        // Wrapping the serial nums this way de-duplicates the serial nums:
        final var nftSerialNums = new ArrayList<>(new LinkedHashSet<>(op.serialNumbers()));
        final var validated = validateSemantics(tokenId, fungibleBurnCount, nftSerialNums, tokenStore, tokenRelStore);
        final var token = validated.token();

        if (token.tokenType() == TokenType.FUNGIBLE_COMMON) {
            changeSupply(
                    validated.token(),
                    validated.tokenTreasuryRel(),
                    -fungibleBurnCount,
                    INVALID_TOKEN_BURN_AMOUNT,
                    accountStore,
                    tokenStore,
                    tokenRelStore);
        } else {
            validateTrue(!nftSerialNums.isEmpty(), INVALID_TOKEN_BURN_METADATA);

            // Load and validate the nfts
            for (final Long nftSerial : nftSerialNums) {
                final var nft = nftStore.get(tokenId, nftSerial);
                validateTrue(nft != null, INVALID_NFT_ID);

                final var nftOwner = nft.ownerNumber();
                validateTrue(treasuryOwnsNft(nftOwner), TREASURY_MUST_OWN_BURNED_NFT);
            }

            // Update counts for accounts and token rels
            changeSupply(
                    token,
                    validated.tokenTreasuryRel(),
                    -nftSerialNums.size(),
                    FAIL_INVALID,
                    accountStore,
                    tokenStore,
                    tokenRelStore);

            // Update treasury's NFT count
            final var treasuryAcct = accountStore.get(asAccount(token.treasuryAccountNumber()));
            final var updatedTreasuryAcct = treasuryAcct
                    .copyBuilder()
                    .numberOwnedNfts(treasuryAcct.numberOwnedNfts() - nftSerialNums.size())
                    .build();
            accountStore.put(updatedTreasuryAcct);

            // Remove the nft objects
            nftSerialNums.forEach(serialNum -> nftStore.remove(tokenId, serialNum));
        }
    }

    private ValidationResult validateSemantics(
            @NonNull final TokenID tokenId,
            final long fungibleBurnCount,
            @NonNull final List<Long> nftSerialNums,
            final ReadableTokenStore tokenStore,
            final ReadableTokenRelationStore tokenRelStore) {
        validateTrue(fungibleBurnCount >= 0, INVALID_TOKEN_BURN_AMOUNT);

        validator.validateBurn(fungibleBurnCount, nftSerialNums);

        final var token = TokenHandlerHelper.getIfUsable(tokenId, tokenStore);
        validateTrue(token.supplyKey() != null, TOKEN_HAS_NO_SUPPLY_KEY);

        final var treasuryAcctId = asAccount(token.treasuryAccountNumber());
        final var treasuryRel = TokenHandlerHelper.getIfUsable(treasuryAcctId, tokenId, tokenRelStore);
        return new ValidationResult(token, treasuryRel);
    }

    private boolean treasuryOwnsNft(final long ownerNum) {
        return ownerNum == 0;
    }

    private record ValidationResult(@NonNull Token token, @NonNull TokenRelation tokenTreasuryRel) {}
}
