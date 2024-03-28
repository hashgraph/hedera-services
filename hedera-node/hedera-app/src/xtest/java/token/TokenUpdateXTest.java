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
import static contract.XTestConstants.RECEIVER_ID;
import static contract.XTestConstants.SN_1234;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenKeyValidation;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.spi.state.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class TokenUpdateXTest extends AbstractTokenXTest {
    private Map<AccountID, Account> accountsCurrent;
    private final Key TOKEN_TREASURY_KEY = Key.newBuilder().ed25519(Bytes.fromHex("00aaa00aaa00aaa00aaa00aaa00aaaab00aaa00aaa00aaa00aaa00aaa00aaaad")).build();
    private final Key INITIAL_KEY = Key.newBuilder().ed25519(Bytes.fromHex("00aaa00aaa00aaa00aaa00aaa00aaaab00aaa00aaa00aaa00aaa00aaa00aaaac")).build();
    private final Key FINAL_KEY = Key.newBuilder().ed25519(Bytes.fromHex("00aaa00aaa00aaa00aaa00aaa00aaaab00aaa00aaa00aaa00aaa00aaa00aaaab")).build();
    private final Key ADMIN_KEY = Key.newBuilder().ed25519(Bytes.fromHex("00aaa00aaa00aaa00aaa00aaa00aaaaa00aaa00aaa00aaa00aaa00aaa00aaaaa")).build();
    protected static final String TOKEN_REMOVE_KEYS_ID = "tokenRemoveKeysId";
    protected static final String TOKEN_CHANGE_KEYS_ID = "tokenChangeKeysId";
    private static final String TOKEN_TREASURY = "tokenTreasury";
    private static final String FIRST_FUNGIBLE = "firstFungible";
    private static final String FIRST_ROYALTY_COLLECTOR = "firstRoyaltyCollector";
    private static final int PLENTY_OF_SLOTS = 10;
    private static final long INITIAL_SUPPLY = 123456789;

    private static final Key DEFAULT_KEY = Key.newBuilder()
            .ed25519(Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()))
            .build();

    @Override
    protected void doScenarioOperations() {
        var account = accountsCurrent.get(idOfNamedAccount(TOKEN_TREASURY));
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(), tokenUpdate(
                        idOfNamedToken(FIRST_FUNGIBLE),
                        Arrays.asList(
                                b -> b.freezeKey(FINAL_KEY),
                                b -> b.name("FINAL_KEY")
                        )
                ), OK);
//        answerSingleQuery(
//                component.tokenInfoHandler(),
//                tokenInfo(idOfNamedToken(FIRST_FUNGIBLE)),
//                account.accountId(),
//                assertingTokenInfo()
//        );

//        // remove freeze key
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
//                                .freezeKey(IMMUTABILITY_SENTINEL_KEY))
//                        .build(),
//                OK);
//        // remove kyc key
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
//                                .kycKey(IMMUTABILITY_SENTINEL_KEY))
//                        .build(),
//                OK);
//        // remove wipe key
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
//                                .wipeKey(IMMUTABILITY_SENTINEL_KEY))
//                        .build(),
//                OK);
//        // remove supply key
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
//                                .supplyKey(IMMUTABILITY_SENTINEL_KEY))
//                        .build(),
//                OK);
//        // remove fee schedule key
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
//                                .feeScheduleKey(IMMUTABILITY_SENTINEL_KEY))
//                        .build(),
//                OK);
//        // remove pause key
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
//                                .pauseKey(IMMUTABILITY_SENTINEL_KEY))
//                        .build(),
//                OK);
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
//                                .metadataKey(IMMUTABILITY_SENTINEL_KEY))
//                        .build(),
//                OK);
//        // remove admin key
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_REMOVE_KEYS_ID))
//                                .adminKey(IMMUTABILITY_SENTINEL_KEY))
//                        .build(),
//                OK);
//
//        // change keys to an "invalid" key i.e `0x0000000000000000000000000000000000000000`
//        // change freeze key to an invalid
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
//                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
//                                .freezeKey(ALL_ZEROS_INVALID_KEY))
//                        .build(),
//                OK);
//        // change kyc key to an invalid
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
//                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
//                                .kycKey(ALL_ZEROS_INVALID_KEY))
//                        .build(),
//                OK);
//        // change wipe key to an invalid
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
//                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
//                                .wipeKey(ALL_ZEROS_INVALID_KEY))
//                        .build(),
//                OK);
//        // change supply key to an invalid
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
//                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
//                                .supplyKey(ALL_ZEROS_INVALID_KEY))
//                        .build(),
//                OK);
//        // change fee schedule key to an invalid
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
//                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
//                                .feeScheduleKey(ALL_ZEROS_INVALID_KEY))
//                        .build(),
//                OK);
//        // change pause key to an invalid
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
//                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
//                                .pauseKey(ALL_ZEROS_INVALID_KEY))
//                        .build(),
//                OK);
//        // change admin key to an invalid
//        handleAndCommitSingleTransaction(
//                component.tokenUpdateHandler(),
//                TransactionBody.newBuilder()
//                        .transactionID(TransactionID.newBuilder()
//                                .accountID(DEFAULT_PAYER_ID)
//                                .build())
//                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
//                                .token(idOfNamedToken(TOKEN_CHANGE_KEYS_ID))
//                                .keyVerificationMode(TokenKeyValidation.NO_VALIDATION)
//                                .adminKey(ALL_ZEROS_INVALID_KEY))
//                        .build(),
//                OK);
    }

    @Override
    protected void assertExpectedNfts(@NonNull ReadableKVState<NftID, Nft> nfts) {
        // Here is where we can e.g. assert the NFT has been transferred to the counterparty
    }

    @Override
    protected void assertExpectedTokenRelations(@NonNull ReadableKVState<EntityIDPair, TokenRelation> tokenRels) {
        // Here is where we can e.g. assert the expected royalties have been collected from the
        // fungible value exchanged for the NFT
    }

    @Override
    protected void assertExpectedTokens(@NonNull ReadableKVState<TokenID, Token> tokens) {
        final var token = Objects.requireNonNull(tokens.get(idOfNamedToken(FIRST_FUNGIBLE)));
        assertEquals("FINAL_KEY", token.name());
        assertEquals(FINAL_KEY, token.freezeKey());
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = super.initialAccounts();
        addNamedAccount(TOKEN_TREASURY, b ->
                b.maxAutoAssociations(PLENTY_OF_SLOTS).key(TOKEN_TREASURY_KEY), accounts);
        addNamedAccount(
                FIRST_ROYALTY_COLLECTOR, b -> b.maxAutoAssociations(PLENTY_OF_SLOTS), accounts);
        accountsCurrent = accounts;
        return accounts;
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
        addNamedFungibleToken(
                FIRST_FUNGIBLE,
                b -> b.treasuryAccountId(idOfNamedAccount(TOKEN_TREASURY))
                        .totalSupply(INITIAL_SUPPLY)
                        .adminKey(ADMIN_KEY)
                        .freezeKey(INITIAL_KEY),
                tokens);

        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRels = super.initialTokenRelationships();
        addNewRelation(TOKEN_TREASURY, FIRST_FUNGIBLE, b -> b.balance(INITIAL_SUPPLY), tokenRels);
        return tokenRels;
    }

}
