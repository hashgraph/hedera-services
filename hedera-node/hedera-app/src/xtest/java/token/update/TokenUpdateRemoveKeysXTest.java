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
import static com.hedera.node.app.spi.key.KeyUtils.IMMUTABILITY_SENTINEL_KEY;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import java.util.Map;
import token.AbstractTokenXTest;

public class TokenUpdateRemoveKeysXTest extends AbstractTokenXTest {
    private final Key TOKEN_TREASURY_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("00aaa00aaa00aaa00aaa00aaa00aaaab00aaa00aaa00aaa00aaa00aaa00aaaad"))
            .build();
    protected static final String TOKEN_REMOVE_KEYS_ID = "tokenRemoveKeysId";
    protected static final String TOKEN_CHANGE_KEYS_ID = "tokenChangeKeysId";
    private static final String TOKEN_TREASURY = "tokenTreasury";
    private static final String FIRST_ROYALTY_COLLECTOR = "firstRoyaltyCollector";
    private static final int PLENTY_OF_SLOTS = 10;

    private static final Key DEFAULT_KEY = Key.newBuilder()
            .ed25519(Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()))
            .build();

    @Override
    protected void doScenarioOperations() {
        // remove freeze key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_REMOVE_KEYS_ID), List.of(b -> b.freezeKey(IMMUTABILITY_SENTINEL_KEY))),
                OK);
        // remove kyc key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_REMOVE_KEYS_ID), List.of(b -> b.kycKey(IMMUTABILITY_SENTINEL_KEY))),
                OK);
        // remove wipe key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_REMOVE_KEYS_ID), List.of(b -> b.wipeKey(IMMUTABILITY_SENTINEL_KEY))),
                OK);
        // remove supply key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_REMOVE_KEYS_ID), List.of(b -> b.supplyKey(IMMUTABILITY_SENTINEL_KEY))),
                OK);
        // remove fee schedule key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(
                        idOfNamedToken(TOKEN_REMOVE_KEYS_ID),
                        List.of(b -> b.feeScheduleKey(IMMUTABILITY_SENTINEL_KEY))),
                OK);
        // remove pause key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_REMOVE_KEYS_ID), List.of(b -> b.pauseKey(IMMUTABILITY_SENTINEL_KEY))),
                OK);
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(
                        idOfNamedToken(TOKEN_REMOVE_KEYS_ID), List.of(b -> b.metadataKey(IMMUTABILITY_SENTINEL_KEY))),
                OK);
        // remove admin key
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                tokenUpdate(idOfNamedToken(TOKEN_REMOVE_KEYS_ID), List.of(b -> b.adminKey(IMMUTABILITY_SENTINEL_KEY))),
                OK);
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = super.initialAccounts();
        addNamedAccount(
                TOKEN_TREASURY, b -> b.maxAutoAssociations(PLENTY_OF_SLOTS).key(TOKEN_TREASURY_KEY), accounts);
        addNamedAccount(FIRST_ROYALTY_COLLECTOR, b -> b.maxAutoAssociations(PLENTY_OF_SLOTS), accounts);
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

        return tokens;
    }
}
