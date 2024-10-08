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

package com.hedera.services.bdd.suites.hip991;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.ArrayList;
import java.util.List;

public class TopicCustomFeeBase {
    protected static final String TOPIC = "topic";
    protected static final String OWNER = "owner";
    protected static final String FUNGIBLE_TOKEN = "fungibleToken";
    protected static final String ADMIN_KEY = "adminKey";
    protected static final String SUBMIT_KEY = "submitKey";
    protected static final String FEE_SCHEDULE_KEY = "feeScheduleKey";
    protected static final String FEE_EXEMPT_KEY_PREFIX = "feeExemptKey_";
    /*        Submit message entities        */
    protected static final String SUBMITTER = "submitter";
    protected static final String TOKEN_TREASURY = "tokenTreasury";
    protected static final String DENOM_TREASURY = "denomTreasury";
    protected static final String BASE_TOKEN = "baseToken";

    /* tokens with multilayer fees */
    protected static final String TOKEN_PREFIX = "token_";
    protected static final String COLLECTOR_PREFIX = "collector_";
    protected static final String DENOM_TOKEN_PREFIX = "denomToken_";

    // This key is truly invalid, as all Ed25519 public keys must be 32 bytes long
    protected static final Key STRUCTURALLY_INVALID_KEY =
            Key.newBuilder().setEd25519(ByteString.fromHex("ff")).build();

    protected static SpecOperation[] setupBaseKeys() {
        return new SpecOperation[] {newKeyNamed(ADMIN_KEY), newKeyNamed(SUBMIT_KEY), newKeyNamed(FEE_SCHEDULE_KEY)};
    }

    protected static SpecOperation[] associateFeeTokensAndSubmitter() {
        return new SpecOperation[] {
            cryptoCreate(SUBMITTER).balance(ONE_MILLION_HBARS),
            cryptoCreate(TOKEN_TREASURY),
            tokenCreate(BASE_TOKEN)
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .treasury(TOKEN_TREASURY)
                    .initialSupply(500L),
            tokenAssociate(SUBMITTER, BASE_TOKEN),
            cryptoTransfer(moving(500L, BASE_TOKEN).between(TOKEN_TREASURY, SUBMITTER))
        };
    }

    /**
     * Create and transfer multiple tokens with 2 layer custom fees to given account.
     *
     * @param owner account to transfer tokens
     * @param numberOfTokens the count of tokens to be transferred
     * @return array of spec operations
     */
    protected SpecOperation[] createMultipleTokensWith2LayerFees(String owner, int numberOfTokens) {
        final var specOperations = new ArrayList<SpecOperation>();
        specOperations.add(cryptoCreate(DENOM_TREASURY));
        specOperations.add(cryptoCreate(TOKEN_TREASURY));
        for (int i = 0; i < numberOfTokens; i++) {
            final var tokenName = TOKEN_PREFIX + i;
            specOperations.addAll(createTokenWith2LayerFee(owner, tokenName, false));
        }
        return specOperations.toArray(new SpecOperation[0]);
    }


    /**
     *
     *
     *
     * @param owner
     * @param tokenName
     * @param createTreasury
     * @return
     */
    protected static List<SpecOperation> createTokenWith2LayerFee(String owner, String tokenName, boolean createTreasury) {
        final var specOperations = new ArrayList<SpecOperation>();
        final var collectorName = COLLECTOR_PREFIX + tokenName;
        final var denomToken = DENOM_TOKEN_PREFIX + tokenName;
        // if we generate multiple tokens, there will be no need to create treasury every time we create new token
        if (createTreasury) {
            specOperations.add(cryptoCreate(DENOM_TREASURY));
            specOperations.add(cryptoCreate(TOKEN_TREASURY));
        }
        // create first common collector
        specOperations.add(cryptoCreate(collectorName).balance(0L));
        // create denomination token with hbar fee
        specOperations.add(tokenCreate(denomToken)
                .tokenType(TokenType.FUNGIBLE_COMMON)
                .treasury(DENOM_TREASURY)
                .withCustom(fixedHbarFee(ONE_HBAR, collectorName)));
        // associate the denomination token with the collector
        specOperations.add(tokenAssociate(collectorName, denomToken));
        // create the token with fixed HTS fee
        specOperations.add(tokenCreate(tokenName)
                .tokenType(TokenType.FUNGIBLE_COMMON)
                .treasury(TOKEN_TREASURY)
                .withCustom(fixedHtsFee(1, denomToken, collectorName)));
        // associate the owner with the two new tokens
        specOperations.add(tokenAssociate(owner, tokenName));
        specOperations.add(tokenAssociate(owner, denomToken));
        // transfer the tokens to the owner
        specOperations.add(cryptoTransfer(moving(100L, tokenName).between(TOKEN_TREASURY, owner)));
        specOperations.add(cryptoTransfer(moving(100L, denomToken).between(DENOM_TREASURY, owner)));
        return specOperations;
    }

    protected static SpecOperation[] newNamedKeysForFEKL(int count) {
        final var list = new ArrayList<SpecOperation>();
        for (int i = 0; i < count; i++) {
            list.add(newKeyNamed(FEE_EXEMPT_KEY_PREFIX + i));
        }
        return list.toArray(new SpecOperation[0]);
    }

    protected static String[] feeExemptKeyNames(int count) {
        final var list = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            list.add(FEE_EXEMPT_KEY_PREFIX + i);
        }
        return list.toArray(new String[0]);
    }
}
