/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.records;

import static com.hedera.node.config.types.StreamMode.RECORDS;
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION;
import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextAdhocPeriod;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.RELAYER;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SOURCE_KEY;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ACCOUNT;
import static com.hedera.services.bdd.suites.leaky.LeakyContractTestsSuite.RECEIVER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.primitives.Longs;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.RepeatableHapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("Records Suite")
public class RecordsSuite {
    public static final String LOG_NOW = "logNow";
    public static final String AUTO_ACCOUNT = "autoAccount";

    @HapiTest
    final Stream<DynamicTest> bigCall() {
        final var contract = "BigBig";
        final var txName = "BigCall";
        final long byteArraySize = (long) (87.5 * 1_024);

        return defaultHapiSpec("bigCall")
                .given(
                        cryptoCreate("payer").balance(10 * ONE_HUNDRED_HBARS),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(contractCall(contract, "pick", byteArraySize)
                        .payingWith("payer")
                        .gas(400_000L)
                        .via(txName))
                .then(getTxnRecord(txName));
    }

    @HapiTest
    final Stream<DynamicTest> txRecordsContainValidTransfers() {
        final var contract = "ParentChildTransfer";

        return hapiTest(
                uploadInitCode(contract),
                contractCreate(contract).balance(10_000L).via("createTx"),
                contractCall(contract, "transferToChild", BigInteger.valueOf(10_000))
                        .via("transferTx"),
                assertionsHold((spec, ctxLog) -> {
                    final var subop01 = getTxnRecord("createTx").saveTxnRecordToRegistry("createTxRec");
                    final var subop02 = getTxnRecord("transferTx").saveTxnRecordToRegistry("transferTxRec");
                    CustomSpecAssert.allRunFor(spec, subop01, subop02);

                    final var createRecord = spec.registry().getTransactionRecord("createTxRec");
                    final var parent = createRecord.getContractCreateResult().getCreatedContractIDs(0);
                    final var child = createRecord.getContractCreateResult().getCreatedContractIDs(1);

                    // validate transfer list
                    final List<AccountAmount> expectedTransfers = new ArrayList<>(2);
                    final var receiverTransfer = AccountAmount.newBuilder()
                            .setAccountID(AccountID.newBuilder()
                                    .setAccountNum(parent.getContractNum())
                                    .build())
                            .setAmount(-10_000L)
                            .build();
                    expectedTransfers.add(receiverTransfer);
                    final var contractTransfer = AccountAmount.newBuilder()
                            .setAccountID(AccountID.newBuilder()
                                    .setAccountNum(child.getContractNum())
                                    .build())
                            .setAmount(10_000L)
                            .build();
                    expectedTransfers.add(contractTransfer);

                    final var transferRecord = spec.registry().getTransactionRecord("transferTxRec");

                    final var transferList = transferRecord.getTransferList();
                    Assertions.assertNotNull(transferList);
                    Assertions.assertNotNull(transferList.getAccountAmountsList());
                    Assertions.assertTrue(transferList.getAccountAmountsList().containsAll(expectedTransfers));
                    final var amountSum = sumAmountsInTransferList(transferList.getAccountAmountsList());
                    Assertions.assertEquals(0, amountSum);
                }));
    }

    @SuppressWarnings("java:S5960")
    @HapiTest
    final Stream<DynamicTest> blck003ReturnsTimestampOfTheBlock() {
        final var contract = "EmitBlockTimestamp";
        final var firstCall = "firstCall";
        final var secondCall = "secondCall";

        return hapiTest(
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                        .via(AUTO_ACCOUNT),
                getTxnRecord(AUTO_ACCOUNT).andAllChildRecords(),
                uploadInitCode(contract),
                contractCreate(contract),
                // Ensure we submit these two transactions in the same block
                waitUntilStartOfNextAdhocPeriod(2_000),
                ethereumCall(contract, LOG_NOW)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .maxFeePerGas(50L)
                        .gasLimit(1_000_000L)
                        .via(firstCall)
                        .deferStatusResolution()
                        .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                ethereumCall(contract, LOG_NOW)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(1)
                        .maxFeePerGas(50L)
                        .gasLimit(1_000_000L)
                        .via(secondCall)
                        .deferStatusResolution()
                        .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                withOpContext((spec, opLog) -> {
                    final var firstBlockOp = getTxnRecord(firstCall).hasRetryAnswerOnlyPrecheck(RECORD_NOT_FOUND);
                    final var recordOp = getTxnRecord(secondCall).hasRetryAnswerOnlyPrecheck(RECORD_NOT_FOUND);
                    allRunFor(spec, firstBlockOp, recordOp);

                    final var firstCallRecord = firstBlockOp.getResponseRecord();
                    final var firstCallLogs =
                            firstCallRecord.getContractCallResult().getLogInfoList();
                    final var firstCallTimeLogData =
                            firstCallLogs.get(0).getData().toByteArray();
                    final var firstCallTimestamp =
                            Longs.fromByteArray(Arrays.copyOfRange(firstCallTimeLogData, 24, 32));

                    final var secondCallRecord = recordOp.getResponseRecord();
                    final var secondCallLogs =
                            secondCallRecord.getContractCallResult().getLogInfoList();
                    final var secondCallTimeLogData =
                            secondCallLogs.get(0).getData().toByteArray();
                    final var secondCallTimestamp =
                            Longs.fromByteArray(Arrays.copyOfRange(secondCallTimeLogData, 24, 32));

                    final var blockPeriod = spec.startupProperties().getLong("hedera.recordStream.logPeriod");
                    final var firstBlockPeriod =
                            canonicalBlockPeriod(firstCallRecord.getConsensusTimestamp(), blockPeriod);
                    final var secondBlockPeriod =
                            canonicalBlockPeriod(secondCallRecord.getConsensusTimestamp(), blockPeriod);

                    // In general both calls will be handled in the same block period, and should hence have the
                    // same Ethereum block timestamp; but timing fluctuations in CI _can_ cause them to be handled
                    // in different block periods, so we allow for that here as well
                    if (firstBlockPeriod < secondBlockPeriod) {
                        assertTrue(
                                firstCallTimestamp < secondCallTimestamp,
                                "Block timestamps should change from period " + firstBlockPeriod + " to "
                                        + secondBlockPeriod);
                    } else {
                        assertEquals(firstCallTimestamp, secondCallTimestamp, "Block timestamps should be equal");
                    }
                }));
    }

    @HapiTest
    final Stream<DynamicTest> blck001And002And003And004ReturnsCorrectBlockProperties() {
        final var contract = "EmitBlockTimestamp";
        final var firstBlock = "firstBlock";
        final var secondBlock = "secondBlock";

        return hapiTest(
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                        .via(AUTO_ACCOUNT),
                getTxnRecord(AUTO_ACCOUNT).andAllChildRecords(),
                uploadInitCode(contract),
                contractCreate(contract),
                waitUntilStartOfNextAdhocPeriod(2_000L),
                ethereumCall(contract, LOG_NOW)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .maxFeePerGas(50L)
                        .gasLimit(1_000_000L)
                        .via(firstBlock)
                        .deferStatusResolution()
                        .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                // Make sure we submit the next transaction in the next block
                waitUntilStartOfNextAdhocPeriod(2_000L),
                ethereumCall(contract, LOG_NOW)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(1)
                        .maxFeePerGas(50L)
                        .gasLimit(1_000_000L)
                        .via(secondBlock)
                        .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                withOpContext((spec, opLog) -> {
                    final var firstBlockOp = getTxnRecord(firstBlock).hasRetryAnswerOnlyPrecheck(RECORD_NOT_FOUND);
                    final var recordOp = getTxnRecord(secondBlock).hasRetryAnswerOnlyPrecheck(RECORD_NOT_FOUND);
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
                    final var firstBlockNumber = Longs.fromByteArray(Arrays.copyOfRange(firstBlockHashLogData, 24, 32));
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

                    assertNotEquals(firstBlockTimestamp, secondBlockTimestamp, "Block timestamps should change");

                    final var secondBlockHashLogData =
                            secondBlockLogs.get(1).getData().toByteArray();
                    final var secondBlockNumber =
                            Longs.fromByteArray(Arrays.copyOfRange(secondBlockHashLogData, 24, 32));

                    if (spec.startupProperties().getStreamMode("blockStream.streamMode") == RECORDS) {
                        // This relationship is only guaranteed if block boundaries are based on time periods
                        assertEquals(firstBlockNumber + 1, secondBlockNumber, "Wrong previous block number");
                    }

                    final var secondBlockHash = Bytes32.wrap(Arrays.copyOfRange(secondBlockHashLogData, 32, 64));

                    assertEquals(Bytes32.ZERO, secondBlockHash);
                }));
    }

    @DisplayName("Block Hash Returns The Hash Of The Latest 256 Blocks")
    @RepeatableHapiTest(NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION)
    final Stream<DynamicTest> blockHashReturnsTheHashOfTheLatest256Blocks() {
        final var contract = "EmitBlockTimestamp";
        return hapiTest(
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                uploadInitCode(contract),
                contractCreate(contract).gas(4_000_000L),
                cryptoCreate(ACCOUNT).balance(6 * ONE_MILLION_HBARS),
                cryptoCreate(RECEIVER),
                cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),
                withOpContext((spec, opLog) -> {
                    doNTransfers(spec, 256);
                    waitUntilStartOfNextAdhocPeriod(2_000L);
                    final var ethCall = ethereumCall(contract, "getAllBlockHashes")
                            .logged()
                            .gasLimit(4_000_000L)
                            .via("blockHashes");
                    final var blockHashRes = getTxnRecord("blockHashes").logged();
                    allRunFor(spec, ethCall, waitUntilStartOfNextAdhocPeriod(2_000L), blockHashRes);
                    assertTrue(blockHashRes
                            .getResponseRecord()
                            .getContractCallResult()
                            .getErrorMessage()
                            .isEmpty());
                    final var res = blockHashRes
                            .getResponseRecord()
                            .getContractCallResult()
                            .getContractCallResult()
                            .substring(64);
                    // Ensure that we have 256 block hashes
                    assertEquals(res.size() / 32, 256);
                }));
    }

