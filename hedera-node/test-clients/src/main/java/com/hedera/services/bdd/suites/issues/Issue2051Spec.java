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

package com.hedera.services.bdd.suites.issues;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.approxChangeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class Issue2051Spec extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Issue2051Spec.class);
    private static final String TRANSFER_CONTRACT = "transferContract";
    private static final String TRANSFER = "transfer";
    private static final String PAYER = "payer";
    private static final String SNAPSHOT = "snapshot";
    private static final String DELETE_TXN = "deleteTxn";

    public static void main(String... args) {
        new Issue2051Spec().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            transferAccountCannotBeDeletedForContractTarget(),
            transferAccountCannotBeDeleted(),
            tbdCanPayForItsOwnDeletion(),
        });
    }

    @HapiTest
    private HapiSpec tbdCanPayForItsOwnDeletion() {
        return defaultHapiSpec("TbdCanPayForItsOwnDeletion")
                .given(cryptoCreate("tbd"), cryptoCreate(TRANSFER))
                .when()
                .then(
                        cryptoDelete("tbd")
                                .via("selfFinanced")
                                .payingWith("tbd")
                                .transfer(TRANSFER),
                        getTxnRecord("selfFinanced").logged());
    }

    private HapiSpec transferAccountCannotBeDeleted() {
        return defaultHapiSpec("TransferAccountCannotBeDeleted")
                .given(cryptoCreate(PAYER), cryptoCreate(TRANSFER), cryptoCreate("tbd"))
                .when(cryptoDelete(TRANSFER))
                .then(
                        balanceSnapshot(SNAPSHOT, PAYER),
                        cryptoDelete("tbd")
                                .via(DELETE_TXN)
                                .payingWith(PAYER)
                                .transfer(TRANSFER)
                                .hasKnownStatus(ACCOUNT_DELETED),
                        getTxnRecord(DELETE_TXN).logged(),
                        getAccountBalance(PAYER).hasTinyBars(approxChangeFromSnapshot(SNAPSHOT, -9384399, 1000)));
    }

    private HapiSpec transferAccountCannotBeDeletedForContractTarget() {
        return defaultHapiSpec("TransferAccountCannotBeDeletedForContractTarget")
                .given(
                        uploadInitCode("CreateTrivial"),
                        uploadInitCode("PayReceivable"),
                        cryptoCreate(TRANSFER),
                        contractCreate("CreateTrivial"),
                        contractCreate("PayReceivable"))
                .when(cryptoDelete(TRANSFER), contractDelete("PayReceivable"))
                .then(
                        balanceSnapshot(SNAPSHOT, GENESIS),
                        contractDelete("CreateTrivial")
                                .via(DELETE_TXN)
                                .transferAccount(TRANSFER)
                                .hasKnownStatus(OBTAINER_DOES_NOT_EXIST),
                        contractDelete("CreateTrivial")
                                .via(DELETE_TXN)
                                .transferContract("PayReceivable")
                                .hasKnownStatus(INVALID_CONTRACT_ID));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
