/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token;

import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static com.hedera.services.txns.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;

import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MintLogic {
    private final UsageLimits usageLimits;
    private final OptionValidator validator;
    private final TypedTokenStore tokenStore;
    private final AccountStore accountStore;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public MintLogic(
            final UsageLimits usageLimits,
            final OptionValidator validator,
            final TypedTokenStore tokenStore,
            final AccountStore accountStore,
            final GlobalDynamicProperties dynamicProperties) {
        this.usageLimits = usageLimits;
        this.validator = validator;
        this.tokenStore = tokenStore;
        this.accountStore = accountStore;
        this.dynamicProperties = dynamicProperties;
    }

    public void mint(
            final Id targetId,
            final int metaDataCount,
            final long amount,
            final List<ByteString> metaDataList,
            final Instant consensusTime) {

        /* --- Load the model objects --- */
        final var token = tokenStore.loadToken(targetId);
        validateMinting(token, metaDataCount);
        final var treasuryRel = tokenStore.loadTokenRelationship(token, token.getTreasury());

        /* --- Instantiate change trackers --- */
        final var ownershipTracker = new OwnershipTracker();

        /* --- Do the business logic --- */
        if (token.getType() == TokenType.FUNGIBLE_COMMON) {
            token.mint(treasuryRel, amount, false);
        } else {
            token.mint(ownershipTracker, treasuryRel, metaDataList, fromJava(consensusTime));
        }

        /* --- Persist the updated models --- */
        tokenStore.commitToken(token);
        tokenStore.commitTokenRelationships(List.of(treasuryRel));
        tokenStore.commitTrackers(ownershipTracker);
        accountStore.commitAccount(token.getTreasury());
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        TokenMintTransactionBody op = txn.getTokenMint();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return validateTokenOpsWith(
                op.getMetadataCount(),
                op.getAmount(),
                dynamicProperties.areNftsEnabled(),
                INVALID_TOKEN_MINT_AMOUNT,
                op.getMetadataList(),
                validator::maxBatchSizeMintCheck,
                validator::nftMetadataCheck);
    }

    private void validateMinting(final Token token, final int metaDataCount) {
        if (token.getType() == NON_FUNGIBLE_UNIQUE) {
            usageLimits.assertMintableNfts(metaDataCount);
        }
    }
}
