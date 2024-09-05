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

package com.hedera.services.bdd.suites.issues;

import static com.hedera.services.bdd.junit.ContextRequirement.PERMISSION_OVERRIDES;
import static com.hedera.services.bdd.junit.ContextRequirement.PROPERTY_OVERRIDES;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.suites.HapiSuite.ADDRESS_BOOK_CONTROL;
import static com.hedera.services.bdd.suites.HapiSuite.API_PERMISSIONS;
import static com.hedera.services.bdd.suites.HapiSuite.APP_PROPERTIES;
import static com.hedera.services.bdd.suites.HapiSuite.EXCHANGE_RATE_CONTROL;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;

import com.hedera.services.bdd.junit.LeakyHapiTest;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class Issue2143Spec {
    @LeakyHapiTest(requirement = {PERMISSION_OVERRIDES})
    final Stream<DynamicTest> account55ControlCanUpdatePermissions() {
        return defaultHapiSpec("Account55ControlCanUpdatePropertiesAndPermissions")
                .given(cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000L)))
                .when(fileUpdate(API_PERMISSIONS)
                        .overridingProps(Map.of("createFile", "0-100"))
                        .payingWith(ADDRESS_BOOK_CONTROL))
                .then(fileUpdate(API_PERMISSIONS)
                        .overridingProps(Map.of("createFile", "0-*"))
                        .payingWith(ADDRESS_BOOK_CONTROL));
    }

    @LeakyHapiTest(requirement = {PERMISSION_OVERRIDES, PROPERTY_OVERRIDES})
    final Stream<DynamicTest> account57ControlCanUpdatePropertiesAndPermissions() {
        return defaultHapiSpec("Account57ControlCanUpdatePropertiesAndPermissions")
                .given(cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, 1_000_000_000L)))
                .when(
                        fileUpdate(APP_PROPERTIES).overridingProps(Map.of()).payingWith(EXCHANGE_RATE_CONTROL),
                        fileUpdate(API_PERMISSIONS)
                                .overridingProps(Map.of("createFile", "0-100"))
                                .payingWith(EXCHANGE_RATE_CONTROL))
                .then(
                        fileUpdate(APP_PROPERTIES).overridingProps(Map.of()).payingWith(EXCHANGE_RATE_CONTROL),
                        fileUpdate(API_PERMISSIONS)
                                .overridingProps(Map.of("createFile", "0-*"))
                                .payingWith(EXCHANGE_RATE_CONTROL));
    }
}
