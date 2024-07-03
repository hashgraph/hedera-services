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
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ANY;
import static com.hedera.services.bdd.spec.keys.SigControl.ED25519_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getEcdsaPrivateKeyFromSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE_TYPE_MISMATCHING_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.node.app.hapi.utils.SignatureGenerator;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.RepeatableKeyGenerator;
import com.hedera.services.bdd.suites.utils.contracts.BoolResult;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA384.Digest;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@SuppressWarnings("java:S1192") // "String literals should not be duplicated" - would impair readability here
public class IsAuthorizedSuite {

    public static final String ACCOUNT = "account";
    public static final String ANOTHER_ACCOUNT = "anotherAccount";

    private static final String ECDSA_KEY = "ecdsaKey";
    private static final String ED25519_KEY = "ed25519Key";
    private static final String THRESHOLD_KEY = "ThreshKey";
    private static final String KEY_LIST = "ListKeys";
    private static final KeyShape THRESHOLD_KEY_SHAPE = KeyShape.threshOf(1, ED25519, SIMPLE);
    private static final KeyShape KEY_LIST_SHAPE = KeyShape.listOf(ED25519, SIMPLE);

    private static final String HRC632_CONTRACT = "HRC632Contract";
    private static final String CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED =
            "contracts.systemContract.accountService.isAuthorizedRawEnabled";

