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
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.ArrayList;

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
    protected static final String BASE_TOKEN = "baseToken";
    protected static final String MULTI_LAYER_FEE_PREFIX = "multiLayerFeePrefix_";
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
     * Create and transfer multiple tokens with fixed hbar custom fee to account.
     * @param account account to transfer tokens
     * @param numberOfTokens the count of tokens to be transferred
     * @return array of spec operations
     */
    protected SpecOperation[] transferMultiLayerFeeTokensTo(String account, int numberOfTokens) {
        final var treasury = MULTI_LAYER_FEE_PREFIX + TOKEN_TREASURY;
        final var list = new ArrayList<SpecOperation>();
        list.add(cryptoCreate(treasury));
        for (int i = 0; i < numberOfTokens; i++) {
            final var tokenName = MULTI_LAYER_FEE_PREFIX + "token_" + i;
            final var collectorName = MULTI_LAYER_FEE_PREFIX + "collector_" + i;
            list.add(cryptoCreate(collectorName).balance(0L));
            list.add(tokenCreate(tokenName)
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .treasury(treasury)
                    .initialSupply(500L)
                    .withCustom(fixedHbarFee(ONE_HBAR, collectorName)));
            list.add(tokenAssociate(account, tokenName));
            list.add(cryptoTransfer(moving(500L, tokenName).between(treasury, account)));
        }
        return list.toArray(new SpecOperation[0]);
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
