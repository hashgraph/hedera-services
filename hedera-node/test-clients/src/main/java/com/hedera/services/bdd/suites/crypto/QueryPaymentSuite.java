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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QueryPaymentSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(QueryPaymentSuite.class);
    private static final String NODE = "0.0.3";

    public static void main(String... args) {
        new QueryPaymentSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(queryPaymentTests());
    }

    private List<HapiSpec> queryPaymentTests() {
        return List.of(new HapiSpec[] {
            queryPaymentsFailsWithInsufficientFunds(),
            queryPaymentsSingleBeneficiaryChecked(),
            queryPaymentsMultiBeneficiarySucceeds(),
            queryPaymentsNotToNodeFails()
        });
    }

    /*
     * 1. multiple payers pay amount to node as well as one more beneficiary. But node gets less query payment fee
     * 2. TransactionPayer will pay for query payment to node and payer has less balance
     * 3. Transaction payer is not involved in transfers for query payment to node and one or more have less balance
     */
    private HapiSpec queryPaymentsFailsWithInsufficientFunds() {
        return defaultHapiSpec("queryPaymentsFailsWithInsufficientFunds")
                .given(
                        cryptoCreate("a").balance(1_234L),
                        cryptoCreate("b").balance(1_234L),
                        cryptoCreate("c").balance(1_234L))
                .when()
                .then(
                        getAccountInfo(GENESIS)
                                .withPayment(cryptoTransfer(spec ->
                                        multiAccountPaymentToNode003AndBeneficiary(spec, "a", "b", "c", 1_000L, 2L)))
                                .setNode(NODE)
                                .payingWith("a")
                                .hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE),
                        getAccountInfo(GENESIS)
                                .withPayment(cryptoTransfer(spec ->
                                        multiAccountPaymentToNode003AndBeneficiary(spec, "a", "b", "c", 5000, 200L)))
                                .setNode(NODE)
                                .hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE),
                        getAccountInfo(GENESIS)
                                .withPayment(cryptoTransfer(spec -> multiAccountPaymentToNode003AndBeneficiary(
                                        spec, "a", GENESIS, "c", 5000, 200L)))
                                .setNode(NODE)
                                .payingWith("a")
                                .hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE));
    }

    /*
     * Tests verified
     * 1. multiple payers pay amount to node as well as one more beneficiary. But node gets correct query payment fee
     * 2. TransactionPayer will pay for query payment to node and payer has enough balance
     * 3. Transaction payer is not involved in transfers for query payment to node and all payers have enough balance
     */
    private HapiSpec queryPaymentsMultiBeneficiarySucceeds() {
        return defaultHapiSpec("queryPaymentsMultiBeneficiarySucceeds")
                .given(
                        cryptoCreate("a").balance(1_234L),
                        cryptoCreate("b").balance(1_234L),
                        cryptoCreate("c").balance(1_234L))
                .when()
                .then(
                        getAccountInfo(GENESIS)
                                .withPayment(cryptoTransfer(spec ->
                                        multiAccountPaymentToNode003AndBeneficiary(spec, "a", "b", "c", 1_000L, 200L)))
                                .setNode(NODE)
                                .hasAnswerOnlyPrecheck(OK),
                        getAccountInfo(GENESIS)
                                .withPayment(cryptoTransfer(spec ->
                                        multiAccountPaymentToNode003AndBeneficiary(spec, "a", "b", "c", 900, 200L)))
                                .setNode(NODE)
                                .payingWith("a")
                                .hasAnswerOnlyPrecheck(OK),
                        getAccountInfo(GENESIS)
                                .withPayment(cryptoTransfer(spec ->
                                        multiAccountPaymentToNode003AndBeneficiary(spec, "a", "b", "c", 1200, 200L)))
                                .setNode(NODE)
                                .payingWith("a")
                                .fee(10L)
                                .hasAnswerOnlyPrecheck(OK));
    }

    // Check if multiple payers or single payer pay amount to node
    private HapiSpec queryPaymentsSingleBeneficiaryChecked() {
        return defaultHapiSpec("queryPaymentsSingleBeneficiaryChecked")
                .given(
                        cryptoCreate("a").balance(1_234L),
                        cryptoCreate("b").balance(1_234L),
                        cryptoCreate("c").balance(1_234L))
                .when()
                .then(
                        getAccountInfo(GENESIS).fee(100L).setNode(NODE).hasAnswerOnlyPrecheck(OK),
                        getAccountInfo(GENESIS)
                                .fee(Long.MAX_VALUE)
                                .setNode(NODE)
                                .hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE),
                        getAccountInfo(GENESIS)
                                .withPayment(
                                        cryptoTransfer(spec -> multiAccountPaymentToNode003(spec, "a", "b", 1_000L)))
                                .hasAnswerOnlyPrecheck(OK));
    }

    // Check if payment is not done to node
    private HapiSpec queryPaymentsNotToNodeFails() {
        return defaultHapiSpec("queryPaymentsNotToNodeFails")
                .given(
                        cryptoCreate("a").balance(1_234L),
                        cryptoCreate("b").balance(1_234L),
                        cryptoCreate("c").balance(1_234L))
                .when()
                .then(getAccountInfo(GENESIS)
                        .withPayment(cryptoTransfer(spec -> invalidPaymentToNode(spec, "a", "b", "c", 1200)))
                        .setNode(NODE)
                        .payingWith("a")
                        .fee(10L)
                        .hasAnswerOnlyPrecheck(INVALID_RECEIVING_NODE_ACCOUNT));
    }

    private TransferList multiAccountPaymentToNode003(HapiSpec spec, String first, String second, long amount) {
        return TransferList.newBuilder()
                .addAccountAmounts(adjust(spec.registry().getAccountID(first), -amount / 2))
                .addAccountAmounts(adjust(spec.registry().getAccountID(second), -amount / 2))
                .addAccountAmounts(adjust(asAccount(NODE), amount))
                .build();
    }

    private TransferList invalidPaymentToNode(HapiSpec spec, String first, String second, String node, long amount) {
        return TransferList.newBuilder()
                .addAccountAmounts(adjust(spec.registry().getAccountID(first), -amount / 2))
                .addAccountAmounts(adjust(spec.registry().getAccountID(second), -amount / 2))
                .addAccountAmounts(adjust(spec.registry().getAccountID(node), amount))
                .build();
    }

    private TransferList multiAccountPaymentToNode003AndBeneficiary(
            HapiSpec spec, String first, String second, String beneficiary, long amount, long queryFee) {
        return TransferList.newBuilder()
                .addAccountAmounts(adjust(spec.registry().getAccountID(first), -amount / 2))
                .addAccountAmounts(adjust(spec.registry().getAccountID(second), -amount / 2))
                .addAccountAmounts(adjust(spec.registry().getAccountID(beneficiary), amount - queryFee))
                .addAccountAmounts(adjust(asAccount(NODE), queryFee))
                .build();
    }

    public AccountAmount adjust(AccountID id, long amount) {
        return AccountAmount.newBuilder().setAccountID(id).setAmount(amount).build();
    }
}
