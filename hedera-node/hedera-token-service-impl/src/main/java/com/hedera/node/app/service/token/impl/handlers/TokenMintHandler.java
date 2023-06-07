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
import static com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils.MAX_NUM_ALLOWED;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.*;
import com.hedera.hapi.node.state.common.UniqueTokenId;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.validators.TokenMintBurnWipeOpsValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_MINT}.
 */
@Singleton
public class TokenMintHandler extends BaseTokenHandler implements TransactionHandler {
    private final TokenMintBurnWipeOpsValidator validator;

    @Inject
    public TokenMintHandler(final TokenMintBurnWipeOpsValidator validator) {
        this.validator = validator;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var txn = context.body();
        pureChecks(txn);
        final var op = txn.tokenMintOrThrow();
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMeta = tokenStore.getTokenMeta(op.tokenOrElse(TokenID.DEFAULT));
        if (tokenMeta == null) throw new PreCheckException(INVALID_TOKEN_ID);
        if (tokenMeta.hasSupplyKey()) {
            context.requireKey(tokenMeta.supplyKey());
        }
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.tokenMintOrThrow();
        validateTruePreCheck(op.hasToken(), INVALID_TOKEN_ID);
        validator.pureChecks(op.metadata().size(), op.amount(), INVALID_TOKEN_MINT_AMOUNT);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        final var op = context.body().tokenMintOrThrow();

        validateSemantics(context);
        final var tokenId = context.body().tokenMintOrThrow().tokenOrThrow();

        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var tokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var accountStore = context.writableStore(WritableAccountStore.class);

        final var token = tokenStore.get(tokenId);
        validateTrue(token != null, INVALID_TOKEN_ID);

        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        final var maxAllowedMints = tokensConfig.nftsMaxAllowedMints();

        final var treasuryRel = tokenRelStore.get(
                AccountID.newBuilder().accountNum(token.treasuryAccountNumber()).build(), tokenId);
        validateTrue(treasuryRel != null, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

        if (token.tokenType() == TokenType.FUNGIBLE_COMMON) {
            // we need to know if treasury mint while creation to ignore supply key exist or not.
            mintFungible(token, treasuryRel, op.amount(), false, accountStore, tokenStore, tokenRelStore);
        } else {
            final var nftStore = context.writableStore(WritableNftStore.class);
            // If
            validateTrue(nftStore.sizeOfState() + 1 < maxAllowedMints, MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED);

            mintNonFungible(
                    treasuryRel,
                    op.metadata(),
                    context.consensusNow(),
                    accountStore,
                    tokenStore,
                    tokenRelStore,
                    nftStore);
        }
    }

    private void validateSemantics(final HandleContext context) {
        requireNonNull(context);
        final var op = context.body().tokenMintOrThrow();
    }

    /**
     * Minting unique tokens creates new instances of the given base unique token. Increments the
     * serial number of the given base unique token, and assigns each of the numbers to each new
     * unique token instance.
     *
     * @param treasuryRel   - the relationship between the treasury account and the token
     * @param metadata      - a list of user-defined metadata, related to the nft instances.
     * @param consensusTime - the consensus time of the token mint transaction
     */
    private void mintNonFungible(
            @NonNull final TokenRelation treasuryRel,
            @NonNull final List<Bytes> metadata,
            @NonNull final Instant consensusTime,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final WritableTokenRelationStore tokenRelStore,
            @NonNull final WritableNftStore nftStore) {
        final var metadataCount = metadata.size();
        validateFalse(metadata.isEmpty(), INVALID_TOKEN_MINT_METADATA);

        final var tokenId = asToken(treasuryRel.tokenNumber());
        final var token = tokenStore.get(tokenId);
        validateTrue(token != null, INVALID_TOKEN_ID);

        final var treasuryAccount = accountStore.get(asAccount(treasuryRel.accountNumber()));
        validateTrue(treasuryAccount != null, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

        var currentSerialNumber = token.lastUsedSerialNumber();
        validateTrue((currentSerialNumber + metadataCount) <= MAX_NUM_ALLOWED, SERIAL_NUMBER_LIMIT_REACHED);

        changeSupply(token, treasuryRel, metadataCount, FAIL_INVALID, accountStore, tokenStore, tokenRelStore);

        for (final var meta : metadata) {
            currentSerialNumber++;
            // The default sentinel account is used (0.0.0) to represent unique tokens owned by the treasury
            final var uniqueToken =
                    buildNewlyMintedNft(treasuryAccount, consensusTime, tokenId, meta, currentSerialNumber);
            nftStore.put(uniqueToken);
            addMintedSerialsToRecord();
        }

        final var copyToken = token.copyBuilder();
        final var copyTreasury = treasuryAccount.copyBuilder();

        copyToken.lastUsedSerialNumber(currentSerialNumber);
        copyTreasury.numberOwnedNfts(treasuryAccount.numberOwnedNfts() + metadataCount);

        tokenStore.put(copyToken.build());
        accountStore.put(copyTreasury.build());
    }

    private void addMintedSerialsToRecord() {}

    private Nft buildNewlyMintedNft(
            @NonNull final Account treasuryAccount,
            @NonNull final Instant consensusTime,
            @NonNull final TokenID tokenId,
            @NonNull final Bytes meta,
            final long currentSerialNumber) {
        return Nft.newBuilder()
                .id(UniqueTokenId.newBuilder()
                        .tokenTypeNumber(tokenId.tokenNum())
                        .serialNumber(currentSerialNumber)
                        .build())
                .ownerNumber(AccountID.DEFAULT.accountNum())
                .mintTime(Timestamp.newBuilder()
                        .seconds(consensusTime.getEpochSecond())
                        .nanos(consensusTime.getNano())
                        .build())
                .metadata(meta)
                .ownerNextNftId(UniqueTokenId.newBuilder()
                        .serialNumber(treasuryAccount.headNftId())
                        .tokenTypeNumber(treasuryAccount.headNftSerialNumber()))
                .ownerPreviousNftId((UniqueTokenId) null)
                .build();
    }
}
