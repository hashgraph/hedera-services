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

package com.hedera.services.bdd.suites.records;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTokenId;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import com.hedera.services.bdd.junit.utils.AccountClassifier;
import com.hedera.services.bdd.junit.validators.AccountNumTokenId;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenBalanceValidation extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenBalanceValidation.class);
    private final Map<AccountNumTokenId, Long> expectedTokenBalances;
    private final AccountClassifier accountClassifier;
    private static final String aFungibleToken = "aFT";
    private static final Long aFungibleTokenId = 12L;
    private static final Long aFungibleAmount = 1_000L;
    private static final Long TOKEN_TREASURY = 123L;

    public TokenBalanceValidation(      //NetworkConfig targetInfo,
            final Map<AccountNumTokenId, Long> expectedTokenBalances,
            final AccountClassifier accountClassifier) {
        this.expectedTokenBalances = expectedTokenBalances;
        this.accountClassifier = accountClassifier;
    }

    public static void main(String... args) {
        //var tokenId = asTokenId(tokenBalance.getKey(), spec);
        Map<AccountNumTokenId, Long> expectedTokenBalances = Map.of(new AccountNumTokenId(TOKEN_TREASURY, 12l), aFungibleAmount);
        new TokenBalanceValidation(expectedTokenBalances, new AccountClassifier()).runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(validateTokenBalances() );
    }

    private HapiSpec validateTokenBalances() {
        final var initBalance = ONE_HBAR;

        return customHapiSpec("ValidateTokenBalances")
                .withProperties(Map.of(
                        "fees.useFixedOffer", "true",
                        "fees.fixedOffer", "100000000"))
                .given(
                        cryptoCreate(TOKEN_TREASURY.toString()).balance(initBalance),
                        tokenCreate(aFungibleToken)
                                .initialSupply(aFungibleAmount)
                                .treasury(TOKEN_TREASURY.toString())
                        )
                .when()
                .then(
                        getAccountBalance(TOKEN_TREASURY.toString())
                                .hasTokenBalance(aFungibleToken, aFungibleAmount));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
