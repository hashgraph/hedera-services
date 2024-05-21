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
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.getNestedContractAddress;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
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
import org.junit.jupiter.api.DynamicTest;

public class ContractKeysHTSV1SecurityModelSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(ContractKeysHTSV1SecurityModelSuite.class);

    private static final long GAS_TO_OFFER = 1_500_000L;

    private static final String TOKEN_TREASURY = "treasury";

    private static final String ACCOUNT = "sender";
    private static final String RECEIVER = "receiver";

    private static final KeyShape CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, KeyShape.CONTRACT);

    private static final String DELEGATE_KEY = "Delegate Contract Key";
    private static final String CONTRACT_KEY = "Contract Key";
    private static final String MULTI_KEY = "Multi Key";
    private static final String SUPPLY_KEY = "Supply Key";

    private static final String ORDINARY_CALLS_CONTRACT = "HTSCalls";
    private static final String OUTER_CONTRACT = "DelegateContract";
    private static final String NESTED_CONTRACT = "ServiceContract";
    private static final String FIRST_STRING_FOR_MINT = "First!";
    private static final String ACCOUNT_NAME = "anybody";
    private static final String TYPE_OF_TOKEN = "fungibleToken";

    public static void main(String... args) {
        new ContractKeysHTSV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(
                delegateCallForTransferWithContractKey(),
                transferWithKeyAsPartOf2OfXThreshold(),
                burnTokenWithFullPrefixAndPartialPrefixKeys());
    }

    final Stream<DynamicTest> transferWithKeyAsPartOf2OfXThreshold() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> receiverID = new AtomicReference<>();
        final var delegateContractKeyShape = KeyShape.threshOf(2, SIMPLE, SIMPLE, DELEGATE_CONTRACT, KeyShape.CONTRACT);

        return propertyPreservingHapiSpec("transferWithKeyAsPartOf2OfXThreshold")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate,TokenMint",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8(FIRST_STRING_FOR_MINT))),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(RECEIVER).exposingCreatedIdTo(receiverID::set),
                        uploadInitCode(OUTER_CONTRACT, NESTED_CONTRACT),
                        contractCreate(NESTED_CONTRACT),
                        tokenAssociate(NESTED_CONTRACT, VANILLA_TOKEN),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN),
                        cryptoTransfer(movingUnique(VANILLA_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT))
                                .payingWith(GENESIS))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                OUTER_CONTRACT, asHeadlongAddress(getNestedContractAddress(NESTED_CONTRACT, spec))),
                        tokenAssociate(OUTER_CONTRACT, VANILLA_TOKEN),
                        newKeyNamed(DELEGATE_KEY)
                                .shape(delegateContractKeyShape.signedWith(
                                        sigs(ON, ON, OUTER_CONTRACT, NESTED_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
                        contractCall(
                                        OUTER_CONTRACT,
                                        "transferDelegateCall",
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(receiverID.get())),
                                        1L)
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("delegateTransferCallWithDelegateContractKeyTxn")
                                .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                "delegateTransferCallWithDelegateContractKeyTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 0),
                        getAccountBalance(RECEIVER).hasTokenBalance(VANILLA_TOKEN, 1));
    }

    final Stream<DynamicTest> delegateCallForTransferWithContractKey() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> receiverID = new AtomicReference<>();

        return propertyPreservingHapiSpec("delegateCallForTransferWithContractKey")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate,TokenMint",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8(FIRST_STRING_FOR_MINT))),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(RECEIVER).exposingCreatedIdTo(receiverID::set),
                        uploadInitCode(OUTER_CONTRACT, NESTED_CONTRACT),
                        contractCreate(NESTED_CONTRACT),
                        tokenAssociate(NESTED_CONTRACT, VANILLA_TOKEN),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN),
                        cryptoTransfer(movingUnique(VANILLA_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT))
                                .payingWith(GENESIS))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                OUTER_CONTRACT, asHeadlongAddress(getNestedContractAddress(NESTED_CONTRACT, spec))),
                        tokenAssociate(OUTER_CONTRACT, VANILLA_TOKEN),
                        newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
                        contractCall(
                                        OUTER_CONTRACT,
                                        "transferDelegateCall",
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(receiverID.get())),
                                        1L)
                                .payingWith(GENESIS)
                                .via("delegateTransferCallWithContractKeyTxn")
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                "delegateTransferCallWithContractKeyTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 1),
                        getAccountBalance(RECEIVER).hasTokenBalance(VANILLA_TOKEN, 0));
    }

    final Stream<DynamicTest> burnTokenWithFullPrefixAndPartialPrefixKeys() {
        final var firstBurnTxn = "firstBurnTxn";
        final var secondBurnTxn = "secondBurnTxn";
        final var amount = 99L;
        final AtomicLong fungibleNum = new AtomicLong();

        return propertyPreservingHapiSpec("burnTokenWithFullPrefixAndPartialPrefixKeys")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenBurn,TokenCreate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT_NAME).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TYPE_OF_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(100)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2])),
                        uploadInitCode(ORDINARY_CALLS_CONTRACT),
                        contractCreate(ORDINARY_CALLS_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ORDINARY_CALLS_CONTRACT,
                                        "burnTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(TYPE_OF_TOKEN))),
                                        BigInteger.ONE,
                                        new long[0])
                                .via(firstBurnTxn)
                                .payingWith(ACCOUNT_NAME)
                                .signedBy(MULTI_KEY)
                                .signedBy(ACCOUNT_NAME)
                                .hasKnownStatus(SUCCESS),
                        contractCall(
                                        ORDINARY_CALLS_CONTRACT,
                                        "burnTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(TYPE_OF_TOKEN))),
                                        BigInteger.ONE,
                                        new long[0])
                                .via(secondBurnTxn)
                                .payingWith(ACCOUNT_NAME)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .hasKnownStatus(SUCCESS))))
                .then(
                        childRecordsCheck(
                                firstBurnTxn,
                                SUCCESS,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_BURN)
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                secondBurnTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_BURN)
                                                        .withStatus(SUCCESS)
                                                        .withTotalSupply(99)))
                                        .newTotalSupply(99)),
                        getTokenInfo(TYPE_OF_TOKEN).hasTotalSupply(amount),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TYPE_OF_TOKEN, amount));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
