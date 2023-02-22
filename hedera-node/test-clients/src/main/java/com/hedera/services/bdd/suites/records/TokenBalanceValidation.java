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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
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
    private final boolean createTransferTransactions;

    private TokenBalanceValidation(
            final Map<AccountNumTokenNum, Long> expectedTokenBalances,
            final AccountClassifier accountClassifier,
            final boolean createTransferTransactions) {
        this.expectedTokenBalances = expectedTokenBalances;
        this.accountClassifier = accountClassifier;
        this.createTransferTransactions = createTransferTransactions;
    }

    public TokenBalanceValidation(
            final Map<AccountNumTokenNum, Long> expectedTokenBalances, final AccountClassifier accountClassifier) {
        this(expectedTokenBalances, accountClassifier, false);
    }

    public static void main(String... args) {
        // define expected amount for a simple token create
        final Long aFungibleToken = 12L;
        final Long aFungibleAmount = 1_000L;
        final Long aReceiverAccount = 3L;

        // define expected amount for a token create + token transfer
        final Long bFungibleToken = 10L;
        final Long bFungibleAmount = 321L;
        final Long bReceiverAccount = 4L;

        // set up a map of expected token balances
        Map<AccountNumTokenNum, Long> expectedTokenBalances = Map.of(
                new AccountNumTokenNum(aReceiverAccount, aFungibleToken),
                aFungibleAmount,
                new AccountNumTokenNum(bReceiverAccount, bFungibleToken),
                bFungibleAmount);

        // run validation using the expected balances
        new TokenBalanceValidation(expectedTokenBalances, new AccountClassifier(), true).runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(validateTokenBalances());
    }

    private HapiSpecOperation[] getHapiSpecsForTransferTxs() {
        if (!createTransferTransactions) return new HapiSpecOperation[0];

        return expectedTokenBalances.entrySet().stream()
                .map(entry -> {
                    final var accountNum = entry.getKey().accountNum();
                    final var tokenNum = entry.getKey().tokenNum();
                    final var tokenAmt = entry.getValue();
                    return new HapiSpecOperation[] {
                        // create and transfer a token
                        // later we'll validate that the receiver has the correct token balance

                        // create treasury account
                        cryptoCreate(TOKEN_TREASURY).balance(10000 * ONE_HUNDRED_HBARS),
                        // create receiver account
                        cryptoCreate(accountNum.toString()).balance(100 * ONE_HUNDRED_HBARS),
                        // create token
                        tokenCreate(tokenNum.toString())
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(tokenAmt * 10)
                                .name(tokenNum.toString()),
                        tokenAssociate(accountNum.toString(), List.of(tokenNum.toString())),
                        // transfer the token from the treasury to the account
                        cryptoTransfer(
                                moving(tokenAmt, tokenNum.toString()).between(TOKEN_TREASURY, accountNum.toString())),
                    };
                })
                .flatMap(Arrays::stream)
                .toArray(HapiSpecOperation[]::new);
    }

    private HapiSpec validateTokenBalances() {
        return defaultHapiSpec("ValidateTokenBalances")
                .given(getHapiSpecsForTransferTxs())
                .when()
                .then(inParallel(expectedTokenBalances.entrySet().stream()
                                .map(entry -> {
                                    final var accountNum = entry.getKey().accountNum();
                                    final var tokenNum = entry.getKey().tokenNum();
                                    final var tokenAmt = entry.getValue();

                                    // validate that the transfer worked and the receiver account has the tokens
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
