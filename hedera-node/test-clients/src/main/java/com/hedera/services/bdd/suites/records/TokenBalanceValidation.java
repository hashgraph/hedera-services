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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.junit.utils.AccountClassifier;
import com.hedera.services.bdd.junit.validators.AccountNumTokenNum;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenBalanceValidation extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenBalanceValidation.class);
    private final Map<AccountNumTokenNum, Long> expectedTokenBalances;
    private final AccountClassifier accountClassifier;

    public TokenBalanceValidation( // NetworkConfig targetInfo,
            final Map<AccountNumTokenNum, Long> expectedTokenBalances, final AccountClassifier accountClassifier) {
        this.expectedTokenBalances = expectedTokenBalances;
        this.accountClassifier = accountClassifier;
    }

    public static void main(String... args) {
        final Long aFungibleToken = 12L;
        final Long aFungibleAmount = 1_000L;
        final Long tokenTreasury = 3L;
        Map<AccountNumTokenNum, Long> expectedTokenBalances =
                Map.of(new AccountNumTokenNum(tokenTreasury, aFungibleToken), aFungibleAmount);
        new TokenBalanceValidation(expectedTokenBalances, new AccountClassifier()).runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(validateTokenBalances());
    }

    private HapiSpec validateTokenBalances() {
        return defaultHapiSpec("ValidateTokenBalances")
                .given(expectedTokenBalances.entrySet().stream()
                        .map(entry -> {
                            final var accountNum = entry.getKey().accountNum();
                            final var tokenNum = entry.getKey().tokenId();
                            final var tokenAmt = entry.getValue();
                            return new HapiSpecOperation[] {
                                cryptoCreate(accountNum.toString()),
                                tokenCreate(tokenNum.toString())
                                        .tokenType(TokenType.FUNGIBLE_COMMON)
                                        .treasury(accountNum.toString())
                                        .initialSupply(tokenAmt)
                                        .name(tokenNum.toString())
                            };
                        })
                        .flatMap(Arrays::stream)
                        .toArray(HapiSpecOperation[]::new))
                .when()
                .then(inParallel(expectedTokenBalances.entrySet().stream()
                                .map(entry -> {
                                    final var accountNum = entry.getKey().accountNum();
                                    final var tokenNum = entry.getKey().tokenId();
                                    final var tokenAmt = entry.getValue();

                                    return getAccountBalance(
                                                    accountNum.toString(), accountClassifier.isContract(accountNum))
                                            .hasAnswerOnlyPrecheckFrom(OK)
                                            .hasTokenBalance(tokenNum.toString(), tokenAmt);
                                })
                                .toArray(HapiSpecOperation[]::new))
                        .failOnErrors());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
