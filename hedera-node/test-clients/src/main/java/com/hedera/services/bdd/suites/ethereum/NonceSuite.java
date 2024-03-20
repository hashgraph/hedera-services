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

package com.hedera.services.bdd.suites.ethereum;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOfDeferred;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordSystemProperty;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.remembering;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.HIGHLY_NON_DETERMINISTIC_FEES;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_ETHEREUM_DATA;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hedera.services.bdd.suites.contract.Utils.mirrorAddrWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData.EthTransactionType;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite(fuzzyMatch = true)
@Tag(SMART_CONTRACT)
@SuppressWarnings("java:S5960")
public class NonceSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(NonceSuite.class);
    private static final long LOW_GAS_PRICE = 1L;
    private static final long ENOUGH_GAS_PRICE = 75L;
    private static final long ENOUGH_GAS_LIMIT = 150_000L;
    private static final String RECEIVER = "receiver";
    private static final String INTERNAL_CALLEE_CONTRACT = "InternalCallee";
    private static final String INTERNAL_CALLER_CONTRACT = "InternalCaller";
    private static final String MANY_CHILDREN_CONTRACT = "ManyChildren";
    private static final String FACTORY_CONTRACT = "FactoryContract";
    private static final String REVERTER_CONSTRUCTOR_CONTRACT = "ReverterConstructor";
    private static final String REVERTER_CONSTRUCTOR_TRANSFER_CONTRACT = "ReverterConstructorTransfer";
    private static final String REVERTER_CONSTRUCTOR_CALL_WITH_VALUE_TO_ETH_PRECOMPILE_CONTRACT =
            "ConsValueEthPrecompile";
    private static final String REVERTER_CONSTRUCTOR_CALL_WITH_VALUE_TO_HEDERA_PRECOMPILE_CONTRACT =
            "ConsValueSysContract";
    private static final String EXTERNAL_FUNCTION = "externalFunction";
    private static final String REVERT_WITH_REVERT_REASON_FUNCTION = "revertWithRevertReason";
    private static final String TRANSFER_TO_FUNCTION = "transferTo";
    private static final String DEPLOYMENT_SUCCESS_FUNCTION = "deploymentSuccess";
    private static final String CHECK_BALANCE_REPEATEDLY_FUNCTION = "checkBalanceRepeatedly";
    private static final String TX = "tx";

    public static void main(String... args) {
        new NonceSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                // pre-checks
                nonceNotUpdatedWhenSignerDoesExistPrecheckFailed(),
                nonceNotUpdatedWhenPayerHasInsufficientBalancePrecheckFailed(),
                nonceNotUpdatedWhenNegativeMaxGasAllowancePrecheckFailed(),
                nonceNotUpdatedWhenInsufficientIntrinsicGasPrecheckFailed(),
                nonceNotUpdatedWhenMaxGasPerSecPrecheckFailed(),
                // handler checks
                nonceNotUpdatedWhenIntrinsicGasHandlerCheckFailed(),
                nonceNotUpdatedWhenUserOfferedGasPriceAndAllowanceAreZeroHandlerCheckFailed(),
                nonceNotUpdatedWhenOfferedGasPriceIsLessThanCurrentAndSenderDoesNotHaveEnoughBalanceHandlerCheckFailed(),
                nonceNotUpdatedWhenOfferedGasPriceIsLessThanCurrentAndGasAllowanceIsLessThanRemainingFeeHandlerCheckFailed(),
                nonceNotUpdatedWhenOfferedGasPriceIsBiggerThanCurrentAndSenderDoesNotHaveEnoughBalanceHandlerCheckFailed(),
                nonceNotUpdatedWhenSenderDoesNotHaveEnoughBalanceHandlerCheckFailed(),
                nonceNotUpdatedForNonEthereumTransaction(),
                revertsWhenSenderDoesNotExist(),
                // evm smart contract reversions
                nonceUpdatedAfterEvmReversionDueContractLogic(),
                nonceUpdatedAfterEvmReversionDueInsufficientGas(),
                nonceUpdatedAfterEvmReversionDueInsufficientTransferAmount(),
                // evm hedera specific reversions
                nonceUpdatedAfterEvmReversionDueSendingValueToEthereumPrecompile0x2(),
                nonceUpdatedAfterEvmReversionDueSendingValueToHederaPrecompile0x167(),
                // evm hedera specific resource validation reversions
                nonceUpdatedAfterEvmReversionDueMaxChildRecordsExceeded(),
                // successful ethereum transactions via internal calls
                nonceUpdatedAfterSuccessfulInternalCall(),
                nonceUpdatedAfterSuccessfulInternalTransfer(),
                nonceUpdatedAfterSuccessfulInternalContractDeployment(),
                // handler checks for contract creation
                nonceNotUpdatedWhenIntrinsicGasHandlerCheckFailedEthContractCreate(),
                nonceNotUpdatedWhenUserOfferedGasPriceAndAllowanceAreZeroHandlerCheckFailedEthContractCreate(),
                nonceNotUpdatedWhenOfferedGasPriceIsLessThanCurrentAndSenderDoesNotHaveEnoughBalanceHandlerCheckFailedEthContractCreate(),
                nonceNotUpdatedWhenOfferedGasPriceIsLessThanCurrentAndGasAllowanceIsLessThanRemainingFeeHandlerCheckFailedEthContractCreate(),
                nonceNotUpdatedWhenOfferedGasPriceIsBiggerThanCurrentAndSenderDoesNotHaveEnoughBalanceHandlerCheckFailedEthContractCreate(),
                nonceNotUpdatedWhenSenderDoesNotHaveEnoughBalanceHandlerCheckFailedEthContractCreate(),
                // evm smart contract reversions for contract creation
                nonceUpdatedAfterEvmReversionDueContractLogicEthContractCreate(),
                nonceUpdatedAfterEvmReversionDueInsufficientGasEthContractCreate(),
                nonceUpdatedAfterEvmReversionDueInsufficientTransferAmountEthContractCreate(),
                // evm hedera specific reversions for contract creation
                nonceUpdatedAfterEvmReversionDueSendingValueToEthereumPrecompileEthContractCreate(),
                // successful ethereum contract deploy
                nonceUpdatedAfterSuccessfulEthereumContractCreation());
    }

    @HapiTest
    HapiSpec nonceNotUpdatedWhenSignerDoesExistPrecheckFailed() {
        return defaultHapiSpec("nonceNotUpdatedWhenSignerDoesExistPrecheckFailed", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .hasRetryPrecheckFrom(BUSY)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .hasPrecheck(INVALID_ACCOUNT_ID))
                .then(getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).hasCostAnswerPrecheck(INVALID_ACCOUNT_ID));
    }

    @HapiTest
    HapiSpec nonceNotUpdatedWhenPayerHasInsufficientBalancePrecheckFailed() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenPayerHasInsufficientBalancePrecheckFailed",
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(1L),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(5644L)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .hasPrecheck(INSUFFICIENT_PAYER_BALANCE))
                .then(getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                        .has(accountWith().nonce(0L)));
    }

    @HapiTest
    HapiSpec nonceNotUpdatedWhenNegativeMaxGasAllowancePrecheckFailed() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenNegativeMaxGasAllowancePrecheckFailed", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .maxGasAllowance(-1L)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .hasPrecheck(NEGATIVE_ALLOWANCE_AMOUNT))
                .then(getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                        .has(accountWith().nonce(0L)));
    }

    @HapiTest
    HapiSpec nonceNotUpdatedWhenInsufficientIntrinsicGasPrecheckFailed() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenInsufficientIntrinsicGasPrecheckFailed", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(0L)
                        .hasPrecheck(INSUFFICIENT_GAS))
                .then(getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                        .has(accountWith().nonce(0L)));
    }

    @HapiTest
    HapiSpec nonceNotUpdatedWhenMaxGasPerSecPrecheckFailed() {
        final Map<String, String> startingProps = new HashMap<>();
        final String USE_GAS_THROTTLE_PROP = "contracts.throttle.throttleByGas";
        AtomicLong maxGasPerSec = new AtomicLong();
        return defaultHapiSpec("nonceNotUpdatedWhenMaxGasPerSecPrecheckFailed", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        remembering(startingProps, USE_GAS_THROTTLE_PROP),
                        overriding(USE_GAS_THROTTLE_PROP, "true"),
                        recordSystemProperty("contracts.maxGasPerSec", Long::parseLong, maxGasPerSec::set),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(sourcing(() -> ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(maxGasPerSec.get() + 1L)
                        .hasPrecheckFrom(MAX_GAS_LIMIT_EXCEEDED, BUSY)))
                .then(
                        overridingAllOfDeferred(() -> startingProps),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)));
    }

    @HapiTest
    HapiSpec nonceNotUpdatedWhenIntrinsicGasHandlerCheckFailed() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenIntrinsicGasHandlerCheckFailed",
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(21_000L)
                        .hasKnownStatus(INSUFFICIENT_GAS)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_GAS)
                                        .contractCallResult(resultWith().signerNonce(0L))));
    }

    @HapiTest
    HapiSpec nonceNotUpdatedWhenUserOfferedGasPriceAndAllowanceAreZeroHandlerCheckFailed() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenUserOfferedGasPriceAndAllowanceAreZeroHandlerCheckFailed",
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .maxFeePerGas(0L)
                        .maxGasAllowance(0L)
                        .hasKnownStatus(INSUFFICIENT_TX_FEE)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_TX_FEE)
                                        .contractCallResult(resultWith().signerNonce(0L))));
    }

    @HapiTest
    HapiSpec nonceNotUpdatedWhenOfferedGasPriceIsLessThanCurrentAndSenderDoesNotHaveEnoughBalanceHandlerCheckFailed() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenOfferedGasPriceIsLessThanCurrentAndSenderDoesNotHaveEnoughBalanceHandlerCheckFailed",
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, 1L)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .maxFeePerGas(LOW_GAS_PRICE)
                        .hasKnownStatus(INSUFFICIENT_PAYER_BALANCE)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_PAYER_BALANCE)
                                        .contractCallResult(resultWith().signerNonce(0L))));
    }

    @HapiTest
    HapiSpec
            nonceNotUpdatedWhenOfferedGasPriceIsLessThanCurrentAndGasAllowanceIsLessThanRemainingFeeHandlerCheckFailed() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenOfferedGasPriceIsLessThanCurrentAndGasAllowanceIsLessThanRemainingFeeHandlerCheckFailed",
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .maxFeePerGas(LOW_GAS_PRICE)
                        .maxGasAllowance(0L)
                        .hasKnownStatus(INSUFFICIENT_TX_FEE)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_TX_FEE)
                                        .contractCallResult(resultWith().signerNonce(0L))));
    }

    @HapiTest
    HapiSpec
            nonceNotUpdatedWhenOfferedGasPriceIsBiggerThanCurrentAndSenderDoesNotHaveEnoughBalanceHandlerCheckFailed() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenOfferedGasPriceIsBiggerThanCurrentAndSenderDoesNotHaveEnoughBalanceHandlerCheckFailed",
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, 1L)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .maxFeePerGas(ENOUGH_GAS_PRICE)
                        .hasKnownStatus(INSUFFICIENT_PAYER_BALANCE)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_PAYER_BALANCE)
                                        .contractCallResult(resultWith().signerNonce(0L))));
    }

    @HapiTest
    HapiSpec nonceNotUpdatedWhenSenderDoesNotHaveEnoughBalanceHandlerCheckFailed() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenSenderDoesNotHaveEnoughBalanceHandlerCheckFailed",
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, 1L)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .maxFeePerGas(0L)
                        .sending(5L)
                        .hasKnownStatus(INSUFFICIENT_PAYER_BALANCE)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_PAYER_BALANCE)
                                        .contractCallResult(resultWith().signerNonce(0L))));
    }

    @HapiTest
    HapiSpec nonceNotUpdatedForNonEthereumTransaction() {
        return defaultHapiSpec("nonceNotUpdatedForNonEthereumTransaction")
                .given(
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(contractCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .gas(ENOUGH_GAS_LIMIT)
                        .payingWith(RELAYER)
                        .signingWith(RELAYER)
                        .via(TX))
                .then(getAccountInfo(RELAYER).has(accountWith().nonce(0L)));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterSuccessfulInternalCall() {
        return defaultHapiSpec(
                        "nonceUpdatedAfterSuccessfulInternalCall",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterEvmReversionDueContractLogic() {
        return defaultHapiSpec(
                        "nonceUpdatedAfterEvmReversionDueContractLogic",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        HIGHLY_NON_DETERMINISTIC_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, REVERT_WITH_REVERT_REASON_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(CONTRACT_REVERT_EXECUTED)
                                        .contractCallResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterEvmReversionDueInsufficientGas() {
        return defaultHapiSpec("nonceUpdatedAfterEvmReversionDueInsufficientGas", NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(21_064L)
                        .hasKnownStatus(INSUFFICIENT_GAS)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                //                                .andAllChildRecords()
                                //                                .logged()
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_GAS)
                                        .contractCallResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterEvmReversionDueInsufficientTransferAmount() {
        AtomicReference<AccountID> receiverId = new AtomicReference<>();
        return defaultHapiSpec(
                        "nonceUpdatedAfterEvmReversionDueInsufficientTransferAmount",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        HIGHLY_NON_DETERMINISTIC_FEES)
                .given(
                        cryptoCreate(RECEIVER).balance(0L).exposingCreatedIdTo(receiverId::set),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        ethereumCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        TRANSFER_TO_FUNCTION,
                                        mirrorAddrWith(receiverId.get().getAccountNum()))
                                .type(EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasLimit(ENOUGH_GAS_LIMIT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .via(TX))))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(CONTRACT_REVERT_EXECUTED)
                                        .contractCallResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterEvmReversionDueSendingValueToEthereumPrecompile0x2() {
        AccountID eth0x2 = AccountID.newBuilder().setAccountNum(2).build();
        return defaultHapiSpec(
                        "nonceUpdatedAfterEvmReversionDueSendingValueToEthereumPrecompile0x2",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        ethereumCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        TRANSFER_TO_FUNCTION,
                                        mirrorAddrWith(eth0x2.getAccountNum()))
                                .type(EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasLimit(ENOUGH_GAS_LIMIT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .via(TX))))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(CONTRACT_REVERT_EXECUTED)
                                        .contractCallResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterEvmReversionDueSendingValueToHederaPrecompile0x167() {
        AccountID eth0x167 = AccountID.newBuilder().setAccountNum(2).build();
        return defaultHapiSpec(
                        "nonceUpdatedAfterEvmReversionDueSendingValueToHederaPrecompile0x167",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        ethereumCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        TRANSFER_TO_FUNCTION,
                                        mirrorAddrWith(eth0x167.getAccountNum()))
                                .type(EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasLimit(ENOUGH_GAS_LIMIT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .via(TX))))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(CONTRACT_REVERT_EXECUTED)
                                        .contractCallResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterEvmReversionDueMaxChildRecordsExceeded() {
        final String TOKEN_TREASURY = "treasury";
        final String FUNGIBLE_TOKEN = "fungibleToken";
        final AtomicReference<String> treasuryMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final var illegalNumChildren =
                HapiSpecSetup.getDefaultNodeProps().getInteger("consensus.handle.maxFollowingRecords") + 1;
        return defaultHapiSpec(
                        "nonceUpdatedAfterEvmReversionDueMaxChildRecordsExceeded",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        cryptoCreate(TOKEN_TREASURY)
                                .exposingCreatedIdTo(id -> treasuryMirrorAddr.set(asHexedSolidityAddress(id))),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(MANY_CHILDREN_CONTRACT),
                        contractCreate(MANY_CHILDREN_CONTRACT),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1_000_000L)
                                .exposingCreatedIdTo(id ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(id)))))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        ethereumCall(
                                        MANY_CHILDREN_CONTRACT,
                                        CHECK_BALANCE_REPEATEDLY_FUNCTION,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(treasuryMirrorAddr.get()),
                                        BigInteger.valueOf(illegalNumChildren))
                                .type(EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasLimit(ENOUGH_GAS_LIMIT)
                                .hasKnownStatus(MAX_CHILD_RECORDS_EXCEEDED)
                                .via(TX))))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(MAX_CHILD_RECORDS_EXCEEDED)
                                        .contractCallResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterSuccessfulInternalTransfer() {
        AtomicReference<AccountID> receiverId = new AtomicReference<>();
        return defaultHapiSpec(
                        "nonceUpdatedAfterSuccessfulInternalTransfer",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        cryptoCreate(RECEIVER).balance(0L).exposingCreatedIdTo(receiverId::set),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        ethereumCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        TRANSFER_TO_FUNCTION,
                                        mirrorAddrWith(receiverId.get().getAccountNum()))
                                .type(EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasLimit(ENOUGH_GAS_LIMIT)
                                .via(TX))))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterSuccessfulInternalContractDeployment() {
        return defaultHapiSpec("nonceUpdatedAfterSuccessfulInternalContractDeployment", NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        cryptoCreate(RECEIVER).balance(0L),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(FACTORY_CONTRACT),
                        contractCreate(FACTORY_CONTRACT))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        ethereumCall(FACTORY_CONTRACT, DEPLOYMENT_SUCCESS_FUNCTION)
                                .type(EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .via(TX))))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceNotUpdatedWhenIntrinsicGasHandlerCheckFailedEthContractCreate() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenIntrinsicGasHandlerCheckFailedEthContractCreate",
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumContractCreate(INTERNAL_CALLEE_CONTRACT)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(21_000L)
                        .hasKnownStatus(INSUFFICIENT_GAS)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_GAS)
                                        .contractCallResult(resultWith().signerNonce(0L))));
    }

    @HapiTest
    HapiSpec nonceNotUpdatedWhenUserOfferedGasPriceAndAllowanceAreZeroHandlerCheckFailedEthContractCreate() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenUserOfferedGasPriceAndAllowanceAreZeroHandlerCheckFailedEthContractCreate",
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumContractCreate(INTERNAL_CALLEE_CONTRACT)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .maxFeePerGas(0L)
                        .maxGasAllowance(0L)
                        .hasKnownStatus(INSUFFICIENT_TX_FEE)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_TX_FEE)
                                        .contractCallResult(resultWith().signerNonce(0L))));
    }

    @HapiTest
    HapiSpec
            nonceNotUpdatedWhenOfferedGasPriceIsLessThanCurrentAndSenderDoesNotHaveEnoughBalanceHandlerCheckFailedEthContractCreate() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenOfferedGasPriceIsLessThanCurrentAndSenderDoesNotHaveEnoughBalanceHandlerCheckFailedEthContractCreate")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, 1L)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumContractCreate(INTERNAL_CALLEE_CONTRACT)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .maxFeePerGas(LOW_GAS_PRICE)
                        .hasKnownStatus(INSUFFICIENT_PAYER_BALANCE)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_PAYER_BALANCE)
                                        .contractCallResult(resultWith().signerNonce(0L))));
    }

    @HapiTest
    HapiSpec
            nonceNotUpdatedWhenOfferedGasPriceIsLessThanCurrentAndGasAllowanceIsLessThanRemainingFeeHandlerCheckFailedEthContractCreate() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenOfferedGasPriceIsLessThanCurrentAndGasAllowanceIsLessThanRemainingFeeHandlerCheckFailedEthContractCreate",
                        HIGHLY_NON_DETERMINISTIC_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumContractCreate(INTERNAL_CALLEE_CONTRACT)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .maxFeePerGas(LOW_GAS_PRICE)
                        .maxGasAllowance(0L)
                        .hasKnownStatus(INSUFFICIENT_TX_FEE)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_TX_FEE)
                                        .contractCallResult(resultWith().signerNonce(0L))));
    }

    @HapiTest
    HapiSpec
            nonceNotUpdatedWhenOfferedGasPriceIsBiggerThanCurrentAndSenderDoesNotHaveEnoughBalanceHandlerCheckFailedEthContractCreate() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenOfferedGasPriceIsBiggerThanCurrentAndSenderDoesNotHaveEnoughBalanceHandlerCheckFailedEthContractCreate")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, 1L)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumContractCreate(INTERNAL_CALLEE_CONTRACT)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .maxFeePerGas(ENOUGH_GAS_PRICE)
                        .hasKnownStatus(INSUFFICIENT_PAYER_BALANCE)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_PAYER_BALANCE)
                                        .contractCallResult(resultWith().signerNonce(0L))));
    }

    @HapiTest
    HapiSpec nonceNotUpdatedWhenSenderDoesNotHaveEnoughBalanceHandlerCheckFailedEthContractCreate() {
        return defaultHapiSpec(
                        "nonceNotUpdatedWhenSenderDoesNotHaveEnoughBalanceHandlerCheckFailedEthContractCreate",
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, 1L)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumContractCreate(INTERNAL_CALLEE_CONTRACT)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .maxFeePerGas(0L)
                        .balance(5L)
                        .hasKnownStatus(INSUFFICIENT_PAYER_BALANCE)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_PAYER_BALANCE)
                                        .contractCallResult(resultWith().signerNonce(0L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterEvmReversionDueContractLogicEthContractCreate() {
        return defaultHapiSpec(
                        "nonceUpdatedAfterEvmReversionDueContractLogicEthContractCreate",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS)),
                        uploadInitCode(REVERTER_CONSTRUCTOR_CONTRACT))
                .when(ethereumContractCreate(REVERTER_CONSTRUCTOR_CONTRACT)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(CONTRACT_REVERT_EXECUTED)
                                        .contractCreateResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterEvmReversionDueInsufficientGasEthContractCreate() {
        return defaultHapiSpec(
                        "nonceUpdatedAfterEvmReversionDueInsufficientGasEthContractCreate",
                        NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS)),
                        uploadInitCode(REVERTER_CONSTRUCTOR_TRANSFER_CONTRACT))
                .when(ethereumContractCreate(REVERTER_CONSTRUCTOR_TRANSFER_CONTRACT)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(60_000L)
                        .hasKnownStatus(INSUFFICIENT_GAS)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(INSUFFICIENT_GAS)
                                        .contractCreateResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterEvmReversionDueInsufficientTransferAmountEthContractCreate() {
        return defaultHapiSpec(
                        "nonceUpdatedAfterEvmReversionDueInsufficientTransferAmountEthContractCreate",
                        NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS)),
                        uploadInitCode(REVERTER_CONSTRUCTOR_TRANSFER_CONTRACT))
                .when(ethereumContractCreate(REVERTER_CONSTRUCTOR_TRANSFER_CONTRACT)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .contractCreateResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterEvmReversionDueSendingValueToEthereumPrecompileEthContractCreate() {
        return defaultHapiSpec(
                        "nonceUpdatedAfterEvmReversionDueSendingValueToEthereumPrecompileEthContractCreate",
                        NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS)),
                        uploadInitCode(REVERTER_CONSTRUCTOR_CALL_WITH_VALUE_TO_ETH_PRECOMPILE_CONTRACT))
                .when(ethereumContractCreate(REVERTER_CONSTRUCTOR_CALL_WITH_VALUE_TO_ETH_PRECOMPILE_CONTRACT)
                        .balance(1L)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(CONTRACT_REVERT_EXECUTED)
                                        .contractCreateResult(resultWith().signerNonce(1L))));
    }

    // depends on https://github.com/hashgraph/hedera-services/pull/11359
    @HapiTest
    HapiSpec nonceUpdatedAfterEvmReversionDueSendingValueToHederaPrecompileEthContractCreate() {
        return defaultHapiSpec("nonceUpdatedAfterEvmReversionDueSendingValueToHederaPrecompileEthContractCreate")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS)),
                        uploadInitCode(REVERTER_CONSTRUCTOR_CALL_WITH_VALUE_TO_HEDERA_PRECOMPILE_CONTRACT))
                .when(ethereumContractCreate(REVERTER_CONSTRUCTOR_CALL_WITH_VALUE_TO_HEDERA_PRECOMPILE_CONTRACT)
                        .balance(1L)
                        .type(EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .status(CONTRACT_REVERT_EXECUTED)
                                        .contractCreateResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec nonceUpdatedAfterSuccessfulEthereumContractCreation() {
        return defaultHapiSpec("nonceUpdatedAfterSuccessfulEthereumContractCreation", NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS)),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT))
                .when(ethereumContractCreate(INTERNAL_CALLER_CONTRACT)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(ENOUGH_GAS_LIMIT)
                        .via(TX))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(1L)),
                        getTxnRecord(TX)
                                .hasPriority(recordWith()
                                        .contractCreateResult(resultWith().signerNonce(1L))));
    }

    @HapiTest
    HapiSpec revertsWhenSenderDoesNotExist() {
        AtomicReference<AccountID> receiverId = new AtomicReference<>();
        return defaultHapiSpec("revertsWhenSenderDoesNotExist")
                .given(
                        cryptoCreate(RECEIVER).balance(0L).exposingCreatedIdTo(receiverId::set),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(INTERNAL_CALLER_CONTRACT),
                        contractCreate(INTERNAL_CALLER_CONTRACT).balance(ONE_HBAR))
                .when(withOpContext((spec, op) -> allRunFor(
                        spec,
                        ethereumCall(
                                        INTERNAL_CALLER_CONTRACT,
                                        TRANSFER_TO_FUNCTION,
                                        mirrorAddrWith(receiverId.get().getAccountNum()))
                                .type(EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasLimit(ENOUGH_GAS_LIMIT)
                                .hasKnownStatus(INVALID_ACCOUNT_ID)
                                .via(TX))))
                .then(getTxnRecord(TX)
                        .hasPriority(
                                recordWith().contractCallResult(resultWith().signerNonce(0L))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
