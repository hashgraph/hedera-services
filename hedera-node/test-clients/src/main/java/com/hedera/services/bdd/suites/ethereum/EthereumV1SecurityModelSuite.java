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

import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getLiteralAliasContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Address;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData.EthTransactionType;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;

@SuppressWarnings("java:S5960")
public class EthereumV1SecurityModelSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(EthereumV1SecurityModelSuite.class);
    private static final String TOKEN_CREATE_CONTRACT = "NewTokenCreateContract";
    private static final String ERC721_CONTRACT_WITH_HTS_CALLS = "ERC721ContractWithHTSCalls";
    private static final String HELLO_WORLD_MINT_CONTRACT = "HelloWorldMint";
    public static final long GAS_LIMIT = 1_000_000;

    private static final String AUTO_ACCOUNT_TRANSACTION_NAME = "autoAccount";
    private static final String TOKEN = "token";
    private static final String MINT_TXN = "mintTxn";

    public static void main(String... args) {
        new EthereumV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(
                etx007FungibleTokenCreateWithFeesHappyPath(),
                etx012PrecompileCallSucceedsWhenNeededSignatureInEthTxn(),
                etx013PrecompileCallSucceedsWhenNeededSignatureInHederaTxn(),
                setApproveForAllUsingLocalNodeSetupPasses());
    }

    final Stream<DynamicTest> setApproveForAllUsingLocalNodeSetupPasses() {
        final AtomicReference<String> spenderAutoCreatedAccountId = new AtomicReference<>();
        final AtomicReference<String> tokenCreateContractID = new AtomicReference<>();
        final AtomicReference<String> erc721ContractID = new AtomicReference<>();
        final AtomicReference<String> contractAddressID = new AtomicReference<>();
        final AtomicReference<ByteString> createdTokenAddressString = new AtomicReference<>();
        final String spenderAlias = "spenderAlias";
        final var createTokenContractNum = new AtomicLong();
        return propertyPreservingHapiSpec("SetApproveForAllUsingLocalNodeSetupPasses")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(spenderAlias).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, spenderAlias, ONE_HUNDRED_HBARS))
                                .via("autoAccountSpender"),
                        getAliasedAccountInfo(spenderAlias)
                                .exposingContractAccountIdTo(spenderAutoCreatedAccountId::set),
                        createLargeFile(
                                GENESIS, TOKEN_CREATE_CONTRACT, TxnUtils.literalInitcodeFor(TOKEN_CREATE_CONTRACT)),
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
                        getContractInfo(TOKEN_CREATE_CONTRACT).exposingEvmAddress(tokenCreateContractID::set))
                .when(
                        withOpContext((spec, opLog) -> {
                            var createNFTPublicFunctionCall = ethereumCall(
                                            TOKEN_CREATE_CONTRACT,
                                            "createNonFungibleTokenPublic",
                                            asHeadlongAddress(tokenCreateContractID.get()))
                                    .type(EthTransactionType.EIP1559)
                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                    .payingWith(RELAYER)
                                    .nonce(1)
                                    .gasPrice(10L)
                                    .sending(10000000000L)
                                    .gasLimit(1_000_000L)
                                    .via("createTokenTxn")
                                    .exposingEventDataTo(createdTokenAddressString::set);

                            allRunFor(spec, createNFTPublicFunctionCall);

                            var uploadEthereumContract = uploadInitCode(ERC721_CONTRACT_WITH_HTS_CALLS);
                            allRunFor(spec, uploadEthereumContract);

                            var createEthereumContract = ethereumContractCreate(ERC721_CONTRACT_WITH_HTS_CALLS)
                                    .type(EthTxData.EthTransactionType.EIP1559)
                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                    .payingWith(RELAYER)
                                    .nonce(2)
                                    .gasPrice(10L)
                                    .maxGasAllowance(ONE_HUNDRED_HBARS)
                                    .gasLimit(1_000_000L)
                                    .hasKnownStatusFrom(SUCCESS);

                            var exposeEthereumContractAddress = getContractInfo(ERC721_CONTRACT_WITH_HTS_CALLS)
                                    .exposingEvmAddress(address -> erc721ContractID.set("0x" + address));
                            allRunFor(spec, createEthereumContract, exposeEthereumContractAddress);

                            var contractInfo = getLiteralAliasContractInfo(
                                            erc721ContractID.get().substring(2))
                                    .exposingEvmAddress(contractAddressID::set);
                            allRunFor(spec, contractInfo);
                            assertEquals(erc721ContractID.get().substring(2), contractAddressID.get());
                        }),
                        withOpContext((spec, opLog) -> {
                            var associateTokenToERC721 = ethereumCall(
                                            ERC721_CONTRACT_WITH_HTS_CALLS,
                                            "associateTokenPublic",
                                            asHeadlongAddress(erc721ContractID.get()),
                                            asHeadlongAddress(Bytes.wrap(createdTokenAddressString
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

                            var associateTokenToSpender = ethereumCall(
                                            TOKEN_CREATE_CONTRACT,
                                            "associateTokenPublic",
                                            asHeadlongAddress(spenderAutoCreatedAccountId.get()),
                                            asHeadlongAddress(Bytes.wrap(createdTokenAddressString
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

                            var isApprovedForAllBefore = ethereumCall(
                                            ERC721_CONTRACT_WITH_HTS_CALLS,
                                            "ercIsApprovedForAll",
                                            asHeadlongAddress(Bytes.wrap(createdTokenAddressString
                                                            .get()
                                                            .toByteArray())
                                                    .toHexString()),
                                            asHeadlongAddress(erc721ContractID.get()),
                                            asHeadlongAddress(spenderAutoCreatedAccountId.get()))
                                    .type(EthTransactionType.EIP1559)
                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                    .payingWith(RELAYER)
                                    .nonce(4)
                                    .gasPrice(10L)
                                    .gasLimit(1_000_000L)
                                    .via("ercIsApprovedForAllBeforeTxn")
                                    .hasKnownStatusFrom(SUCCESS)
                                    .logged();

                            var isApprovedForAllBeforeCheck = childRecordsCheck(
                                    "ercIsApprovedForAllBeforeTxn",
                                    SUCCESS,
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.ERC_IS_APPROVED_FOR_ALL)
                                                            .withIsApprovedForAll(false))));

                            var setApprovalForAll = ethereumCall(
                                            ERC721_CONTRACT_WITH_HTS_CALLS,
                                            "ercSetApprovalForAll",
                                            asHeadlongAddress(Bytes.wrap(createdTokenAddressString
                                                            .get()
                                                            .toByteArray())
                                                    .toHexString()),
                                            asHeadlongAddress(spenderAutoCreatedAccountId.get()),
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

                            var isApprovedForAllAfter = ethereumCall(
                                            ERC721_CONTRACT_WITH_HTS_CALLS,
                                            "ercIsApprovedForAll",
                                            asHeadlongAddress(Bytes.wrap(createdTokenAddressString
                                                            .get()
                                                            .toByteArray())
                                                    .toHexString()),
                                            asHeadlongAddress(erc721ContractID.get()),
                                            asHeadlongAddress(spenderAutoCreatedAccountId.get()))
                                    .type(EthTransactionType.EIP1559)
                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                    .payingWith(RELAYER)
                                    .nonce(6)
                                    .gasPrice(10L)
                                    .gasLimit(1_000_000L)
                                    .via("ercIsApprovedForAllAfterTxn")
                                    .hasKnownStatusFrom(SUCCESS)
                                    .logged();

                            var isApprovedForAllAfterCheck = childRecordsCheck(
                                    "ercIsApprovedForAllAfterTxn",
                                    SUCCESS,
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.ERC_IS_APPROVED_FOR_ALL)
                                                            .withIsApprovedForAll(true))));

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

    final Stream<DynamicTest> etx012PrecompileCallSucceedsWhenNeededSignatureInEthTxn() {
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
        final String fungibleToken = TOKEN;
        final String mintTxn = MINT_TXN;
        return propertyPreservingHapiSpec("etx012PrecompileCallSucceedsWhenNeededSignatureInEthTxn")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate,TokenMint",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
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
                                .adminKey(SECP_256K1_SOURCE_KEY)
                                .supplyKey(SECP_256K1_SOURCE_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))))
                .when(
                        sourcing(() -> contractCreate(
                                HELLO_WORLD_MINT_CONTRACT, asHeadlongAddress(asAddress(fungible.get())))),
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
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        getTxnRecord(mintTxn)
                                .logged()
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .logs(inOrder())
                                                .senderId(spec.registry()
                                                        .getAccountID(spec.registry()
                                                                .keyAliasIdFor(SECP_256K1_SOURCE_KEY)
                                                                .getAlias()
                                                                .toStringUtf8())))
                                        .ethereumHash(ByteString.copyFrom(
                                                spec.registry().getBytes(ETH_HASH_KEY)))))));
    }

    final Stream<DynamicTest> etx013PrecompileCallSucceedsWhenNeededSignatureInHederaTxn() {
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
        final String fungibleToken = TOKEN;
        final String mintTxn = MINT_TXN;
        final String MULTI_KEY = "MULTI_KEY";
        return propertyPreservingHapiSpec("etx013PrecompileCallSucceedsWhenNeededSignatureInHederaTxn")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate,TokenMint",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
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
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .nonce(0)
                                .gasPrice(50L)
                                .maxGasAllowance(FIVE_HBARS)
                                .gasLimit(1_000_000L)
                                .via(mintTxn)
                                .hasKnownStatus(SUCCESS))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        getTxnRecord(mintTxn)
                                .logged()
                                .hasPriority(recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .logs(inOrder())
                                                .senderId(spec.registry()
                                                        .getAccountID(spec.registry()
                                                                .keyAliasIdFor(SECP_256K1_SOURCE_KEY)
                                                                .getAlias()
                                                                .toStringUtf8())))
                                        .ethereumHash(ByteString.copyFrom(
                                                spec.registry().getBytes(ETH_HASH_KEY)))))));
    }

    final Stream<DynamicTest> etx007FungibleTokenCreateWithFeesHappyPath() {
        final var createdTokenNum = new AtomicLong();
        final var feeCollectorAndAutoRenew = "feeCollectorAndAutoRenew";
        final var contract = "TokenCreateContract";
        final var EXISTING_TOKEN = "EXISTING_TOKEN";
        final var firstTxn = "firstCreateTxn";
        final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;

        return propertyPreservingHapiSpec("etx007FungibleTokenCreateWithFeesHappyPath")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT_TRANSACTION_NAME),
                        cryptoCreate(feeCollectorAndAutoRenew)
                                .keyShape(SigControl.ED25519_ON)
                                .balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(contract),
                        contractCreate(contract).gas(GAS_LIMIT),
                        tokenCreate(EXISTING_TOKEN).decimals(5),
                        tokenAssociate(feeCollectorAndAutoRenew, EXISTING_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        ethereumCall(
                                        contract,
                                        "createTokenWithAllCustomFeesAvailable",
                                        spec.registry()
                                                .getKey(SECP_256K1_SOURCE_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(feeCollectorAndAutoRenew))),
                                        asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(EXISTING_TOKEN))),
                                        asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(feeCollectorAndAutoRenew))),
                                        8_000_000L)
                                .via(firstTxn)
                                .gasLimit(GAS_LIMIT)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .alsoSigningWithFullPrefix(feeCollectorAndAutoRenew)
                                .exposingResultTo(result -> {
                                    log.info("Explicit create result" + " is {}", result[0]);
                                    final var res = (Address) result[0];
                                    createdTokenNum.set(res.value().longValueExact());
                                }))))
                .then(
                        getTxnRecord(firstTxn).andAllChildRecords().logged(),
                        childRecordsCheck(
                                firstTxn,
                                ResponseCodeEnum.SUCCESS,
                                TransactionRecordAsserts.recordWith().status(ResponseCodeEnum.SUCCESS)),
                        withOpContext((spec, ignore) -> {
                            final var op = getTxnRecord(firstTxn);
                            allRunFor(spec, op);

                            final var callResult = op.getResponseRecord().getContractCallResult();
                            final var gasUsed = callResult.getGasUsed();
                            final var amount = callResult.getAmount();
                            final var gasLimit = callResult.getGas();
                            Assertions.assertEquals(DEFAULT_AMOUNT_TO_SEND, amount);
                            Assertions.assertEquals(GAS_LIMIT, gasLimit);
                            Assertions.assertTrue(gasUsed > 0L);
                            Assertions.assertTrue(callResult.hasContractID() && callResult.hasSenderId());
                        }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
