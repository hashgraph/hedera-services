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

package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.FIVE_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SOURCE_KEY;

import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@HapiTestLifecycle
@OrderedInIsolation
@Tag(SMART_CONTRACT)
public class SmartContractServiceFeesTest {

    @Contract(contract = "SmartContractsFees")
    static SpecContract contract;

    @Account(tinybarBalance = ONE_HUNDRED_HBARS)
    static SpecAccount civilian;

    @BeforeAll
    public static void setup(final TestLifecycle lifecycle) {
        lifecycle.doAdhoc(contract.getInfo(), civilian.getInfo());
    }

    @HapiTest
    @DisplayName("Create a smart contract and assure proper fee charged")
    @Order(0)
    final Stream<DynamicTest> contractCreateBaseUSDFee() {
        final var creation = "creation";

        return hapiTest(
                uploadInitCode("EmptyOne"),
                contractCreate("EmptyOne")
                        .gas(500_000L)
                        .payingWith(civilian.name())
                        .via(creation),
                validateChargedUsdWithin(creation, 1, 50));
    }

    @HapiTest
    @DisplayName("Call a smart contract and assure proper fee charged")
    @Order(1)
    final Stream<DynamicTest> contractCallBaseUSDFee() {
        final var creation = "creation";

        return hapiTest(
                contract.call("contractCall1Byte", new byte[] {0}).gas(500_000L).via(creation),
                validateChargedUsdWithin(creation, 0.05, 50));
    }

    @HapiTest
    @DisplayName("Do an ethereum transaction and assure proper fee charged")
    @Order(2)
    final Stream<DynamicTest> ethereumTransactionBaseUSDFee(
            @Account(tinybarBalance = ONE_HUNDRED_HBARS) final SpecAccount receiver) {
        return hapiTest(
                receiver.getInfo(),
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                        .via("autoAccount"),
                ethereumCryptoTransfer(receiver.name(), FIVE_HBARS)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .payingWith(civilian.name())
                        .via("creation"),
                validateChargedUsdWithin("creation", 0.006, 50));
    }

    @HapiTest
    @DisplayName("Call a local smart contract local and assure proper fee charged")
    @Order(3)
    final Stream<DynamicTest> contractLocalCallBaseUSDFee() {

        return hapiTest(withOpContext((spec, opLog) -> allRunFor(
                spec,
                contractCallLocal(contract.name(), "contractLocalCallGet1Byte")
                        .gas(500_000L)
                        .payingWith(civilian.name())
                        .via("creation"),
                validateChargedUsdWithin("creation", 0.0001, 50))));
    }
}
