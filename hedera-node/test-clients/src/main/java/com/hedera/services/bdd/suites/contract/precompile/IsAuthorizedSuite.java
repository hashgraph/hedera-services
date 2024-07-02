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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPrivateKey;
import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getEcdsaPrivateKeyFromSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.keys.RepeatableKeyGenerator;
import com.hedera.services.bdd.suites.utils.contracts.BoolResult;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jcajce.provider.digest.Keccak;
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
        return propertyPreservingHapiSpec("isAuthorizedRawHappyPath")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED, "true"),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE).generator(new RepeatableKeyGenerator()),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, ECDSA_KEY, ONE_HUNDRED_HBARS)),
                        uploadInitCode(HRC632_CONTRACT),
                        contractCreate(HRC632_CONTRACT))
                .when(withOpContext((spec, opLog) -> {
                    final var messageHash = new Keccak.Digest256().digest("submit".getBytes());

                    // Approach 2: Generate Public key & address to make sure test suite is not giving another one.
                    final var privateKey = getEcdsaPrivateKeyFromSpec(spec, ECDSA_KEY);
                    final var addressBytes = recoverAddressFromPrivateKey(privateKey);
                    final var signedBytes = EthTxSigs.signMessage(messageHash, privateKey);

                    var call = contractCall(
                                    HRC632_CONTRACT,
                                    "isAuthorizedRawCall",
                                    asHeadlongAddress(addressBytes),
                                    messageHash,
                                    signedBytes)
                            .via("authorizeCall")
                            .gas(2_000_000L);
                    allRunFor(spec, call);
                }))
                .then(getTxnRecord("authorizeCall")
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith().contractCallResult(BoolResult.flag(true)))));
    }
}
