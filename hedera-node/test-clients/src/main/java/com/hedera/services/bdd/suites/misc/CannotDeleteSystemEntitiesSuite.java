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

package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemFileDelete;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.BddMethodIsNotATest;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

@HapiTestSuite
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
    public List<DynamicTest> getSpecsInSuite() {
        return List.of(
                genesisCannotDeleteSystemAccountsFrom1To100(),
                genesisCannotDeleteSystemAccountsFrom700To750(),
                systemAdminCannotDeleteSystemAccountsFrom1To100(),
                systemAdminCannotDeleteSystemAccountsFrom700To750(),
                systemDeleteAdminCannotDeleteSystemAccountsFrom1To100(),
                systemDeleteAdminCannotDeleteSystemAccountsFrom700To750(),
                normalUserCannotDeleteSystemAccountsFrom1To100(),
                normalUserCannotDeleteSystemAccountsFrom700To750(),
                genesisCannotDeleteSystemFileIds(),
                systemAdminCannotDeleteSystemFileIds(),
                systemDeleteAdminCannotDeleteSystemFileIds(),
                normalUserCannotDeleteSystemFileIds(),
                genesisCannotSystemFileDeleteFileIds(),
                systemAdminCannotSystemFileDeleteFileIds(),
                systemDeleteAdminCannotSystemFileDeleteFileIds());
    }

    @HapiTest
    final DynamicTest ensureSystemAccountsHaveSomeFunds() {
        return defaultHapiSpec("EnsureSystemAccountsHaveSomeFunds")
                .given()
                .when()
                .then(
                        cryptoTransfer(movingHbar(100 * ONE_HUNDRED_HBARS)
                                        .distributing(GENESIS, SYSTEM_ADMIN, SYSTEM_DELETE_ADMIN))
                                .payingWith(GENESIS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_DELETE_ADMIN, 10 * ONE_HUNDRED_HBARS))
                                .payingWith(GENESIS));
    }

    @HapiTest
    final DynamicTest genesisCannotDeleteSystemAccountsFrom1To100() {
        return systemUserCannotDeleteSystemAccounts(1, 100, GENESIS);
    }

    @HapiTest
    final DynamicTest genesisCannotDeleteSystemAccountsFrom700To750() {
        return systemUserCannotDeleteSystemAccounts(700, 750, GENESIS);
    }

    @HapiTest
    final DynamicTest systemAdminCannotDeleteSystemAccountsFrom1To100() {
        return systemUserCannotDeleteSystemAccounts(1, 100, SYSTEM_ADMIN);
    }

    @HapiTest
    final DynamicTest systemAdminCannotDeleteSystemAccountsFrom700To750() {
        return systemUserCannotDeleteSystemAccounts(700, 750, SYSTEM_ADMIN);
    }

    @HapiTest
    final DynamicTest systemDeleteAdminCannotDeleteSystemAccountsFrom1To100() {
        return systemUserCannotDeleteSystemAccounts(1, 100, SYSTEM_DELETE_ADMIN);
    }

    @HapiTest
    final DynamicTest systemDeleteAdminCannotDeleteSystemAccountsFrom700To750() {
        return systemUserCannotDeleteSystemAccounts(700, 750, SYSTEM_DELETE_ADMIN);
    }

    @HapiTest
    final DynamicTest normalUserCannotDeleteSystemAccountsFrom1To100() {
        return normalUserCannotDeleteSystemAccounts(1, 100);
    }

    @HapiTest
    final DynamicTest normalUserCannotDeleteSystemAccountsFrom700To750() {
        return normalUserCannotDeleteSystemAccounts(700, 750);
    }

    @HapiTest
    final DynamicTest genesisCannotDeleteSystemFileIds() {
        return systemUserCannotDeleteSystemFiles(sysFileIds, GENESIS);
    }

    @HapiTest
    final DynamicTest systemAdminCannotDeleteSystemFileIds() {
        return systemUserCannotDeleteSystemFiles(sysFileIds, SYSTEM_ADMIN);
    }

    @HapiTest
    final DynamicTest systemDeleteAdminCannotDeleteSystemFileIds() {
        return systemUserCannotDeleteSystemFiles(sysFileIds, SYSTEM_DELETE_ADMIN);
    }

    @HapiTest
    final DynamicTest normalUserCannotDeleteSystemFileIds() {
        return normalUserCannotDeleteSystemFiles(sysFileIds);
    }

    @HapiTest
    final DynamicTest genesisCannotSystemFileDeleteFileIds() {
        return systemDeleteCannotDeleteSystemFiles(sysFileIds, GENESIS);
    }

    @HapiTest
    final DynamicTest systemAdminCannotSystemFileDeleteFileIds() {
        return systemDeleteCannotDeleteSystemFiles(sysFileIds, SYSTEM_ADMIN);
    }

    @HapiTest
    final DynamicTest systemDeleteAdminCannotSystemFileDeleteFileIds() {
        return systemDeleteCannotDeleteSystemFiles(sysFileIds, SYSTEM_DELETE_ADMIN);
    }

    @BddMethodIsNotATest
    final DynamicTest systemUserCannotDeleteSystemAccounts(int firstAccount, int lastAccount, String sysUser) {
        return defaultHapiSpec("systemUserCannotDeleteSystemAccounts")
                .given(
                        cryptoCreate("unluckyReceiver").balance(0L),
                        cryptoTransfer(movingHbar(100 * ONE_HUNDRED_HBARS)
                                        .distributing(GENESIS, SYSTEM_ADMIN, SYSTEM_DELETE_ADMIN))
                                .payingWith(GENESIS))
                .when()
                .then(inParallel(IntStream.rangeClosed(firstAccount, lastAccount)
                        .mapToObj(id -> cryptoDelete("0.0." + id)
                                .transfer("unluckyReceiver")
                                .payingWith(sysUser)
                                .signedBy(sysUser)
                                .hasPrecheckFrom(ENTITY_NOT_ALLOWED_TO_DELETE))
                        .toArray(HapiSpecOperation[]::new)));
    }

    @BddMethodIsNotATest
    final DynamicTest normalUserCannotDeleteSystemAccounts(int firstAccount, int lastAccount) {
        return defaultHapiSpec("normalUserCannotDeleteSystemAccounts")
                .given(newKeyNamed("normalKey"), cryptoCreate("unluckyReceiver").balance(0L))
                .when(cryptoCreate("normalUser").key("normalKey").balance(1_000_000_000L))
                .then(inParallel(IntStream.rangeClosed(firstAccount, lastAccount)
                        .mapToObj(id -> cryptoDelete("0.0." + id)
                                .transfer("unluckyReceiver")
                                .payingWith("normalUser")
                                .signedBy("normalKey")
                                .hasPrecheck(ENTITY_NOT_ALLOWED_TO_DELETE))
                        .toArray(HapiSpecOperation[]::new)));
    }

    @BddMethodIsNotATest
    final DynamicTest systemUserCannotDeleteSystemFiles(int[] fileIds, String sysUser) {
        return defaultHapiSpec("systemUserCannotDeleteSystemFiles")
                .given()
                .when()
                .then(inParallel(Arrays.stream(fileIds)
                        .mapToObj(id -> cryptoDelete("0.0." + id)
                                .payingWith(sysUser)
                                .signedBy(sysUser)
                                .hasPrecheck(ENTITY_NOT_ALLOWED_TO_DELETE))
                        .toArray(HapiSpecOperation[]::new)));
    }

    @BddMethodIsNotATest
    final DynamicTest normalUserCannotDeleteSystemFiles(int[] fileIds) {
        return defaultHapiSpec("normalUserCannotDeleteSystemFiles")
                .given(newKeyNamed("normalKey"))
                .when(cryptoCreate("normalUser").key("normalKey").balance(1_000_000_000L))
                .then(inParallel(Arrays.stream(fileIds)
                        .mapToObj(id -> fileDelete("0.0." + id)
                                .payingWith("normalUser")
                                .signedBy("normalKey")
                                .hasPrecheck(ENTITY_NOT_ALLOWED_TO_DELETE))
                        .toArray(HapiSpecOperation[]::new)));
    }

    @BddMethodIsNotATest
    final DynamicTest systemDeleteCannotDeleteSystemFiles(int[] fileIds, String sysUser) {
        return defaultHapiSpec("systemDeleteCannotDeleteSystemFiles")
                .given()
                .when()
                .then(inParallel(Arrays.stream(fileIds)
                        .mapToObj(id -> systemFileDelete("0.0." + id)
                                .payingWith(sysUser)
                                .signedBy(sysUser)
                                .hasPrecheck(ENTITY_NOT_ALLOWED_TO_DELETE))
                        .toArray(HapiSpecOperation[]::new)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
