/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.contract.hapi;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractDeleteSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(ContractDeleteSuite.class);
    private static final String CONTRACT = "Multipurpose";
    private static final String PAYABLE_CONSTRUCTOR = "PayableConstructor";

    public static void main(String... args) {
        new ContractDeleteSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
                    rejectsWithoutProperSig(),
                    systemCannotDeleteOrUndeleteContracts(),
                    deleteWorksWithMutableContract(),
                    deleteFailsWithImmutableContract(),
                    deleteTransfersToAccount(),
                    deleteTransfersToContract(),
                    cannotDeleteOrSelfDestructTokenTreasury(),
                    cannotDeleteOrSelfDestructContractWithNonZeroBalance(),
                    cannotSendValueToTokenAccount(),
                    cannotUseMoreThanChildContractLimit(),
                });
    }

    private HapiApiSpec cannotUseMoreThanChildContractLimit() {
        final var illegalNumChildren =
                HapiSpecSetup.getDefaultNodeProps()
                                .getInteger("consensus.handle.maxFollowingRecords")
                        + 1;
        final var fungible = "fungible";
        final var contract = "ManyChildren";
        final var precompileViolation = "precompileViolation";
        final var internalCreateViolation = "internalCreateViolation";
        final AtomicReference<String> treasuryMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        return defaultHapiSpec("CannotUseMoreThanChildContractLimit")
                .given(
                        cryptoCreate(TOKEN_TREASURY)
                                .exposingCreatedIdTo(
                                        id -> treasuryMirrorAddr.set(asHexedSolidityAddress(id))),
                        tokenCreate(fungible).treasury(TOKEN_TREASURY),
                        tokenCreate(fungible)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1234567)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tokenMirrorAddr.set(
                                                        asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(
                                                                        idLit)))))
                .when(
                        uploadInitCode(contract),
                        contractCreate(contract),
                        sourcing(
                                () ->
                                        contractCall(
                                                        contract,
                                                        "checkBalanceRepeatedly",
                                                        tokenMirrorAddr.get(),
                                                        treasuryMirrorAddr.get(),
                                                        illegalNumChildren)
                                                .via(precompileViolation)
                                                .hasKnownStatus(MAX_CHILD_RECORDS_EXCEEDED)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        contract,
                                                        "createThingsRepeatedly",
                                                        illegalNumChildren)
                                                .via(internalCreateViolation)
                                                .gas(15_000_000)
                                                .hasKnownStatus(MAX_CHILD_RECORDS_EXCEEDED)))
                .then(
                        getTxnRecord(precompileViolation)
                                .andAllChildRecords()
                                .hasChildRecords(
                                        IntStream.range(0, 50)
                                                .mapToObj(
                                                        i -> recordWith().status(REVERTED_SUCCESS))
                                                .toArray(TransactionRecordAsserts[]::new)),
                        getTxnRecord(internalCreateViolation)
                                .andAllChildRecords()
                                // Reverted internal CONTRACT_CREATION messages are not externalized
                                .hasChildRecords());
    }

    private HapiApiSpec cannotSendValueToTokenAccount() {
        final var multiKey = "multiKey";
        final var nonFungibleToken = "NFT";
        final var contract = "ManyChildren";
        final var internalViolation = "internal";
        final var externalViolation = "external";
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        return defaultHapiSpec("CannotSendValueToTokenAccount")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(nonFungibleToken)
                                .supplyType(TokenSupplyType.INFINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .supplyKey(multiKey)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tokenMirrorAddr.set(
                                                        asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(
                                                                        idLit)))))
                .when(
                        uploadInitCode(contract),
                        contractCreate(contract),
                        sourcing(
                                () ->
                                        contractCall(
                                                        contract,
                                                        "sendSomeValueTo",
                                                        tokenMirrorAddr.get())
                                                .sending(ONE_HBAR)
                                                .payingWith(TOKEN_TREASURY)
                                                .via(internalViolation)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(
                                (() ->
                                        contractCall(tokenMirrorAddr.get())
                                                .sending(1L)
                                                .payingWith(TOKEN_TREASURY)
                                                .via(externalViolation)
                                                .hasKnownStatus(
                                                        LOCAL_CALL_MODIFICATION_EXCEPTION))))
                .then(
                        getTxnRecord(internalViolation)
                                .hasPriority(recordWith().feeGreaterThan(0L)),
                        getTxnRecord(externalViolation)
                                .hasPriority(recordWith().feeGreaterThan(0L)));
    }

    HapiApiSpec cannotDeleteOrSelfDestructTokenTreasury() {
        final var someToken = "someToken";
        final var selfDestructCallable = "SelfDestructCallable";
        final var multiKey = "multi";
        final var escapeRoute = "civilian";

        return defaultHapiSpec("CannotDeleteOrSelfDestructTokenTreasury")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(escapeRoute),
                        uploadInitCode(selfDestructCallable),
                        contractCustomCreate(selfDestructCallable, "1")
                                .adminKey(multiKey)
                                .balance(123),
                        contractCustomCreate(selfDestructCallable, "2")
                                .adminKey(multiKey)
                                .balance(321),
                        tokenCreate(someToken)
                                .adminKey(multiKey)
                                .treasury(selfDestructCallable + "1"))
                .when(
                        contractDelete(selfDestructCallable + "1")
                                .hasKnownStatus(ACCOUNT_IS_TREASURY),
                        tokenAssociate(selfDestructCallable + "2", someToken),
                        tokenUpdate(someToken).treasury(selfDestructCallable + "2"),
                        contractDelete(selfDestructCallable + "1"),
                        contractCall(selfDestructCallable + "2", "destroy")
                                .hasKnownStatus(CONTRACT_EXECUTION_EXCEPTION),
                        tokenAssociate(escapeRoute, someToken),
                        tokenUpdate(someToken).treasury(escapeRoute))
                .then(contractCall(selfDestructCallable + "2", "destroy"));
    }

    HapiApiSpec cannotDeleteOrSelfDestructContractWithNonZeroBalance() {
        final var someToken = "someToken";
        final var multiKey = "multi";
        final var selfDestructableContract = "SelfDestructCallable";
        final var otherMiscContract = "PayReceivable";

        return defaultHapiSpec("CannotDeleteOrSelfDestructContractWithNonZeroBalance")
                .given(
                        newKeyNamed(multiKey),
                        uploadInitCode(selfDestructableContract),
                        contractCreate(selfDestructableContract).adminKey(multiKey).balance(123),
                        uploadInitCode(otherMiscContract),
                        contractCreate(otherMiscContract),
                        tokenCreate(someToken)
                                .initialSupply(0L)
                                .adminKey(multiKey)
                                .supplyKey(multiKey)
                                .treasury(selfDestructableContract)
                                .supplyType(TokenSupplyType.INFINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE))
                .when(
                        mintToken(someToken, List.of(ByteString.copyFromUtf8("somemetadata"))),
                        tokenAssociate(otherMiscContract, someToken),
                        cryptoTransfer(
                                TokenMovement.movingUnique(someToken, 1)
                                        .between(selfDestructableContract, otherMiscContract)))
                .then(
                        contractDelete(otherMiscContract)
                                .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES),
                        contractCall(selfDestructableContract, "destroy")
                                .hasKnownStatus(CONTRACT_EXECUTION_EXCEPTION));
    }

    HapiApiSpec rejectsWithoutProperSig() {
        return defaultHapiSpec("ScDelete")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when()
                .then(contractDelete(CONTRACT).signedBy(GENESIS).hasKnownStatus(INVALID_SIGNATURE));
    }

    private HapiApiSpec systemCannotDeleteOrUndeleteContracts() {
        return defaultHapiSpec("SystemCannotDeleteOrUndeleteContracts")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when()
                .then(
                        systemContractDelete(CONTRACT)
                                .payingWith(SYSTEM_DELETE_ADMIN)
                                .hasPrecheck(NOT_SUPPORTED),
                        systemContractUndelete(CONTRACT)
                                .payingWith(SYSTEM_UNDELETE_ADMIN)
                                .hasPrecheck(NOT_SUPPORTED),
                        getContractInfo(CONTRACT).hasAnswerOnlyPrecheck(OK));
    }

    private HapiApiSpec deleteWorksWithMutableContract() {
        final var tbdFile = "FTBD";
        final var tbdContract = "CTBD";
        return defaultHapiSpec("DeleteWorksWithMutableContract")
                .given(
                        fileCreate(tbdFile),
                        fileDelete(tbdFile),
                        createDefaultContract(tbdContract)
                                .bytecode(tbdFile)
                                .hasKnownStatus(FILE_DELETED))
                .when(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .then(
                        contractDelete(CONTRACT),
                        getContractInfo(CONTRACT).has(contractWith().isDeleted()));
    }

    private HapiApiSpec deleteFailsWithImmutableContract() {
        return defaultHapiSpec("DeleteFailsWithImmutableContract")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT).omitAdminKey())
                .when()
                .then(contractDelete(CONTRACT).hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT));
    }

    private HapiApiSpec deleteTransfersToAccount() {
        return defaultHapiSpec("DeleteTransfersToAccount")
                .given(
                        cryptoCreate("receiver").balance(0L),
                        uploadInitCode(PAYABLE_CONSTRUCTOR),
                        contractCreate(PAYABLE_CONSTRUCTOR).balance(1L))
                .when(contractDelete(PAYABLE_CONSTRUCTOR).transferAccount("receiver"))
                .then(getAccountBalance("receiver").hasTinyBars(1L));
    }

    private HapiApiSpec deleteTransfersToContract() {
        final var suffix = "Receiver";

        return defaultHapiSpec("DeleteTransfersToContract")
                .given(
                        uploadInitCode(PAYABLE_CONSTRUCTOR),
                        contractCreate(PAYABLE_CONSTRUCTOR).balance(0L),
                        contractCustomCreate(PAYABLE_CONSTRUCTOR, suffix).balance(1L))
                .when(
                        contractDelete(PAYABLE_CONSTRUCTOR)
                                .transferContract(PAYABLE_CONSTRUCTOR + suffix))
                .then(getAccountBalance(PAYABLE_CONSTRUCTOR + suffix).hasTinyBars(1L));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
