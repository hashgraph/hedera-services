/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.evm;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContract;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(SMART_CONTRACT)
public class Evm38ValidationSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(Evm38ValidationSuite.class);
    private static final String NAME = "name";
    private static final String ERC_721_ABI = "ERC721ABI";
    private static final String NON_EXISTING_MIRROR_ADDRESS = "0000000000000000000000000000000000123456";
    private static final String NON_EXISTING_NON_MIRROR_ADDRESS = "1234561234561234561234561234568888123456";
    private static final String INTERNAL_CALLER_CONTRACT = "InternalCaller";
    private static final String INTERNAL_CALLEE_CONTRACT = "InternalCallee";
    private static final String REVERT_WITH_REVERT_REASON_FUNCTION = "revertWithRevertReason";
    private static final String REVERT_WITHOUT_REVERT_REASON_FUNCTION = "revertWithoutRevertReason";
    private static final String CALL_NON_EXISTING_FUNCTION = "callNonExisting";
    private static final String CALL_EXTERNAL_FUNCTION = "callExternalFunction";
    private static final String CALL_REVERT_WITH_REVERT_REASON_FUNCTION = "callRevertWithRevertReason";
    private static final String CALL_REVERT_WITHOUT_REVERT_REASON_FUNCTION = "callRevertWithoutRevertReason";
    private static final String TRANSFER_TO_FUNCTION = "transferTo";
    private static final String SEND_TO_FUNCTION = "sendTo";
    private static final String CALL_WITH_VALUE_TO_FUNCTION = "callWithValueTo";
    private static final String INNER_TXN = "innerTx";
    private static final Long INTRINSIC_GAS_COST = 21000L;
    private static final Long GAS_LIMIT_FOR_CALL = 25000L;
    private static final Long NOT_ENOUGH_GAS_LIMIT_FOR_CREATION = 500_000L;
    private static final Long ENOUGH_GAS_LIMIT_FOR_CREATION = 900_000L;
    private static final String RECEIVER = "receiver";
    private static final String ECDSA_KEY = "ecdsaKey";
    private static final String CUSTOM_PAYER = "customPayer";
    private static final String EVM_VERSION_PROPERTY = "contracts.evm.version";
    private static final String DYNAMIC_EVM_PROPERTY = "contracts.evm.version.dynamic";
    private static final String EVM_VERSION_038 = "v0.38";
    private static final String CREATE_TRIVIAL = "CreateTrivial";
    private static final String BALANCE_OF = "balanceOf";

    public static void main(String... args) {
        new Evm38ValidationSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                invalidContractCall(),
                cannotSendValueToTokenAccount(),
                verifiesExistenceOfAccountsAndContracts(),
                verifiesExistence());
    }

    @HapiTest
    HapiSpec invalidContractCall() {
        final var function = getABIFor(FUNCTION, "getIndirect", CREATE_TRIVIAL);

        return propertyPreservingHapiSpec("InvalidContract")
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_038),
                        withOpContext(
                                (spec, ctxLog) -> spec.registry().saveContractId("invalid", asContract("0.0.5555"))))
                .when()
                .then(contractCallWithFunctionAbi("invalid", function).hasKnownStatus(INVALID_CONTRACT_ID));
    }

    @HapiTest
    private HapiSpec cannotSendValueToTokenAccount() {
        final var multiKey = "multiKey";
        final var nonFungibleToken = "NFT";
        final var contract = "ManyChildren";
        final var internalViolation = "internal";
        final var externalViolation = "external";
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("cannotSendValueToTokenAccount")
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_038),
                        newKeyNamed(multiKey),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(nonFungibleToken)
                                .supplyType(TokenSupplyType.INFINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .supplyKey(multiKey)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))))
                .when(
                        uploadInitCode(contract),
                        contractCreate(contract),
                        sourcing(() -> contractCall(
                                        contract, "sendSomeValueTo", asHeadlongAddress(tokenMirrorAddr.get()))
                                .sending(ONE_HBAR)
                                .payingWith(TOKEN_TREASURY)
                                .via(internalViolation)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing((() -> contractCall(tokenMirrorAddr.get())
                                .sending(1L)
                                .payingWith(TOKEN_TREASURY)
                                .refusingEthConversion()
                                .via(externalViolation)
                                .hasKnownStatus(LOCAL_CALL_MODIFICATION_EXCEPTION))))
                .then(
                        getTxnRecord(internalViolation).hasPriority(recordWith().feeGreaterThan(0L)),
                        getTxnRecord(externalViolation).hasPriority(recordWith().feeGreaterThan(0L)));
    }

    @HapiTest
    HapiSpec verifiesExistenceOfAccountsAndContracts() {
        final var contract = "BalanceChecker";
        final var BALANCE = 10L;
        final var ACCOUNT = "test";
        final var INVALID_ADDRESS = "0x0000000000000000000000000000000000123456";

        return propertyPreservingHapiSpec("verifiesExistenceOfAccountsAndContracts")
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_038),
                        cryptoCreate("test").balance(BALANCE),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(
                        contractCall(contract, BALANCE_OF, asHeadlongAddress(INVALID_ADDRESS))
                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS),
                        contractCallLocal(contract, BALANCE_OF, asHeadlongAddress(INVALID_ADDRESS))
                                .hasAnswerOnlyPrecheck(INVALID_SOLIDITY_ADDRESS),
                        withOpContext((spec, opLog) -> {
                            final var id = spec.registry().getAccountID(ACCOUNT);
                            final var contractID = spec.registry().getContractId(contract);

                            final var solidityAddress = HapiParserUtil.asHeadlongAddress(asAddress(id));
                            final var contractAddress = asHeadlongAddress(asHexedSolidityAddress(contractID));

                            final var call = contractCall(contract, BALANCE_OF, solidityAddress)
                                    .via("callRecord");

                            final var callRecord = getTxnRecord("callRecord")
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .resultThruAbi(
                                                            getABIFor(FUNCTION, BALANCE_OF, contract),
                                                            isLiteralResult(
                                                                    new Object[] {BigInteger.valueOf(BALANCE)}))));

                            final var callLocal = contractCallLocal(contract, BALANCE_OF, solidityAddress)
                                    .has(ContractFnResultAsserts.resultWith()
                                            .resultThruAbi(
                                                    getABIFor(FUNCTION, BALANCE_OF, contract),
                                                    ContractFnResultAsserts.isLiteralResult(
                                                            new Object[] {BigInteger.valueOf(BALANCE)})));

                            final var contractCallLocal = contractCallLocal(contract, BALANCE_OF, contractAddress)
                                    .has(ContractFnResultAsserts.resultWith()
                                            .resultThruAbi(
                                                    getABIFor(FUNCTION, BALANCE_OF, contract),
                                                    ContractFnResultAsserts.isLiteralResult(
                                                            new Object[] {BigInteger.valueOf(0)})));

                            allRunFor(spec, call, callLocal, callRecord, contractCallLocal);
                        }));
    }

    @HapiTest
    HapiSpec verifiesExistence() {
        final var contract = "CallOperationsChecker";
        final var INVALID_ADDRESS = "0x0000000000000000000000000000000000123456";
        return propertyPreservingHapiSpec("verifiesExistence")
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_038),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(
                        contractCall(contract, "callCode", asHeadlongAddress(INVALID_ADDRESS))
                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS),
                        withOpContext((spec, opLog) -> {
                            final var id = spec.registry().getAccountID(DEFAULT_PAYER);
                            final var solidityAddress = HapiPropertySource.asHexedSolidityAddress(id);

                            final var contractCall = contractCall(
                                            contract, "callCode", asHeadlongAddress(solidityAddress))
                                    .hasKnownStatus(SUCCESS);

                            allRunFor(spec, contractCall);
                        }));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