    // Helper method to perform multiple transfers and simulate multiple block creations
    private void doNTransfers(@NonNull final HapiSpec spec, final int amount) {
        allRunFor(
                spec,
                Stream.iterate(1, i -> i + 1)
                        .limit(amount)
                        .mapMulti((Integer i, Consumer<HapiSpecOperation> consumer) -> {
                            consumer.accept(sleepFor(2_000L));
                            consumer.accept(
                                    cryptoTransfer(TokenMovement.movingHbar(i).between(ACCOUNT, RECEIVER)));
                        })
                        .toArray(HapiSpecOperation[]::new));
    }

    /**
     * Returns the canonical block period for the given consensus timestamp.
     *
     * @param consensusTimestamp the consensus timestamp
     * @param blockPeriod the number of seconds in a block period
     * @return the canonical block period
     */
    private long canonicalBlockPeriod(@NonNull final Timestamp consensusTimestamp, long blockPeriod) {
        return Objects.requireNonNull(consensusTimestamp).getSeconds() / blockPeriod;
    }

    private long sumAmountsInTransferList(List<AccountAmount> transferList) {
        var sumToReturn = 0L;
        for (AccountAmount currAccAmount : transferList) {
            sumToReturn += currAccAmount.getAmount();
        }
        return sumToReturn;
    }
}
