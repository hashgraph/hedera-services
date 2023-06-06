/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.transactions.contract.HapiEthereumCall;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Some of the test cases cannot be converted to use eth calls,
// since they use admin keys, which are held by the txn payer.
// In the case of an eth txn, we revoke the payers keys and the txn would fail.
// The only way an eth account to create a token is the admin key to be of a contractId type.
public class CreatePrecompileSuite extends HapiSuite {
    public static final String ACCOUNT_2 = "account2";
    public static final String CONTRACT_ADMIN_KEY = "contractAdminKey";
    public static final String ACCOUNT_TO_ASSOCIATE = "account3";
    public static final String ACCOUNT_TO_ASSOCIATE_KEY = "associateKey";

    public static final String FALSE = "false";
    public static final String CREATE_TOKEN_WITH_ALL_CUSTOM_FEES_AVAILABLE = "createTokenWithAllCustomFeesAvailable";
    private static final Logger log = LogManager.getLogger(CreatePrecompileSuite.class);
    private static final long GAS_TO_OFFER = 1_000_000L;
    public static final long AUTO_RENEW_PERIOD = 8_000_000L;
    public static final String TOKEN_SYMBOL = "tokenSymbol";
    public static final String TOKEN_NAME = "tokenName";
    public static final String MEMO = "memo";
    public static final String TOKEN_CREATE_CONTRACT_AS_KEY = "tokenCreateContractAsKey";
    private static final String ACCOUNT = "account";
    public static final String TOKEN_CREATE_CONTRACT = "TokenCreateContract";
    public static final String FIRST_CREATE_TXN = "firstCreateTxn";
    private static final String ACCOUNT_BALANCE = "ACCOUNT_BALANCE";
    public static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
    public static final String ED25519KEY = "ed25519key";
    public static final String ECDSA_KEY = "ecdsa";
    public static final String EXISTING_TOKEN = "EXISTING_TOKEN";
    public static final String EXPLICIT_CREATE_RESULT = "Explicit create result is {}";
    private static final String CREATE_NFT_WITH_KEYS_AND_EXPIRY_FUNCTION = "createNFTTokenWithKeysAndExpiry";

    public static void main(String... args) {
        new CreatePrecompileSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    // TODO: Fix contract name in TokenCreateContract.sol
    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(
                // TODO: where are the security model V2 _positive_ tests?
                );
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(
                tokenCreateWithEmptyKeysReverts(),
                tokenCreateWithKeyWithMultipleKeyValuesReverts(),
                tokenCreateWithFixedFeeWithMultiplePaymentsReverts(),
                createTokenWithEmptyTokenStruct(),
                createTokenWithInvalidExpiry(),
                createTokenWithInvalidTreasury(),
                createTokenWithInsufficientValueSent(),
                delegateCallTokenCreateFails());
    }

