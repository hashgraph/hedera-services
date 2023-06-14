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
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.*;
import com.hedera.hapi.node.state.common.UniqueTokenId;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.*;
import com.hedera.node.app.service.token.impl.records.TokenMintRecordBuilder;
import com.hedera.node.app.service.token.impl.validators.TokenSupplyChangeOpsValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_MINT}.
 */
@Singleton
public class TokenMintHandler extends BaseTokenHandler implements TransactionHandler {
    private final TokenSupplyChangeOpsValidator validator;

    @Inject
    public TokenMintHandler(@NonNull final TokenSupplyChangeOpsValidator validator) {
        this.validator = requireNonNull(validator);
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
        validateFalsePreCheck(!op.metadata().isEmpty() && op.amount() > 0, INVALID_TRANSACTION_BODY);
        validateFalsePreCheck(op.amount() < 0, INVALID_TOKEN_MINT_AMOUNT);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        final var op = context.body().tokenMintOrThrow();
        final var tokenId = context.body().tokenMintOrThrow().tokenOrThrow();

        validateSemantics(context);

        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var tokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var accountStore = context.writableStore(WritableAccountStore.class);
        // validate token exists
        final var token = tokenStore.get(tokenId);
        validateTrue(token != null, INVALID_TOKEN_ID);
        // validate treasury relation exists
        final var treasuryRel = tokenRelStore.get(
                AccountID.newBuilder().accountNum(token.treasuryAccountNumber()).build(), tokenId);
        validateTrue(treasuryRel != null, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

        if (token.tokenType() == TokenType.FUNGIBLE_COMMON) {
            // we need to know if treasury mint while creation to ignore supply key exist or not.
            mintFungible(token, treasuryRel, op.amount(), false, accountStore, tokenStore, tokenRelStore);
        } else {
            // get the config needed for validation
            final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
            final var maxAllowedMints = tokensConfig.nftsMaxAllowedMints();
            final var nftStore = context.writableStore(WritableNftStore.class);
            // validate resources exist for minting nft
            final var meta = op.metadata();
            validateTrue(
                    nftStore.sizeOfState() + meta.size() < maxAllowedMints, MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED);
            // mint nft
            final var mintedSerials = mintNonFungible(
                    token,
                    treasuryRel,
                    meta,
                    context.consensusNow(),
                    accountStore,
                    tokenStore,
                    tokenRelStore,
                    nftStore);
            final var recordBuilder = context.recordBuilder(TokenMintRecordBuilder.class);

            recordBuilder.serialNumbers(mintedSerials);
            // TODO: Need to build transfer ownership from list to transfer NFT to treasury
            // This should probably be done in finalize method on token service which constructs the
            // transfer list looking at state
        }
    }

    /**
     * Validates the semantics of the token mint transaction that involve state or config.
     * @param context - the handle context of the token mint transaction
     */
    private void validateSemantics(final HandleContext context) {
        requireNonNull(context);
        final var op = context.body().tokenMintOrThrow();
        validator.validateMint(op.amount(), op.metadata());
    }

    /**
     * Minting nfts creates new instances of the given non-fungible token. Increments the
     * serial number of the given base unique token, and increments total owned nfts of the
     * non-fungible token.
     *
     * @param token
     * @param treasuryRel   - the treasury relation of the token
     * @param metadata      - the metadata of the nft to be minted
     * @param consensusTime - the consensus time of the transaction
     * @param accountStore  - the account store
     * @param tokenStore    - the token store
     * @param tokenRelStore - the token relation store
     * @param nftStore      - the nft store
     */
    private List<Long> mintNonFungible(
            final Token token,
            @NonNull final TokenRelation treasuryRel,
            @NonNull final List<Bytes> metadata,
            @NonNull final Instant consensusTime,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final WritableTokenRelationStore tokenRelStore,
            @NonNull final WritableNftStore nftStore) {
        final var metadataCount = metadata.size();
        validateFalse(metadata.isEmpty(), INVALID_TOKEN_MINT_METADATA);

        // validate token number from treasury relation
        final var tokenId = asToken(treasuryRel.tokenNumber());
        validateTrue(treasuryRel.tokenNumber() == token.tokenNumber(), FAIL_INVALID);

        // get the treasury account
        final var treasuryAccount = accountStore.get(asAccount(treasuryRel.accountNumber()));
        validateTrue(treasuryAccount != null, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

        // get the latest serial number minted for the token
        var currentSerialNumber = token.lastUsedSerialNumber();
        validateTrue((currentSerialNumber + metadataCount) <= MAX_NUM_ALLOWED, SERIAL_NUMBER_LIMIT_REACHED);

        // Change the supply on token
        changeSupply(token, treasuryRel, metadataCount, FAIL_INVALID, accountStore, tokenStore, tokenRelStore);

        final var mintedSerials = new ArrayList<Long>(metadata.size());

        // for each serial number minted increment serial numbers and create new unique token
        for (final var meta : metadata) {
            currentSerialNumber++;
            // The default sentinel account is used (0.0.0) to represent unique tokens owned by the treasury
            final var uniqueToken =
                    buildNewlyMintedNft(treasuryAccount, consensusTime, tokenId, meta, currentSerialNumber);
            nftStore.put(uniqueToken);
            // all minted serials should be added to the receipt
            mintedSerials.add(currentSerialNumber);
        }
        // Update last used serial number and number of owned nfts and put the updated token and treasury
        // into the store
        final var copyToken = token.copyBuilder();
        final var copyTreasury = treasuryAccount.copyBuilder();
        // Update Token and treasury
        copyToken.lastUsedSerialNumber(currentSerialNumber);
        copyTreasury.numberOwnedNfts(treasuryAccount.numberOwnedNfts() + metadataCount);

        tokenStore.put(copyToken.build());
        accountStore.put(copyTreasury.build());

        return mintedSerials;
    }

    /**
     * Builds a new unique token when minting a non-fungible token.
     * @param treasuryAccount - the treasury account
     * @param consensusTime - the consensus time of the transaction
     * @param tokenId - the token id
     * @param meta - the metadata of the nft
     * @param currentSerialNumber - the current serial number of the nft
     * @return - the newly built nft
     */
    @NonNull
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
                .ownerNumber(0L)
                .mintTime(Timestamp.newBuilder()
                        .seconds(consensusTime.getEpochSecond())
                        .nanos(consensusTime.getNano())
                        .build())
                .metadata(meta)
                .build();
    }
}
