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

import static com.hedera.node.app.hapi.utils.CommonUtils.asEvmAddress;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.includingDeduction;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAutoCreatedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdateAliased;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.ALLOW_SKIPPED_ENTITY_IDS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.HIGHLY_NON_DETERMINISTIC_FEES;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONSTRUCTOR_PARAMETERS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONTRACT_CALL_RESULTS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_ETHEREUM_DATA;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_NONCE;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hedera.services.bdd.suites.contract.Utils.aaWith;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.getResourcePath;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.TOKEN_NAME;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ACCOUNT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.hyperledger.besu.datatypes.Address.contractAddress;
import static org.hyperledger.besu.datatypes.Address.fromHexString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Address;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData.EthTransactionType;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.BddMethodIsNotATest;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.utility.CommonUtils;
import java.io.File;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;

@HapiTestSuite(fuzzyMatch = true)
@Tag(SMART_CONTRACT)
@SuppressWarnings("java:S5960")
public class EthereumSuite extends HapiSuite {

    public static final long GAS_LIMIT = 1_000_000;
    public static final String ERC20_CONTRACT = "ERC20Contract";
    public static final String EMIT_SENDER_ORIGIN_CONTRACT = "EmitSenderOrigin";
    private static final Logger log = LogManager.getLogger(EthereumSuite.class);
    private static final long DEPOSIT_AMOUNT = 20_000L;
    private static final String PARTY = "party";
    private static final String LAZY_MEMO = "lazy-created account";
    private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
    private static final String TOKEN_CREATE_CONTRACT = "NewTokenCreateContract";
    private static final String HELLO_WORLD_MINT_CONTRACT = "HelloWorldMint";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String AUTO_ACCOUNT_TRANSACTION_NAME = "autoAccount";
    private static final String TOKEN = "token";
    private static final String MINT_TXN = "mintTxn";
    private static final String PAY_TXN = "payTxn";
    private static final String TOTAL_SUPPLY_TX = "totalSupplyTx";
    private static final String ERC20_ABI = "ERC20ABI";

    public static void main(String... args) {
        new EthereumSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return Stream.concat(
                        feePaymentMatrix().stream(),
                        Stream.of(
                                invalidTxData(),
                                etx008ContractCreateExecutesWithExpectedRecord(),
                                etx009CallsToTokenAddresses(),
                                etx010TransferToCryptoAccountSucceeds(),
                                etx013PrecompileCallFailsWhenSignatureMissingFromBothEthereumAndHederaTxn(),
                                etx014ContractCreateInheritsSignerProperties(),
                                etx009CallsToTokenAddresses(),
                                originAndSenderAreEthereumSigner(),
                                etx031InvalidNonceEthereumTxFailsAndChargesRelayer(),
                                etxSvc003ContractGetBytecodeQueryReturnsDeployedCode(),
                                sendingLargerBalanceThanAvailableFailsGracefully(),
                                directTransferWorksForERC20(),
                                transferHbarsViaEip2930TxSuccessfully(),
                                callToTokenAddressViaEip2930TxSuccessfully(),
                                transferTokensViaEip2930TxSuccessfully(),
                                accountDeletionResetsTheAliasNonce(),
                                legacyUnprotectedEtxBeforeEIP155WithDefaultChainId()))
                .toList();
    }

