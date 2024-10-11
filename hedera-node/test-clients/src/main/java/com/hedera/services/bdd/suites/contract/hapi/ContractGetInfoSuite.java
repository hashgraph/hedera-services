/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.hapi;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.approxChangeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sendModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withTargetLedgerId;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedQueryIds;
import static com.hedera.services.bdd.suites.HapiSuite.CIVILIAN_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.TINY_PARTS_PER_WHOLE;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.MEMO;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
public class ContractGetInfoSuite {
    private static final String NON_EXISTING_CONTRACT =
            HapiSpecSetup.getDefaultInstance().invalidContractName();

    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        final var contract = "Multipurpose";
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).entityMemo(MEMO).autoRenewSecs(6999999L))
                .when()
                .then(sendModified(withSuccessivelyVariedQueryIds(), () -> getContractInfo(contract)));
    }

    @HapiTest
    final Stream<DynamicTest> getInfoWorks() {
        final var contract = "Multipurpose";
        final var MEMO = "This is a test.";
        final var canonicalUsdPrice = 0.0001;
        final var canonicalQueryFeeAtActiveRate = new AtomicLong();
        return hapiTest(
                newKeyNamed("adminKey"),
                cryptoCreate(CIVILIAN_PAYER).balance(ONE_HUNDRED_HBARS),
                balanceSnapshot("beforeQuery", CIVILIAN_PAYER),
                uploadInitCode(contract),
                // refuse eth conversion because ethereum transaction is missing admin key and memo is same as
                // parent
                contractCreate(contract)
                        .adminKey("adminKey")
                        .entityMemo(MEMO)
                        .autoRenewSecs(6999999L)
                        .refusingEthConversion(),
                withOpContext((spec, opLog) -> canonicalQueryFeeAtActiveRate.set(spec.ratesProvider()
                        .toTbWithActiveRates((long) (canonicalUsdPrice * 100 * TINY_PARTS_PER_WHOLE)))),
                withTargetLedgerId(ledgerId -> getContractInfo(contract)
                        .payingWith(CIVILIAN_PAYER)
                        .hasEncodedLedgerId(ledgerId)
                        .hasExpectedInfo()
                        .has(contractWith().memo(MEMO).adminKey("adminKey"))),
                // Wait for the query payment transaction to be handled
                sleepFor(5_000),
                sourcing(() -> getAccountBalance(CIVILIAN_PAYER)
                        .hasTinyBars(
                                // Just sanity-check a fee within 50% of the canonical fee to be safe
                                approxChangeFromSnapshot(
                                        "beforeQuery",
                                        -canonicalQueryFeeAtActiveRate.get(),
                                        canonicalQueryFeeAtActiveRate.get() / 2))));
    }

    @HapiTest
    final Stream<DynamicTest> invalidContractFromCostAnswer() {
        return defaultHapiSpec("InvalidContractFromCostAnswer")
                .given()
                .when()
                .then(getContractInfo(NON_EXISTING_CONTRACT)
                        .hasCostAnswerPrecheck(ResponseCodeEnum.INVALID_CONTRACT_ID));
    }

    @HapiTest
    final Stream<DynamicTest> invalidContractFromAnswerOnly() {
        return defaultHapiSpec("InvalidContractFromAnswerOnly")
                .given()
                .when()
                .then(getContractInfo(NON_EXISTING_CONTRACT)
                        .nodePayment(27_159_182L)
                        .hasAnswerOnlyPrecheck(ResponseCodeEnum.INVALID_CONTRACT_ID));
    }
}
