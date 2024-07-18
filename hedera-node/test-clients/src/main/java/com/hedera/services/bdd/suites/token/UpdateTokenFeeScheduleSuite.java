/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.token;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.ADMIN_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.FEE_SCHEDULE_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.SUPPLY_KEY;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHbarFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHtsFeeInSchedule;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleToken;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("updateTokenFeeSchedule")
@HapiTestLifecycle
// To avoid race conditions; it is very fast regardless
@OrderedInIsolation
public class UpdateTokenFeeScheduleSuite {

    @Contract(contract = "UpdateTokenFeeSchedules", creationGas = 4_000_000L)
    static SpecContract updateTokenFeeSchedules;

    @FungibleToken(
            name = "fungibleToken",
            keys = {ADMIN_KEY, FEE_SCHEDULE_KEY})
    static SpecFungibleToken fungibleToken;

    @FungibleToken(name = "feeToken")
    static SpecFungibleToken feeToken;

    @NonFungibleToken(
            name = "fungibleToken",
            keys = {ADMIN_KEY, FEE_SCHEDULE_KEY, SUPPLY_KEY})
    static SpecNonFungibleToken nonFungibleToken;

    @Account(name = "account", balance = ONE_HUNDRED_HBARS)
    static SpecAccount feeCollector;

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        testLifecycle.doAdhoc(
                fungibleToken.authorizeContracts(updateTokenFeeSchedules),
                nonFungibleToken.authorizeContracts(updateTokenFeeSchedules),
                feeCollector.associateTokens(feeToken));
    }

    @Order(0)
    @HapiTest
    @DisplayName("fungible token with fixed ℏ fee")
    public Stream<DynamicTest> updateFungibleTokenWithHbarFixedFee() {
        return hapiTest(
                updateTokenFeeSchedules.call("updateFungibleFixedHbarFee", fungibleToken, 10L, feeCollector),
                fungibleToken
                        .getInfo()
                        .andAssert(info -> info.hasCustom(fixedHbarFeeInSchedule(10L, feeCollector.name()))));
    }

    @Order(1)
    @HapiTest
    @DisplayName("non fungible token with fixed ℏ fee")
    public Stream<DynamicTest> updateNonFungibleTokenWithHbarFixedFee() {
        return hapiTest(
                updateTokenFeeSchedules.call("updateNonFungibleFixedHbarFee", nonFungibleToken, 10L, feeCollector),
                nonFungibleToken
                        .getInfo()
                        .andAssert(info -> info.hasCustom(fixedHbarFeeInSchedule(10L, feeCollector.name()))));
    }

    @Order(2)
    @HapiTest
    @DisplayName("fungible token with token fixed fee")
    public Stream<DynamicTest> updateFungibleTokenWithTokenFixedFee() {
        return hapiTest(
                updateTokenFeeSchedules.call("updateFungibleFixedHtsFee", fungibleToken, feeToken, 1L, feeCollector),
                fungibleToken
                        .getInfo()
                        .andAssert(info ->
                                info.hasCustom(fixedHtsFeeInSchedule(1L, feeToken.name(), feeCollector.name()))));
    }

    @Order(3)
    @HapiTest
    @DisplayName("non fungible token with token fixed fee")
    public Stream<DynamicTest> updateNonFungibleTokenWithTokenFixedFee() {
        return hapiTest(
                updateTokenFeeSchedules.call(
                        "updateNonFungibleFixedHtsFee", nonFungibleToken, feeToken, 1L, feeCollector),
                nonFungibleToken
                        .getInfo()
                        .andAssert(info ->
                                info.hasCustom(fixedHtsFeeInSchedule(1L, feeToken.name(), feeCollector.name()))));
    }
}
