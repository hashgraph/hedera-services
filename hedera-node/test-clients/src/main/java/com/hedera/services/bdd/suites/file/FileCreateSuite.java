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

package com.hedera.services.bdd.suites.file;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withTargetLedgerId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Transaction;
import java.nio.file.Path;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class FileCreateSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(FileCreateSuite.class);

    private static final long defaultMaxLifetime =
            Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));

    public static void main(String... args) {
        new FileCreateSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                createWithMemoWorks(),
                createFailsWithMissingSigs(),
                createFailsWithPayerAccountNotFound(),
                createFailsWithExcessiveLifetime(),
                exchangeRateControlAccountIsntCharged());
    }

    @HapiTest
    private HapiSpec exchangeRateControlAccountIsntCharged() {
        return defaultHapiSpec("ExchangeRateControlAccountIsntCharged")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, 1_000_000_000_000L)),
                        balanceSnapshot("pre", EXCHANGE_RATE_CONTROL),
                        getFileContents(EXCHANGE_RATES).saveTo("exchangeRates.bin"))
                .when(fileUpdate(EXCHANGE_RATES)
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .path(Path.of("./", "exchangeRates.bin").toString()))
                .then(getAccountBalance(EXCHANGE_RATE_CONTROL).hasTinyBars(changeFromSnapshot("pre", 0)));
    }

    @HapiTest
    private HapiSpec createFailsWithExcessiveLifetime() {
        return defaultHapiSpec("CreateFailsWithExcessiveLifetime")
                .given()
                .when()
                .then(fileCreate("test")
                        .lifetime(defaultMaxLifetime + 12_345L)
                        .hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE));
    }

    @HapiTest
    private HapiSpec createWithMemoWorks() {
        String memo = "Really quite something!";

        return defaultHapiSpec("createWithMemoWorks")
                .given(
                        fileCreate("ntb").entityMemo(ZERO_BYTE_MEMO).hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        fileCreate("memorable").entityMemo(memo))
                .when()
                .then(withTargetLedgerId(ledgerId ->
                        getFileInfo("memorable").hasEncodedLedgerId(ledgerId).hasMemo(memo)));
    }

    @HapiTest
    private HapiSpec createFailsWithMissingSigs() {
        KeyShape shape = listOf(SIMPLE, threshOf(2, 3), threshOf(1, 3));
        SigControl validSig = shape.signedWith(sigs(ON, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));
        SigControl invalidSig = shape.signedWith(sigs(OFF, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));

        return defaultHapiSpec("CreateFailsWithMissingSigs")
                .given()
                .when()
                .then(
                        fileCreate("test")
                                .waclShape(shape)
                                .sigControl(forKey("test", invalidSig))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        fileCreate("test").waclShape(shape).sigControl(forKey("test", validSig)));
    }

    private static Transaction replaceTxnNodeAccount(Transaction txn) {
        AccountID badNodeAccount = AccountID.newBuilder()
                .setAccountNum(2000)
                .setRealmNum(0)
                .setShardNum(0)
                .build();
        return TxnUtils.replaceTxnNodeAccount(txn, badNodeAccount);
    }

    @HapiTest
    private HapiSpec createFailsWithPayerAccountNotFound() {
        KeyShape shape = listOf(SIMPLE, threshOf(2, 3), threshOf(1, 3));
        SigControl validSig = shape.signedWith(sigs(ON, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));

        return defaultHapiSpec("CreateFailsWithPayerAccountNotFound")
                .given()
                .when()
                .then(fileCreate("test")
                        .withProtoStructure(HapiSpecSetup.TxnProtoStructure.OLD)
                        .waclShape(shape)
                        .sigControl(forKey("test", validSig))
                        .scrambleTxnBody(FileCreateSuite::replaceTxnNodeAccount)
                        .hasPrecheckFrom(INVALID_NODE_ACCOUNT));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
