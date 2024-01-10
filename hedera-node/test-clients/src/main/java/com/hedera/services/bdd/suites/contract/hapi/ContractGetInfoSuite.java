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
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withTargetLedgerId;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite(fuzzyMatch = true)
@Tag(SMART_CONTRACT)
public class ContractGetInfoSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(ContractGetInfoSuite.class);

    private static final String NON_EXISTING_CONTRACT =
            HapiSpecSetup.getDefaultInstance().invalidContractName();

    public static void main(String... args) {
        new ContractGetInfoSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(getInfoWorks(), invalidContractFromCostAnswer(), invalidContractFromAnswerOnly());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @HapiTest
    final HapiSpec getInfoWorks() {
        final var contract = "Multipurpose";
        final var MEMO = "This is a test.";
        return defaultHapiSpec("GetInfoWorks")
                .given(
                        newKeyNamed("adminKey"),
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .adminKey("adminKey")
                                .entityMemo(MEMO)
                                .autoRenewSecs(6999999L))
                .when()
                .then(withTargetLedgerId(ledgerId -> getContractInfo(contract)
                        .hasEncodedLedgerId(ledgerId)
                        .hasExpectedInfo()
                        .has(contractWith().memo(MEMO).adminKey("adminKey"))));
    }

    @HapiTest
    final HapiSpec invalidContractFromCostAnswer() {
        return defaultHapiSpec("InvalidContractFromCostAnswer")
                .given()
                .when()
                .then(getContractInfo(NON_EXISTING_CONTRACT)
                        .hasCostAnswerPrecheck(ResponseCodeEnum.INVALID_CONTRACT_ID));
    }

    @HapiTest
    final HapiSpec invalidContractFromAnswerOnly() {
        return defaultHapiSpec("InvalidContractFromAnswerOnly")
                .given()
                .when()
                .then(getContractInfo(NON_EXISTING_CONTRACT)
                        .nodePayment(27_159_182L)
                        .hasAnswerOnlyPrecheck(ResponseCodeEnum.INVALID_CONTRACT_ID));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