    @HapiTest
    HapiSpec sendingLargerBalanceThanAvailableFailsGracefully() {
        final AtomicReference<Address> tokenCreateContractAddress = new AtomicReference<>();

        return defaultHapiSpec("sendingLargerBalanceThanAvailableFailsGracefully", NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS - 1)),
                        createLargeFile(
                                GENESIS, TOKEN_CREATE_CONTRACT, TxnUtils.literalInitcodeFor(TOKEN_CREATE_CONTRACT)))
                .when(
                        ethereumContractCreate(TOKEN_CREATE_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .bytecode(TOKEN_CREATE_CONTRACT)
                                .gasPrice(10L)
                                .maxGasAllowance(ONE_HUNDRED_HBARS)
                                .gasLimit(1_000_000L)
                                .hasKnownStatusFrom(SUCCESS)
                                .via("deployTokenCreateContract"),
                        getContractInfo(TOKEN_CREATE_CONTRACT)
                                .exposingEvmAddress(cb -> tokenCreateContractAddress.set(asHeadlongAddress(cb))))
                .then(
                        withOpContext((spec, opLog) -> {
                            var call = ethereumCall(
                                            TOKEN_CREATE_CONTRACT,
                                            "createNonFungibleTokenPublic",
                                            tokenCreateContractAddress.get())
                                    .type(EthTxData.EthTransactionType.EIP1559)
                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                    .payingWith(RELAYER)
                                    .hasRetryPrecheckFrom(BUSY)
                                    .nonce(1)
                                    .gasPrice(10L)
                                    .sending(ONE_HUNDRED_HBARS)
                                    .gasLimit(1_000_000L)
                                    .via("createTokenTxn")
                                    .hasKnownStatus(INSUFFICIENT_PAYER_BALANCE);
                            allRunFor(spec, call);
                        }),
                        // Quick assertion to verify top-level HAPI fees were still charged after aborting
                        getTxnRecord("createTokenTxn")
                                .hasPriority(recordWith().transfers(includingDeduction("HAPI fees", RELAYER))));
    }

    @HapiTest
    HapiSpec etx010TransferToCryptoAccountSucceeds() {
        String RECEIVER = "RECEIVER";
        final String aliasBalanceSnapshot = "aliasBalance";
        return defaultHapiSpec(
                        "etx010TransferToCryptoAccountSucceeds", NONDETERMINISTIC_NONCE, NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RECEIVER).balance(0L),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                        getTxnRecord(AUTO_ACCOUNT_TRANSACTION_NAME).andAllChildRecords())
                .when(
                        balanceSnapshot(aliasBalanceSnapshot, SECP_256K1_SOURCE_KEY)
                                .accountIsAlias(),
                        ethereumCryptoTransfer(RECEIVER, FIVE_HBARS)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .maxFeePerGas(0L)
                                .maxGasAllowance(FIVE_HBARS)
                                .gasLimit(2_000_000L)
                                .via(PAY_TXN)
                                .hasKnownStatus(SUCCESS))
                .then(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                getTxnRecord(PAY_TXN)
                                        .logged()
                                        .hasPriority(recordWith()
                                                .status(SUCCESS)
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
                                .has(accountWith().nonce(1L)),
                        getAccountBalance(RECEIVER).hasTinyBars(FIVE_HBARS),
                        getAutoCreatedAccountBalance(SECP_256K1_SOURCE_KEY)
                                .hasTinyBars(changeFromSnapshot(aliasBalanceSnapshot, -FIVE_HBARS)));
    }

    List<HapiSpec> feePaymentMatrix() {
        final long gasPrice = 71;
        final long chargedGasLimit = GAS_LIMIT * 4 / 5;

        final long noPayment = 0L;
        final long thirdOfFee = gasPrice / 3;
        final long thirdOfPayment = thirdOfFee * chargedGasLimit;
        final long thirdOfLimit = thirdOfFee * GAS_LIMIT;
        final long fullAllowance = gasPrice * chargedGasLimit * 5 / 4;
        final long fullPayment = gasPrice * chargedGasLimit;
        final long ninetyPercentFee = gasPrice * 9 / 10;

        return Stream.of(
                        new Object[] {false, noPayment, noPayment, noPayment},
                        new Object[] {false, noPayment, thirdOfPayment, noPayment},
                        new Object[] {true, noPayment, fullAllowance, noPayment},
                        new Object[] {false, thirdOfFee, noPayment, noPayment},
                        new Object[] {false, thirdOfFee, thirdOfPayment, noPayment},
                        new Object[] {true, thirdOfFee, fullAllowance, thirdOfLimit},
                        new Object[] {true, thirdOfFee, fullAllowance * 9 / 10, thirdOfLimit},
                        new Object[] {false, ninetyPercentFee, noPayment, noPayment},
                        new Object[] {true, ninetyPercentFee, thirdOfPayment, fullPayment},
                        new Object[] {true, gasPrice, noPayment, fullPayment},
                        new Object[] {true, gasPrice, thirdOfPayment, fullPayment},
                        new Object[] {true, gasPrice, fullAllowance, fullPayment})
                .map(params ->
                        // [0] - success
                        // [1] - sender gas price
                        // [2] - relayer offered
                        // [3] - sender charged amount
                        // relayer charged amount can easily be calculated via
                        // wholeTransactionFee - senderChargedAmount
                        matrixedPayerRelayerTest(
                                (boolean) params[0], (long) params[1], (long) params[2], (long) params[3]))
                .toList();
    }

    @HapiTest
    HapiSpec matrixedPayerRelayerTest1() {
        return feePaymentMatrix().get(0);
    }

    @HapiTest
    HapiSpec matrixedPayerRelayerTest2() {
        return feePaymentMatrix().get(1);
    }

    @HapiTest
    HapiSpec matrixedPayerRelayerTest3() {
        return feePaymentMatrix().get(2);
    }

    @HapiTest
    HapiSpec matrixedPayerRelayerTest4() {
        return feePaymentMatrix().get(3);
    }

    @HapiTest
    HapiSpec matrixedPayerRelayerTest5() {
        return feePaymentMatrix().get(4);
    }

    @HapiTest
    HapiSpec matrixedPayerRelayerTest6() {
        return feePaymentMatrix().get(5);
    }

    @HapiTest
    HapiSpec matrixedPayerRelayerTest7() {
        return feePaymentMatrix().get(6);
    }

    @HapiTest
    HapiSpec matrixedPayerRelayerTest8() {
        return feePaymentMatrix().get(7);
    }

    @HapiTest
    HapiSpec matrixedPayerRelayerTest9() {
        return feePaymentMatrix().get(8);
    }

    @HapiTest
    HapiSpec matrixedPayerRelayerTest10() {
        return feePaymentMatrix().get(9);
    }

    @HapiTest
    HapiSpec matrixedPayerRelayerTest11() {
        return feePaymentMatrix().get(10);
    }

    @HapiTest
    HapiSpec matrixedPayerRelayerTest12() {
        return feePaymentMatrix().get(11);
    }

    @BddMethodIsNotATest
    HapiSpec matrixedPayerRelayerTest(
            final boolean success, final long senderGasPrice, final long relayerOffered, final long senderCharged) {
        return defaultHapiSpec(
                        "feePaymentMatrix "
                                + (success ? "Success/" : "Failure/")
                                + senderGasPrice
                                + "/"
                                + relayerOffered,
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        ALLOW_SKIPPED_ENTITY_IDS,
                        HIGHLY_NON_DETERMINISTIC_FEES)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME),
                        getTxnRecord(AUTO_ACCOUNT_TRANSACTION_NAME).andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when()
                .then(withOpContext((spec, ignore) -> {
                    final String senderBalance = "senderBalance";
                    final String payerBalance = "payerBalance";
                    final var subop1 = balanceSnapshot(senderBalance, SECP_256K1_SOURCE_KEY)
                            .accountIsAlias();
                    final var subop2 = balanceSnapshot(payerBalance, RELAYER);
                    final var subop3 = ethereumCall(
                                    PAY_RECEIVABLE_CONTRACT, "deposit", BigInteger.valueOf(DEPOSIT_AMOUNT))
                            .type(EthTxData.EthTransactionType.EIP1559)
                            .signingWith(SECP_256K1_SOURCE_KEY)
                            .payingWith(RELAYER)
                            .via(PAY_TXN)
                            .nonce(0)
                            .maxGasAllowance(relayerOffered)
                            .maxFeePerGas(senderGasPrice)
                            .gasLimit(GAS_LIMIT)
                            .sending(DEPOSIT_AMOUNT)
                            .hasKnownStatus(success ? ResponseCodeEnum.SUCCESS : ResponseCodeEnum.INSUFFICIENT_TX_FEE);

                    final HapiGetTxnRecord hapiGetTxnRecord =
                            getTxnRecord(PAY_TXN).logged();
                    allRunFor(spec, subop1, subop2, subop3, hapiGetTxnRecord);

                    final long wholeTransactionFee =
                            hapiGetTxnRecord.getResponseRecord().getTransactionFee();
                    final var subop4 = getAutoCreatedAccountBalance(SECP_256K1_SOURCE_KEY)
                            .hasTinyBars(
                                    changeFromSnapshot(senderBalance, success ? (-DEPOSIT_AMOUNT - senderCharged) : 0));
                    final var subop5 = getAccountBalance(RELAYER)
                            .hasTinyBars(changeFromSnapshot(
                                    payerBalance,
                                    success ? -(wholeTransactionFee - senderCharged) : -wholeTransactionFee));
                    allRunFor(spec, subop4, subop5);
                }));
    }

    @HapiTest
    HapiSpec invalidTxData() {
        return defaultHapiSpec("InvalidTxData")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME),
                        getTxnRecord(AUTO_ACCOUNT_TRANSACTION_NAME).andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT))
                .when(ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasPrice(10L)
                        .maxGasAllowance(5L)
                        .maxPriorityGas(2L)
                        .invalidateEthereumData()
                        .gasLimit(1_000_000L)
                        .hasPrecheck(INVALID_ETHEREUM_TRANSACTION)
                        .via(PAY_TXN))
                .then();
    }

    @HapiTest
    HapiSpec etx014ContractCreateInheritsSignerProperties() {
        final AtomicReference<String> contractID = new AtomicReference<>();
        final String MEMO = "memo";
        final String PROXY = "proxy";
        final long INITIAL_BALANCE = 100L;
        final long AUTO_RENEW_PERIOD = THREE_MONTHS_IN_SECONDS + 60;
        return defaultHapiSpec("etx014ContractCreateInheritsSignerProperties", NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                        getTxnRecord(AUTO_ACCOUNT_TRANSACTION_NAME).andAllChildRecords(),
                        cryptoCreate(PROXY))
                .when(
                        cryptoUpdateAliased(SECP_256K1_SOURCE_KEY)
                                .autoRenewPeriod(AUTO_RENEW_PERIOD)
                                .entityMemo(MEMO)
                                .payingWith(GENESIS)
                                .signedBy(SECP_256K1_SOURCE_KEY, GENESIS),
                        ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .balance(INITIAL_BALANCE)
                                .gasPrice(10L)
                                .maxGasAllowance(ONE_HUNDRED_HBARS)
                                .exposingNumTo(num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(SUCCESS),
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, "getBalance")
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(1L)
                                .gasPrice(10L)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(SUCCESS))
                .then(getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).logged(), sourcing(() -> getContractInfo(
                                contractID.get())
                        .logged()
                        .has(contractWith()
                                .defaultAdminKey()
                                .autoRenew(AUTO_RENEW_PERIOD)
                                .balance(INITIAL_BALANCE)
                                .memo(MEMO))));
    }

    @HapiTest
    HapiSpec etx031InvalidNonceEthereumTxFailsAndChargesRelayer() {
        final var relayerSnapshot = "relayer";
        final var senderSnapshot = "sender";
        return defaultHapiSpec(
                        "etx031InvalidNonceEthereumTxFailsAndChargesRelayer",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME),
                        getTxnRecord(AUTO_ACCOUNT_TRANSACTION_NAME).andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        balanceSnapshot(relayerSnapshot, RELAYER),
                        balanceSnapshot(senderSnapshot, SECP_256K1_SOURCE_KEY).accountIsAlias(),
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, "deposit", BigInteger.valueOf(DEPOSIT_AMOUNT))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(999L)
                                .via(PAY_TXN)
                                .hasKnownStatus(ResponseCodeEnum.WRONG_NONCE))
                .then(
                        withOpContext((spec, opLog) -> {
                            final var payTxn = getTxnRecord(PAY_TXN)
                                    .logged()
                                    .hasPriority(recordWith()
                                            .ethereumHash(ByteString.copyFrom(
                                                    spec.registry().getBytes(ETH_HASH_KEY))));
                            allRunFor(spec, payTxn);
                            final var fee = payTxn.getResponseRecord().getTransactionFee();
                            final var relayerBalance =
                                    getAccountBalance(RELAYER).hasTinyBars(changeFromSnapshot(relayerSnapshot, -fee));
                            final var senderBalance = getAutoCreatedAccountBalance(SECP_256K1_SOURCE_KEY)
                                    .hasTinyBars(changeFromSnapshot(senderSnapshot, 0));
                            allRunFor(spec, relayerBalance, senderBalance);
                        }),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .has(accountWith().nonce(0L)));
    }

    @HapiTest
    HapiSpec etx013PrecompileCallFailsWhenSignatureMissingFromBothEthereumAndHederaTxn() {
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
        final String fungibleToken = TOKEN;
        final String mintTxn = MINT_TXN;
        final String MULTI_KEY = "MULTI_KEY";
        return defaultHapiSpec(
                        "etx013PrecompileCallFailsWhenSignatureMissingFromBothEthereumAndHederaTxn",
                        NONDETERMINISTIC_CONSTRUCTOR_PARAMETERS,
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                        getTxnRecord(AUTO_ACCOUNT_TRANSACTION_NAME).andAllChildRecords(),
                        uploadInitCode(HELLO_WORLD_MINT_CONTRACT),
                        tokenCreate(fungibleToken)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))))
                .when(
                        sourcing(() -> contractCreate(
                                HELLO_WORLD_MINT_CONTRACT, asHeadlongAddress(asAddress(fungible.get())))),
                        ethereumCall(HELLO_WORLD_MINT_CONTRACT, "brrr", BigInteger.valueOf(5))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .nonce(0)
                                .via(mintTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))
                .then(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                getTxnRecord(mintTxn)
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
                        childRecordsCheck(
                                mintTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)));
    }

    @HapiTest
    HapiSpec etx009CallsToTokenAddresses() {
        final AtomicReference<String> tokenNum = new AtomicReference<>();
        final var totalSupply = 50;

        return defaultHapiSpec(
                        "etx009CallsToTokenAddresses",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(totalSupply)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(SECP_256K1_SOURCE_KEY)
                                .supplyKey(SECP_256K1_SOURCE_KEY)
                                .exposingCreatedIdTo(tokenNum::set),
                        uploadInitCode(ERC20_CONTRACT),
                        contractCreate(ERC20_CONTRACT).adminKey(THRESHOLD))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        ethereumCallWithFunctionAbi(
                                        true,
                                        FUNGIBLE_TOKEN,
                                        getABIFor(Utils.FunctionType.FUNCTION, "totalSupply", ERC20_ABI))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .via(TOTAL_SUPPLY_TX)
                                .nonce(0)
                                .gasPrice(50L)
                                .maxGasAllowance(FIVE_HBARS)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS))))
                .then(childRecordsCheck(
                        TOTAL_SUPPLY_TX,
                        SUCCESS,
                        recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .contractCallResult(htsPrecompileResult()
                                                .forFunction(FunctionType.ERC_TOTAL_SUPPLY)
                                                .withTotalSupply(totalSupply)))));
    }

    // ETX-011 and ETX-030
    @HapiTest
    HapiSpec originAndSenderAreEthereumSigner() {
        return defaultHapiSpec(
                        "originAndSenderAreEthereumSigner",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                        getTxnRecord(AUTO_ACCOUNT_TRANSACTION_NAME).andAllChildRecords(),
                        uploadInitCode(EMIT_SENDER_ORIGIN_CONTRACT),
                        contractCreate(EMIT_SENDER_ORIGIN_CONTRACT))
                .when(ethereumCall(EMIT_SENDER_ORIGIN_CONTRACT, "logNow")
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .maxFeePerGas(50L)
                        .gasLimit(1_000_000L)
                        .via(PAY_TXN)
                        .hasKnownStatus(ResponseCodeEnum.SUCCESS))
                .then(
                        withOpContext((spec, ignore) -> allRunFor(
                                spec,
                                getTxnRecord(PAY_TXN)
                                        .logged()
                                        .hasPriority(recordWith()
                                                .contractCallResult(resultWith()
                                                        .logs(inOrder(logWith()
                                                                .ecdsaAliasStartingAt(SECP_256K1_SOURCE_KEY, 12)
                                                                .ecdsaAliasStartingAt(SECP_256K1_SOURCE_KEY, 44)
                                                                .withTopicsInOrder(List.of(
                                                                        eventSignatureOf("Info(address,address)")))))
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

    @HapiTest
    HapiSpec etx008ContractCreateExecutesWithExpectedRecord() {
        final var txn = "creation";
        final var contract = "Fuse";

        return defaultHapiSpec(
                        "etx008ContractCreateExecutesWithExpectedRecord",
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_NONCE,
                        ALLOW_SKIPPED_ENTITY_IDS)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME),
                        uploadInitCode(contract),
                        ethereumContractCreate(contract)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .gasLimit(GAS_LIMIT)
                                .via(txn),
                        withOpContext((spec, opLog) -> {
                            final var op = getTxnRecord(txn);
                            allRunFor(spec, op);
                            final var record = op.getResponseRecord();
                            final var creationResult = record.getContractCreateResult();
                            final var createdIds = creationResult.getCreatedContractIDsList().stream()
                                    .sorted(Comparator.comparing(id -> id.getContractNum()))
                                    .toList();
                            assertEquals(4, createdIds.size(), "Expected four creations but got " + createdIds);

                            final var ecdsaKey = spec.registry()
                                    .getKey(SECP_256K1_SOURCE_KEY)
                                    .getECDSASecp256K1()
                                    .toByteArray();
                            final var senderAddress = CommonUtils.hex(recoverAddressFromPubKey(ecdsaKey));
                            final var senderNonce = 0;

                            final var expectedParentContractAddress =
                                    contractAddress(fromHexString(senderAddress), senderNonce);
                            final var expectedFirstChildContractAddress =
                                    contractAddress(expectedParentContractAddress, 1);
                            final var expectedSecondChildContractAddress =
                                    contractAddress(expectedParentContractAddress, 2);
                            final var expectedThirdChildContractAddress =
                                    contractAddress(expectedParentContractAddress, 3);

                            final var parentContractId = CommonUtils.hex(
                                    asEvmAddress(createdIds.get(0).getContractNum()));
                            final var firstChildContractId = CommonUtils.hex(
                                    asEvmAddress(createdIds.get(1).getContractNum()));
                            final var secondChildContractId = CommonUtils.hex(
                                    asEvmAddress(createdIds.get(2).getContractNum()));
                            final var thirdChildContractId = CommonUtils.hex(
                                    asEvmAddress(createdIds.get(3).getContractNum()));

                            final var parentContractInfo = getContractInfo(parentContractId)
                                    .has(contractWith()
                                            .addressOrAlias(expectedParentContractAddress.toUnprefixedHexString()));
                            final var firstChildContractInfo = getContractInfo(firstChildContractId)
                                    .has(contractWith()
                                            .addressOrAlias(expectedFirstChildContractAddress.toUnprefixedHexString()));
                            final var secondChildContractInfo = getContractInfo(secondChildContractId)
                                    .has(contractWith()
                                            .addressOrAlias(
                                                    expectedSecondChildContractAddress.toUnprefixedHexString()));
                            final var thirdChildContractInfo = getContractInfo(thirdChildContractId)
                                    .has(contractWith()
                                            .addressOrAlias(expectedThirdChildContractAddress.toUnprefixedHexString()))
                                    .logged();

                            allRunFor(
                                    spec,
                                    parentContractInfo,
                                    firstChildContractInfo,
                                    secondChildContractInfo,
                                    thirdChildContractInfo);
                        }))
                .when()
                .then();
    }

    @HapiTest
    HapiSpec etxSvc003ContractGetBytecodeQueryReturnsDeployedCode() {
        final var txn = "creation";
        final var contract = "EmptyConstructor";
        return HapiSpec.defaultHapiSpec(
                        "etxSvc003ContractGetBytecodeQueryReturnsDeployedCode", NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME),
                        uploadInitCode(contract),
                        ethereumContractCreate(contract)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .gasLimit(GAS_LIMIT)
                                .via(txn))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    final var getBytecode = getContractBytecode(contract).saveResultTo("contractByteCode");
                    allRunFor(spec, getBytecode);

                    final var originalBytecode =
                            Hex.decode(Files.toByteArray(new File(getResourcePath(contract, ".bin"))));
                    final var actualBytecode = spec.registry().getBytes("contractByteCode");
                    // The original bytecode is modified on deployment
                    final var expectedBytecode = Arrays.copyOfRange(originalBytecode, 29, originalBytecode.length);
                    Assertions.assertArrayEquals(expectedBytecode, actualBytecode);
                }));
    }

    @HapiTest
    HapiSpec directTransferWorksForERC20() {
        final var tokenSymbol = "FDFGF";
        final var tokenTotalSupply = 5;
        final var tokenTransferAmount = 3;
        final var transferTxn = "decimalsTxn";
        return defaultHapiSpec(
                        "directTransferWorksForERC20",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(tokenTotalSupply)
                                .name(TOKEN_NAME)
                                .symbol(tokenSymbol)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(ACCOUNT, FUNGIBLE_TOKEN),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(moving(tokenTransferAmount, FUNGIBLE_TOKEN)
                                        .between(TOKEN_TREASURY, SECP_256K1_SOURCE_KEY))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME))
                .when(withOpContext((spec, ignore) -> allRunFor(
                        spec,
                        ethereumCallWithFunctionAbi(
                                        true,
                                        FUNGIBLE_TOKEN,
                                        getABIFor(Utils.FunctionType.FUNCTION, "transfer", ERC20_ABI),
                                        asHeadlongAddress(asHexedSolidityAddress(
                                                spec.registry().getAccountID(ACCOUNT))),
                                        BigInteger.valueOf(tokenTransferAmount))
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .nonce(0)
                                .gasPrice(50L)
                                .via(transferTxn)
                                .gasLimit(1_000_000)
                                .maxFeePerGas(0)
                                .type(EthTransactionType.EIP1559)
                                .maxGasAllowance(ONE_HBAR * 5)
                                .payingWith(ACCOUNT))))
                .then(withOpContext((spec, ignore) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                transferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TRANSFER)
                                                        .withErcFungibleTransferStatus(true)))))));
    }

    @HapiTest
    HapiSpec transferHbarsViaEip2930TxSuccessfully() {
        final String RECEIVER = "RECEIVER";
        final String aliasBalanceSnapshot = "aliasBalance";
        return defaultHapiSpec("transferHbarsViaEip2930TxSuccessfully", NONDETERMINISTIC_ETHEREUM_DATA)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RECEIVER).balance(0L),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                        getTxnRecord(AUTO_ACCOUNT_TRANSACTION_NAME).andAllChildRecords())
                .when(
                        balanceSnapshot(aliasBalanceSnapshot, SECP_256K1_SOURCE_KEY)
                                .accountIsAlias(),
                        ethereumCryptoTransfer(RECEIVER, FIVE_HBARS)
                                .type(EthTxData.EthTransactionType.EIP2930)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasPrice(0L)
                                .gasLimit(2_000_000L)
                                .via(PAY_TXN)
                                .hasKnownStatus(SUCCESS))
                .then(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                getTxnRecord(PAY_TXN)
                                        .logged()
                                        .hasPriority(recordWith()
                                                .status(SUCCESS)
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
                                .has(accountWith().nonce(1L)),
                        getAccountBalance(RECEIVER).hasTinyBars(FIVE_HBARS),
                        getAutoCreatedAccountBalance(SECP_256K1_SOURCE_KEY)
                                .hasTinyBars(changeFromSnapshot(aliasBalanceSnapshot, -FIVE_HBARS)));
    }

    @HapiTest
    HapiSpec callToTokenAddressViaEip2930TxSuccessfully() {
        final AtomicReference<String> tokenNum = new AtomicReference<>();
        final var totalSupply = 50;

        return defaultHapiSpec(
                        "callToTokenAddressViaEip2930TxSuccessfully",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(totalSupply)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(SECP_256K1_SOURCE_KEY)
                                .supplyKey(SECP_256K1_SOURCE_KEY)
                                .exposingCreatedIdTo(tokenNum::set),
                        uploadInitCode(ERC20_CONTRACT),
                        contractCreate(ERC20_CONTRACT).adminKey(THRESHOLD))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        ethereumCallWithFunctionAbi(
                                        true,
                                        FUNGIBLE_TOKEN,
                                        getABIFor(Utils.FunctionType.FUNCTION, "totalSupply", ERC20_ABI))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .hasRetryPrecheckFrom(BUSY)
                                .via(TOTAL_SUPPLY_TX)
                                .nonce(0)
                                .gasPrice(0L)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS))))
                .then(childRecordsCheck(
                        TOTAL_SUPPLY_TX,
                        SUCCESS,
                        recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .contractCallResult(htsPrecompileResult()
                                                .forFunction(FunctionType.ERC_TOTAL_SUPPLY)
                                                .withTotalSupply(totalSupply)))));
    }

    @HapiTest
    HapiSpec transferTokensViaEip2930TxSuccessfully() {
        final var tokenSymbol = "FDFGF";
        final var tokenTotalSupply = 5;
        final var tokenTransferAmount = 3;
        final var transferTxn = "decimalsTxn";
        return defaultHapiSpec(
                        "transferTokensViaEip2930TxSuccessfully",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_NONCE,
                        ALLOW_SKIPPED_ENTITY_IDS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(tokenTotalSupply)
                                .name(TOKEN_NAME)
                                .symbol(tokenSymbol)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(ACCOUNT, FUNGIBLE_TOKEN),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(moving(tokenTransferAmount, FUNGIBLE_TOKEN)
                                        .between(TOKEN_TREASURY, SECP_256K1_SOURCE_KEY))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME))
                .when(withOpContext((spec, ignore) -> allRunFor(
                        spec,
                        ethereumCallWithFunctionAbi(
                                        true,
                                        FUNGIBLE_TOKEN,
                                        getABIFor(Utils.FunctionType.FUNCTION, "transfer", ERC20_ABI),
                                        asHeadlongAddress(asHexedSolidityAddress(
                                                spec.registry().getAccountID(ACCOUNT))),
                                        BigInteger.valueOf(tokenTransferAmount))
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .nonce(0)
                                .gasPrice(0L)
                                .via(transferTxn)
                                .gasLimit(1_000_000)
                                .type(EthTransactionType.EIP2930)
                                .payingWith(RELAYER))))
                .then(withOpContext((spec, ignore) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                transferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TRANSFER)
                                                        .withErcFungibleTransferStatus(true)))))));
    }

    @HapiTest
    final HapiSpec accountDeletionResetsTheAliasNonce() {

        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();
        final AtomicReference<ByteString> counterAlias = new AtomicReference<>();
        final AtomicReference<AccountID> aliasedAccountId = new AtomicReference<>();
        final AtomicReference<String> tokenNum = new AtomicReference<>();
        final var totalSupply = 50;
        final var ercUser = "ercUser";
        final var HBAR_XFER = "hbarXfer";

        return defaultHapiSpec("accountDeletionResetsTheAliasNonce")
                .given(
                        cryptoCreate(PARTY).maxAutomaticTokenAssociations(2),
                        cryptoCreate(TOKEN_TREASURY),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        withOpContext((spec, opLog) -> {
                            final var registry = spec.registry();
                            final var ecdsaKey = registry.getKey(SECP_256K1_SOURCE_KEY);
                            final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                            final var addressBytes = recoverAddressFromPubKey(tmp);
                            final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                            partyId.set(registry.getAccountID(PARTY));
                            partyAlias.set(ByteString.copyFrom(asSolidityAddress(partyId.get())));
                            counterAlias.set(evmAddressBytes);
                        }),
                        tokenCreate("token")
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(totalSupply)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(SECP_256K1_SOURCE_KEY)
                                .supplyKey(SECP_256K1_SOURCE_KEY)
                                .exposingCreatedIdTo(tokenNum::set))
                .when(
                        withOpContext((spec, opLog) -> {
                            var op1 = cryptoTransfer((s, b) -> b.setTransfers(TransferList.newBuilder()
                                            .addAccountAmounts(aaWith(partyAlias.get(), -2 * ONE_HBAR))
                                            .addAccountAmounts(aaWith(counterAlias.get(), +2 * ONE_HBAR))))
                                    .signedBy(DEFAULT_PAYER, PARTY)
                                    .via(HBAR_XFER);

                            var op2 = getAliasedAccountInfo(counterAlias.get())
                                    .logged()
                                    .exposingIdTo(aliasedAccountId::set)
                                    .has(accountWith()
                                            .hasEmptyKey()
                                            .noAlias()
                                            .nonce(0)
                                            .autoRenew(THREE_MONTHS_IN_SECONDS)
                                            .receiverSigReq(false)
                                            .memo(LAZY_MEMO));

                            // send eth transaction signed by the ecdsa key
                            var op3 = ethereumCallWithFunctionAbi(
                                            true,
                                            "token",
                                            getABIFor(Utils.FunctionType.FUNCTION, "totalSupply", ERC20_ABI))
                                    .type(EthTxData.EthTransactionType.EIP1559)
                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                    .payingWith(GENESIS)
                                    .nonce(0)
                                    .gasPrice(50L)
                                    .maxGasAllowance(FIVE_HBARS)
                                    .maxPriorityGas(2L)
                                    .gasLimit(1_000_000L)
                                    .hasKnownStatus(ResponseCodeEnum.SUCCESS);

                            // assert account nonce is increased to 1
                            var op4 = getAliasedAccountInfo(counterAlias.get())
                                    .logged()
                                    .has(accountWith().nonce(1));

                            allRunFor(spec, op1, op2, op3, op4);

                            spec.registry().saveAccountId(ercUser, aliasedAccountId.get());
                            spec.registry().saveKey(ercUser, spec.registry().getKey(SECP_256K1_SOURCE_KEY));
                        }),
                        // delete the account currently holding the alias
                        cryptoDelete(ercUser))
                .then(
                        // try to create a new account with the same alias
                        withOpContext((spec, opLog) -> {
                            var op1 = cryptoTransfer((s, b) -> b.setTransfers(TransferList.newBuilder()
                                            .addAccountAmounts(aaWith(partyAlias.get(), -2 * ONE_HBAR))
                                            .addAccountAmounts(aaWith(counterAlias.get(), +2 * ONE_HBAR))))
                                    .signedBy(DEFAULT_PAYER, PARTY)
                                    .hasKnownStatus(SUCCESS);

                            var op2 = getAliasedAccountInfo(counterAlias.get())
                                    // TBD: balance should be 4 or 2 hbars
                                    .has(accountWith().nonce(0).balance(2 * ONE_HBAR));

                            allRunFor(spec, op1, op2);
                        }));
    }

    // test unprotected legacy ethereum transactions before EIP155,
    // without changing `CHAIN_ID` bootstrap property
    // this tests the behaviour when the `v` field
    // is calculated -> v = {0,1} + 27
    // source: https://eips.ethereum.org/EIPS/eip-155
    @HapiTest
    HapiSpec legacyUnprotectedEtxBeforeEIP155WithDefaultChainId() {
        final String DEPOSIT = "deposit";
        final long depositAmount = 20_000L;
        final Integer chainId = 0;

        return defaultHapiSpec(
                        "legacyUnprotectedEtxBeforeEIP155WithDefaultChainId",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(ethereumCall(PAY_RECEIVABLE_CONTRACT, DEPOSIT, BigInteger.valueOf(depositAmount))
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
                        .hasKnownStatus(ResponseCodeEnum.SUCCESS))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        getTxnRecord("legacyBeforeEIP155")
                                .logged()
                                .hasPriority(recordWith().status(SUCCESS)))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
