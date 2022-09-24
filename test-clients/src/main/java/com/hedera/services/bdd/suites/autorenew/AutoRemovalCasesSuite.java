/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.autorenew;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.disablingAutoRenewWithDefaults;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.propsForAccountAutoRenewOnWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutoRemovalCasesSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(AutoRemovalCasesSuite.class);

    public static void main(String... args) {
        new AutoRemovalCasesSuite().runSuiteSync();
    }

    @Override
    @SuppressWarnings("java:S3878")
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
                    displacesTokenUnitsAsExpected(),
                    immediatelyRemovesDeletedAccountOnExpiry(),
                    ignoresExpiredDeletedContracts(),
                    autoRemovalCasesSuiteCleanup(),
                });
    }

    private HapiApiSpec ignoresExpiredDeletedContracts() {
        final var adminKey = "tac";
        final var tbd = "dead";

        return defaultHapiSpec("IgnoresExpiredDeletedContracts")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(propsForAccountAutoRenewOnWith(1, 7776000L)),
                        newKeyNamed(adminKey),
                        contractCreate(tbd).balance(0L).autoRenewSecs(5).adminKey(adminKey),
                        contractDelete(tbd))
                .when(sleepFor(5_500L), cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)))
                .then(getContractInfo(tbd).hasCostAnswerPrecheck(CONTRACT_DELETED));
    }

    private HapiApiSpec immediatelyRemovesDeletedAccountOnExpiry() {
        final var tbd = "dead";
        final var onlyDetached = "gone";

        return defaultHapiSpec("ImmediatelyRemovesDeletedAccountOnExpiry")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(propsForAccountAutoRenewOnWith(1, 7776000L)),
                        cryptoCreate(tbd).balance(0L).autoRenewSecs(5),
                        cryptoCreate(onlyDetached).balance(0L).autoRenewSecs(5),
                        cryptoDelete(tbd))
                .when(sleepFor(5_500L), cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)))
                .then(
                        getAccountInfo(onlyDetached),
                        getAccountInfo(tbd).hasCostAnswerPrecheck(INVALID_ACCOUNT_ID));
    }

    private HapiApiSpec displacesTokenUnitsAsExpected() {
        final long startSupply = 10;
        final long displacedSupply = 1;
        final var adminKey = "tak";
        final var civilian = "misc";
        final var removedAccount = "gone";
        final var deletedToken = "unreturnable";
        final var liveToken = "returnable";
        final var anotherLiveToken = "alsoReturnable";

        return defaultHapiSpec("DisplacesTokenUnitsAsExpected")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(propsForAccountAutoRenewOnWith(1, 0L)),
                        newKeyNamed(adminKey),
                        cryptoCreate(civilian).balance(0L),
                        tokenCreate(deletedToken)
                                .initialSupply(startSupply)
                                .adminKey(adminKey)
                                .treasury(civilian),
                        tokenCreate(liveToken).initialSupply(startSupply).treasury(civilian),
                        tokenCreate(anotherLiveToken).initialSupply(startSupply).treasury(civilian),
                        cryptoCreate(removedAccount)
                                .maxAutomaticTokenAssociations(1)
                                .balance(0L)
                                .autoRenewSecs(5),
                        tokenAssociate(removedAccount, List.of(deletedToken, liveToken)),
                        cryptoTransfer(
                                moving(displacedSupply, deletedToken)
                                        .between(civilian, removedAccount),
                                moving(displacedSupply, liveToken)
                                        .between(civilian, removedAccount),
                                moving(displacedSupply, anotherLiveToken)
                                        .between(civilian, removedAccount)),
                        tokenDelete(deletedToken))
                .when(sleepFor(5_500L), cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)))
                .then(
                        getAccountInfo(removedAccount).hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
                        getAccountBalance(civilian)
                                .hasTokenBalance(deletedToken, startSupply - displacedSupply)
                                .hasTokenBalance(liveToken, startSupply)
                                .hasTokenBalance(anotherLiveToken, startSupply));
    }

    private HapiApiSpec autoRemovalCasesSuiteCleanup() {
        return defaultHapiSpec("AutoRemovalCasesSuiteCleanup")
                .given()
                .when()
                .then(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(disablingAutoRenewWithDefaults()));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
