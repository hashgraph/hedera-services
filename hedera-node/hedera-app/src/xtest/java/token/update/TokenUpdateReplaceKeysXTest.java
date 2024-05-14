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

package token.update;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenKeyValidation;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.spi.ReadableKVStateBase;
import com.swirlds.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import token.AbstractTokenXTest;

public class TokenUpdateReplaceKeysXTest extends AbstractTokenXTest {
    // This key is structurally valid, but effectively unusable because there is no
    // known way to invert the SHA-512 hash of its associated curve point
    public static final Key UNUSABLE_ZEROS_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000000"))
            .build();
    private Map<AccountID, Account> accountsCurrent;
    private final Key TOKEN_TREASURY_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("00aaa00aaa00aaa00aaa00aaa00aaaab00aaa00aaa00aaa00aaa00aaa00aaaad"))
            .build();
    private final Key INITIAL_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("00aaa00aaa00aaa00aaa00aaa00aaaab00aaa00aaa00aaa00aaa00aaa00aaaac"))
            .build();
    private final Key FINAL_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("00aaa00aaa00aaa00aaa00aaa00aaaab00aaa00aaa00aaa00aaa00aaa00aaaab"))
            .build();
    private final Key ADMIN_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("00aaa00aaa00aaa00aaa00aaa00aaaaa00aaa00aaa00aaa00aaa00aaa00aaaaa"))
            .build();
    protected static final String TOKEN_UPDATE_KEYS_ID = "tokenUpdateKeysId";
    protected static final String TOKEN_INVALID_KEYS_ID = "tokenInvalidKeysId";
    private static final String TOKEN_TREASURY = "tokenTreasury";
    private static final String FIRST_ROYALTY_COLLECTOR = "firstRoyaltyCollector";
    private static final int PLENTY_OF_SLOTS = 10;
    private static final long INITIAL_SUPPLY = 123456789;

    @Override
    protected void doScenarioOperations() {
        // update freeze key
        accountsCurrent.get(idOfNamedAccount(TOKEN_TREASURY));
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_UPDATE_KEYS_ID), List.of(b -> b.freezeKey(FINAL_KEY))),
                OK);
        // update feeScheduleKey key
        accountsCurrent.get(idOfNamedAccount(TOKEN_TREASURY));
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_UPDATE_KEYS_ID), List.of(b -> b.feeScheduleKey(FINAL_KEY))),
                OK);
        // update kycKey key
        accountsCurrent.get(idOfNamedAccount(TOKEN_TREASURY));
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_UPDATE_KEYS_ID), List.of(b -> b.kycKey(FINAL_KEY))),
                OK);
        // update pauseKey key
        accountsCurrent.get(idOfNamedAccount(TOKEN_TREASURY));
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_UPDATE_KEYS_ID), List.of(b -> b.pauseKey(FINAL_KEY))),
                OK);
        // update supplyKey key
        accountsCurrent.get(idOfNamedAccount(TOKEN_TREASURY));
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_UPDATE_KEYS_ID), List.of(b -> b.supplyKey(FINAL_KEY))),
                OK);
        // update wipeKey key
        accountsCurrent.get(idOfNamedAccount(TOKEN_TREASURY));
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_UPDATE_KEYS_ID), List.of(b -> b.wipeKey(FINAL_KEY))),
                OK);
        // update metadataKey key
        accountsCurrent.get(idOfNamedAccount(TOKEN_TREASURY));
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_UPDATE_KEYS_ID), List.of(b -> b.metadataKey(FINAL_KEY))),
                OK);

        // change keys to an "invalid" key i.e `0x0000000000000000000000000000000000000000`
        // change freeze key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_INVALID_KEYS_ID), List.of(b -> b.freezeKey(UNUSABLE_ZEROS_KEY)
                        .keyVerificationMode(TokenKeyValidation.NO_VALIDATION))),
                OK);
        // change kyc key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_INVALID_KEYS_ID), List.of(b -> b.kycKey(UNUSABLE_ZEROS_KEY)
                        .keyVerificationMode(TokenKeyValidation.NO_VALIDATION))),
                OK);
        // change wipe key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_INVALID_KEYS_ID), List.of(b -> b.wipeKey(UNUSABLE_ZEROS_KEY)
                        .keyVerificationMode(TokenKeyValidation.NO_VALIDATION))),
                OK);
        // change supply key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_INVALID_KEYS_ID), List.of(b -> b.supplyKey(UNUSABLE_ZEROS_KEY)
                        .keyVerificationMode(TokenKeyValidation.NO_VALIDATION))),
                OK);
        // change fee schedule key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_INVALID_KEYS_ID), List.of(b -> b.feeScheduleKey(UNUSABLE_ZEROS_KEY)
                        .keyVerificationMode(TokenKeyValidation.NO_VALIDATION))),
                OK);
        // change pause key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_INVALID_KEYS_ID), List.of(b -> b.pauseKey(UNUSABLE_ZEROS_KEY)
                        .keyVerificationMode(TokenKeyValidation.NO_VALIDATION))),
                OK);
        // change metadata key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_INVALID_KEYS_ID), List.of(b -> b.metadataKey(UNUSABLE_ZEROS_KEY)
                        .keyVerificationMode(TokenKeyValidation.NO_VALIDATION))),
                OK);
        // change admin key to an invalid
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_INVALID_KEYS_ID), List.of(b -> b.adminKey(UNUSABLE_ZEROS_KEY)
                        .keyVerificationMode(TokenKeyValidation.NO_VALIDATION))),
                OK);
    }

    @Override
    protected void assertExpectedTokens(@NonNull ReadableKVState<TokenID, Token> tokens) {
        ((ReadableKVStateBase<TokenID, Token>) tokens).reset();
        final var token = Objects.requireNonNull(tokens.get(idOfNamedToken(TOKEN_UPDATE_KEYS_ID)));
        assertEquals(FINAL_KEY, token.freezeKey());
        assertEquals(FINAL_KEY, token.feeScheduleKey());
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = super.initialAccounts();
        addNamedAccount(
                TOKEN_TREASURY, b -> b.maxAutoAssociations(PLENTY_OF_SLOTS).key(TOKEN_TREASURY_KEY), accounts);
        addNamedAccount(FIRST_ROYALTY_COLLECTOR, b -> b.maxAutoAssociations(PLENTY_OF_SLOTS), accounts);
        accountsCurrent = accounts;
        return accounts;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = super.initialTokens();
        addNamedFungibleToken(
                TOKEN_UPDATE_KEYS_ID,
                b -> b.treasuryAccountId(idOfNamedAccount(TOKEN_TREASURY))
                        .totalSupply(INITIAL_SUPPLY)
                        .adminKey(ADMIN_KEY)
                        .freezeKey(INITIAL_KEY)
                        .feeScheduleKey(INITIAL_KEY)
                        .pauseKey(INITIAL_KEY)
                        .supplyKey(INITIAL_KEY)
                        .wipeKey(INITIAL_KEY)
                        .metadataKey(INITIAL_KEY)
                        .kycKey(INITIAL_KEY),
                tokens);
        addNamedFungibleToken(
                TOKEN_INVALID_KEYS_ID,
                b -> b.treasuryAccountId(idOfNamedAccount(TOKEN_TREASURY))
                        .totalSupply(INITIAL_SUPPLY)
                        .adminKey(ADMIN_KEY)
                        .freezeKey(INITIAL_KEY)
                        .feeScheduleKey(INITIAL_KEY)
                        .pauseKey(INITIAL_KEY)
                        .supplyKey(INITIAL_KEY)
                        .wipeKey(INITIAL_KEY)
                        .metadataKey(INITIAL_KEY)
                        .kycKey(INITIAL_KEY),
                tokens);
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRels = super.initialTokenRelationships();
        addNewRelation(TOKEN_TREASURY, TOKEN_UPDATE_KEYS_ID, b -> b.balance(INITIAL_SUPPLY), tokenRels);
        return tokenRels;
    }
}
