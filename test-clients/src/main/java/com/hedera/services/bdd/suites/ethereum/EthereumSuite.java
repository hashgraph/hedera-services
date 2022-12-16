/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAutoCreatedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
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
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.getResourcePath;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Address;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData.EthTransactionType;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.io.File;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Assertions;

public class EthereumSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(EthereumSuite.class);
    private static final long depositAmount = 20_000L;
    private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
    private static final String TOKEN_CREATE_CONTRACT = "NewTokenCreateContract";
    private static final String ERC721_CONTRACT_WITH_HTS_CALLS = "ERC721ContractWithHTSCalls";
    private static final String HELLO_WORLD_MINT_CONTRACT = "HelloWorldMint";
    public static final long GAS_LIMIT = 1_000_000;

    public static final String ERC20_CONTRACT = "ERC20Contract";
    public static final String EMIT_SENDER_ORIGIN_CONTRACT = "EmitSenderOrigin";

    private static final String FUNGIBLE_TOKEN = "fungibleToken";

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
                                etx007FungibleTokenCreateWithFeesHappyPath(),
                                etx008ContractCreateExecutesWithExpectedRecord(),
                                etx009CallsToTokenAddresses(),
                                etx010TransferToCryptoAccountSucceeds(),
                                etx012PrecompileCallSucceedsWhenNeededSignatureInEthTxn(),
                                etx013PrecompileCallSucceedsWhenNeededSignatureInHederaTxn(),
                                etx013PrecompileCallFailsWhenSignatureMissingFromBothEthereumAndHederaTxn(),
                                etx014ContractCreateInheritsSignerProperties(),
                                etx009CallsToTokenAddresses(),
                                originAndSenderAreEthereumSigner(),
                                etx031InvalidNonceEthereumTxFailsAndChargesRelayer(),
                                etxSvc003ContractGetBytecodeQueryReturnsDeployedCode(),
                                sendingLargerBalanceThanAvailableFailsGracefully(),
                                setApproveForAllUsingLocalNodeSetupPasses()))
                .toList();
    }

    HapiSpec sendingLargerBalanceThanAvailableFailsGracefully() {
        final AtomicReference<Address> tokenCreateContractAddress = new AtomicReference<>();

        return defaultHapiSpec("Sending Larger Balance Than Available Fails Gracefully")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                tinyBarsFromAccountToAlias(
                                        GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS - 1)),
                        createLargeFile(
                                GENESIS,
                                TOKEN_CREATE_CONTRACT,
                                TxnUtils.literalInitcodeFor(TOKEN_CREATE_CONTRACT)))
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
                                .exposingEvmAddress(
                                        cb ->
                                                tokenCreateContractAddress.set(
                                                        asHeadlongAddress(cb))))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    var call =
                                            ethereumCall(
                                                            TOKEN_CREATE_CONTRACT,
                                                            "createNonFungibleTokenPublic",
                                                            tokenCreateContractAddress.get())
                                                    .type(EthTxData.EthTransactionType.EIP1559)
                                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                                    .payingWith(RELAYER)
                                                    .nonce(1)
                                                    .gasPrice(10L)
                                                    .sending(ONE_HUNDRED_HBARS)
                                                    .gasLimit(1_000_000L)
                                                    .via("createTokenTxn")
                                                    .hasKnownStatus(INSUFFICIENT_PAYER_BALANCE);
                                    allRunFor(spec, call);
                                }));
    }

    HapiSpec setApproveForAllUsingLocalNodeSetupPasses() {
        final AtomicReference<String> spenderAutoCreatedAccountId = new AtomicReference<>();
        final AtomicReference<String> tokenCreateContractID = new AtomicReference<>();
        final AtomicReference<String> erc721ContractID = new AtomicReference<>();
        final AtomicReference<ByteString> createdTokenAddressString = new AtomicReference<>();
        final String spenderAlias = "spenderAlias";
        final var createTokenContractNum = new AtomicLong();
        return defaultHapiSpec("SetApproveForAllUsingLocalNodeSetupPasses")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(spenderAlias).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS))
                                .via("autoAccount"),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, spenderAlias, ONE_HUNDRED_HBARS))
                                .via("autoAccountSpender"),
                        getAliasedAccountInfo(spenderAlias)
                                .exposingContractAccountIdTo(spenderAutoCreatedAccountId::set),
                        createLargeFile(
                                GENESIS,
                                TOKEN_CREATE_CONTRACT,
                                TxnUtils.literalInitcodeFor(TOKEN_CREATE_CONTRACT)),
                        ethereumContractCreate(TOKEN_CREATE_CONTRACT)
                                .type(EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .bytecode(TOKEN_CREATE_CONTRACT)
                                .gasPrice(10L)
                                .maxGasAllowance(ONE_HUNDRED_HBARS)
                                .gasLimit(1_000_000L)
                                .gas(1_000_000L)
                                .hasKnownStatusFrom(SUCCESS)
                                .exposingNumTo(createTokenContractNum::set),
                        getContractInfo(TOKEN_CREATE_CONTRACT)
                                .exposingEvmAddress(tokenCreateContractID::set))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    var createNFTPublicFunctionCall =
                                            ethereumCall(
                                                            TOKEN_CREATE_CONTRACT,
                                                            "createNonFungibleTokenPublic",
                                                            asHeadlongAddress(
                                                                    tokenCreateContractID.get()))
                                                    .type(EthTransactionType.EIP1559)
                                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                                    .payingWith(RELAYER)
                                                    .nonce(1)
                                                    .gasPrice(10L)
                                                    .sending(10000000000L)
                                                    .gasLimit(1_000_000L)
                                                    .via("createTokenTxn")
                                                    .exposingEventDataTo(
                                                            createdTokenAddressString::set);

                                    allRunFor(spec, createNFTPublicFunctionCall);

                                    var uploadEthereumContract =
                                            uploadInitCode(ERC721_CONTRACT_WITH_HTS_CALLS);
                                    allRunFor(spec, uploadEthereumContract);

                                    var createEthereumContract =
                                            ethereumContractCreate(ERC721_CONTRACT_WITH_HTS_CALLS)
                                                    .type(EthTxData.EthTransactionType.EIP1559)
                                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                                    .payingWith(RELAYER)
                                                    .nonce(2)
                                                    .gasPrice(10L)
                                                    .maxGasAllowance(ONE_HUNDRED_HBARS)
                                                    .gasLimit(1_000_000L)
                                                    .hasKnownStatusFrom(SUCCESS);

                                    var exposeEthereumContractAddress =
                                            getContractInfo(ERC721_CONTRACT_WITH_HTS_CALLS)
                                                    .exposingEvmAddress(
                                                            address ->
                                                                    erc721ContractID.set(
                                                                            "0x" + address));

                                    allRunFor(
                                            spec,
                                            createEthereumContract,
                                            exposeEthereumContractAddress);
                                }),
                        withOpContext(
                                (spec, opLog) -> {
                                    var associateTokenToERC721 =
                                            ethereumCall(
                                                            ERC721_CONTRACT_WITH_HTS_CALLS,
                                                            "associateTokenPublic",
                                                            asHeadlongAddress(
                                                                    erc721ContractID.get()),
                                                            asHeadlongAddress(
                                                                    Bytes.wrap(
                                                                                    createdTokenAddressString
                                                                                            .get()
                                                                                            .toByteArray())
                                                                            .toHexString()))
                                                    .type(EthTransactionType.EIP1559)
                                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                                    .payingWith(GENESIS)
                                                    .nonce(3)
                                                    .gasPrice(10L)
                                                    .gasLimit(1_000_000L)
                                                    .via("associateTokenTxn")
                                                    .hasKnownStatusFrom(SUCCESS);

                                    var associateTokenToSpender =
                                            ethereumCall(
                                                            TOKEN_CREATE_CONTRACT,
                                                            "associateTokenPublic",
                                                            asHeadlongAddress(
                                                                    spenderAutoCreatedAccountId
                                                                            .get()),
                                                            asHeadlongAddress(
                                                                    Bytes.wrap(
                                                                                    createdTokenAddressString
                                                                                            .get()
                                                                                            .toByteArray())
                                                                            .toHexString()))
                                                    .type(EthTransactionType.EIP1559)
                                                    .signingWith(spenderAlias)
                                                    .payingWith(GENESIS)
                                                    .nonce(0)
                                                    .gasPrice(10L)
                                                    .gasLimit(1_000_000L)
                                                    .via("associateTokenTxn")
                                                    .hasKnownStatusFrom(SUCCESS);

                                    var isApprovedForAllBefore =
                                            ethereumCall(
                                                            ERC721_CONTRACT_WITH_HTS_CALLS,
                                                            "ercIsApprovedForAll",
                                                            asHeadlongAddress(
                                                                    Bytes.wrap(
                                                                                    createdTokenAddressString
                                                                                            .get()
                                                                                            .toByteArray())
                                                                            .toHexString()),
                                                            asHeadlongAddress(
                                                                    erc721ContractID.get()),
                                                            asHeadlongAddress(
                                                                    spenderAutoCreatedAccountId
                                                                            .get()))
                                                    .type(EthTransactionType.EIP1559)
                                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                                    .payingWith(RELAYER)
                                                    .nonce(4)
                                                    .gasPrice(10L)
                                                    .gasLimit(1_000_000L)
                                                    .via("ercIsApprovedForAllBeforeTxn")
                                                    .hasKnownStatusFrom(SUCCESS)
                                                    .logged();

                                    var isApprovedForAllBeforeCheck =
                                            childRecordsCheck(
                                                    "ercIsApprovedForAllBeforeTxn",
                                                    SUCCESS,
                                                    recordWith()
                                                            .status(SUCCESS)
                                                            .contractCallResult(
                                                                    resultWith()
                                                                            .contractCallResult(
                                                                                    htsPrecompileResult()
                                                                                            .forFunction(
                                                                                                    FunctionType
                                                                                                            .ERC_IS_APPROVED_FOR_ALL)
                                                                                            .withIsApprovedForAll(
                                                                                                    false))));

                                    var setApprovalForAll =
                                            ethereumCall(
                                                            ERC721_CONTRACT_WITH_HTS_CALLS,
                                                            "ercSetApprovalForAll",
                                                            asHeadlongAddress(
                                                                    Bytes.wrap(
                                                                                    createdTokenAddressString
                                                                                            .get()
                                                                                            .toByteArray())
                                                                            .toHexString()),
                                                            asHeadlongAddress(
                                                                    spenderAutoCreatedAccountId
                                                                            .get()),
                                                            true)
                                                    .type(EthTransactionType.EIP1559)
                                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                                    .payingWith(RELAYER)
                                                    .nonce(5)
                                                    .gasPrice(10L)
                                                    .gasLimit(1_000_000L)
                                                    .via("ercSetApproveForAllTxn")
                                                    .hasKnownStatusFrom(SUCCESS)
                                                    .logged();

                                    var isApprovedForAllAfter =
                                            ethereumCall(
                                                            ERC721_CONTRACT_WITH_HTS_CALLS,
                                                            "ercIsApprovedForAll",
                                                            asHeadlongAddress(
                                                                    Bytes.wrap(
                                                                                    createdTokenAddressString
                                                                                            .get()
                                                                                            .toByteArray())
                                                                            .toHexString()),
                                                            asHeadlongAddress(
                                                                    erc721ContractID.get()),
                                                            asHeadlongAddress(
                                                                    spenderAutoCreatedAccountId
                                                                            .get()))
                                                    .type(EthTransactionType.EIP1559)
                                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                                    .payingWith(RELAYER)
                                                    .nonce(6)
                                                    .gasPrice(10L)
                                                    .gasLimit(1_000_000L)
                                                    .via("ercIsApprovedForAllAfterTxn")
                                                    .hasKnownStatusFrom(SUCCESS)
                                                    .logged();

                                    var isApprovedForAllAfterCheck =
                                            childRecordsCheck(
                                                    "ercIsApprovedForAllAfterTxn",
                                                    SUCCESS,
                                                    recordWith()
                                                            .status(SUCCESS)
                                                            .contractCallResult(
                                                                    resultWith()
                                                                            .contractCallResult(
                                                                                    htsPrecompileResult()
                                                                                            .forFunction(
                                                                                                    FunctionType
                                                                                                            .ERC_IS_APPROVED_FOR_ALL)
                                                                                            .withIsApprovedForAll(
                                                                                                    true))));

                                    allRunFor(
                                            spec,
                                            associateTokenToERC721,
                                            associateTokenToSpender,
                                            isApprovedForAllBefore,
                                            isApprovedForAllBeforeCheck,
                                            setApprovalForAll,
                                            isApprovedForAllAfter,
                                            isApprovedForAllAfterCheck);
                                }))
                .then(withOpContext((spec, opLog) -> {}));
    }

    HapiSpec etx010TransferToCryptoAccountSucceeds() {
        String RECEIVER = "RECEIVER";
        final String aliasBalanceSnapshot = "aliasBalance";
        return defaultHapiSpec("ETX_010_transferToCryptoAccountSucceeds")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RECEIVER).balance(0L),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                        getTxnRecord("autoAccount").andAllChildRecords())
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
                                .via("payTxn")
                                .hasKnownStatus(SUCCESS))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                getTxnRecord("payTxn")
                                                        .logged()
                                                        .hasPriority(
                                                                recordWith()
                                                                        .status(SUCCESS)
                                                                        .contractCallResult(
                                                                                resultWith()
                                                                                        .logs(
                                                                                                inOrder())
                                                                                        .senderId(
                                                                                                spec.registry()
                                                                                                        .getAccountID(
                                                                                                                spec.registry()
                                                                                                                        .aliasIdFor(
                                                                                                                                SECP_256K1_SOURCE_KEY)
                                                                                                                        .getAlias()
                                                                                                                        .toStringUtf8())))
                                                                        .ethereumHash(
                                                                                ByteString.copyFrom(
                                                                                        spec.registry()
                                                                                                .getBytes(
                                                                                                        ETH_HASH_KEY)))))),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).has(accountWith().nonce(1L)),
                        getAccountBalance(RECEIVER).hasTinyBars(FIVE_HBARS),
                        getAutoCreatedAccountBalance(SECP_256K1_SOURCE_KEY)
                                .hasTinyBars(
                                        changeFromSnapshot(aliasBalanceSnapshot, -FIVE_HBARS)));
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
                .map(
                        params ->
                                // [0] - success
                                // [1] - sender gas price
                                // [2] - relayer offered
                                // [3] - sender charged amount
                                // relayer charged amount can easily be calculated via
                                // wholeTransactionFee - senderChargedAmount
                                matrixedPayerRelayerTest(
                                        (boolean) params[0],
                                        (long) params[1],
                                        (long) params[2],
                                        (long) params[3]))
                .toList();
    }

    HapiSpec matrixedPayerRelayerTest(
            final boolean success,
            final long senderGasPrice,
            final long relayerOffered,
            final long senderCharged) {
        return defaultHapiSpec(
                        "feePaymentMatrix "
                                + (success ? "Success/" : "Failure/")
                                + senderGasPrice
                                + "/"
                                + relayerOffered)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when()
                .then(
                        withOpContext(
                                (spec, ignore) -> {
                                    final String senderBalance = "senderBalance";
                                    final String payerBalance = "payerBalance";
                                    final var subop1 =
                                            balanceSnapshot(senderBalance, SECP_256K1_SOURCE_KEY)
                                                    .accountIsAlias();
                                    final var subop2 = balanceSnapshot(payerBalance, RELAYER);
                                    final var subop3 =
                                            ethereumCall(
                                                            PAY_RECEIVABLE_CONTRACT,
                                                            "deposit",
                                                            BigInteger.valueOf(depositAmount))
                                                    .type(EthTxData.EthTransactionType.EIP1559)
                                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                                    .payingWith(RELAYER)
                                                    .via("payTxn")
                                                    .nonce(0)
                                                    .maxGasAllowance(relayerOffered)
                                                    .maxFeePerGas(senderGasPrice)
                                                    .gasLimit(GAS_LIMIT)
                                                    .sending(depositAmount)
                                                    .hasKnownStatus(
                                                            success
                                                                    ? ResponseCodeEnum.SUCCESS
                                                                    : ResponseCodeEnum
                                                                            .INSUFFICIENT_TX_FEE);

                                    final HapiGetTxnRecord hapiGetTxnRecord =
                                            getTxnRecord("payTxn").logged();
                                    allRunFor(spec, subop1, subop2, subop3, hapiGetTxnRecord);

                                    final long wholeTransactionFee =
                                            hapiGetTxnRecord
                                                    .getResponseRecord()
                                                    .getTransactionFee();
                                    final var subop4 =
                                            getAutoCreatedAccountBalance(SECP_256K1_SOURCE_KEY)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(
                                                                    senderBalance,
                                                                    success
                                                                            ? (-depositAmount
                                                                                    - senderCharged)
                                                                            : 0));
                                    final var subop5 =
                                            getAccountBalance(RELAYER)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(
                                                                    payerBalance,
                                                                    success
                                                                            ? -(wholeTransactionFee
                                                                                    - senderCharged)
                                                                            : -wholeTransactionFee));
                                    allRunFor(spec, subop4, subop5);
                                }));
    }

    HapiSpec invalidTxData() {
        return defaultHapiSpec("InvalidTxData")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT))
                .when(
                        ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
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
                                .via("payTxn"))
                .then();
    }

    HapiSpec etx014ContractCreateInheritsSignerProperties() {
        final AtomicReference<String> contractID = new AtomicReference<>();
        final String MEMO = "memo";
        final String PROXY = "proxy";
        final long INITIAL_BALANCE = 100L;
        final long AUTO_RENEW_PERIOD = THREE_MONTHS_IN_SECONDS + 60;
        return defaultHapiSpec("ContractCreateInheritsProperties")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                        getTxnRecord("autoAccount").andAllChildRecords(),
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
                                .exposingNumTo(
                                        num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
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
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).logged(),
                        sourcing(
                                () ->
                                        getContractInfo(contractID.get())
                                                .logged()
                                                .has(
                                                        ContractInfoAsserts.contractWith()
                                                                .defaultAdminKey()
                                                                .autoRenew(AUTO_RENEW_PERIOD)
                                                                .balance(INITIAL_BALANCE)
                                                                .memo(MEMO))));
    }

    HapiSpec etx031InvalidNonceEthereumTxFailsAndChargesRelayer() {
        final var relayerSnapshot = "relayer";
        final var senderSnapshot = "sender";
        return defaultHapiSpec("ETX_031_invalidNonceEthereumTxFailsAndChargesRelayer")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        balanceSnapshot(relayerSnapshot, RELAYER),
                        balanceSnapshot(senderSnapshot, SECP_256K1_SOURCE_KEY).accountIsAlias(),
                        ethereumCall(
                                        PAY_RECEIVABLE_CONTRACT,
                                        "deposit",
                                        BigInteger.valueOf(depositAmount))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(999L)
                                .via("payTxn")
                                .hasKnownStatus(ResponseCodeEnum.WRONG_NONCE))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var payTxn =
                                            getTxnRecord("payTxn")
                                                    .logged()
                                                    .hasPriority(
                                                            recordWith()
                                                                    .ethereumHash(
                                                                            ByteString.copyFrom(
                                                                                    spec.registry()
                                                                                            .getBytes(
                                                                                                    ETH_HASH_KEY))));
                                    allRunFor(spec, payTxn);
                                    final var fee = payTxn.getResponseRecord().getTransactionFee();
                                    final var relayerBalance =
                                            getAccountBalance(RELAYER)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(
                                                                    relayerSnapshot, -fee));
                                    final var senderBalance =
                                            getAutoCreatedAccountBalance(SECP_256K1_SOURCE_KEY)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(senderSnapshot, 0));
                                    allRunFor(spec, relayerBalance, senderBalance);
                                }),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).has(accountWith().nonce(0L)));
    }

    HapiSpec etx012PrecompileCallSucceedsWhenNeededSignatureInEthTxn() {
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
        final String fungibleToken = "token";
        final String mintTxn = "mintTxn";
        return defaultHapiSpec("ETX_012_precompileCallSucceedsWhenNeededSignatureInEthTxn")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(HELLO_WORLD_MINT_CONTRACT),
                        tokenCreate(fungibleToken)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .adminKey(SECP_256K1_SOURCE_KEY)
                                .supplyKey(SECP_256K1_SOURCE_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))))
                .when(
                        sourcing(
                                () ->
                                        contractCreate(
                                                HELLO_WORLD_MINT_CONTRACT,
                                                asHeadlongAddress(asAddress(fungible.get())))),
                        ethereumCall(HELLO_WORLD_MINT_CONTRACT, "brrr", BigInteger.valueOf(5))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasPrice(50L)
                                .maxGasAllowance(FIVE_HBARS)
                                .gasLimit(1_000_000L)
                                .via(mintTxn)
                                .hasKnownStatus(SUCCESS))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                getTxnRecord(mintTxn)
                                                        .logged()
                                                        .hasPriority(
                                                                recordWith()
                                                                        .status(SUCCESS)
                                                                        .contractCallResult(
                                                                                resultWith()
                                                                                        .logs(
                                                                                                inOrder())
                                                                                        .senderId(
                                                                                                spec.registry()
                                                                                                        .getAccountID(
                                                                                                                spec.registry()
                                                                                                                        .aliasIdFor(
                                                                                                                                SECP_256K1_SOURCE_KEY)
                                                                                                                        .getAlias()
                                                                                                                        .toStringUtf8())))
                                                                        .ethereumHash(
                                                                                ByteString.copyFrom(
                                                                                        spec.registry()
                                                                                                .getBytes(
                                                                                                        ETH_HASH_KEY)))))));
    }

    HapiSpec etx013PrecompileCallSucceedsWhenNeededSignatureInHederaTxn() {
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
        final String fungibleToken = "token";
        final String mintTxn = "mintTxn";
        final String MULTI_KEY = "MULTI_KEY";
        return defaultHapiSpec("ETX_013_precompileCallSucceedsWhenNeededSignatureInHederaTxn")
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(HELLO_WORLD_MINT_CONTRACT),
                        tokenCreate(fungibleToken)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))))
                .when(
                        sourcing(
                                () ->
                                        contractCreate(
                                                HELLO_WORLD_MINT_CONTRACT,
                                                asHeadlongAddress(asAddress(fungible.get())))),
                        ethereumCall(HELLO_WORLD_MINT_CONTRACT, "brrr", BigInteger.valueOf(5))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .nonce(0)
                                .gasPrice(50L)
                                .maxGasAllowance(FIVE_HBARS)
                                .gasLimit(1_000_000L)
                                .via(mintTxn)
                                .hasKnownStatus(SUCCESS))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                getTxnRecord(mintTxn)
                                                        .logged()
                                                        .hasPriority(
                                                                recordWith()
                                                                        .status(SUCCESS)
                                                                        .contractCallResult(
                                                                                resultWith()
                                                                                        .logs(
                                                                                                inOrder())
                                                                                        .senderId(
                                                                                                spec.registry()
                                                                                                        .getAccountID(
                                                                                                                spec.registry()
                                                                                                                        .aliasIdFor(
                                                                                                                                SECP_256K1_SOURCE_KEY)
                                                                                                                        .getAlias()
                                                                                                                        .toStringUtf8())))
                                                                        .ethereumHash(
                                                                                ByteString.copyFrom(
                                                                                        spec.registry()
                                                                                                .getBytes(
                                                                                                        ETH_HASH_KEY)))))));
    }

    HapiSpec etx013PrecompileCallFailsWhenSignatureMissingFromBothEthereumAndHederaTxn() {
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
        final String fungibleToken = "token";
        final String mintTxn = "mintTxn";
        final String MULTI_KEY = "MULTI_KEY";
        return defaultHapiSpec(
                        "ETX_013_precompileCallFailsWhenSignatureMissingFromBothEthereumAndHederaTxn")
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(HELLO_WORLD_MINT_CONTRACT),
                        tokenCreate(fungibleToken)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))))
                .when(
                        sourcing(
                                () ->
                                        contractCreate(
                                                HELLO_WORLD_MINT_CONTRACT,
                                                asHeadlongAddress(asAddress(fungible.get())))),
                        ethereumCall(HELLO_WORLD_MINT_CONTRACT, "brrr", BigInteger.valueOf(5))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .nonce(0)
                                .via(mintTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                getTxnRecord(mintTxn)
                                                        .logged()
                                                        .hasPriority(
                                                                recordWith()
                                                                        .contractCallResult(
                                                                                resultWith()
                                                                                        .logs(
                                                                                                inOrder())
                                                                                        .senderId(
                                                                                                spec.registry()
                                                                                                        .getAccountID(
                                                                                                                spec.registry()
                                                                                                                        .aliasIdFor(
                                                                                                                                SECP_256K1_SOURCE_KEY)
                                                                                                                        .getAlias()
                                                                                                                        .toStringUtf8())))
                                                                        .ethereumHash(
                                                                                ByteString.copyFrom(
                                                                                        spec.registry()
                                                                                                .getBytes(
                                                                                                        ETH_HASH_KEY)))))),
                        childRecordsCheck(
                                mintTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)));
    }

    HapiSpec etx009CallsToTokenAddresses() {
        final AtomicReference<String> tokenNum = new AtomicReference<>();
        final var totalSupply = 50;

        return defaultHapiSpec("CallsToTokenAddresses")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoTransfer(
                                tinyBarsFromAccountToAlias(
                                        GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(totalSupply)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(SECP_256K1_SOURCE_KEY)
                                .supplyKey(SECP_256K1_SOURCE_KEY)
                                .exposingCreatedIdTo(tokenNum::set),
                        uploadInitCode(ERC20_CONTRACT),
                        contractCreate(ERC20_CONTRACT).adminKey(THRESHOLD))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                ethereumCallWithFunctionAbi(
                                                                true,
                                                                FUNGIBLE_TOKEN,
                                                                getABIFor(
                                                                        Utils.FunctionType.FUNCTION,
                                                                        "totalSupply",
                                                                        "ERC20ABI"))
                                                        .type(EthTxData.EthTransactionType.EIP1559)
                                                        .signingWith(SECP_256K1_SOURCE_KEY)
                                                        .payingWith(RELAYER)
                                                        .via("totalSupplyTxn")
                                                        .nonce(0)
                                                        .gasPrice(50L)
                                                        .maxGasAllowance(FIVE_HBARS)
                                                        .maxPriorityGas(2L)
                                                        .gasLimit(1_000_000L)
                                                        .hasKnownStatus(ResponseCodeEnum.SUCCESS))))
                .then(
                        childRecordsCheck(
                                "totalSupplyTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .ERC_TOTAL_SUPPLY)
                                                                        .withTotalSupply(
                                                                                totalSupply)))));
    }

    // ETX-011 and ETX-030
    HapiSpec originAndSenderAreEthereumSigner() {
        return defaultHapiSpec("originAndSenderAreEthereumSigner")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, SECP_256K1_SOURCE_KEY)),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(EMIT_SENDER_ORIGIN_CONTRACT),
                        contractCreate(EMIT_SENDER_ORIGIN_CONTRACT))
                .when(
                        ethereumCall(EMIT_SENDER_ORIGIN_CONTRACT, "logNow")
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .maxFeePerGas(50L)
                                .gasLimit(1_000_000L)
                                .via("payTxn")
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS))
                .then(
                        withOpContext(
                                (spec, ignore) ->
                                        allRunFor(
                                                spec,
                                                getTxnRecord("payTxn")
                                                        .logged()
                                                        .hasPriority(
                                                                recordWith()
                                                                        .contractCallResult(
                                                                                resultWith()
                                                                                        .logs(
                                                                                                inOrder(
                                                                                                        logWith()
                                                                                                                .ecdsaAliasStartingAt(
                                                                                                                        SECP_256K1_SOURCE_KEY,
                                                                                                                        12)
                                                                                                                .ecdsaAliasStartingAt(
                                                                                                                        SECP_256K1_SOURCE_KEY,
                                                                                                                        44)
                                                                                                                .withTopicsInOrder(
                                                                                                                        List
                                                                                                                                .of(
                                                                                                                                        eventSignatureOf(
                                                                                                                                                "Info(address,address)")))))
                                                                                        .senderId(
                                                                                                spec.registry()
                                                                                                        .getAccountID(
                                                                                                                spec.registry()
                                                                                                                        .aliasIdFor(
                                                                                                                                SECP_256K1_SOURCE_KEY)
                                                                                                                        .getAlias()
                                                                                                                        .toStringUtf8())))
                                                                        .ethereumHash(
                                                                                ByteString.copyFrom(
                                                                                        spec.registry()
                                                                                                .getBytes(
                                                                                                        ETH_HASH_KEY)))))),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).has(accountWith().nonce(1L)));
    }

    private HapiSpec etx008ContractCreateExecutesWithExpectedRecord() {
        final var txn = "creation";
        final var contract = "Fuse";

        return defaultHapiSpec("ETX_008_contractCreateExecutesWithExpectedRecord")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        uploadInitCode(contract),
                        ethereumContractCreate(contract)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .gasLimit(GAS_LIMIT)
                                .via(txn),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var op = getTxnRecord(txn);
                                    allRunFor(spec, op);
                                    final var record = op.getResponseRecord();
                                    final var creationResult = record.getContractCreateResult();
                                    final var createdIds =
                                            creationResult.getCreatedContractIDsList();
                                    assertEquals(
                                            4,
                                            createdIds.size(),
                                            "Expected four creations but got " + createdIds);
                                }))
                .when()
                .then();
    }

    private HapiSpec etx007FungibleTokenCreateWithFeesHappyPath() {
        final var createdTokenNum = new AtomicLong();
        final var feeCollectorAndAutoRenew = "feeCollectorAndAutoRenew";
        final var contract = "TokenCreateContract";
        final var EXISTING_TOKEN = "EXISTING_TOKEN";
        final var firstTxn = "firstCreateTxn";
        final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;

        return defaultHapiSpec("ETX_007_fungibleTokenCreateWithFeesHappyPath")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        cryptoCreate(feeCollectorAndAutoRenew)
                                .keyShape(SigControl.ED25519_ON)
                                .balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(contract),
                        contractCreate(contract).gas(GAS_LIMIT),
                        tokenCreate(EXISTING_TOKEN).decimals(5),
                        tokenAssociate(feeCollectorAndAutoRenew, EXISTING_TOKEN))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                ethereumCall(
                                                                contract,
                                                                "createTokenWithAllCustomFeesAvailable",
                                                                spec.registry()
                                                                        .getKey(
                                                                                SECP_256K1_SOURCE_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                feeCollectorAndAutoRenew))),
                                                                asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                EXISTING_TOKEN))),
                                                                asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                feeCollectorAndAutoRenew))),
                                                                8_000_000L)
                                                        .via(firstTxn)
                                                        .gasLimit(GAS_LIMIT)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .alsoSigningWithFullPrefix(
                                                                feeCollectorAndAutoRenew)
                                                        .exposingResultTo(
                                                                result -> {
                                                                    log.info(
                                                                            "Explicit create result"
                                                                                    + " is {}",
                                                                            result[0]);
                                                                    final var res =
                                                                            (Address) result[0];
                                                                    createdTokenNum.set(
                                                                            res.value()
                                                                                    .longValueExact());
                                                                }))))
                .then(
                        getTxnRecord(firstTxn).andAllChildRecords().logged(),
                        childRecordsCheck(
                                firstTxn,
                                ResponseCodeEnum.SUCCESS,
                                TransactionRecordAsserts.recordWith()
                                        .status(ResponseCodeEnum.SUCCESS)),
                        withOpContext(
                                (spec, ignore) -> {
                                    final var op = getTxnRecord(firstTxn);
                                    allRunFor(spec, op);

                                    final var callResult =
                                            op.getResponseRecord().getContractCallResult();
                                    final var gasUsed = callResult.getGasUsed();
                                    final var amount = callResult.getAmount();
                                    final var gasLimit = callResult.getGas();
                                    Assertions.assertEquals(DEFAULT_AMOUNT_TO_SEND, amount);
                                    Assertions.assertEquals(GAS_LIMIT, gasLimit);
                                    Assertions.assertTrue(gasUsed > 0L);
                                    Assertions.assertTrue(
                                            callResult.hasContractID() && callResult.hasSenderId());
                                }));
    }

    private HapiSpec etxSvc003ContractGetBytecodeQueryReturnsDeployedCode() {
        final var txn = "creation";
        final var contract = "EmptyConstructor";
        return HapiSpec.defaultHapiSpec("contractGetBytecodeQueryReturnsDeployedCode")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        uploadInitCode(contract),
                        ethereumContractCreate(contract)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .gasLimit(GAS_LIMIT)
                                .via(txn))
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var getBytecode =
                                            getContractBytecode(contract)
                                                    .saveResultTo("contractByteCode");
                                    allRunFor(spec, getBytecode);

                                    final var originalBytecode =
                                            Hex.decode(
                                                    Files.toByteArray(
                                                            new File(
                                                                    getResourcePath(
                                                                            contract, ".bin"))));
                                    final var actualBytecode =
                                            spec.registry().getBytes("contractByteCode");
                                    // The original bytecode is modified on deployment
                                    final var expectedBytecode =
                                            Arrays.copyOfRange(
                                                    originalBytecode, 29, originalBytecode.length);
                                    Assertions.assertArrayEquals(expectedBytecode, actualBytecode);
                                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
