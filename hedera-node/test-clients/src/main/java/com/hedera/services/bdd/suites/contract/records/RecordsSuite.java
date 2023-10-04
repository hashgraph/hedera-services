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

package com.hedera.services.bdd.suites.contract.records;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

@HapiTestSuite
public class RecordsSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(RecordsSuite.class);

    public static void main(String... args) {
        new RecordsSuite().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(bigCall(), txRecordsContainValidTransfers());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @HapiTest
    HapiSpec bigCall() {
        final var contract = "BigBig";
        final var txName = "BigCall";
        final long byteArraySize = (long) (87.5 * 1_024);

        return defaultHapiSpec("bigCall")
                .given(
                        cryptoCreate("payer").balance(10 * ONE_HUNDRED_HBARS),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(contractCall(contract, "pick", byteArraySize)
                        .payingWith("payer")
                        .gas(400_000L)
                        .via(txName))
                .then(getTxnRecord(txName));
    }

    @HapiTest
    HapiSpec txRecordsContainValidTransfers() {
        final var contract = "ParentChildTransfer";

        return defaultHapiSpec("TXRecordsContainValidTransfers")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).balance(10_000L).via("createTx"))
                .when(contractCall(contract, "transferToChild", BigInteger.valueOf(10_000))
                        .via("transferTx"))
                .then(assertionsHold((spec, ctxLog) -> {
                    final var subop01 = getTxnRecord("createTx").saveTxnRecordToRegistry("createTxRec");
                    final var subop02 = getTxnRecord("transferTx").saveTxnRecordToRegistry("transferTxRec");
                    CustomSpecAssert.allRunFor(spec, subop01, subop02);

                    final var createRecord = spec.registry().getTransactionRecord("createTxRec");
                    final var parent = createRecord.getContractCreateResult().getCreatedContractIDs(0);
                    final var child = createRecord.getContractCreateResult().getCreatedContractIDs(1);

                    // validate transfer list
                    final List<AccountAmount> expectedTransfers = new ArrayList<>(2);
                    final var receiverTransfer = AccountAmount.newBuilder()
                            .setAccountID(AccountID.newBuilder()
                                    .setAccountNum(parent.getContractNum())
                                    .build())
                            .setAmount(-10_000L)
                            .build();
                    expectedTransfers.add(receiverTransfer);
                    final var contractTransfer = AccountAmount.newBuilder()
                            .setAccountID(AccountID.newBuilder()
                                    .setAccountNum(child.getContractNum())
                                    .build())
                            .setAmount(10_000L)
                            .build();
                    expectedTransfers.add(contractTransfer);

                    final var transferRecord = spec.registry().getTransactionRecord("transferTxRec");

                    final var transferList = transferRecord.getTransferList();
                    Assertions.assertNotNull(transferList);
                    Assertions.assertNotNull(transferList.getAccountAmountsList());
                    Assertions.assertTrue(transferList.getAccountAmountsList().containsAll(expectedTransfers));
                    final var amountSum = sumAmountsInTransferList(transferList.getAccountAmountsList());
                    Assertions.assertEquals(0, amountSum);
                }));
    }

    private long sumAmountsInTransferList(List<AccountAmount> transferList) {
        var sumToReturn = 0L;
        for (AccountAmount currAccAmount : transferList) {
            sumToReturn += currAccAmount.getAmount();
        }
        return sumToReturn;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
