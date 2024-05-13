/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.expectedPrecompileGasFor;
import static com.hedera.services.bdd.suites.contract.Utils.getNestedContractAddress;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;

import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class ContractMintHTSV1SecurityModelSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(ContractMintHTSV1SecurityModelSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String TOKEN_TREASURY = "treasury";
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE =
            KeyShape.threshOf(1, KeyShape.SIMPLE, DELEGATE_CONTRACT);
    private static final String DELEGATE_KEY = "DelegateKey";
    private static final String CONTRACT_KEY = "ContractKey";
    private static final String MULTI_KEY = "purpose";
    public static final String MINT_CONTRACT = "MintContract";
    public static final String MINT_NFT_CONTRACT = "MintNFTContract";
    private static final String NESTED_MINT_CONTRACT = "NestedMint";
    private static final String HELLO_WORLD_MINT = "HelloWorldMint";
    private static final String ACCOUNT = "anybody";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String FIRST_MINT_TXN = "firstMintTxn";
    private static final String SECOND_MINT_TXN = "secondMintTxn";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String TEST_METADATA_1 = "Test metadata 1";
    private static final String TEST_METADATA_2 = "Test metadata 2";
    private static final String RECIPIENT = "recipient";
    private static final String MINT_FUNGIBLE_TOKEN = "mintFungibleToken";
    public static final String MINT_FUNGIBLE_TOKEN_WITH_EVENT = "mintFungibleTokenWithEvent";

    public static void main(final String... args) {
        new ContractMintHTSV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<Stream<DynamicTest>> negativeSpecs() {
        return List.of(
                rollbackOnFailedAssociateAfterNonFungibleMint(), gasCostNotMetSetsInsufficientGasStatusInChildRecord());
    }

    List<Stream<DynamicTest>> positiveSpecs() {
        return List.of(
                helloWorldFungibleMint(),
                helloWorldNftMint(),
                happyPathFungibleTokenMint(),
                happyPathNonFungibleTokenMint(),
                happyPathZeroUnitFungibleTokenMint());
    }

    final Stream<DynamicTest> happyPathZeroUnitFungibleTokenMint() {
        final var amount = 0L;
        final var gasUsed = 14085L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("happyPathZeroUnitFungibleTokenMint")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).payingWith(GENESIS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))),
                        uploadInitCode(MINT_CONTRACT),
                        sourcing(() -> contractCreate(
                                MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(fungible.get())))))
                .when(
                        contractCall(MINT_CONTRACT, MINT_FUNGIBLE_TOKEN_WITH_EVENT, BigInteger.valueOf(amount))
                                .via(FIRST_MINT_TXN)
                                .gas(GAS_TO_OFFER)
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(MULTI_KEY),
                        getTxnRecord(FIRST_MINT_TXN).andAllChildRecords().logged())
                .then(childRecordsCheck(
                        FIRST_MINT_TXN,
                        SUCCESS,
                        recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .contractCallResult(htsPrecompileResult()
                                                .forFunction(FunctionType.HAPI_MINT)
                                                .withStatus(SUCCESS)
                                                .withTotalSupply(0)
                                                .withSerialNumbers())
                                        .gasUsed(gasUsed))
                                .newTotalSupply(0)));
    }

    final Stream<DynamicTest> helloWorldFungibleMint() {
        final var amount = 1_234_567L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("helloWorldFungibleMint")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))),
                        uploadInitCode(HELLO_WORLD_MINT))
                .when(
                        sourcing(() -> contractCreate(
                                HELLO_WORLD_MINT, HapiParserUtil.asHeadlongAddress(asAddress(fungible.get())))),
                        contractCall(HELLO_WORLD_MINT, "brrr", BigInteger.valueOf(amount))
                                .via(FIRST_MINT_TXN)
                                .alsoSigningWithFullPrefix(MULTI_KEY),
                        getTxnRecord(FIRST_MINT_TXN).andAllChildRecords().logged(),
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(amount),
                        /* And now make the token contract-controlled so no explicit supply sig is required */
                        newKeyNamed(CONTRACT_KEY).shape(DELEGATE_CONTRACT.signedWith(HELLO_WORLD_MINT)),
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                        getTokenInfo(FUNGIBLE_TOKEN).logged(),
                        contractCall(HELLO_WORLD_MINT, "brrr", BigInteger.valueOf(amount))
                                .via(SECOND_MINT_TXN),
                        getTxnRecord(SECOND_MINT_TXN).andAllChildRecords().logged(),
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(2 * amount))
                .then(childRecordsCheck(
                        SECOND_MINT_TXN,
                        SUCCESS,
                        recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .contractCallResult(htsPrecompileResult()
                                                .forFunction(FunctionType.HAPI_MINT)
                                                .withStatus(SUCCESS)
                                                .withTotalSupply(2469134L)
                                                .withSerialNumbers()))
                                .newTotalSupply(2469134L)
                                .tokenTransfers(
                                        changingFungibleBalances().including(FUNGIBLE_TOKEN, DEFAULT_PAYER, amount))));
    }

    final Stream<DynamicTest> helloWorldNftMint() {
        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("helloWorldNftMint")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(idLit -> nonFungible.set(asToken(idLit))),
                        uploadInitCode(HELLO_WORLD_MINT),
                        sourcing(() -> contractCreate(
                                HELLO_WORLD_MINT, HapiParserUtil.asHeadlongAddress(asAddress(nonFungible.get())))))
                .when(
                        contractCall(HELLO_WORLD_MINT, "mint")
                                .via(FIRST_MINT_TXN)
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(MULTI_KEY),
                        getTxnRecord(FIRST_MINT_TXN).andAllChildRecords().logged(),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(1),
                        /* And now make the token contract-controlled so no explicit supply sig is required */
                        newKeyNamed(CONTRACT_KEY).shape(DELEGATE_CONTRACT.signedWith(HELLO_WORLD_MINT)),
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).logged(),
                        contractCall(HELLO_WORLD_MINT, "mint")
                                .via(SECOND_MINT_TXN)
                                .gas(GAS_TO_OFFER),
                        getTxnRecord(SECOND_MINT_TXN).andAllChildRecords().logged())
                .then(
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(2),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).logged(),
                        childRecordsCheck(
                                FIRST_MINT_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_MINT)
                                                        .withStatus(SUCCESS)
                                                        .withTotalSupply(1)
                                                        .withSerialNumbers(1)))
                                        .newTotalSupply(1)
                                        .serialNos(List.of(1L))),
                        childRecordsCheck(
                                SECOND_MINT_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_MINT)
                                                        .withStatus(SUCCESS)
                                                        .withTotalSupply(2)
                                                        .withSerialNumbers(2)))
                                        .newTotalSupply(2)
                                        .serialNos(List.of(2L))));
    }

    final Stream<DynamicTest> happyPathFungibleTokenMint() {
        final var amount = 10L;
        final var gasUsed = 14085L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("happyPathFungibleTokenMint")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).payingWith(GENESIS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))),
                        uploadInitCode(MINT_CONTRACT),
                        sourcing(() -> contractCreate(
                                MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(fungible.get())))))
                .when(
                        contractCall(MINT_CONTRACT, MINT_FUNGIBLE_TOKEN_WITH_EVENT, BigInteger.valueOf(10))
                                .via(FIRST_MINT_TXN)
                                .gas(GAS_TO_OFFER)
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(MULTI_KEY),
                        getTxnRecord(FIRST_MINT_TXN).andAllChildRecords().logged(),
                        getTxnRecord(FIRST_MINT_TXN)
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith()
                                                .logs(inOrder(logWith()
                                                        .noData()
                                                        .withTopicsInOrder(List.of(
                                                                parsedToByteString(amount), parsedToByteString(0))))))))
                .then(
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(amount),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(FUNGIBLE_TOKEN, amount),
                        childRecordsCheck(
                                FIRST_MINT_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_MINT)
                                                        .withStatus(SUCCESS)
                                                        .withTotalSupply(10)
                                                        .withSerialNumbers())
                                                .gasUsed(gasUsed))
                                        .newTotalSupply(10)));
    }

    final Stream<DynamicTest> happyPathNonFungibleTokenMint() {
        final var totalSupply = 2;
        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("happyPathNonFungibleTokenMint")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(idLit -> nonFungible.set(asToken(idLit))),
                        uploadInitCode(MINT_CONTRACT),
                        sourcing(() -> contractCreate(
                                MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(nonFungible.get())))))
                .when(
                        contractCall(MINT_CONTRACT, "mintNonFungibleTokenWithEvent", (Object)
                                        new byte[][] {TEST_METADATA_1.getBytes(), TEST_METADATA_2.getBytes()})
                                .via(FIRST_MINT_TXN)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(MULTI_KEY),
                        getTxnRecord(FIRST_MINT_TXN).andAllChildRecords().logged(),
                        getTxnRecord(FIRST_MINT_TXN)
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith()
                                                .logs(inOrder(logWith()
                                                        .noData()
                                                        .withTopicsInOrder(List.of(
                                                                parsedToByteString(totalSupply),
                                                                parsedToByteString(1))))))))
                .then(
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(totalSupply),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NON_FUNGIBLE_TOKEN, totalSupply),
                        childRecordsCheck(
                                FIRST_MINT_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_MINT)
                                                        .withStatus(SUCCESS)
                                                        .withTotalSupply(2L)
                                                        .withSerialNumbers(1L, 2L))
                                                .gasUsed(563380L))
                                        .newTotalSupply(2)
                                        .serialNos(Arrays.asList(1L, 2L))));
    }

    final Stream<DynamicTest> rollbackOnFailedAssociateAfterNonFungibleMint() {
        final var nestedMintTxn = "nestedMintTxn";

        return propertyPreservingHapiSpec("rollbackOnFailedAssociateAfterNonFungibleMint")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(MINT_NFT_CONTRACT, NESTED_MINT_CONTRACT),
                        contractCreate(MINT_NFT_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                        NESTED_MINT_CONTRACT,
                                        asHeadlongAddress(getNestedContractAddress(MINT_NFT_CONTRACT, spec)),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .gas(GAS_TO_OFFER),
                        newKeyNamed(DELEGATE_KEY)
                                .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, NESTED_MINT_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
                        contractCall(
                                        NESTED_MINT_CONTRACT,
                                        "revertMintAfterFailedAssociate",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        new byte[][] {TEST_METADATA_1.getBytes()})
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .via(nestedMintTxn)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTxnRecord(nestedMintTxn).andAllChildRecords().logged())))
                .then(
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
                        childRecordsCheck(
                                nestedMintTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(REVERTED_SUCCESS)
                                        .newTotalSupply(0)
                                        .serialNos(List.of()),
                                recordWith()
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(INVALID_TOKEN_ID)))));
    }

    final Stream<DynamicTest> gasCostNotMetSetsInsufficientGasStatusInChildRecord() {
        final var amount = 10L;
        final var baselineMintWithEnoughGas = "baselineMintWithEnoughGas";

        final AtomicLong expectedInsufficientGas = new AtomicLong();
        final AtomicReference<TokenID> fungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("gasCostNotMetSetsInsufficientGasStatusInChildRecord")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(5 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))))
                .when(uploadInitCode(MINT_CONTRACT), sourcing(() -> contractCreate(
                                MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(fungible.get())))
                        .payingWith(ACCOUNT)
                        .gas(GAS_TO_OFFER)))
                .then(
                        contractCall(MINT_CONTRACT, MINT_FUNGIBLE_TOKEN, BigInteger.valueOf(amount))
                                .via(baselineMintWithEnoughGas)
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(64_000L),
                        withOpContext((spec, opLog) -> {
                            final var expectedPrecompileGas =
                                    expectedPrecompileGasFor(spec, TokenMint, TOKEN_FUNGIBLE_COMMON);
                            final var baselineCostLookup = getTxnRecord(baselineMintWithEnoughGas)
                                    .andAllChildRecords()
                                    .logged()
                                    .assertingNothing();
                            allRunFor(spec, baselineCostLookup);
                            final var baselineGas = baselineCostLookup
                                    .getResponseRecord()
                                    .getContractCallResult()
                                    .getGasUsed();
                            expectedInsufficientGas.set(baselineGas - expectedPrecompileGas);
                        }),
                        sourcing(() -> contractCall(MINT_CONTRACT, MINT_FUNGIBLE_TOKEN, BigInteger.valueOf(amount))
                                .via(FIRST_MINT_TXN)
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(expectedInsufficientGas.get())
                                .hasKnownStatus(INSUFFICIENT_GAS)),
                        getTxnRecord(FIRST_MINT_TXN).andAllChildRecords().logged(),
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(amount),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(FUNGIBLE_TOKEN, amount),
                        childRecordsCheck(
                                FIRST_MINT_TXN,
                                INSUFFICIENT_GAS,
                                recordWith()
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_MINT)
                                                        .withStatus(INSUFFICIENT_GAS)
                                                        .withTotalSupply(0L)
                                                        .withSerialNumbers()))));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
