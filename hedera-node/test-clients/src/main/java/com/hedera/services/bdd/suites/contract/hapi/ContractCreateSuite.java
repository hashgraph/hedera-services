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

package com.hedera.services.bdd.suites.contract.hapi;

import static com.hedera.node.app.hapi.utils.ethereum.EthTxSigs.signMessage;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isContractWith;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.bytecodePath;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.explicitContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.explicitEthereumTransaction;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.contractListWithPropertiesInheritedFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getPrivateKeyFromSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite.ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ERROR_DECODING_BYTESTRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.esaulpaugh.headlong.util.Integers;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.utility.CommonUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;

@HapiTestSuite
public class ContractCreateSuite extends HapiSuite {

    public static final String EMPTY_CONSTRUCTOR_CONTRACT = "EmptyConstructor";
    public static final String PARENT_INFO = "parentInfo";
    private static final String PAYER = "payer";
    private static final Logger log = LogManager.getLogger(ContractCreateSuite.class);

    public static void main(String... args) {
        new ContractCreateSuite().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                createEmptyConstructor(),
                insufficientPayerBalanceUponCreation(),
                rejectsInvalidMemo(),
                rejectsInsufficientFee(),
                rejectsInvalidBytecode(),
                revertsNonzeroBalance(),
                createFailsIfMissingSigs(),
                rejectsInsufficientGas(),
                createsVanillaContractAsExpectedWithOmittedAdminKey(),
                childCreationsHaveExpectedKeysWithOmittedAdminKey(),
                cannotCreateTooLargeContract(),
                revertedTryExtCallHasNoSideEffects(),
                cannotSendToNonExistentAccount(),
                invalidSystemInitcodeFileFailsWithInvalidFileId(),
                delegateContractIdRequiredForTransferInDelegateCall(),
                vanillaSuccess(),
                blockTimestampChangesWithinFewSeconds(),
                contractWithAutoRenewNeedSignatures(),
                newAccountsCanUsePureContractIdKey(),
                createContractWithStakingFields());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @HapiTest
    HapiSpec createContractWithStakingFields() {
        final var contract = "CreateTrivial";
        return defaultHapiSpec("createContractWithStakingFields")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .adminKey(THRESHOLD)
                                .declinedReward(true)
                                .stakedNodeId(0),
                        getContractInfo(contract)
                                .has(contractWith()
                                        .isDeclinedReward(true)
                                        .noStakedAccountId()
                                        .stakedNodeId(0))
                                .logged())
                .when(
                        contractCreate(contract)
                                .adminKey(THRESHOLD)
                                .declinedReward(true)
                                .stakedAccountId("0.0.10"),
                        getContractInfo(contract)
                                .has(contractWith()
                                        .isDeclinedReward(true)
                                        .noStakingNodeId()
                                        .stakedAccountId("0.0.10"))
                                .logged())
                .then(
                        contractCreate(contract)
                                .adminKey(THRESHOLD)
                                .declinedReward(false)
                                .stakedNodeId(0),
                        getContractInfo(contract)
                                .has(contractWith()
                                        .isDeclinedReward(false)
                                        .noStakedAccountId()
                                        .stakedNodeId(0))
                                .logged(),
                        contractCreate(contract)
                                .adminKey(THRESHOLD)
                                .declinedReward(false)
                                .stakedAccountId("0.0.10"),
                        getContractInfo(contract)
                                .has(contractWith()
                                        .isDeclinedReward(false)
                                        .noStakingNodeId()
                                        .stakedAccountId("0.0.10"))
                                .logged(),
                        /* sentinel values throw */
                        contractCreate(contract)
                                .adminKey(THRESHOLD)
                                .declinedReward(false)
                                .stakedAccountId("0.0.0")
                                .hasPrecheck(INVALID_STAKING_ID),
                        contractCreate(contract)
                                .adminKey(THRESHOLD)
                                .declinedReward(false)
                                .stakedNodeId(-1L)
                                .hasPrecheck(INVALID_STAKING_ID));
    }

    @HapiTest
    private HapiSpec insufficientPayerBalanceUponCreation() {
        return defaultHapiSpec("InsufficientPayerBalanceUponCreation")
                .given(cryptoCreate("bankrupt").balance(0L), uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when()
                .then(contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                        .payingWith("bankrupt")
                        .hasPrecheck(INSUFFICIENT_PAYER_BALANCE));
    }

    @HapiTest
    HapiSpec cannotSendToNonExistentAccount() {
        final var contract = "Multipurpose";
        Object[] donationArgs = new Object[] {666666L, "Hey, Ma!"};

        return defaultHapiSpec("CannotSendToNonExistentAccount")
                .given(uploadInitCode(contract))
                .when(contractCreate(contract).balance(666))
                .then(contractCall(contract, "donate", donationArgs).hasKnownStatus(INVALID_SOLIDITY_ADDRESS));
    }

    @HapiTest
    HapiSpec invalidSystemInitcodeFileFailsWithInvalidFileId() {
        final var neverToBe = "NeverToBe";
        final var systemFileId = FileID.newBuilder().setFileNum(159).build();
        return defaultHapiSpec("InvalidSystemInitcodeFileFailsWithInvalidFileId")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS))
                .when()
                .then(
                        explicitContractCreate(neverToBe, (spec, b) -> b.setFileID(systemFileId))
                                .hasKnownStatus(INVALID_FILE_ID),
                        explicitEthereumTransaction(neverToBe, (spec, b) -> {
                                    final var signedEthTx = signMessage(
                                            placeholderEthTx(), getPrivateKeyFromSpec(spec, SECP_256K1_SOURCE_KEY));
                                    b.setCallData(systemFileId)
                                            .setEthereumData(ByteString.copyFrom(signedEthTx.encodeTx()));
                                })
                                .hasPrecheck(INVALID_FILE_ID));
    }

    @HapiTest
    private HapiSpec createsVanillaContractAsExpectedWithOmittedAdminKey() {
        return defaultHapiSpec("createsVanillaContractAsExpectedWithOmittedAdminKey")
                .given(uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when()
                .then(
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).omitAdminKey(),
                        getContractInfo(EMPTY_CONSTRUCTOR_CONTRACT)
                                .has(contractWith().immutableContractKey(EMPTY_CONSTRUCTOR_CONTRACT))
                                .logged());
    }

    @HapiTest
    private HapiSpec childCreationsHaveExpectedKeysWithOmittedAdminKey() {
        final AtomicLong firstStickId = new AtomicLong();
        final AtomicLong secondStickId = new AtomicLong();
        final AtomicLong thirdStickId = new AtomicLong();
        final var txn = "creation";
        final var contract = "Fuse";

        return defaultHapiSpec("ChildCreationsHaveExpectedKeysWithOmittedAdminKey")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).omitAdminKey().gas(300_000).via(txn),
                        withOpContext((spec, opLog) -> {
                            final var op = getTxnRecord(txn);
                            allRunFor(spec, op);
                            final var record = op.getResponseRecord();
                            final var creationResult = record.getContractCreateResult();
                            final var createdIds = creationResult.getCreatedContractIDsList();
                            assertEquals(4, createdIds.size(), "Expected four creations but got " + createdIds);
                            firstStickId.set(createdIds.get(1).getContractNum());
                            secondStickId.set(createdIds.get(2).getContractNum());
                            thirdStickId.set(createdIds.get(3).getContractNum());
                        }))
                .when(
                        sourcing(() -> getContractInfo("0.0." + firstStickId.get())
                                .has(contractWith().immutableContractKey("0.0." + firstStickId.get()))
                                .logged()),
                        sourcing(() -> getContractInfo("0.0." + secondStickId.get())
                                .has(contractWith().immutableContractKey("0.0." + secondStickId.get()))
                                .logged()),
                        sourcing(() ->
                                getContractInfo("0.0." + thirdStickId.get()).logged()),
                        contractCall(contract, "light").via("lightTxn"))
                .then(
                        sourcing(() -> getContractInfo("0.0." + firstStickId.get())
                                .has(contractWith().isDeleted())),
                        sourcing(() -> getContractInfo("0.0." + secondStickId.get())
                                .has(contractWith().isDeleted())),
                        sourcing(() -> getContractInfo("0.0." + thirdStickId.get())
                                .has(contractWith().isDeleted())));
    }

    @HapiTest
    private HapiSpec createEmptyConstructor() {
        return defaultHapiSpec("createEmptyConstructor")
                .given(uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when()
                .then(contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).hasKnownStatus(SUCCESS));
    }

    @HapiTest
    private HapiSpec revertedTryExtCallHasNoSideEffects() {
        final var balance = 3_000;
        final int sendAmount = balance / 3;
        final var contract = "RevertingSendTry";
        final var aBeneficiary = "aBeneficiary";
        final var bBeneficiary = "bBeneficiary";
        final var txn = "txn";

        return defaultHapiSpec("RevertedTryExtCallHasNoSideEffects")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).balance(balance),
                        cryptoCreate(aBeneficiary).balance(0L),
                        cryptoCreate(bBeneficiary).balance(0L))
                .when(withOpContext((spec, opLog) -> {
                    final var registry = spec.registry();
                    final var aNum = (int) registry.getAccountID(aBeneficiary).getAccountNum();
                    final var bNum = (int) registry.getAccountID(bBeneficiary).getAccountNum();
                    final var sendArgs =
                            new Object[] {Long.valueOf(sendAmount), Long.valueOf(aNum), Long.valueOf(bNum)};

                    final var op = contractCall(contract, "sendTo", sendArgs)
                            .gas(110_000)
                            .via(txn);
                    allRunFor(spec, op);
                }))
                .then(
                        getTxnRecord(txn).logged(),
                        getAccountBalance(aBeneficiary).logged(),
                        getAccountBalance(bBeneficiary).logged());
    }

    @HapiTest
    private HapiSpec createFailsIfMissingSigs() {
        final var shape = listOf(SIMPLE, threshOf(2, 3), threshOf(1, 3));
        final var validSig = shape.signedWith(sigs(ON, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));
        final var invalidSig = shape.signedWith(sigs(OFF, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));

        return defaultHapiSpec("CreateFailsIfMissingSigs")
                .given(uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when()
                .then(
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .adminKeyShape(shape)
                                .sigControl(forKey(EMPTY_CONSTRUCTOR_CONTRACT, invalidSig))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .adminKeyShape(shape)
                                .sigControl(forKey(EMPTY_CONSTRUCTOR_CONTRACT, validSig))
                                .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    private HapiSpec rejectsInsufficientGas() {
        return defaultHapiSpec("RejectsInsufficientGas")
                .given(uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when()
                .then(contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).gas(0L).hasKnownStatus(INSUFFICIENT_GAS));
    }

    @HapiTest
    private HapiSpec rejectsInvalidMemo() {
        return defaultHapiSpec("RejectsInvalidMemo")
                .given()
                .when()
                .then(
                        uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT),
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .entityMemo(TxnUtils.nAscii(101))
                                .hasPrecheck(MEMO_TOO_LONG),
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .entityMemo(ZERO_BYTE_MEMO)
                                .hasPrecheck(INVALID_ZERO_BYTE_IN_STRING));
    }

    private HapiSpec rejectsInsufficientFee() {
        return defaultHapiSpec("RejectsInsufficientFee")
                .given(cryptoCreate(PAYER), uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when()
                .then(contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                        .payingWith(PAYER)
                        .fee(1L)
                        .hasPrecheck(INSUFFICIENT_TX_FEE));
    }

    @HapiTest
    private HapiSpec rejectsInvalidBytecode() {
        final var contract = "InvalidBytecode";
        return defaultHapiSpec("RejectsInvalidBytecode")
                .given(uploadInitCode(contract))
                .when()
                .then(contractCreate(contract).hasKnownStatus(ERROR_DECODING_BYTESTRING));
    }

    @HapiTest
    private HapiSpec revertsNonzeroBalance() {
        return defaultHapiSpec("RevertsNonzeroBalance")
                .given(uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when()
                .then(contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).balance(1L).hasKnownStatus(CONTRACT_REVERT_EXECUTED));
    }

    private HapiSpec delegateContractIdRequiredForTransferInDelegateCall() {
        final var justSendContract = "JustSend";
        final var sendInternalAndDelegateContract = "SendInternalAndDelegate";

        final var beneficiary = "civilian";
        final var totalToSend = 1_000L;
        final var origKey = KeyShape.threshOf(1, SIMPLE, CONTRACT);
        final var revisedKey = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);
        final var newKey = "delegateContractKey";

        final AtomicLong justSendContractNum = new AtomicLong();
        final AtomicLong beneficiaryAccountNum = new AtomicLong();

        return defaultHapiSpec("DelegateContractIdRequiredForTransferInDelegateCall")
                .given(
                        uploadInitCode(justSendContract, sendInternalAndDelegateContract),
                        contractCreate(justSendContract).gas(300_000L).exposingNumTo(justSendContractNum::set),
                        contractCreate(sendInternalAndDelegateContract)
                                .gas(300_000L)
                                .balance(2 * totalToSend))
                .when(cryptoCreate(beneficiary)
                        .balance(0L)
                        .keyShape(origKey.signedWith(sigs(ON, sendInternalAndDelegateContract)))
                        .receiverSigRequired(true)
                        .exposingCreatedIdTo(id -> beneficiaryAccountNum.set(id.getAccountNum())))
                .then(
                        /* Without delegateContractId permissions, the second send via delegate call will
                         * fail, so only half of totalToSend will make it to the beneficiary. (Note the entire
                         * call doesn't fail because exceptional halts in "raw calls" don't automatically
                         * propagate up the stack like a Solidity revert does.) */
                        sourcing(() -> contractCall(
                                sendInternalAndDelegateContract,
                                "sendRepeatedlyTo",
                                BigInteger.valueOf(justSendContractNum.get()),
                                BigInteger.valueOf(beneficiaryAccountNum.get()),
                                BigInteger.valueOf(totalToSend / 2))),
                        getAccountBalance(beneficiary).hasTinyBars(totalToSend / 2),
                        /* But now we update the beneficiary to have a delegateContractId */
                        newKeyNamed(newKey).shape(revisedKey.signedWith(sigs(ON, sendInternalAndDelegateContract))),
                        cryptoUpdate(beneficiary).key(newKey),
                        sourcing(() -> contractCall(
                                sendInternalAndDelegateContract,
                                "sendRepeatedlyTo",
                                BigInteger.valueOf(justSendContractNum.get()),
                                BigInteger.valueOf(beneficiaryAccountNum.get()),
                                BigInteger.valueOf(totalToSend / 2))),
                        getAccountBalance(beneficiary).hasTinyBars(3 * (totalToSend / 2)));
    }

    @HapiTest
    private HapiSpec cannotCreateTooLargeContract() {
        ByteString contents;
        try {
            contents = ByteString.copyFrom(Files.readAllBytes(Path.of(bytecodePath("CryptoKitties"))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final var FILE_KEY = "fileKey";
        final var KEY_LIST = "keyList";
        final var ACCOUNT = "acc";
        return defaultHapiSpec("cannotCreateTooLargeContract")
                .given(
                        newKeyNamed(FILE_KEY),
                        newKeyListNamed(KEY_LIST, List.of(FILE_KEY)),
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS * 10).key(FILE_KEY),
                        fileCreate("bytecode")
                                .path(bytecodePath("CryptoKitties"))
                                .hasPrecheck(TRANSACTION_OVERSIZE)
                                // Modularized code will not allow a message larger than 6144 bytes at all
                                .orUnavailableStatus())
                .when(
                        fileCreate("bytecode").contents("").key(KEY_LIST),
                        UtilVerbs.updateLargeFile(ACCOUNT, "bytecode", contents))
                .then(contractCreate("contract")
                        .bytecode("bytecode")
                        .payingWith(ACCOUNT)
                        .hasKnownStatus(INSUFFICIENT_GAS));
    }

    HapiSpec blockTimestampChangesWithinFewSeconds() {
        final var contract = "EmitBlockTimestamp";
        final var firstBlock = "firstBlock";
        final var timeLoggingTxn = "timeLoggingTxn";

        return defaultHapiSpec("blockTimestampChangesWithinFewSeconds")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when(
                        contractCall(contract, "logNow").via(firstBlock),
                        cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(GENESIS, FUNDING, 1)),
                        sleepFor(3_000),
                        contractCall(contract, "logNow").via(timeLoggingTxn))
                .then(
                        withOpContext((spec, opLog) -> {
                            final var firstBlockOp = getTxnRecord(firstBlock);
                            final var recordOp = getTxnRecord(timeLoggingTxn);
                            allRunFor(spec, firstBlockOp, recordOp);

                            // First block info
                            final var firstBlockRecord = firstBlockOp.getResponseRecord();
                            final var firstBlockLogs =
                                    firstBlockRecord.getContractCallResult().getLogInfoList();
                            final var firstBlockTimeLogData =
                                    firstBlockLogs.get(0).getData().toByteArray();
                            final var firstBlockTimestamp =
                                    Longs.fromByteArray(Arrays.copyOfRange(firstBlockTimeLogData, 24, 32));
                            final var firstBlockHashLogData =
                                    firstBlockLogs.get(1).getData().toByteArray();
                            final var firstBlockNumber =
                                    Longs.fromByteArray(Arrays.copyOfRange(firstBlockHashLogData, 24, 32));
                            final var firstBlockHash = Bytes32.wrap(Arrays.copyOfRange(firstBlockHashLogData, 32, 64));
                            assertEquals(Bytes32.ZERO, firstBlockHash);

                            // Second block info
                            final var secondBlockRecord = recordOp.getResponseRecord();
                            final var secondBlockLogs =
                                    secondBlockRecord.getContractCallResult().getLogInfoList();
                            assertEquals(2, secondBlockLogs.size());
                            final var secondBlockTimeLogData =
                                    secondBlockLogs.get(0).getData().toByteArray();
                            final var secondBlockTimestamp =
                                    Longs.fromByteArray(Arrays.copyOfRange(secondBlockTimeLogData, 24, 32));
                            assertNotEquals(
                                    firstBlockTimestamp, secondBlockTimestamp, "Block timestamps should change");

                            final var secondBlockHashLogData =
                                    secondBlockLogs.get(1).getData().toByteArray();
                            final var secondBlockNumber =
                                    Longs.fromByteArray(Arrays.copyOfRange(secondBlockHashLogData, 24, 32));
                            assertNotEquals(firstBlockNumber, secondBlockNumber, "Wrong previous block number");
                            final var secondBlockHash =
                                    Bytes32.wrap(Arrays.copyOfRange(secondBlockHashLogData, 32, 64));

                            assertEquals(Bytes32.ZERO, secondBlockHash);
                        }),
                        contractCallLocal(contract, "getLastBlockHash")
                                .exposingTypedResultsTo(
                                        results -> log.info("Results were {}", CommonUtils.hex((byte[]) results[0]))));
    }

    @HapiTest
    HapiSpec vanillaSuccess() {
        final var contract = "CreateTrivial";
        return defaultHapiSpec("VanillaSuccess")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract).adminKey(THRESHOLD),
                        getContractInfo(contract).saveToRegistry(PARENT_INFO))
                .when(
                        contractCall(contract, "create").gas(1_000_000L).via("createChildTxn"),
                        contractCall(contract, "getIndirect").gas(1_000_000L).via("getChildResultTxn"),
                        contractCall(contract, "getAddress").gas(1_000_000L).via("getChildAddressTxn"))
                .then(
                        getTxnRecord("createChildTxn")
                                .saveCreatedContractListToRegistry("createChild")
                                .logged(),
                        getTxnRecord("getChildResultTxn")
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith()
                                                .resultThruAbi(
                                                        getABIFor(FUNCTION, "getIndirect", contract),
                                                        isLiteralResult(new Object[] {BigInteger.valueOf(7L)})))),
                        getTxnRecord("getChildAddressTxn")
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith()
                                                .resultThruAbi(
                                                        getABIFor(FUNCTION, "getAddress", contract),
                                                        isContractWith(contractWith()
                                                                .nonNullContractId()
                                                                .propertiesInheritedFrom(PARENT_INFO)))
                                                .logs(inOrder()))),
                        contractListWithPropertiesInheritedFrom("createChildCallResult", 1, PARENT_INFO));
    }

    @HapiTest
    HapiSpec newAccountsCanUsePureContractIdKey() {
        final var contract = "CreateTrivial";
        final var contractControlled = "contractControlled";
        return defaultHapiSpec("NewAccountsCanUsePureContractIdKey")
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract),
                        cryptoCreate(contractControlled).keyShape(CONTRACT.signedWith(contract)))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    final var registry = spec.registry();
                    final var contractIdKey = Key.newBuilder()
                            .setContractID(registry.getContractId(contract))
                            .build();
                    final var keyCheck =
                            getAccountInfo(contractControlled).has(accountWith().key(contractIdKey));
                    allRunFor(spec, keyCheck);
                }));
    }

    @HapiTest
    HapiSpec contractWithAutoRenewNeedSignatures() {
        final var contract = "CreateTrivial";
        final var autoRenewAccount = "autoRenewAccount";
        return defaultHapiSpec("contractWithAutoRenewNeedSignatures")
                .given(
                        newKeyNamed(ADMIN_KEY),
                        uploadInitCode(contract),
                        cryptoCreate(autoRenewAccount).balance(ONE_HUNDRED_HBARS),
                        contractCreate(contract)
                                .adminKey(ADMIN_KEY)
                                .autoRenewAccountId(autoRenewAccount)
                                .signedBy(DEFAULT_PAYER, ADMIN_KEY)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        contractCreate(contract)
                                .adminKey(ADMIN_KEY)
                                .autoRenewAccountId(autoRenewAccount)
                                .signedBy(DEFAULT_PAYER, ADMIN_KEY, autoRenewAccount)
                                .logged(),
                        getContractInfo(contract)
                                .has(ContractInfoAsserts.contractWith().autoRenewAccountId(autoRenewAccount))
                                .logged())
                .when()
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    private EthTxData placeholderEthTx() {
        return new EthTxData(
                null,
                EthTxData.EthTransactionType.EIP1559,
                Integers.toBytes(CHAIN_ID),
                0L,
                BigInteger.ONE.toByteArray(),
                BigInteger.ONE.toByteArray(),
                BigInteger.ONE.toByteArray(),
                1234,
                new byte[] {},
                BigInteger.ONE,
                new byte[] {},
                new byte[] {},
                0,
                null,
                null,
                null);
    }
}
