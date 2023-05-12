/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.token;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenManagementSpecsStateful extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenManagementSpecsStateful.class);

    private static final String TOKENS_NFTS_MAX_ALLOWED_MINTS = "tokens.nfts.maxAllowedMints";
    private static final String defaultMaxNftMints =
            HapiSpecSetup.getDefaultNodeProps().get(TOKENS_NFTS_MAX_ALLOWED_MINTS);
    private static final String FUNGIBLE_TOKEN = "fungibleToken";

    public static void main(String... args) {
        new TokenManagementSpecsStateful().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            /* Stateful specs from TokenManagementSpecs */
            freezeMgmtFailureCasesWork(),
        });
    }

    public HapiSpec freezeMgmtFailureCasesWork() {
        var unfreezableToken = "without";
        var freezableToken = "withPlusDefaultTrue";

        return defaultHapiSpec("FreezeMgmtFailureCasesWork")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("tokens.maxPerAccount", "" + 1000)),
                        newKeyNamed("oneFreeze"),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate("go").balance(0L),
                        tokenCreate(unfreezableToken).treasury(TOKEN_TREASURY),
                        tokenCreate(freezableToken)
                                .freezeDefault(true)
                                .freezeKey("oneFreeze")
                                .treasury(TOKEN_TREASURY))
                .when(
                        tokenFreeze(unfreezableToken, TOKEN_TREASURY)
                                .signedBy(GENESIS)
                                .hasKnownStatus(TOKEN_HAS_NO_FREEZE_KEY),
                        tokenFreeze(freezableToken, "1.2.3").hasKnownStatus(INVALID_ACCOUNT_ID),
                        tokenFreeze(freezableToken, TOKEN_TREASURY)
                                .signedBy(GENESIS)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        tokenFreeze(freezableToken, "go").hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        tokenUnfreeze(freezableToken, "go").hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        tokenUnfreeze(unfreezableToken, TOKEN_TREASURY)
                                .signedBy(GENESIS)
                                .hasKnownStatus(TOKEN_HAS_NO_FREEZE_KEY),
                        tokenUnfreeze(freezableToken, "1.2.3").hasKnownStatus(INVALID_ACCOUNT_ID),
                        tokenUnfreeze(freezableToken, TOKEN_TREASURY)
                                .signedBy(GENESIS)
                                .hasKnownStatus(INVALID_SIGNATURE))
                .then(getTokenInfo(unfreezableToken)
                        .hasRegisteredId(unfreezableToken)
                        .logged());
    }

    private HapiSpec nftMintingCapIsEnforced() {
        return defaultHapiSpec("NftMintingCapIsEnforced")
                .given(
                        newKeyNamed("supplyKey"),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .supplyKey("supplyKey"),
                        mintToken(FUNGIBLE_TOKEN, List.of(ByteString.copyFromUtf8("Why not?"))))
                .when(fileUpdate(APP_PROPERTIES)
                        .payingWith(ADDRESS_BOOK_CONTROL)
                        .overridingProps(Map.of(TOKENS_NFTS_MAX_ALLOWED_MINTS, "" + 1)))
                .then(
                        mintToken(FUNGIBLE_TOKEN, List.of(ByteString.copyFromUtf8("Again, why not?")))
                                .hasKnownStatus(MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of(TOKENS_NFTS_MAX_ALLOWED_MINTS, "" + defaultMaxNftMints)),
                        mintToken(FUNGIBLE_TOKEN, List.of(ByteString.copyFromUtf8("Again, why not?"))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
