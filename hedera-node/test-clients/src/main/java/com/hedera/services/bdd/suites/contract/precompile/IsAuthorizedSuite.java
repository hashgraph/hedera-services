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
import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
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
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.node.app.hapi.utils.SignatureGenerator;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.keys.RepeatableKeyGenerator;
import com.hedera.services.bdd.suites.utils.contracts.BoolResult;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA384.Digest;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
public class IsAuthorizedSuite {
    private static final Logger log = LogManager.getLogger(IsAuthorizedSuite.class);
    private static final String ECDSA_KEY = "ecdsaKey";
    private static final String ED25519_KEY = "ed25519Key";
    private static final String HRC632_CONTRACT = "HRC632Contract";
    private static final String CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED =
            "contracts.systemContract.accountService.isAuthorizedRawEnabled";

    @HapiTest
    final Stream<DynamicTest> isAuthorizedEcdsaRawHappyPath() {
        return propertyPreservingHapiSpec("isAuthorizedEcdsaRawHappyPath")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED, "true"),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE).generator(new RepeatableKeyGenerator()),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, ECDSA_KEY, ONE_HUNDRED_HBARS)),
                        uploadInitCode(HRC632_CONTRACT),
                        contractCreate(HRC632_CONTRACT))
                .when(withOpContext((spec, opLog) -> {
                    final var messageHash = new Keccak.Digest256().digest("submit".getBytes());

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

    @HapiTest
    final Stream<DynamicTest> isAuthorizedEcdsaRawDifferentHash() {
        return propertyPreservingHapiSpec("isAuthorizedEcdsaRawDifferentHash")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED, "true"),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE).generator(new RepeatableKeyGenerator()),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, ECDSA_KEY, ONE_HUNDRED_HBARS)),
                        uploadInitCode(HRC632_CONTRACT),
                        contractCreate(HRC632_CONTRACT))
                .when(withOpContext((spec, opLog) -> {
                    final var messageHash = new Keccak.Digest256().digest("submit".getBytes());
                    final var differentHash = new Keccak.Digest256().digest("submit1".getBytes());

                    final var privateKey = getEcdsaPrivateKeyFromSpec(spec, ECDSA_KEY);
                    final var addressBytes = recoverAddressFromPrivateKey(privateKey);
                    final var signedBytes = EthTxSigs.signMessage(messageHash, privateKey);

                    var call = contractCall(
                                    HRC632_CONTRACT,
                                    "isAuthorizedRawCall",
                                    asHeadlongAddress(addressBytes),
                                    differentHash,
                                    signedBytes)
                            .via("authorizeCall")
                            .gas(2_000_000L);
                    allRunFor(spec, call);
                }))
                .then(getTxnRecord("authorizeCall")
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith().contractCallResult(BoolResult.flag(false)))));
    }

    @HapiTest
    final Stream<DynamicTest> isAuthorizedEdRawHappyPath() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("isAuthorizedEdRawHappyPath")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED, "true"),
                        newKeyNamed(ED25519_KEY).shape(ED25519).generator(new RepeatableKeyGenerator()),
                        cryptoCreate(ACCOUNT)
                                .balance(ONE_MILLION_HBARS)
                                .key(ED25519_KEY)
                                .exposingCreatedIdTo(id -> accountNum.set(idAsHeadlongAddress(id))),
                        uploadInitCode(HRC632_CONTRACT),
                        contractCreate(HRC632_CONTRACT))
                .when(withOpContext((spec, opLog) -> {
                    final var messageHash = new Digest().digest("submit".getBytes());

                    final var edKey = spec.registry().getKey(ED25519_KEY);
                    final var privateKey = spec.keys()
                            .getEd25519PrivateKey(com.swirlds.common.utility.CommonUtils.hex(edKey.toByteArray())
                                    .substring(4));
                    final var signedBytes = SignatureGenerator.signBytes(messageHash, privateKey);

                    var call = contractCall(
                                    HRC632_CONTRACT, "isAuthorizedRawCall", accountNum.get(), messageHash, signedBytes)
                            .via("authorizeCall")
                            .gas(2_000_000L);
                    allRunFor(spec, call);
                }))
                .then(getTxnRecord("authorizeCall")
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith().contractCallResult(BoolResult.flag(true)))));
    }

    @HapiTest
    final Stream<DynamicTest> isAuthorizedEdRawDifferentHash() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("isAuthorizedEdRawDifferentHash")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED, "true"),
                        newKeyNamed(ED25519_KEY).shape(ED25519).generator(new RepeatableKeyGenerator()),
                        cryptoCreate(ACCOUNT)
                                .balance(ONE_MILLION_HBARS)
                                .key(ED25519_KEY)
                                .exposingCreatedIdTo(id -> accountNum.set(idAsHeadlongAddress(id))),
                        uploadInitCode(HRC632_CONTRACT),
                        contractCreate(HRC632_CONTRACT))
                .when(withOpContext((spec, opLog) -> {
                    final var messageHash = new Digest().digest("submit".getBytes());
                    final var differentHash = new Digest().digest("submit1".getBytes());

                    final var edKey = spec.registry().getKey(ED25519_KEY);
                    final var privateKey = spec.keys()
                            .getEd25519PrivateKey(com.swirlds.common.utility.CommonUtils.hex(edKey.toByteArray())
                                    .substring(4));
                    final var signedBytes = SignatureGenerator.signBytes(messageHash, privateKey);

                    var call = contractCall(
                                    HRC632_CONTRACT,
                                    "isAuthorizedRawCall",
                                    accountNum.get(),
                                    differentHash,
                                    signedBytes)
                            .via("authorizeCall")
                            .gas(2_000_000L);
                    allRunFor(spec, call);
                }))
                .then(getTxnRecord("authorizeCall")
                        .hasPriority(recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith().contractCallResult(BoolResult.flag(false)))));
    }
}
