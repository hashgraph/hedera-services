/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
public class IsAuthorizedSuite {
    private static final Logger log = LogManager.getLogger(IsAuthorizedSuite.class);
    private static final String ECDSA_KEY = "ecdsaKey";
    private static final String HRC632_CONTRACT = "HRC632Contract";
    private static final String CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED =
            "contracts.systemContract.accountService.isAuthorizedRawEnabled";

    @HapiTest
    final Stream<DynamicTest> isAuthorizedRawHappyPath() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();
        final AtomicReference<Address> spenderNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("hrc632AllowanceFromContract")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED, "true"),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, ECDSA_KEY, ONE_HUNDRED_HBARS)),
                        uploadInitCode(HRC632_CONTRACT),
                        contractCreate(HRC632_CONTRACT))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                }))
                .then();
    }
}
