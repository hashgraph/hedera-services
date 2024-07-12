/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.leaky;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContractIdWithEvmAddress;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONTRACT_CALL_RESULTS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_ETHEREUM_DATA;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.RELAYER;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SOURCE_KEY;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ACCOUNT;
import static com.hedera.services.bdd.suites.leaky.LeakyContractTestsSuite.RECEIVER;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.swirlds.common.utility.CommonUtils.unhex;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData.EthTransactionType;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@SuppressWarnings("java:S5960")
public class LeakyEthereumTestsSuite {
    private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";

    private static final String EVM_VERSION_PROPERTY = "contracts.evm.version";
    private static final String ALLOW_CALLS_TO_NON_CONTRACT_ACCOUNTS = "contracts.evm.allowCallsToNonContractAccounts";
    private static final String DYNAMIC_EVM_PROPERTY = "contracts.evm.version.dynamic";
    private static final String EVM_VERSION_050 = "v0.50";

    // test unprotected legacy ethereum transactions before EIP155
    // this tests the behaviour when the `v` field is 27 or 28
    // in this case the passed chainId = 0 so ETX is before EIP155
    // and so `v` is calculated -> v = {0,1} + 27
    // source: https://eips.ethereum.org/EIPS/eip-155
    @LeakyHapiTest(overrides = {"contracts.chainId"})
    final Stream<DynamicTest> legacyUnprotectedEtxBeforeEIP155() {
        final String DEPOSIT = "deposit";
        final long depositAmount = 20_000L;
        final Integer chainId = 0;

        return hapiTest(
                overriding("contracts.chainId", "" + chainId),
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                        .via("autoAccount"),
                getTxnRecord("autoAccount").andAllChildRecords(),
                uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD),
                ethereumCall(PAY_RECEIVABLE_CONTRACT, DEPOSIT, BigInteger.valueOf(depositAmount))
                        .type(EthTransactionType.LEGACY_ETHEREUM)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .via("legacyBeforeEIP155")
                        .nonce(0)
                        .chainId(chainId)
                        .gasPrice(50L)
                        .maxPriorityGas(2L)
                        .gasLimit(1_000_000L)
                        .sending(depositAmount)
                        .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        getTxnRecord("legacyBeforeEIP155")
                                .logged()
                                .hasPriority(recordWith().status(SUCCESS)))));
    }

    // test legacy ethereum transactions after EIP155
    // this tests the behaviour when the `v` field is 37 or 38
    // in this case the passed chainId = 1 so ETX is after EIP155
    // and so `v` is calculated -> v = {0,1} + CHAIN_ID * 2 + 35
    // source: https://eips.ethereum.org/EIPS/eip-155
    @LeakyHapiTest
    final Stream<DynamicTest> legacyEtxAfterEIP155() {
        final String DEPOSIT = "deposit";
        final long depositAmount = 20_000L;
        final Integer chainId = 1;

        return propertyPreservingHapiSpec(
                        "legacyEtxAfterEIP155", NONDETERMINISTIC_ETHEREUM_DATA, NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .preserving("contracts.chainId")
                .given(
                        overriding("contracts.chainId", "" + chainId),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        overriding("contracts.chainId", "" + chainId),
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, DEPOSIT, BigInteger.valueOf(depositAmount))
                                .type(EthTransactionType.LEGACY_ETHEREUM)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .via("legacyAfterEIP155")
                                .nonce(0)
                                .chainId(chainId)
                                .gasPrice(50L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .sending(depositAmount)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        getTxnRecord("legacyAfterEIP155")
                                .logged()
                                .hasPriority(recordWith().status(SUCCESS)))));
    }

    @LeakyHapiTest
    final Stream<DynamicTest> callHtsSystemContractTest() {
        final var callHtsSystemContractTxn = "callHtsSystemContractTxn";
        final var function = getABIFor(FUNCTION, "transferToken", "IHederaTokenService");
        final var HTS_SYSTEM_CONTRACT = "hts";
        final var HTS_SYSTEM_CONTRACT_ADDRESS = "0000000000000000000000000000000000000167";

        return propertyPreservingHapiSpec("callHtsSystemContractTest")
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_050),
                        overriding(ALLOW_CALLS_TO_NON_CONTRACT_ACCOUNTS, "true"),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoCreate(ACCOUNT).balance(6 * ONE_MILLION_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(RECEIVER),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(1_000),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(withOpContext((spec, opLog) -> {
                    final var receiver1 =
                            asHeadlongAddress(asAddress(spec.registry().getAccountID(RECEIVER)));
                    final var sender =
                            asHeadlongAddress(asAddress(spec.registry().getAccountID(ACCOUNT)));

                    spec.registry()
                            .saveContractId(
                                    HTS_SYSTEM_CONTRACT,
                                    asContractIdWithEvmAddress(
                                            ByteString.copyFrom(unhex(HTS_SYSTEM_CONTRACT_ADDRESS))));
                    allRunFor(
                            spec,
                            ethereumCallWithFunctionAbi(
                                            false,
                                            HTS_SYSTEM_CONTRACT,
                                            function,
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(VANILLA_TOKEN))),
                                            sender,
                                            receiver1,
                                            1L)
                                    .payingWith(RELAYER)
                                    .type(EthTransactionType.EIP1559)
                                    .via(callHtsSystemContractTxn)
                                    .hasKnownStatus(SUCCESS));
                }))
                .then();
    }
}
