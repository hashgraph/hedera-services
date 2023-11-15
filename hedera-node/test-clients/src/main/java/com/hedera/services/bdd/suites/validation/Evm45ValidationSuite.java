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

package com.hedera.services.bdd.suites.validation;

import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.suites.contract.Utils.headlongFromHexed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.List;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// @HapiTestSuite
public class Evm45ValidationSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(Evm45ValidationSuite.class);

    public static void main(String... args) {
        new Evm45ValidationSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                directCallToNonExistingLongZeroAddress(),
                directCallToNonExistingEvmAddress(),
                internalCallToNonExistingLongZeroAddress(),
                //                internalCallToExistingLongZeroAddress(),
                internalCallToNonExistingEvmAddress(),
                //                internalCallToExistingEvmAddress(),

                internalTransferToNonExistingLongZeroAddress(),
                //                internalTransferToExistingLongZeroAddress(),
                internalTransferToNonExistingEvmAddress(),
                //                internalTransferToExistingEvmAddress(),

                internalCallWithValueToNonExistingLongZeroAddress(),
                //                internalCallWithValueToExistingLongZeroAddress(),
                internalCallWithValueToNonExistingEvmAddress()
                //                internalCallWithValueToExistingEvmAddress()
                );
    }

    private static String shuffle(String inputString) {
        Random random = new Random();
        char a[] = inputString.toCharArray();
        for (int i = 0; i < a.length; i++) {
            int j = random.nextInt(a.length);
            char temp = a[i];
            a[i] = a[j];
            a[j] = temp;
        }

        return new String(a);
    }

    private HapiSpec directCallToNonExistingLongZeroAddress() {
        final String SPONSOR = "autoCreateSponsor";

        return defaultHapiSpec("directCallToNonExistingLongZeroAddress")
                .given(cryptoCreate(SPONSOR).balance(10L * ONE_HBAR))
                .when()
                .then(cryptoTransfer(tinyBarsFromTo(SPONSOR, "0x000000000000000000000000000000000000160c", ONE_HBAR))
                        .hasKnownStatusFrom(SUCCESS));
    }

    private HapiSpec directCallToNonExistingEvmAddress() {
        final String SPONSOR = "autoCreateSponsor";
        return defaultHapiSpec("directCallToNonExistingEvmAddress")
                .given(cryptoCreate(SPONSOR).balance(10L * ONE_HBAR))
                .when()
                .then(cryptoTransfer(tinyBarsFromTo(
                                SPONSOR, "0x" + shuffle("0B759e491B554D8b3fD3F2fe8Be4035E289b489C"), ONE_HBAR))
                        .hasKnownStatusFrom(SUCCESS));
    }

    private HapiSpec internalCallToNonExistingLongZeroAddress() {
        final var contract = "InternalCaller";

        return defaultHapiSpec("internalCallToNonExistingLongZeroAddress")
                .given(uploadInitCode(contract), contractCreate(contract).balance(ONE_HBAR))
                .when()
                .then(contractCall(
                                contract,
                                "callContract",
                                idAsHeadlongAddress(AccountID.newBuilder()
                                        .setAccountNum(5644L)
                                        .build()))
                        .hasKnownStatus(SUCCESS));
    }

    private HapiSpec internalCallToNonExistingEvmAddress() {
        final var contract = "InternalCaller";

        return defaultHapiSpec("internalCallToNonExistingEvmAddress")
                .given(uploadInitCode(contract), contractCreate(contract).balance(ONE_HBAR))
                .when()
                .then(contractCall(
                                contract,
                                "callContract",
                                headlongFromHexed(shuffle("0B759e491B554D8b3fD3F2fe8Be4035E289b489C")))
                        .hasKnownStatus(SUCCESS));
    }

    private HapiSpec internalTransferToNonExistingLongZeroAddress() {
        final var contract = "InternalCaller";

        return defaultHapiSpec("internalTransferToNonExistingLongZeroAddress")
                .given(uploadInitCode(contract), contractCreate(contract).balance(ONE_HBAR))
                .when()
                .then(contractCall(
                                contract,
                                "callTransfer",
                                idAsHeadlongAddress(AccountID.newBuilder()
                                        .setAccountNum(5644L)
                                        .build()))
                        .hasKnownStatus(SUCCESS));
    }

    private HapiSpec internalTransferToNonExistingEvmAddress() {
        final var contract = "InternalCaller";

        return defaultHapiSpec("internalTransferToNonExistingEvmAddress")
                .given(uploadInitCode(contract), contractCreate(contract).balance(ONE_HBAR))
                .when()
                .then(contractCall(
                                contract,
                                "callTransfer",
                                headlongFromHexed(shuffle("0B759e491B554D8b3fD3F2fe8Be4035E289b489C")))
                        .hasKnownStatus(SUCCESS));
    }

    private HapiSpec internalCallWithValueToNonExistingLongZeroAddress() {
        final var contract = "InternalCaller";

        return defaultHapiSpec("internalCallWithValueToNonExistingLongZeroAddress")
                .given(uploadInitCode(contract), contractCreate(contract).balance(ONE_HBAR))
                .when()
                .then(contractCall(
                                contract,
                                "callWithValue",
                                idAsHeadlongAddress(AccountID.newBuilder()
                                        .setAccountNum(5644L)
                                        .build()))
                        .hasKnownStatus(SUCCESS));
    }

    private HapiSpec internalCallWithValueToNonExistingEvmAddress() {
        final var contract = "InternalCaller";

        return defaultHapiSpec("internalCallWithValueToNonExistingEvmAddress")
                .given(uploadInitCode(contract), contractCreate(contract).balance(ONE_HBAR))
                .when()
                .then(contractCall(
                                contract,
                                "callWithValue",
                                headlongFromHexed(shuffle("0B759e491B554D8b3fD3F2fe8Be4035E289b489C")))
                        .hasKnownStatus(SUCCESS));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