    @HapiTest
    final Stream<DynamicTest> isAuthorizedRawECDSAHappyPath() {
        return propertyPreservingHapiSpec("isAuthorizedRawECDSAHappyPath")
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
    final Stream<DynamicTest> isAuthorizedRawECDSAInsufficientGas() {
        return propertyPreservingHapiSpec("isAuthorizedRawECDSAInsufficientGas")
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
                            .hasPrecheck(INSUFFICIENT_GAS)
                            .gas(1L);
                    allRunFor(spec, call);
                }))
                .then();
    }

    @HapiTest
    final Stream<DynamicTest> isAuthorizedRawECDSADifferentHash() {
        return propertyPreservingHapiSpec("isAuthorizedRawECDSADifferentHash")
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
    final Stream<DynamicTest> isAuthorizedRawECDSAInvalidHash() {
        return propertyPreservingHapiSpec("isAuthorizedRawECDSAInvalidHash")
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
                    var signature = EthTxSigs.signMessage(messageHash, privateKey);
                    signature[signature.length - 1] = (byte) 2;

                    var call = contractCall(
                                    HRC632_CONTRACT,
                                    "isAuthorizedRawCall",
                                    asHeadlongAddress(addressBytes),
                                    messageHash,
                                    signature)
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
    final Stream<DynamicTest> isAuthorizedRawECDSAInvalidSignatureLength() {
        return propertyPreservingHapiSpec("isAuthorizedRawECDSAInvalidSignatureLength")
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
                    final byte[] invalidSignature = new byte[64];

                    var call = contractCall(
                                    HRC632_CONTRACT,
                                    "isAuthorizedRawCall",
                                    asHeadlongAddress(addressBytes),
                                    messageHash,
                                    invalidSignature)
                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                            .via("authorizeCall")
                            .gas(2_000_000L);
                    allRunFor(spec, call);
                }))
                .then(childRecordsCheck(
                        "authorizeCall",
                        CONTRACT_REVERT_EXECUTED,
                        recordWith().status(INVALID_SIGNATURE_TYPE_MISMATCHING_KEY)));
    }

    @HapiTest
    final Stream<DynamicTest> isAuthorizedRawEDHappyPath() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("isAuthorizedRawEDHappyPath")
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
    final Stream<DynamicTest> isAuthorizedRawEDDifferentHash() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("isAuthorizedRawEDInsufficientGas")
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

    @HapiTest
    final Stream<DynamicTest> isAuthorizedRawEDInsufficientGas() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("isAuthorizedRawEDInsufficientGas")
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
                            .hasPrecheck(INSUFFICIENT_GAS)
                            .gas(1L);
                    allRunFor(spec, call);
                }))
                .then();
    }

    @HapiTest
    final Stream<DynamicTest> isAuthorizedRawEDInvalidSignatureAddressPairsFails() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("isAuthorizedRawEDInvalidMissMatchSignatureLength")
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
                    final var messageHash = new Keccak.Digest256().digest("submit".getBytes());

                    var callECSigWithLongZero = contractCall(
                                    HRC632_CONTRACT, "isAuthorizedRawCall", accountNum.get(), messageHash, new byte[65])
                            .via("authorizeCallECWithLongZero")
                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                            .gas(2_000_000L);
                    var callECWithInvalidSignature = contractCall(
                                    HRC632_CONTRACT, "isAuthorizedRawCall", accountNum.get(), messageHash, new byte[63])
                            .via("authorizeCallECWithInvalidSignature")
                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                            .gas(2_000_000L);
                    allRunFor(spec, callECSigWithLongZero, callECWithInvalidSignature);
                }))
                .then(
                        childRecordsCheck(
                                "authorizeCallECWithLongZero",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ACCOUNT_ID)),
                        childRecordsCheck(
                                "authorizeCallECWithInvalidSignature",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(FAIL_INVALID)));
    }

    @HapiTest
    final Stream<DynamicTest> isAuthorizedRawAliasWithECFails() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();

        return propertyPreservingHapiSpec("isAuthorizedRawAliasWithECFails")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED, "true"),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE).generator(new RepeatableKeyGenerator()),
                        cryptoCreate(ACCOUNT)
                                .balance(ONE_MILLION_HBARS)
                                .key(ECDSA_KEY)
                                .exposingCreatedIdTo(id -> accountNum.set(idAsHeadlongAddress(id))),
                        uploadInitCode(HRC632_CONTRACT),
                        contractCreate(HRC632_CONTRACT))
                .when(withOpContext((spec, opLog) -> {
                    final var messageHash = new Digest().digest("submit".getBytes());
                    final var privateKey = getEcdsaPrivateKeyFromSpec(spec, ECDSA_KEY);
                    final var signedBytes = EthTxSigs.signMessage(messageHash, privateKey);

                    var call = contractCall(
                                    HRC632_CONTRACT, "isAuthorizedRawCall", accountNum.get(), messageHash, signedBytes)
                            .via("authorizeCall")
                            .gas(2_000_000L)
                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED);
                    allRunFor(spec, call);
                }))
                .then(childRecordsCheck(
                        "authorizeCall", CONTRACT_REVERT_EXECUTED, recordWith().status(FAIL_INVALID)));
    }

    @HapiTest
    final Stream<DynamicTest> isAuthorizedRawEDWithComplexKeysFails() {
        final AtomicReference<Address> accountNum = new AtomicReference<>();
        final AtomicReference<Address> accountAnotherNum = new AtomicReference<>();
        return propertyPreservingHapiSpec("isAuthorizedRawEDWithComplexKeysFails")
                .preserving(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED)
                .given(
                        overriding(CONTRACTS_SYSTEM_CONTRACT_ACCOUNT_SERVICE_IS_AUTHORIZED_ENABLED, "true"),
                        newKeyNamed(THRESHOLD_KEY).shape(THRESHOLD_KEY_SHAPE.signedWith(sigs(ED25519_ON, ANY))),
                        newKeyNamed(KEY_LIST).shape(KEY_LIST_SHAPE.signedWith(sigs(ED25519_ON, ANY))),
                        newKeyNamed(ED25519_KEY).shape(ED25519).generator(new RepeatableKeyGenerator()),
                        cryptoCreate(ACCOUNT)
                                .balance(ONE_MILLION_HBARS)
                                .key(THRESHOLD_KEY)
                                .exposingCreatedIdTo(id -> accountNum.set(idAsHeadlongAddress(id))),
                        cryptoCreate(ANOTHER_ACCOUNT)
                                .balance(ONE_MILLION_HBARS)
                                .key(KEY_LIST)
                                .exposingCreatedIdTo(id -> accountAnotherNum.set(idAsHeadlongAddress(id))),
                        uploadInitCode(HRC632_CONTRACT),
                        contractCreate(HRC632_CONTRACT))
                .when(withOpContext((spec, opLog) -> {
                    final var messageHash = new Digest().digest("submit".getBytes());
                    final var edKey = spec.registry().getKey(ED25519_KEY);
                    final var privateKey = spec.keys()
                            .getEd25519PrivateKey(com.swirlds.common.utility.CommonUtils.hex(edKey.toByteArray())
                                    .substring(4));
                    final var signedBytes = SignatureGenerator.signBytes(messageHash, privateKey);

                    final var callWithThreshold = contractCall(
                                    HRC632_CONTRACT, "isAuthorizedRawCall", accountNum.get(), messageHash, signedBytes)
                            .via("authorizeCallWithThreshold")
                            .gas(2_000_000L)
                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED);

                    final var callWithKeyList = contractCall(
                                    HRC632_CONTRACT,
                                    "isAuthorizedRawCall",
                                    accountAnotherNum.get(),
                                    messageHash,
                                    signedBytes)
                            .via("authorizeCallWithKeyList")
                            .gas(2_000_000L)
                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED);
                    allRunFor(spec, callWithThreshold, callWithKeyList);
                }))
                .then(
                        childRecordsCheck(
                                "authorizeCallWithThreshold",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(FAIL_INVALID)),
                        childRecordsCheck(
                                "authorizeCallWithKeyList",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(FAIL_INVALID)));
    }
}
