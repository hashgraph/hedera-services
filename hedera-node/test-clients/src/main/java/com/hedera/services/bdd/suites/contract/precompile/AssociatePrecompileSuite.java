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

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONTRACT_CALL_RESULTS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_NONCE;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite(fuzzyMatch = true)
@Tag(SMART_CONTRACT)
public class AssociatePrecompileSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(AssociatePrecompileSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);
    private static final String TOKEN_TREASURY = "treasury";
    private static final String INNER_CONTRACT = "AssociateDissociate";
    public static final String THE_CONTRACT = "AssociateDissociate";
    private static final String THE_GRACEFULLY_FAILING_CONTRACT = "GracefullyFailing";
    private static final String ACCOUNT = "anybody";
    private static final String DELEGATE_KEY = "Delegate key";
    private static final byte[] ACCOUNT_ADDRESS =
            asAddress(AccountID.newBuilder().build());
    private static final byte[] TOKEN_ADDRESS = asAddress(TokenID.newBuilder().build());
    private static final String INVALID_SINGLE_ABI_CALL_TXN = "Invalid Single Abi Call txn";
    public static final String TOKEN_ASSOCIATE_FUNCTION = "tokenAssociate";
    private static final String VANILLA_TOKEN_ASSOCIATE_TXN = "vanillaTokenAssociateTxn";
    private static final String NEGATIVE_ASSOCIATIONS_CONTRACT = "NegativeAssociationsContract";
    private static final String TOKEN = "Token";
    private static final String TOKEN1 = "Token1";
    private static final String CONTRACT_KEY = "ContractKey";
    private static final KeyShape KEY_SHAPE = KeyShape.threshOf(1, ED25519, CONTRACT);

    public static void main(String... args) {
        new AssociatePrecompileSuite().runSuiteAsync();
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
                functionCallWithLessThanFourBytesFailsWithinSingleContractCall(),
                nonSupportedAbiCallGracefullyFailsWithMultipleContractCalls(),
                invalidlyFormattedAbiCallGracefullyFailsWithMultipleContractCalls(),
                nonSupportedAbiCallGracefullyFailsWithinSingleContractCall(),
                invalidAbiCallGracefullyFailsWithinSingleContractCall(),
                invalidSingleAbiCallConsumesAllProvidedGas(),
                associateTokensNegativeScenarios(),
                associateTokenNegativeScenarios());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(associateWithMissingEvmAddressHasSaneTxnAndRecord());
    }

    /* -- HSCS-PREC-27 from HTS Precompile Test Plan -- */
    @HapiTest
    final HapiSpec functionCallWithLessThanFourBytesFailsWithinSingleContractCall() {
        return defaultHapiSpec("functionCallWithLessThanFourBytesFailsWithinSingleContractCall")
                .given(uploadInitCode(THE_GRACEFULLY_FAILING_CONTRACT), contractCreate(THE_GRACEFULLY_FAILING_CONTRACT))
                .when(contractCall(
                                THE_GRACEFULLY_FAILING_CONTRACT,
                                "performLessThanFourBytesFunctionCall",
                                HapiParserUtil.asHeadlongAddress(ACCOUNT_ADDRESS),
                                HapiParserUtil.asHeadlongAddress(TOKEN_ADDRESS))
                        .notTryingAsHexedliteral()
                        .via("Function call with less than 4 bytes txn")
                        .gas(100_000))
                .then(childRecordsCheck("Function call with less than 4 bytes txn", SUCCESS));
    }

    /* -- HSCS-PREC-27 from HTS Precompile Test Plan -- */
    @HapiTest
    final HapiSpec invalidAbiCallGracefullyFailsWithinSingleContractCall() {
        return defaultHapiSpec("invalidAbiCallGracefullyFailsWithinSingleContractCall")
                .given(uploadInitCode(THE_GRACEFULLY_FAILING_CONTRACT), contractCreate(THE_GRACEFULLY_FAILING_CONTRACT))
                .when(contractCall(
                                THE_GRACEFULLY_FAILING_CONTRACT,
                                "performInvalidlyFormattedFunctionCall",
                                HapiParserUtil.asHeadlongAddress(ACCOUNT_ADDRESS),
                                new Address[] {
                                    HapiParserUtil.asHeadlongAddress(TOKEN_ADDRESS),
                                    HapiParserUtil.asHeadlongAddress(TOKEN_ADDRESS)
                                })
                        .notTryingAsHexedliteral()
                        .via("Invalid Abi Function call txn"))
                .then(childRecordsCheck("Invalid Abi Function call txn", SUCCESS));
    }

    /* -- HSCS-PREC-26 from HTS Precompile Test Plan -- */
    @HapiTest
    final HapiSpec nonSupportedAbiCallGracefullyFailsWithinSingleContractCall() {
        return defaultHapiSpec(
                        "nonSupportedAbiCallGracefullyFailsWithinSingleContractCall", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(uploadInitCode(THE_GRACEFULLY_FAILING_CONTRACT), contractCreate(THE_GRACEFULLY_FAILING_CONTRACT))
                .when(contractCall(
                                THE_GRACEFULLY_FAILING_CONTRACT,
                                "performNonExistingServiceFunctionCall",
                                HapiParserUtil.asHeadlongAddress(ACCOUNT_ADDRESS),
                                HapiParserUtil.asHeadlongAddress(TOKEN_ADDRESS))
                        .notTryingAsHexedliteral()
                        .via("nonExistingFunctionCallTxn"))
                .then(childRecordsCheck("nonExistingFunctionCallTxn", SUCCESS));
    }

    /* -- HSCS-PREC-26 from HTS Precompile Test Plan -- */
    @HapiTest
    final HapiSpec nonSupportedAbiCallGracefullyFailsWithMultipleContractCalls() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return defaultHapiSpec(
                        "nonSupportedAbiCallGracefullyFailsWithMultipleContractCalls",
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(THE_CONTRACT),
                        contractCreate(THE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
                        contractCall(
                                        THE_CONTRACT,
                                        "nonSupportedFunction",
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())))
                                .payingWith(GENESIS)
                                .via("notSupportedFunctionCallTxn")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        THE_CONTRACT,
                                        TOKEN_ASSOCIATE_FUNCTION,
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())))
                                .payingWith(GENESIS)
                                .via(VANILLA_TOKEN_ASSOCIATE_TXN)
                                .gas(GAS_TO_OFFER))))
                .then(
                        emptyChildRecordsCheck("notSupportedFunctionCallTxn", CONTRACT_REVERT_EXECUTED),
                        childRecordsCheck(
                                VANILLA_TOKEN_ASSOCIATE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN)));
    }

    /* -- HSCS-PREC-27 from HTS Precompile Test Plan -- */
    @HapiTest
    final HapiSpec invalidlyFormattedAbiCallGracefullyFailsWithMultipleContractCalls() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final var invalidAbiArgument = new byte[20];

        return defaultHapiSpec(
                        "invalidlyFormattedAbiCallGracefullyFailsWithMultipleContractCalls",
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_NONCE)
                .given(
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(THE_CONTRACT),
                        contractCreate(THE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
                        contractCall(
                                        THE_CONTRACT,
                                        TOKEN_ASSOCIATE_FUNCTION,
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())),
                                        HapiParserUtil.asHeadlongAddress(invalidAbiArgument))
                                .payingWith(GENESIS)
                                .via("functionCallWithInvalidArgumentTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        THE_CONTRACT,
                                        TOKEN_ASSOCIATE_FUNCTION,
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())))
                                .payingWith(GENESIS)
                                .via(VANILLA_TOKEN_ASSOCIATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS))))
                .then(
                        childRecordsCheck(
                                "functionCallWithInvalidArgumentTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_TOKEN_ID)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(INVALID_TOKEN_ID)))),
                        childRecordsCheck(
                                VANILLA_TOKEN_ASSOCIATE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN)));
    }

    @HapiTest
    final HapiSpec associateWithMissingEvmAddressHasSaneTxnAndRecord() {
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        final var missingAddress =
                Address.wrap(Address.toChecksumAddress("0xabababababababababababababababababababab"));
        final var txn = "txn";

        return defaultHapiSpec(
                        "associateWithMissingEvmAddressHasSaneTxnAndRecord",
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        uploadInitCode(INNER_CONTRACT),
                        contractCreate(INNER_CONTRACT),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit ->
                                        tokenAddress.set(idAsHeadlongAddress(HapiPropertySource.asToken(idLit)))))
                .when(sourcing(
                        () -> contractCall(INNER_CONTRACT, TOKEN_ASSOCIATE_FUNCTION, missingAddress, tokenAddress.get())
                                .via(txn)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(getTxnRecord(txn).andAllChildRecords().logged());
    }

    /* -- HSCS-PREC-27 from HTS Precompile Test Plan -- */
    @HapiTest
    final HapiSpec invalidSingleAbiCallConsumesAllProvidedGas() {
        return defaultHapiSpec("invalidSingleAbiCallConsumesAllProvidedGas", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(uploadInitCode(THE_GRACEFULLY_FAILING_CONTRACT), contractCreate(THE_GRACEFULLY_FAILING_CONTRACT))
                .when(
                        contractCall(
                                        THE_GRACEFULLY_FAILING_CONTRACT,
                                        "performInvalidlyFormattedSingleFunctionCall",
                                        HapiParserUtil.asHeadlongAddress(ACCOUNT_ADDRESS))
                                .notTryingAsHexedliteral()
                                .via(INVALID_SINGLE_ABI_CALL_TXN)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTxnRecord(INVALID_SINGLE_ABI_CALL_TXN).saveTxnRecordToRegistry(INVALID_SINGLE_ABI_CALL_TXN))
                .then(withOpContext((spec, ignore) -> {
                    final var gasUsed = spec.registry()
                            .getTransactionRecord(INVALID_SINGLE_ABI_CALL_TXN)
                            .getContractCallResult()
                            .getGasUsed();
                    assertEquals(99014, gasUsed);
                }));
    }

    @HapiTest
    final HapiSpec associateTokensNegativeScenarios() {
        final AtomicReference<Address> tokenAddress1 = new AtomicReference<>();
        final AtomicReference<Address> tokenAddress2 = new AtomicReference<>();
        final AtomicReference<Address> accountAddress = new AtomicReference<>();
        final var nonExistingAccount = "nonExistingAccount";
        final var nonExistingTokenArray = "nonExistingTokenArray";
        final var someNonExistingTokenArray = "someNonExistingTokenArray";
        final var zeroAccountAddress = "zeroAccountAddress";
        final var nullTokenArray = "nullTokens";
        final var nonExistingTokensInArray = "nonExistingTokensInArray";
        return defaultHapiSpec("associateTokensNegativeScenarios")
                .given(
                        uploadInitCode(NEGATIVE_ASSOCIATIONS_CONTRACT),
                        contractCreate(NEGATIVE_ASSOCIATIONS_CONTRACT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .treasury(TOKEN_TREASURY)
                                .exposingAddressTo(tokenAddress1::set),
                        tokenCreate(TOKEN1)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .treasury(TOKEN_TREASURY)
                                .exposingAddressTo(tokenAddress2::set),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(id -> accountAddress.set(idAsHeadlongAddress(id))))
                .when(withOpContext((spec, custom) -> allRunFor(
                        spec,
                        contractCall(
                                        NEGATIVE_ASSOCIATIONS_CONTRACT,
                                        "associateTokensWithNonExistingAccountAddress",
                                        (Object) new Address[] {tokenAddress1.get(), tokenAddress2.get()})
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .via(nonExistingAccount)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN1),
                        newKeyNamed(CONTRACT_KEY).shape(KEY_SHAPE.signedWith(sigs(ON, NEGATIVE_ASSOCIATIONS_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
                        contractCall(
                                        NEGATIVE_ASSOCIATIONS_CONTRACT,
                                        "associateTokensWithEmptyTokensArray",
                                        accountAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .signingWith(ACCOUNT)
                                .via(nonExistingTokenArray)
                                .logged(),
                        contractCall(NEGATIVE_ASSOCIATIONS_CONTRACT, "associateTokensWithNullAccount", (Object)
                                        new Address[] {tokenAddress1.get(), tokenAddress2.get()})
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .via(zeroAccountAddress)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN1),
                        contractCall(
                                        NEGATIVE_ASSOCIATIONS_CONTRACT,
                                        "associateTokensWithNullTokensArray",
                                        accountAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .signingWith(ACCOUNT)
                                .via(nullTokenArray)
                                .logged(),
                        contractCall(
                                        NEGATIVE_ASSOCIATIONS_CONTRACT,
                                        "associateTokensWithNonExistingTokensArray",
                                        accountAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .signingWith(ACCOUNT)
                                .via(nonExistingTokensInArray)
                                .logged(),
                        contractCall(
                                        NEGATIVE_ASSOCIATIONS_CONTRACT,
                                        "associateTokensWithTokensArrayWithSomeNonExistingAddresses",
                                        accountAddress.get(),
                                        new Address[] {tokenAddress1.get(), tokenAddress2.get()})
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER)
                                .signingWith(ACCOUNT)
                                .via(someNonExistingTokenArray)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(TOKEN)),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(TOKEN1)))))
                .then(
                        childRecordsCheck(
                                nonExistingAccount,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ACCOUNT_ID)),
                        childRecordsCheck(
                                nonExistingTokenArray,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(REVERTED_SUCCESS)),
                        childRecordsCheck(
                                someNonExistingTokenArray, SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                zeroAccountAddress,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ACCOUNT_ID)),
                        childRecordsCheck(
                                nullTokenArray,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                nonExistingTokensInArray,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)));
    }

    @HapiTest
    final HapiSpec associateTokenNegativeScenarios() {
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        final AtomicReference<Address> accountAddress = new AtomicReference<>();
        final var nonExistingAccount = "nonExistingAccount";
        final var nullAccount = "nullAccount";
        final var nonExistingToken = "nonExistingToken";
        final var nullToken = "nullToken";
        return defaultHapiSpec("associateTokenNegativeScenarios")
                .given(
                        uploadInitCode(NEGATIVE_ASSOCIATIONS_CONTRACT),
                        contractCreate(NEGATIVE_ASSOCIATIONS_CONTRACT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .treasury(TOKEN_TREASURY)
                                .exposingAddressTo(tokenAddress::set),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(id -> accountAddress.set(idAsHeadlongAddress(id))))
                .when(withOpContext((spec, custom) -> allRunFor(
                        spec,
                        newKeyNamed(CONTRACT_KEY).shape(KEY_SHAPE.signedWith(sigs(ON, NEGATIVE_ASSOCIATIONS_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
                        contractCall(
                                        NEGATIVE_ASSOCIATIONS_CONTRACT,
                                        "associateTokenWithNonExistingAccount",
                                        tokenAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .via(nonExistingAccount)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN),
                        contractCall(
                                        NEGATIVE_ASSOCIATIONS_CONTRACT,
                                        "associateTokenWithNullAccount",
                                        tokenAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .via(nullAccount)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN),
                        contractCall(
                                        NEGATIVE_ASSOCIATIONS_CONTRACT,
                                        "associateTokenWithNonExistingTokenAddress",
                                        accountAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .via(nonExistingToken)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN),
                        contractCall(
                                        NEGATIVE_ASSOCIATIONS_CONTRACT,
                                        "associateTokenWithNullTokenAddress",
                                        accountAddress.get())
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER)
                                .via(nullToken)
                                .logged(),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(TOKEN))))
                .then(
                        childRecordsCheck(
                                nonExistingAccount,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ACCOUNT_ID)),
                        childRecordsCheck(
                                nullAccount,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ACCOUNT_ID)),
                        childRecordsCheck(
                                nonExistingToken,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                nullToken,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
