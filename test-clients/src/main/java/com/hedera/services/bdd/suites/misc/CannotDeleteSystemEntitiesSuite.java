/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemFileDelete;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CannotDeleteSystemEntitiesSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CannotDeleteSystemEntitiesSuite.class);

    final int[] sysFileIds = {101, 102, 111, 112, 121, 122, 150};

    public static void main(String... args) {
        CannotDeleteSystemEntitiesSuite suite = new CannotDeleteSystemEntitiesSuite();
        suite.runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    ensureSystemAccountsHaveSomeFunds(),
                    systemUserCannotDeleteSystemAccounts(1, 100, GENESIS),
                    systemUserCannotDeleteSystemAccounts(700, 750, GENESIS),
                    systemUserCannotDeleteSystemAccounts(1, 100, SYSTEM_ADMIN),
                    systemUserCannotDeleteSystemAccounts(700, 750, SYSTEM_ADMIN),
                    systemUserCannotDeleteSystemAccounts(1, 100, SYSTEM_DELETE_ADMIN),
                    systemUserCannotDeleteSystemAccounts(700, 750, SYSTEM_DELETE_ADMIN),
                    normalUserCannotDeleteSystemAccounts(1, 100),
                    normalUserCannotDeleteSystemAccounts(700, 750),
                    systemUserCannotDeleteSystemFiles(sysFileIds, GENESIS),
                    systemUserCannotDeleteSystemFiles(sysFileIds, SYSTEM_ADMIN),
                    systemUserCannotDeleteSystemFiles(sysFileIds, SYSTEM_DELETE_ADMIN),
                    normalUserCannotDeleteSystemFiles(sysFileIds),
                    systemDeleteCannotDeleteSystemFiles(sysFileIds, GENESIS),
                    systemDeleteCannotDeleteSystemFiles(sysFileIds, SYSTEM_ADMIN),
                    systemDeleteCannotDeleteSystemFiles(sysFileIds, SYSTEM_DELETE_ADMIN)
                });
    }

    private HapiSpec ensureSystemAccountsHaveSomeFunds() {
        return defaultHapiSpec("EnsureSystemAccountsHaveSomeFunds")
                .given()
                .when()
                .then(
                        cryptoTransfer(
                                        tinyBarsFromTo(
                                                GENESIS, SYSTEM_ADMIN, 10 * ONE_HUNDRED_HBARS))
                                .payingWith(GENESIS),
                        cryptoTransfer(
                                        tinyBarsFromTo(
                                                GENESIS,
                                                SYSTEM_DELETE_ADMIN,
                                                10 * ONE_HUNDRED_HBARS))
                                .payingWith(GENESIS));
    }

    private HapiSpec systemUserCannotDeleteSystemAccounts(
            int firstAccount, int lastAccount, String sysUser) {
        return defaultHapiSpec("systemUserCannotDeleteSystemAccounts")
                .given(cryptoCreate("unluckyReceiver").balance(0L))
                .when()
                .then(
                        inParallel(
                                IntStream.rangeClosed(firstAccount, lastAccount)
                                        .mapToObj(
                                                id ->
                                                        cryptoDelete("0.0." + id)
                                                                .transfer("unluckyReceiver")
                                                                .payingWith(sysUser)
                                                                .signedBy(sysUser)
                                                                .hasPrecheck(
                                                                        ENTITY_NOT_ALLOWED_TO_DELETE))
                                        .toArray(HapiSpecOperation[]::new)));
    }

    private HapiSpec normalUserCannotDeleteSystemAccounts(int firstAccount, int lastAccount) {
        return defaultHapiSpec("normalUserCannotDeleteSystemAccounts")
                .given(newKeyNamed("normalKey"), cryptoCreate("unluckyReceiver").balance(0L))
                .when(cryptoCreate("normalUser").key("normalKey").balance(1_000_000_000L))
                .then(
                        inParallel(
                                IntStream.rangeClosed(firstAccount, lastAccount)
                                        .mapToObj(
                                                id ->
                                                        cryptoDelete("0.0." + id)
                                                                .transfer("unluckyReceiver")
                                                                .payingWith("normalUser")
                                                                .signedBy("normalKey")
                                                                .hasPrecheck(
                                                                        ENTITY_NOT_ALLOWED_TO_DELETE))
                                        .toArray(HapiSpecOperation[]::new)));
    }

    private HapiSpec systemUserCannotDeleteSystemFiles(int[] fileIds, String sysUser) {
        return defaultHapiSpec("systemUserCannotDeleteSystemFiles")
                .given()
                .when()
                .then(
                        inParallel(
                                Arrays.stream(fileIds)
                                        .mapToObj(
                                                id ->
                                                        cryptoDelete("0.0." + id)
                                                                .payingWith(sysUser)
                                                                .signedBy(sysUser)
                                                                .hasPrecheck(
                                                                        ENTITY_NOT_ALLOWED_TO_DELETE))
                                        .toArray(HapiSpecOperation[]::new)));
    }

    private HapiSpec normalUserCannotDeleteSystemFiles(int[] fileIds) {
        return defaultHapiSpec("normalUserCannotDeleteSystemFiles")
                .given(newKeyNamed("normalKey"))
                .when(cryptoCreate("normalUser").key("normalKey").balance(1_000_000_000L))
                .then(
                        inParallel(
                                Arrays.stream(fileIds)
                                        .mapToObj(
                                                id ->
                                                        fileDelete("0.0." + id)
                                                                .payingWith("normalUser")
                                                                .signedBy("normalKey")
                                                                .hasPrecheck(
                                                                        ENTITY_NOT_ALLOWED_TO_DELETE))
                                        .toArray(HapiSpecOperation[]::new)));
    }

    private HapiSpec systemDeleteCannotDeleteSystemFiles(int[] fileIds, String sysUser) {
        return defaultHapiSpec("systemDeleteCannotDeleteSystemFiles")
                .given()
                .when()
                .then(
                        inParallel(
                                Arrays.stream(fileIds)
                                        .mapToObj(
                                                id ->
                                                        systemFileDelete("0.0." + id)
                                                                .payingWith(sysUser)
                                                                .signedBy(sysUser)
                                                                .hasPrecheck(
                                                                        ENTITY_NOT_ALLOWED_TO_DELETE))
                                        .toArray(HapiSpecOperation[]::new)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
