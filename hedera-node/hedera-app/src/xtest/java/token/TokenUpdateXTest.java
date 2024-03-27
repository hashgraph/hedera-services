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

package token;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.spi.key.KeyUtils.ALL_ZEROS_INVALID_KEY;
import static com.hedera.node.app.spi.key.KeyUtils.IMMUTABILITY_SENTINEL_KEY;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenKeyValidation;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Map;

public class TokenUpdateXTest extends AbstractTokenUpdateXTest {

    protected static final String TOKEN_REMOVE_KEYS_ID = "tokenRemoveKeysId";
    protected static final String TOKEN_CHANGE_KEYS_ID = "tokenChangeKeysId";

    private static final Key DEFAULT_KEY = Key.newBuilder()
            .ed25519(Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()))
            .build();

    @Override
    protected void doScenarioOperations() {
        // if a key is set to `IMMUTABILITY_SENTINEL_KEY` then it is considered to be "removed"

        // remove freeze key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
                                .freezeKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        // remove kyc key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
                                .kycKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        // remove wipe key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
                                .wipeKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        // remove supply key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
                                .supplyKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        // remove fee schedule key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
                                .feeScheduleKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        // remove pause key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
                                .pauseKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
                                .metadataKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        // remove admin key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
                                .adminKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);

        // change keys to an "invalid" key i.e `0x0000000000000000000000000000000000000000`
        // change freeze key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
                                .freezeKey(ALL_ZEROS_INVALID_KEY))
                        .build(),
                OK);
        // change kyc key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
                                .kycKey(ALL_ZEROS_INVALID_KEY))
                        .build(),
                OK);
        // change wipe key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
                                .wipeKey(ALL_ZEROS_INVALID_KEY))
                        .build(),
                OK);
        // change supply key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
                                .supplyKey(ALL_ZEROS_INVALID_KEY))
                        .build(),
                OK);
        // change fee schedule key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
                                .feeScheduleKey(ALL_ZEROS_INVALID_KEY))
                        .build(),
                OK);
        // change pause key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
                                .pauseKey(ALL_ZEROS_INVALID_KEY))
                        .build(),
                OK);
        // change admin key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(DEFAULT_PAYER_ID)
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
                                .adminKey(ALL_ZEROS_INVALID_KEY))
                        .build(),
                OK);
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        /**
         * Initial token should be initialized with all keys to test removal. Set DEFAULT_KEY as default value for all keys.
         */
        final var tokens = super.initialTokens();
        addNamedFungibleToken(
                TOKEN_REMOVE_KEYS_ID,
                b -> b.treasuryAccountId(idOfNamedAccount(TOKEN_TREASURY))
                        .adminKey(DEFAULT_KEY)
                        .freezeKey(DEFAULT_KEY)
                        .supplyKey(DEFAULT_KEY)
                        .kycKey(DEFAULT_KEY)
                        .feeScheduleKey(DEFAULT_KEY)
                        .metadataKey(DEFAULT_KEY)
                        .wipeKey(DEFAULT_KEY)
                        .pauseKey(DEFAULT_KEY),
                tokens);
        addNamedFungibleToken(
                TOKEN_CHANGE_KEYS_ID,
                b -> b.treasuryAccountId(idOfNamedAccount(TOKEN_TREASURY))
                        .adminKey(DEFAULT_KEY)
                        .freezeKey(DEFAULT_KEY)
                        .supplyKey(DEFAULT_KEY)
                        .kycKey(DEFAULT_KEY)
                        .feeScheduleKey(DEFAULT_KEY)
                        .metadataKey(DEFAULT_KEY)
                        .wipeKey(DEFAULT_KEY)
                        .pauseKey(DEFAULT_KEY),
                tokens);
        return tokens;
    }
}