    // TEST-007 & TEST-016
    private HapiSpec tokenCreateWithEmptyKeysReverts() {
        return defaultHapiSpec("tokenCreateWithEmptyKeysReverts")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot(DEFAULT_CONTRACT_SENDER))
                .when(withOpContext((spec, opLog) ->
                        allRunFor(spec, contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))))
                .then(
                        withOpContext((spec, ignore) -> {
                            final var balanceSnapshot = balanceSnapshot(
                                    ACCOUNT_BALANCE, spec.isUsingEthCalls() ? DEFAULT_CONTRACT_SENDER : ACCOUNT);
                            final var hapiContractCall = contractCall(
                                            TOKEN_CREATE_CONTRACT,
                                            "createTokenWithEmptyKeysArray",
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(ACCOUNT))),
                                            AUTO_RENEW_PERIOD)
                                    .via(FIRST_CREATE_TXN)
                                    .gas(GAS_TO_OFFER)
                                    .sending(DEFAULT_AMOUNT_TO_SEND)
                                    .payingWith(ACCOUNT)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED);
                            final var txnRecord = getTxnRecord(FIRST_CREATE_TXN);
                            allRunFor(
                                    spec,
                                    balanceSnapshot,
                                    hapiContractCall,
                                    txnRecord,
                                    getAccountBalance(TOKEN_CREATE_CONTRACT).hasTinyBars(0L),
                                    emptyChildRecordsCheck(
                                            FIRST_CREATE_TXN, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED));
                            final var delta = spec.isUsingEthCalls()
                                    ? GAS_TO_OFFER * HapiEthereumCall.DEFAULT_GAS_PRICE_TINYBARS
                                    : txnRecord.getResponseRecord().getTransactionFee();
                            final var effectivePayer = spec.isUsingEthCalls() ? DEFAULT_CONTRACT_SENDER : ACCOUNT;
                            final var changeFromSnapshot = getAccountBalance(effectivePayer)
                                    .hasTinyBars(changeFromSnapshot(ACCOUNT_BALANCE, -(delta)));
                            allRunFor(spec, changeFromSnapshot);
                        }),
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged());
    }

    // TEST-008
    private HapiSpec tokenCreateWithKeyWithMultipleKeyValuesReverts() {
        return defaultHapiSpec("tokenCreateWithKeyWithMultipleKeyValuesReverts")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createTokenWithKeyWithMultipleValues",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        emptyChildRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED));
    }

    // TEST-009
    private HapiSpec tokenCreateWithFixedFeeWithMultiplePaymentsReverts() {
        return defaultHapiSpec("tokenCreateWithFixedFeeWithMultiplePaymentsReverts")
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createTokenWithInvalidFixedFee",
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        emptyChildRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED));
    }

    // TEST-010 & TEST-017
    private HapiSpec createTokenWithEmptyTokenStruct() {
        return defaultHapiSpec("createTokenWithEmptyTokenStruct")
                .given(cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS), uploadInitCode(TOKEN_CREATE_CONTRACT))
                .when(withOpContext((spec, opLog) ->
                        allRunFor(spec, contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))))
                .then(
                        withOpContext((spec, ignore) -> {
                            final var accountSnapshot = spec.isUsingEthCalls()
                                    ? balanceSnapshot(ACCOUNT_BALANCE, DEFAULT_CONTRACT_SENDER)
                                    : balanceSnapshot(ACCOUNT_BALANCE, ACCOUNT);
                            final var contractCall = contractCall(
                                            TOKEN_CREATE_CONTRACT, "createTokenWithEmptyTokenStruct")
                                    .via(FIRST_CREATE_TXN)
                                    .gas(GAS_TO_OFFER)
                                    .payingWith(ACCOUNT)
                                    .sending(DEFAULT_AMOUNT_TO_SEND)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED);
                            final var txnRecord = getTxnRecord(FIRST_CREATE_TXN);
                            allRunFor(
                                    spec,
                                    accountSnapshot,
                                    contractCall,
                                    txnRecord,
                                    childRecordsCheck(
                                            FIRST_CREATE_TXN,
                                            CONTRACT_REVERT_EXECUTED,
                                            TransactionRecordAsserts.recordWith()
                                                    .status(MISSING_TOKEN_SYMBOL)
                                                    .contractCallResult(ContractFnResultAsserts.resultWith()
                                                            .error(MISSING_TOKEN_SYMBOL.name()))));
                            final var delta = spec.isUsingEthCalls()
                                    ? GAS_TO_OFFER * HapiEthereumCall.DEFAULT_GAS_PRICE_TINYBARS
                                    : txnRecord.getResponseRecord().getTransactionFee();
                            final var effectivePayer = spec.isUsingEthCalls() ? DEFAULT_CONTRACT_SENDER : ACCOUNT;
                            final var accountBalance = getAccountBalance(effectivePayer)
                                    .hasTinyBars(changeFromSnapshot(ACCOUNT_BALANCE, -(delta)));
                            allRunFor(
                                    spec,
                                    accountBalance,
                                    getAccountBalance(TOKEN_CREATE_CONTRACT).hasTinyBars(0L));
                        }),
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged());
    }

    // TEST-011
    private HapiSpec createTokenWithInvalidExpiry() {
        return defaultHapiSpec("createTokenWithInvalidExpiry")
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createTokenWithInvalidExpiry",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                ResponseCodeEnum.CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_EXPIRATION_TIME)
                                        .contractCallResult(ContractFnResultAsserts.resultWith()
                                                .error(INVALID_EXPIRATION_TIME.name()))));
    }

    // TEST-013
    private HapiSpec createTokenWithInvalidTreasury() {
        return defaultHapiSpec("createTokenWithInvalidTreasury")
                .given(
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        CREATE_NFT_WITH_KEYS_AND_EXPIRY_FUNCTION,
                                        HapiParserUtil.asHeadlongAddress((byte[])
                                                ArrayUtils.toPrimitive(Utils.asSolidityAddress(0, 0, 999_999_999L))),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_ACCOUNT_ID)
                                        .contractCallResult(ContractFnResultAsserts.resultWith()
                                                .error(INVALID_ACCOUNT_ID.name()))));
    }

    // TEST-018
    private HapiSpec createTokenWithInsufficientValueSent() {
        return defaultHapiSpec("createTokenWithInsufficientValueSent")
                .given(
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        cryptoCreate(ACCOUNT).key(ED25519KEY).balance(ONE_MILLION_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT))
                .when(withOpContext((spec, opLog) ->
                        allRunFor(spec, contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))))
                .then(
                        withOpContext((spec, ignore) -> {
                            final var balanceSnapshot = spec.isUsingEthCalls()
                                    ? balanceSnapshot(ACCOUNT_BALANCE, DEFAULT_CONTRACT_SENDER)
                                    : balanceSnapshot(ACCOUNT_BALANCE, ACCOUNT);
                            final long sentAmount = ONE_HBAR / 100;
                            final var hapiContractCall = contractCall(
                                            TOKEN_CREATE_CONTRACT,
                                            CREATE_NFT_WITH_KEYS_AND_EXPIRY_FUNCTION,
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(ACCOUNT))),
                                            spec.registry()
                                                    .getKey(ED25519KEY)
                                                    .getEd25519()
                                                    .toByteArray(),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(ACCOUNT))),
                                            AUTO_RENEW_PERIOD)
                                    .via(FIRST_CREATE_TXN)
                                    .gas(GAS_TO_OFFER)
                                    .sending(sentAmount)
                                    .payingWith(ACCOUNT)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED);
                            final var txnRecord = getTxnRecord(FIRST_CREATE_TXN);
                            allRunFor(
                                    spec,
                                    balanceSnapshot,
                                    hapiContractCall,
                                    txnRecord,
                                    getAccountBalance(TOKEN_CREATE_CONTRACT).hasTinyBars(0L),
                                    childRecordsCheck(
                                            FIRST_CREATE_TXN,
                                            ResponseCodeEnum.CONTRACT_REVERT_EXECUTED,
                                            TransactionRecordAsserts.recordWith()
                                                    .status(INSUFFICIENT_TX_FEE)
                                                    .contractCallResult(ContractFnResultAsserts.resultWith()
                                                            .error(INSUFFICIENT_TX_FEE.name()))));
                            final var delta = spec.isUsingEthCalls()
                                    ? GAS_TO_OFFER * HapiEthereumCall.DEFAULT_GAS_PRICE_TINYBARS
                                    : txnRecord.getResponseRecord().getTransactionFee();
                            final var effectivePayer = spec.isUsingEthCalls() ? DEFAULT_CONTRACT_SENDER : ACCOUNT;
                            var changeFromSnapshot = getAccountBalance(effectivePayer)
                                    .hasTinyBars(changeFromSnapshot(ACCOUNT_BALANCE, -(delta)));
                            allRunFor(spec, changeFromSnapshot);
                        }),
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged());
    }

    // TEST-020
    private HapiSpec delegateCallTokenCreateFails() {
        return defaultHapiSpec("delegateCallTokenCreateFails")
                .given(
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "delegateCallCreate",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).hasChildRecordCount(0),
                        getAccountBalance(ACCOUNT),
                        getAccountBalance(TOKEN_CREATE_CONTRACT),
                        getContractInfo(TOKEN_CREATE_CONTRACT));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
