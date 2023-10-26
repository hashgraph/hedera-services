/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.ethereum;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccountString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCryptoTransferToExplicit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCodeWithConstructorArguments;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.CONSTRUCTOR;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class HelloWorldEthereumSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(HelloWorldEthereumSuite.class);
    private static final long depositAmount = 20_000L;

    private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
    private static final String TOKEN_CREATE_CONTRACT = "TokenCreateContract";
    private static final String OC_TOKEN_CONTRACT = "OcToken";
    private static final String CALLDATA_SIZE_CONTRACT = "CalldataSize";
    private static final String DEPOSIT = "deposit";

    public static void main(String... args) {
        new HelloWorldEthereumSuite().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(ethereumCalls(), ethereumCreates());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    List<HapiSpec> ethereumCalls() {
        return List.of(
                relayerFeeAsExpectedIfSenderCoversGas(),
                depositSuccess(),
                badRelayClient(),
                topLevelBurnToZeroAddressReverts(),
                topLevelLazyCreateOfMirrorAddressReverts(),
                topLevelSendToReceiverSigRequiredAccountReverts(),
                internalBurnToZeroAddressReverts(),
                ethereumCallWithCalldataBiggerThanMaxSucceeds(),
                createWithSelfDestructInConstructorHasSaneRecord());
    }

    List<HapiSpec> ethereumCreates() {
        return List.of(smallContractCreate(), contractCreateWithConstructorArgs(), bigContractCreate());
    }

    HapiSpec badRelayClient() {
        final var adminKey = "adminKey";
        final var exploitToken = "exploitToken";
        final var exploitContract = "BadRelayClient";
        final var maliciousTxn = "theft";
        final var maliciousEOA = "maliciousEOA";
        final var maliciousAutoCreation = "maliciousAutoCreation";
        final var maliciousStartBalance = ONE_HUNDRED_HBARS;
        final AtomicReference<String> maliciousEOAId = new AtomicReference<>();
        final AtomicReference<String> relayerEvmAddress = new AtomicReference<>();
        final AtomicReference<String> exploitTokenEvmAddress = new AtomicReference<>();

        return defaultHapiSpec("badRelayClient")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(maliciousEOA).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER)
                                .balance(10 * ONE_MILLION_HBARS)
                                .exposingCreatedIdTo(
                                        id -> relayerEvmAddress.set(asHexedSolidityAddress(0, 0, id.getAccountNum()))),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, maliciousEOA, maliciousStartBalance))
                                .via(maliciousAutoCreation),
                        withOpContext((spec, opLog) -> {
                            final var lookup = getTxnRecord(maliciousAutoCreation)
                                    .andAllChildRecords()
                                    .logged();
                            allRunFor(spec, lookup);
                            final var childCreation = lookup.getChildRecord(0);
                            maliciousEOAId.set(
                                    asAccountString(childCreation.getReceipt().getAccountID()));
                        }),
                        uploadInitCode(exploitContract),
                        contractCreate(exploitContract).adminKey(adminKey),
                        sourcing(() -> tokenCreate(exploitToken)
                                .treasury(maliciousEOAId.get())
                                .symbol("IDYM")
                                .symbol("I DRINK YOUR MILKSHAKE")
                                .initialSupply(Long.MAX_VALUE)
                                .decimals(0)
                                .withCustom(fixedHbarFee(ONE_MILLION_HBARS, maliciousEOAId.get()))
                                .signedBy(DEFAULT_PAYER, maliciousEOA)
                                .exposingCreatedIdTo(id -> exploitTokenEvmAddress.set(
                                        asHexedSolidityAddress(0, 0, asToken(id).getTokenNum())))))
                .when(sourcing(() -> ethereumCall(
                                exploitContract,
                                "stealFrom",
                                asHeadlongAddress(relayerEvmAddress.get()),
                                asHeadlongAddress(exploitTokenEvmAddress.get()))
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(maliciousEOA)
                        .payingWith(RELAYER)
                        .via(maliciousTxn)
                        .nonce(0)
                        .gasLimit(1_000_000L)
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        getTxnRecord(maliciousTxn).andAllChildRecords().logged(),
                        childRecordsCheck(
                                maliciousTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        sourcing(() -> getAccountBalance(maliciousEOAId.get())
                                .hasTinyBars(spec -> amount -> (amount > maliciousStartBalance)
                                        ? Optional.of("Malicious" + " EOA balance" + " increased")
                                        : Optional.empty())),
                        getAliasedAccountInfo(maliciousEOA).has(accountWith().nonce(1L)));
    }

    HapiSpec relayerFeeAsExpectedIfSenderCoversGas() {
        final var canonicalTxn = "canonical";

        return defaultHapiSpec("relayerFeeAsExpectedIfSenderCoversGas")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        // The cost to the relayer to transmit a simple call with sufficient gas
                        // allowance is ≈ $0.0001
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, DEPOSIT, BigInteger.valueOf(depositAmount))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .via(canonicalTxn)
                                .nonce(0)
                                .gasPrice(100L)
                                .maxFeePerGas(100L)
                                .maxPriorityGas(2_000_000L)
                                .gasLimit(1_000_000L)
                                .sending(depositAmount))
                .then(getAccountInfo(RELAYER)
                        .has(accountWith().expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.0001, 0.5))
                        .logged());
    }

    @HapiTest
    HapiSpec depositSuccess() {
        return defaultHapiSpec("depositSuccess")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        // EIP1559 Ethereum Calls Work
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, DEPOSIT, BigInteger.valueOf(depositAmount))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .via("payTxn")
                                .nonce(0)
                                .maxFeePerGas(50L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .sending(depositAmount)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                        // Legacy Ethereum Calls Work
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, DEPOSIT, BigInteger.valueOf(depositAmount))
                                .type(EthTxData.EthTransactionType.LEGACY_ETHEREUM)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .via("payTxn")
                                .nonce(1)
                                .gasPrice(50L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .sending(depositAmount)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                        // Ethereum Call with FileID callData works
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, DEPOSIT, BigInteger.valueOf(depositAmount))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .via("payTxn")
                                .nonce(2)
                                .maxFeePerGas(50L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .sending(depositAmount)
                                .createCallDataFile()
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)))
                .then(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                getTxnRecord("payTxn")
                                        .logged()
                                        .hasPriority(recordWith()
                                                .contractCallResult(resultWith()
                                                        .logs(inOrder())
                                                        .senderId(spec.registry()
                                                                .getAccountID(spec.registry()
                                                                        .aliasIdFor(SECP_256K1_SOURCE_KEY)
                                                                        .getAlias()
                                                                        .toStringUtf8())))
                                                .ethereumHash(ByteString.copyFrom(
                                                        spec.registry().getBytes(ETH_HASH_KEY)))))),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(3L)));
    }

    @HapiTest
    HapiSpec ethereumCallWithCalldataBiggerThanMaxSucceeds() {
        final var largerThanMaxCalldata = new byte[MAX_CALL_DATA_SIZE + 1];
        return defaultHapiSpec("ethereumCallWithCalldataBiggerThanMaxSucceeds")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(CALLDATA_SIZE_CONTRACT),
                        contractCreate(CALLDATA_SIZE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        ethereumCall(CALLDATA_SIZE_CONTRACT, "callme", largerThanMaxCalldata)
                                .via("payTxn")
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)))
                .then(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                getTxnRecord("payTxn")
                                        .logged()
                                        .hasPriority(recordWith()
                                                .contractCallResult(resultWith()
                                                        .logs(inOrder(logWith()
                                                                .longAtBytes(largerThanMaxCalldata.length, 24)
                                                                .withTopicsInOrder(
                                                                        List.of(eventSignatureOf("Info(uint256)")))))
                                                        .senderId(spec.registry()
                                                                .getAccountID(spec.registry()
                                                                        .aliasIdFor(SECP_256K1_SOURCE_KEY)
                                                                        .getAlias()
                                                                        .toStringUtf8())))
                                                .ethereumHash(ByteString.copyFrom(
                                                        spec.registry().getBytes(ETH_HASH_KEY)))))),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)));
    }

    HapiSpec createWithSelfDestructInConstructorHasSaneRecord() {
        final var txn = "txn";
        final var selfDestructingContract = "FactorySelfDestructConstructor";
        return defaultHapiSpec("createWithSelfDestructInConstructorHasSaneRecord")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        uploadInitCode(selfDestructingContract))
                .when(ethereumContractCreate(selfDestructingContract)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .maxGasAllowance(ONE_HUNDRED_HBARS)
                        .gasLimit(5_000_000L)
                        .via(txn))
                .then(childRecordsCheck(txn, SUCCESS, recordWith(), recordWith().hasMirrorIdInReceipt()));
    }

    @HapiTest
    HapiSpec smallContractCreate() {
        return defaultHapiSpec("smallContractCreate")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT))
                .when(
                        ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .maxGasAllowance(ONE_HUNDRED_HBARS)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(SUCCESS)
                                .via("payTxn"),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)))
                .then(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                getTxnRecord("payTxn")
                                        .logged()
                                        .hasPriority(recordWith()
                                                .contractCreateResult(resultWith()
                                                        .logs(inOrder())
                                                        .senderId(spec.registry()
                                                                .getAccountID(spec.registry()
                                                                        .aliasIdFor(SECP_256K1_SOURCE_KEY)
                                                                        .getAlias()
                                                                        .toStringUtf8()))
                                                        .create1EvmAddress(
                                                                ByteString.copyFrom(spec.registry()
                                                                        .getBytes(ETH_SENDER_ADDRESS)),
                                                                0L))
                                                .ethereumHash(ByteString.copyFrom(
                                                        spec.registry().getBytes(ETH_HASH_KEY)))))),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)));
    }

    @HapiTest
    HapiSpec bigContractCreate() {
        final var contractAdminKey = "contractAdminKey";
        return defaultHapiSpec("bigContractCreate")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        newKeyNamed(contractAdminKey),
                        uploadInitCode(TOKEN_CREATE_CONTRACT))
                .when(
                        ethereumContractCreate(TOKEN_CREATE_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .maxGasAllowance(ONE_HUNDRED_HBARS)
                                .gasLimit(1_000_000L)
                                .via("payTxn")
                                .hasKnownStatus(SUCCESS),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)))
                .then(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                getTxnRecord("payTxn")
                                        .logged()
                                        .hasPriority(recordWith()
                                                .contractCreateResult(resultWith()
                                                        .logs(inOrder())
                                                        .senderId(spec.registry()
                                                                .getAccountID(spec.registry()
                                                                        .aliasIdFor(SECP_256K1_SOURCE_KEY)
                                                                        .getAlias()
                                                                        .toStringUtf8()))
                                                        .create1EvmAddress(
                                                                ByteString.copyFrom(spec.registry()
                                                                        .getBytes(ETH_SENDER_ADDRESS)),
                                                                0L))
                                                .ethereumHash(ByteString.copyFrom(
                                                        spec.registry().getBytes(ETH_HASH_KEY)))))),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)));
    }

    @HapiTest
    HapiSpec contractCreateWithConstructorArgs() {
        final var contractAdminKey = "contractAdminKey";
        return defaultHapiSpec("contractCreateWithConstructorArgs")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        newKeyNamed(contractAdminKey),
                        uploadInitCodeWithConstructorArguments(
                                OC_TOKEN_CONTRACT,
                                getABIFor(CONSTRUCTOR, EMPTY, OC_TOKEN_CONTRACT),
                                BigInteger.valueOf(1_000_000L),
                                "OpenCrowd Token",
                                "OCT"))
                .when(
                        ethereumContractCreate(OC_TOKEN_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasPrice(10L)
                                .maxGasAllowance(ONE_HUNDRED_HBARS)
                                .gasLimit(1_000_000L)
                                .via("payTxn")
                                .hasKnownStatus(SUCCESS),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)))
                .then(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                getTxnRecord("payTxn")
                                        .logged()
                                        .hasPriority(recordWith()
                                                .contractCreateResult(resultWith()
                                                        .logs(inOrder())
                                                        .senderId(spec.registry()
                                                                .getAccountID(spec.registry()
                                                                        .aliasIdFor(SECP_256K1_SOURCE_KEY)
                                                                        .getAlias()
                                                                        .toStringUtf8()))
                                                        .create1EvmAddress(
                                                                ByteString.copyFrom(spec.registry()
                                                                        .getBytes(ETH_SENDER_ADDRESS)),
                                                                0L))
                                                .ethereumHash(ByteString.copyFrom(
                                                        spec.registry().getBytes(ETH_HASH_KEY)))))),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)));
    }

    private static final String JUST_SEND_CONTRACT = "JustSend";

    private static final String SEND_TO = "sendTo";

    HapiSpec topLevelBurnToZeroAddressReverts() {
        final var ethBurnAddress = new byte[20];
        return defaultHapiSpec("topLevelBurnToZeroAddressReverts")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(123 * ONE_HUNDRED_HBARS))
                .when(cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)))
                .then(ethereumCryptoTransferToExplicit(ethBurnAddress, 123)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .maxFeePerGas(50L)
                        .maxPriorityGas(2L)
                        .gasLimit(1_000_000L)
                        .hasKnownStatus(CONTRACT_EXECUTION_EXCEPTION));
    }

    HapiSpec topLevelLazyCreateOfMirrorAddressReverts() {
        final var nonExistentMirrorAddress = Utils.asSolidityAddress(0, 0, 666_666);
        return defaultHapiSpec("topLevelLazyCreateOfMirrorAddressReverts")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(123 * ONE_HUNDRED_HBARS))
                .when(cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)))
                .then(ethereumCryptoTransferToExplicit(nonExistentMirrorAddress, 123)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .maxFeePerGas(50L)
                        .maxPriorityGas(2L)
                        .gasLimit(1_000_000L)
                        .hasKnownStatus(INVALID_CONTRACT_ID));
    }

    HapiSpec topLevelSendToReceiverSigRequiredAccountReverts() {
        final var receiverSigAccount = "receiverSigAccount";
        final AtomicReference<byte[]> receiverMirrorAddr = new AtomicReference<>();
        final var preCallBalance = "preCallBalance";
        return defaultHapiSpec("topLevelSendToReceiverSigRequiredAccountReverts")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(123 * ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),
                        cryptoCreate(receiverSigAccount)
                                .receiverSigRequired(true)
                                .exposingCreatedIdTo(id -> receiverMirrorAddr.set(asSolidityAddress(id))),
                        uploadInitCode(JUST_SEND_CONTRACT),
                        contractCreate(JUST_SEND_CONTRACT))
                .when(
                        balanceSnapshot(preCallBalance, receiverSigAccount),
                        sourcing(() -> ethereumCryptoTransferToExplicit(receiverMirrorAddr.get(), 123)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .maxFeePerGas(50L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(INVALID_SIGNATURE)))
                .then(getAccountBalance(receiverSigAccount).hasTinyBars(changeFromSnapshot(preCallBalance, 0L)));
    }

    HapiSpec internalBurnToZeroAddressReverts() {
        return defaultHapiSpec("internalBurnToZeroAddressReverts")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(123 * ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)))
                .when(uploadInitCode(JUST_SEND_CONTRACT), contractCreate(JUST_SEND_CONTRACT))
                .then(ethereumCall(JUST_SEND_CONTRACT, SEND_TO, BigInteger.ZERO, BigInteger.valueOf(123))
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .maxFeePerGas(50L)
                        .maxPriorityGas(2L)
                        .gasLimit(1_000_000L)
                        .sending(depositAmount)
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
