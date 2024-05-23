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

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asHexedAddress;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class ContractBurnHTSV1SecurityModelSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(ContractBurnHTSV1SecurityModelSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    public static final String THE_BURN_CONTRACT = "BurnToken";
    public static final String ALICE = "Alice";
    private static final String TOKEN = "Token";
    private static final String TOKEN_TREASURY = "TokenTreasury";
    private static final String MULTI_KEY = "purpose";
    private static final String SUPPLY_KEY = "Supply key";
    private static final String CONTRACT_KEY = "Contract key";
    public static final String CREATION_TX = "creationTx";
    private static final String BURN_AFTER_NESTED_MINT_TX = "burnAfterNestedMint";
    public static final String BURN_TOKEN_WITH_EVENT = "burnTokenWithEvent";
    private static final String FIRST = "First!";
    private static final String SECOND = "Second!";

    public static void main(String... args) {
        new ContractBurnHTSV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<Stream<DynamicTest>> negativeSpecs() {
        return List.of(hscsPreC020RollbackBurnThatFailsAfterAPrecompileTransfer());
    }

    List<Stream<DynamicTest>> positiveSpecs() {
        return List.of(
                hscsPrec004TokenBurnOfFungibleTokenUnits(),
                hscsPrec005TokenBurnOfNft(),
                hscsPrec011BurnAfterNestedMint());
    }

    final Stream<DynamicTest> hscsPreC020RollbackBurnThatFailsAfterAPrecompileTransfer() {
        final var bob = "bob";
        final var feeCollector = "feeCollector";
        final var tokenWithHbarFee = "tokenWithHbarFee";
        final var theContract = "TransferAndBurn";

        return propertyPreservingHapiSpec("hscsPreC020RollbackBurnThatFailsAfterAPrecompileTransfer")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenBurn,TokenCreate,TokenMint",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(ALICE).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(bob).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(feeCollector).balance(0L),
                        tokenCreate(tokenWithHbarFee)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .initialSupply(0L)
                                .treasury(TOKEN_TREASURY)
                                .withCustom(fixedHbarFee(300 * ONE_HBAR, feeCollector)),
                        mintToken(tokenWithHbarFee, List.of(copyFromUtf8(FIRST))),
                        mintToken(tokenWithHbarFee, List.of(copyFromUtf8(SECOND))),
                        uploadInitCode(theContract),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCreate(
                                                theContract,
                                                asHeadlongAddress(asHexedAddress(
                                                        spec.registry().getTokenID(tokenWithHbarFee))))
                                        .payingWith(bob)
                                        .gas(GAS_TO_OFFER))),
                        tokenAssociate(ALICE, tokenWithHbarFee),
                        tokenAssociate(bob, tokenWithHbarFee),
                        tokenAssociate(theContract, tokenWithHbarFee),
                        cryptoTransfer(movingUnique(tokenWithHbarFee, 2L).between(TOKEN_TREASURY, ALICE))
                                .payingWith(GENESIS),
                        getAccountInfo(feeCollector)
                                .has(AccountInfoAsserts.accountWith().balance(0L)))
                .when(
                        withOpContext((spec, opLog) -> {
                            final var serialNumbers = new long[] {1L};
                            allRunFor(
                                    spec,
                                    contractCall(
                                                    theContract,
                                                    "transferBurn",
                                                    HapiParserUtil.asHeadlongAddress(asAddress(
                                                            spec.registry().getAccountID(ALICE))),
                                                    HapiParserUtil.asHeadlongAddress(asAddress(
                                                            spec.registry().getAccountID(bob))),
                                                    BigInteger.ZERO,
                                                    2L,
                                                    serialNumbers)
                                            .alsoSigningWithFullPrefix(ALICE, SUPPLY_KEY)
                                            .gas(GAS_TO_OFFER)
                                            .via("contractCallTxn")
                                            .hasKnownStatus(CONTRACT_REVERT_EXECUTED));
                        }),
                        childRecordsCheck(
                                "contractCallTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(REVERTED_SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_BURN)
                                                        .withStatus(SUCCESS)
                                                        .withTotalSupply(1))),
                                recordWith()
                                        .status(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(
                                                                INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE)))))
                .then(
                        getAccountBalance(bob).hasTokenBalance(tokenWithHbarFee, 0),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(tokenWithHbarFee, 1),
                        getAccountBalance(ALICE).hasTokenBalance(tokenWithHbarFee, 1));
    }

    final Stream<DynamicTest> hscsPrec004TokenBurnOfFungibleTokenUnits() {
        final var gasUsed = 14085L;
        return propertyPreservingHapiSpec("hscsPrec004TokenBurnOfFungibleTokenUnits")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenBurn,TokenCreate,TokenUpdate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .treasury(TOKEN_TREASURY),
                        uploadInitCode(THE_BURN_CONTRACT),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCreate(
                                                THE_BURN_CONTRACT,
                                                asHeadlongAddress(asHexedAddress(
                                                        spec.registry().getTokenID(TOKEN))))
                                        .payingWith(ALICE)
                                        .via(CREATION_TX)
                                        .gas(GAS_TO_OFFER))),
                        getTxnRecord(CREATION_TX).logged())
                .when(
                        contractCall(THE_BURN_CONTRACT, BURN_TOKEN_WITH_EVENT, BigInteger.ZERO, new long[0])
                                .payingWith(ALICE)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .via("burnZero"),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 50),
                        contractCall(THE_BURN_CONTRACT, BURN_TOKEN_WITH_EVENT, BigInteger.ONE, new long[0])
                                .payingWith(ALICE)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .via("burn"),
                        getTxnRecord("burn")
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith()
                                                .logs(inOrder(logWith()
                                                        .noData()
                                                        .withTopicsInOrder(List.of(parsedToByteString(49))))))),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 49),
                        childRecordsCheck(
                                "burn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_BURN)
                                                        .withStatus(SUCCESS)
                                                        .withTotalSupply(49))
                                                .gasUsed(gasUsed))
                                        .newTotalSupply(49)
                                        .tokenTransfers(
                                                changingFungibleBalances().including(TOKEN, TOKEN_TREASURY, -1))
                                        .newTotalSupply(49)),
                        newKeyNamed(CONTRACT_KEY).shape(DELEGATE_CONTRACT.signedWith(THE_BURN_CONTRACT)),
                        tokenUpdate(TOKEN).supplyKey(CONTRACT_KEY).signedByPayerAnd(MULTI_KEY),
                        contractCall(THE_BURN_CONTRACT, "burnToken", BigInteger.ONE, new long[0])
                                .via("burn with contract key")
                                .gas(GAS_TO_OFFER),
                        childRecordsCheck(
                                "burn with contract key",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_BURN)
                                                        .withStatus(SUCCESS)
                                                        .withTotalSupply(48)))
                                        .newTotalSupply(48)
                                        .tokenTransfers(
                                                changingFungibleBalances().including(TOKEN, TOKEN_TREASURY, -1))))
                .then(getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 48));
    }

    final Stream<DynamicTest> hscsPrec005TokenBurnOfNft() {
        final var gasUsed = 14085;
        return propertyPreservingHapiSpec("hscsPrec005TokenBurnOfNft")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,TokenBurn,TokenCreate,TokenMint",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .treasury(TOKEN_TREASURY),
                        mintToken(TOKEN, List.of(copyFromUtf8(FIRST))),
                        mintToken(TOKEN, List.of(copyFromUtf8(SECOND))),
                        uploadInitCode(THE_BURN_CONTRACT),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCreate(
                                                THE_BURN_CONTRACT,
                                                asHeadlongAddress(asHexedAddress(
                                                        spec.registry().getTokenID(TOKEN))))
                                        .payingWith(ALICE)
                                        .via(CREATION_TX)
                                        .gas(GAS_TO_OFFER))),
                        getTxnRecord(CREATION_TX).logged())
                .when(
                        withOpContext((spec, opLog) -> {
                            final var serialNumbers = new long[] {1L};
                            allRunFor(
                                    spec,
                                    contractCall(THE_BURN_CONTRACT, "burnToken", BigInteger.ZERO, serialNumbers)
                                            .payingWith(ALICE)
                                            .alsoSigningWithFullPrefix(MULTI_KEY)
                                            .gas(GAS_TO_OFFER)
                                            .via("burn"));
                        }),
                        childRecordsCheck(
                                "burn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_BURN)
                                                        .withStatus(SUCCESS)
                                                        .withTotalSupply(1))
                                                .gasUsed(gasUsed))
                                        .newTotalSupply(1)))
                .then(getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 1));
    }

    final Stream<DynamicTest> hscsPrec011BurnAfterNestedMint() {
        final var innerContract = "MintToken";
        final var outerContract = "NestedBurn";
        final var revisedKey = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT, DELEGATE_CONTRACT);

        return propertyPreservingHapiSpec("hscsPrec011BurnAfterNestedMint")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,TokenBurn,TokenCreate,TokenMint",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .treasury(TOKEN_TREASURY),
                        uploadInitCode(innerContract, outerContract),
                        contractCreate(innerContract).gas(GAS_TO_OFFER),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCreate(
                                                outerContract,
                                                asHeadlongAddress(getNestedContractAddress(innerContract, spec)))
                                        .payingWith(ALICE)
                                        .via(CREATION_TX)
                                        .gas(GAS_TO_OFFER))),
                        getTxnRecord(CREATION_TX).logged())
                .when(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                newKeyNamed(CONTRACT_KEY)
                                        .shape(revisedKey.signedWith(sigs(ON, innerContract, outerContract))),
                                tokenUpdate(TOKEN).supplyKey(CONTRACT_KEY).signedByPayerAnd(MULTI_KEY),
                                contractCall(
                                                outerContract,
                                                BURN_AFTER_NESTED_MINT_TX,
                                                BigInteger.ONE,
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getTokenID(TOKEN))),
                                                new long[0])
                                        .payingWith(ALICE)
                                        .via(BURN_AFTER_NESTED_MINT_TX))),
                        childRecordsCheck(
                                BURN_AFTER_NESTED_MINT_TX,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_MINT)
                                                        .withStatus(SUCCESS)
                                                        .withTotalSupply(51)
                                                        .withSerialNumbers()))
                                        .tokenTransfers(
                                                changingFungibleBalances().including(TOKEN, TOKEN_TREASURY, 1))
                                        .newTotalSupply(51),
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_BURN)
                                                        .withStatus(SUCCESS)
                                                        .withTotalSupply(50)))
                                        .tokenTransfers(
                                                changingFungibleBalances().including(TOKEN, TOKEN_TREASURY, -1))
                                        .newTotalSupply(50)))
                .then(getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 50));
    }

    @NonNull
    private String getNestedContractAddress(String outerContract, HapiSpec spec) {
        return HapiPropertySource.asHexedSolidityAddress(spec.registry().getContractId(outerContract));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
