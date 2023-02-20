/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.utils.contracts.FunctionParameters.functionParameters;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.utils.contracts.FunctionParameters;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class ContractMintHTSSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(ContractMintHTSSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final long TOTAL_SUPPLY = 1_000;
    private static final String TOKEN_TREASURY = "treasury";
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE =
            KeyShape.threshOf(1, KeyShape.SIMPLE, DELEGATE_CONTRACT);
    private static final String DELEGATE_KEY = "DelegateKey";
    private static final String CONTRACT_KEY = "ContractKey";
    private static final String MULTI_KEY = "purpose";

    private static final String MINT_CONTRACT = "MintContract";
    private static final String MINT_NFT_CONTRACT = "MintNFTContract";
    private static final String NESTED_MINT_CONTRACT = "NestedMint";
    private static final String HELLO_WORLD_MINT = "HelloWorldMint";
    private static final String ACCOUNT = "anybody";
    private static final String DELEGATE_CONTRACT_KEY_NAME = "contractKey";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String FIRST_MINT_TXN = "firstMintTxn";
    private static final String SECOND_MINT_TXN = "secondMintTxn";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String TEST_METADATA_1 = "Test metadata 1";
    private static final String TEST_METADATA_2 = "Test metadata 2";
    private static final String RECIPIENT = "recipient";
    private static final String MINT_FUNGIBLE_TOKEN = "mintFungibleToken";

    public static void main(final String... args) {
        new ContractMintHTSSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(
                rollbackOnFailedMintAfterFungibleTransfer(),
                rollbackOnFailedAssociateAfterNonFungibleMint(),
                gasCostNotMetSetsInsufficientGasStatusInChildRecord());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(
                helloWorldFungibleMint(),
                helloWorldNftMint(),
                happyPathFungibleTokenMint(),
                happyPathNonFungibleTokenMint(),
                transferNftAfterNestedMint(),
                happyPathZeroUnitFungibleTokenMint());
    }

    private HapiSpec happyPathZeroUnitFungibleTokenMint() {
        final var amount = 0L;
        final var gasUsed = 14085L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();

        return defaultHapiSpec("happyPathZeroUnitFungibleTokenMint")
                .given(
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
                        contractCall(MINT_CONTRACT, "mintFungibleTokenWithEvent", BigInteger.valueOf(amount))
                                .via(FIRST_MINT_TXN)
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

    private HapiSpec helloWorldFungibleMint() {
        final var amount = 1_234_567L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();

        return defaultHapiSpec("HelloWorldFungibleMint")
                .given(
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

    private HapiSpec helloWorldNftMint() {
        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();

        return defaultHapiSpec("HelloWorldNftMint")
                .given(
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

    private HapiSpec happyPathFungibleTokenMint() {
        final var amount = 10L;
        final var gasUsed = 14085L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();

        return defaultHapiSpec("FungibleMint")
                .given(
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
                        contractCall(MINT_CONTRACT, "mintFungibleTokenWithEvent", BigInteger.valueOf(10))
                                .via(FIRST_MINT_TXN)
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

    private HapiSpec happyPathNonFungibleTokenMint() {
        final var totalSupply = 2;
        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();

        return defaultHapiSpec("NonFungibleMint")
                .given(
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
                                                .gasUsed(704226L))
                                        .newTotalSupply(2)
                                        .serialNos(Arrays.asList(1L, 2L))));
    }

    private HapiSpec transferNftAfterNestedMint() {
        final var nestedTransferTxn = "nestedTransferTxn";

        return defaultHapiSpec("TransferNftAfterNestedMint")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT).maxAutomaticTokenAssociations(1),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(NESTED_MINT_CONTRACT, MINT_NFT_CONTRACT),
                        contractCreate(MINT_NFT_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                        NESTED_MINT_CONTRACT,
                                        asHeadlongAddress(getNestedContractAddress(MINT_NFT_CONTRACT, spec)),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .gas(GAS_TO_OFFER),
                        newKeyNamed(DELEGATE_CONTRACT_KEY_NAME)
                                .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, NESTED_MINT_CONTRACT))),
                        cryptoUpdate(TOKEN_TREASURY).key(DELEGATE_CONTRACT_KEY_NAME),
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(DELEGATE_CONTRACT_KEY_NAME),
                        contractCall(
                                        NESTED_MINT_CONTRACT,
                                        "sendNFTAfterMint",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(TOKEN_TREASURY))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        new byte[][] {TEST_METADATA_1.getBytes()},
                                        1L)
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .via(nestedTransferTxn)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTxnRecord(nestedTransferTxn).andAllChildRecords().logged())))
                .then(
                        withOpContext((spec, opLog) -> {
                            if (!spec.isUsingEthCalls()) {
                                allRunFor(
                                        spec,
                                        assertTxnRecordHasNoTraceabilityEnrichedContractFnResult(nestedTransferTxn));
                            }
                        }),
                        withOpContext((spec, opLog) -> {
                            final var expectedGasUsage =
                                    expectedPrecompileGasFor(spec, TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE);
                            allRunFor(
                                    spec,
                                    childRecordsCheck(
                                            nestedTransferTxn,
                                            SUCCESS,
                                            recordWith()
                                                    .status(SUCCESS)
                                                    .contractCallResult(resultWith()
                                                            .approxGasUsed(expectedGasUsage, 5)
                                                            .contractCallResult(htsPrecompileResult()
                                                                    .forFunction(FunctionType.HAPI_MINT)
                                                                    .withStatus(SUCCESS)
                                                                    .withTotalSupply(1L)
                                                                    .withSerialNumbers(1L))
                                                            .gas(3_838_738L)
                                                            .amount(0L)
                                                            .functionParameters(functionParameters()
                                                                    .forFunction(
                                                                            FunctionParameters.PrecompileFunction.MINT)
                                                                    .withTokenAddress(
                                                                            asAddress(
                                                                                    spec.registry()
                                                                                            .getTokenID(
                                                                                                    NON_FUNGIBLE_TOKEN)))
                                                                    .withAmount(0L)
                                                                    .withMetadata(List.of("Test metadata" + " 1"))
                                                                    .build())),
                                            recordWith()
                                                    .status(SUCCESS)
                                                    .contractCallResult(resultWith()
                                                            .contractCallResult(htsPrecompileResult()
                                                                    .withStatus(SUCCESS)))
                                                    .tokenTransfers(NonFungibleTransfers.changingNFTBalances()
                                                            .including(
                                                                    NON_FUNGIBLE_TOKEN,
                                                                    TOKEN_TREASURY,
                                                                    RECIPIENT,
                                                                    1))));
                        }));
    }

    @SuppressWarnings("java:S5669")
    private HapiSpec rollbackOnFailedMintAfterFungibleTransfer() {
        final var failedMintTxn = "failedMintTxn";

        return defaultHapiSpec("RollbackOnFailedMintAfterFungibleTransfer")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(5 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        uploadInitCode(MINT_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                MINT_CONTRACT,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)))),
                        newKeyNamed(DELEGATE_KEY)
                                .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, MINT_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
                        contractCall(
                                        MINT_CONTRACT,
                                        "revertMintAfterFailedMint",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        20L)
                                .payingWith(GENESIS)
                                .via(failedMintTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTxnRecord(failedMintTxn).andAllChildRecords().logged())))
                .then(
                        getAccountBalance(ACCOUNT).hasTokenBalance(FUNGIBLE_TOKEN, 200),
                        getAccountBalance(RECIPIENT).hasTokenBalance(FUNGIBLE_TOKEN, 0),
                        childRecordsCheck(
                                failedMintTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(REVERTED_SUCCESS),
                                recordWith()
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_MINT)
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                                        .withTotalSupply(0L)
                                                        .withSerialNumbers()))));
    }

    private HapiSpec rollbackOnFailedAssociateAfterNonFungibleMint() {
        final var nestedMintTxn = "nestedMintTxn";

        return defaultHapiSpec("RollbackOnFailedAssociateAfterNonFungibleMint")
                .given(
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

    private HapiSpec gasCostNotMetSetsInsufficientGasStatusInChildRecord() {
        final var amount = 10L;
        final var baselineMintWithEnoughGas = "baselineMintWithEnoughGas";

        final AtomicLong expectedInsufficientGas = new AtomicLong();
        final AtomicReference<TokenID> fungible = new AtomicReference<>();

        return defaultHapiSpec("gasCostNotMetSetsInsufficientGasStatusInChildRecord")
                .given(
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

    private long expectedPrecompileGasFor(final HapiSpec spec, final HederaFunctionality function, final SubType type) {
        final var gasThousandthsOfTinycentPrice = spec.fees()
                .getCurrentOpFeeData()
                .get(ContractCall)
                .get(DEFAULT)
                .getServicedata()
                .getGas();
        final var assetsLoader = new AssetsLoader();
        final BigDecimal hapiUsdPrice;
        try {
            hapiUsdPrice = assetsLoader.loadCanonicalPrices().get(function).get(type);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        final var precompileTinycentPrice = hapiUsdPrice
                .multiply(BigDecimal.valueOf(1.2))
                .multiply(BigDecimal.valueOf(100 * 100_000_000L))
                .longValueExact();
        return (precompileTinycentPrice * 1000 / gasThousandthsOfTinycentPrice);
    }

    @NotNull
    private String getNestedContractAddress(final String contract, final HapiSpec spec) {
        return AssociatePrecompileSuite.getNestedContractAddress(contract, spec);
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }

    @NotNull
    @SuppressWarnings("java:S5960")
    private CustomSpecAssert assertTxnRecordHasNoTraceabilityEnrichedContractFnResult(final String nestedTransferTxn) {
        return assertionsHold((spec, log) -> {
            final var subOp = getTxnRecord(nestedTransferTxn);
            allRunFor(spec, subOp);

            final var rcd = subOp.getResponseRecord();

            final var contractCallResult = rcd.getContractCallResult();
            assertEquals(0L, contractCallResult.getGas(), "Result not expected to externalize gas");
            assertEquals(0L, contractCallResult.getAmount(), "Result not expected to externalize amount");
            assertEquals(ByteString.EMPTY, contractCallResult.getFunctionParameters());
        });
    }
}
