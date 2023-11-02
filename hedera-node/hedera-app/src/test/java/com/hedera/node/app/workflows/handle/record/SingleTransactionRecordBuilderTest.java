/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.record;

import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"DataFlowIssue"})
@ExtendWith(MockitoExtension.class)
public class SingleTransactionRecordBuilderTest {
    public static final Instant CONSENSUS_TIME = Instant.now();
    public static final Instant PARENT_CONSENSUS_TIME = CONSENSUS_TIME.plusNanos(1L);
    public static final long TRANSACTION_FEE = 6846513L;
    public static final int ENTROPY_NUMBER = 87372879;
    public static final long TOPIC_SEQUENCE_NUMBER = 928782L;
    public static final long TOPIC_RUNNING_HASH_VERSION = 153513L;
    public static final long NEW_TOTAL_SUPPLY = 34134546L;
    public static final String MEMO = "Yo Memo";
    private @Mock Transaction transaction;
    private @Mock TransactionBody transactionBody;
    private @Mock TransactionID transactionID;
    private final Bytes transactionBytes = Bytes.wrap("Hello Tester");
    private @Mock ContractFunctionResult contractCallResult;
    private @Mock ContractFunctionResult contractCreateResult;
    private @Mock TransferList transferList;
    private @Mock TokenTransferList tokenTransfer;
    private @Mock ScheduleID scheduleRef;
    private @Mock AssessedCustomFee assessedCustomFee;
    private @Mock TokenAssociation tokenAssociation;
    private @Mock Bytes alias;
    private @Mock Bytes ethereumHash;
    private @Mock Bytes prngBytes;
    private @Mock AccountAmount accountAmount;
    private @Mock Bytes evmAddress;
    private @Mock ResponseCodeEnum status;
    private @Mock AccountID accountID;
    private @Mock FileID fileID;
    private @Mock ContractID contractID;
    private @Mock ExchangeRateSet exchangeRate;
    private @Mock TopicID topicID;
    private @Mock Bytes topicRunningHash;
    private @Mock TokenID tokenID;
    private @Mock ScheduleID scheduleID;
    private @Mock TransactionID scheduledTransactionID;
    private @Mock ContractStateChanges contractStateChanges;
    private @Mock ContractActions contractActions;
    private @Mock ContractBytecode contractBytecode;

    @ParameterizedTest
    @EnumSource(TransactionRecord.EntropyOneOfType.class)
    void testBuilder(TransactionRecord.EntropyOneOfType entropyOneOfType) {
        if (entropyOneOfType == TransactionRecord.EntropyOneOfType.UNSET) {
            return;
        }

        final List<TokenTransferList> tokenTransferLists = List.of(tokenTransfer);
        final List<AssessedCustomFee> assessedCustomFees = List.of(assessedCustomFee);
        final List<TokenAssociation> automaticTokenAssociations = List.of(tokenAssociation);
        final List<AccountAmount> paidStakingRewards = List.of(accountAmount);
        final List<Long> serialNumbers = List.of(1L, 2L, 3L);

        SingleTransactionRecordBuilderImpl singleTransactionRecordBuilder =
                new SingleTransactionRecordBuilderImpl(CONSENSUS_TIME);
        assertEquals(CONSENSUS_TIME, singleTransactionRecordBuilder.consensusNow());

        singleTransactionRecordBuilder
                .parentConsensus(PARENT_CONSENSUS_TIME)
                .transaction(transaction)
                .transactionBytes(transactionBytes)
                .transactionID(transactionID)
                .memo(MEMO)
                .transactionFee(TRANSACTION_FEE)
                .contractCallResult(contractCallResult)
                .contractCreateResult(contractCreateResult)
                .transferList(transferList)
                .tokenTransferLists(tokenTransferLists)
                .scheduleRef(scheduleRef)
                .assessedCustomFees(assessedCustomFees)
                .automaticTokenAssociations(automaticTokenAssociations)
                .alias(alias)
                .ethereumHash(ethereumHash)
                .paidStakingRewards(paidStakingRewards)
                .evmAddress(evmAddress)
                .status(status)
                .accountID(accountID)
                .fileID(fileID)
                .contractID(contractID)
                .exchangeRate(exchangeRate)
                .topicID(topicID)
                .topicSequenceNumber(TOPIC_SEQUENCE_NUMBER)
                .topicRunningHash(topicRunningHash)
                .topicRunningHashVersion(TOPIC_RUNNING_HASH_VERSION)
                .tokenID(tokenID)
                .newTotalSupply(NEW_TOTAL_SUPPLY)
                .scheduleID(scheduleID)
                .scheduledTransactionID(scheduledTransactionID)
                .serialNumbers(serialNumbers)
                .contractStateChanges(List.of(new AbstractMap.SimpleEntry<>(contractStateChanges, false)))
                .contractActions(List.of(new AbstractMap.SimpleEntry<>(contractActions, false)))
                .contractBytecodes(List.of(new AbstractMap.SimpleEntry<>(contractBytecode, false)));

        if (entropyOneOfType == TransactionRecord.EntropyOneOfType.PRNG_BYTES) {
            singleTransactionRecordBuilder.entropyBytes(prngBytes);
        } else if (entropyOneOfType == TransactionRecord.EntropyOneOfType.PRNG_NUMBER) {
            singleTransactionRecordBuilder.entropyNumber(ENTROPY_NUMBER);
        } else {
            fail("Unknown entropy type");
        }

        SingleTransactionRecord singleTransactionRecord = singleTransactionRecordBuilder.build();
        assertEquals(
                HapiUtils.asTimestamp(PARENT_CONSENSUS_TIME),
                singleTransactionRecord.transactionRecord().parentConsensusTimestamp());
        assertEquals(transaction, singleTransactionRecord.transaction());

        if (entropyOneOfType == TransactionRecord.EntropyOneOfType.PRNG_BYTES) {
            assertTrue(singleTransactionRecord.transactionRecord().hasPrngBytes());
            assertEquals(prngBytes, singleTransactionRecord.transactionRecord().prngBytes());
        } else {
            assertTrue(singleTransactionRecord.transactionRecord().hasPrngNumber());
            assertEquals(
                    ENTROPY_NUMBER, singleTransactionRecord.transactionRecord().prngNumber());
        }

        final Bytes transactionHash;
        try {
            final MessageDigest digest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
            transactionHash = Bytes.wrap(digest.digest(transactionBytes.toByteArray()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        assertEquals(
                transactionHash, singleTransactionRecord.transactionRecord().transactionHash());
        assertEquals(
                HapiUtils.asTimestamp(CONSENSUS_TIME),
                singleTransactionRecord.transactionRecord().consensusTimestamp());
        assertEquals(transactionID, singleTransactionRecord.transactionRecord().transactionID());
        assertEquals(MEMO, singleTransactionRecord.transactionRecord().memo());
        assertEquals(
                TRANSACTION_FEE, singleTransactionRecord.transactionRecord().transactionFee());
        assertEquals(transferList, singleTransactionRecord.transactionRecord().transferList());
        assertEquals(
                tokenTransferLists, singleTransactionRecord.transactionRecord().tokenTransferLists());
        assertEquals(scheduleRef, singleTransactionRecord.transactionRecord().scheduleRef());
        assertEquals(
                assessedCustomFees, singleTransactionRecord.transactionRecord().assessedCustomFees());
        assertEquals(
                automaticTokenAssociations,
                singleTransactionRecord.transactionRecord().automaticTokenAssociations());
        assertEquals(
                HapiUtils.asTimestamp(PARENT_CONSENSUS_TIME),
                singleTransactionRecord.transactionRecord().parentConsensusTimestamp());
        assertEquals(alias, singleTransactionRecord.transactionRecord().alias());
        assertEquals(ethereumHash, singleTransactionRecord.transactionRecord().ethereumHash());
        assertEquals(
                paidStakingRewards, singleTransactionRecord.transactionRecord().paidStakingRewards());
        assertEquals(evmAddress, singleTransactionRecord.transactionRecord().evmAddress());

        assertTransactionReceiptProps(
                singleTransactionRecord.transactionRecord().receipt(), serialNumbers);

        final var expectedTransactionSidecarRecords = List.of(
                new TransactionSidecarRecord(
                        HapiUtils.asTimestamp(CONSENSUS_TIME),
                        false,
                        new OneOf<>(
                                TransactionSidecarRecord.SidecarRecordsOneOfType.STATE_CHANGES, contractStateChanges)),
                new TransactionSidecarRecord(
                        HapiUtils.asTimestamp(CONSENSUS_TIME),
                        false,
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.ACTIONS, contractActions)),
                new TransactionSidecarRecord(
                        HapiUtils.asTimestamp(CONSENSUS_TIME),
                        false,
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.BYTECODE, contractBytecode)));
        assertEquals(expectedTransactionSidecarRecords, singleTransactionRecord.transactionSidecarRecords());
    }

    private void assertTransactionReceiptProps(TransactionReceipt receipt, List<Long> serialNumbers) {
        assertEquals(status, receipt.status());
        assertEquals(accountID, receipt.accountID());
        assertEquals(fileID, receipt.fileID());
        assertEquals(contractID, receipt.contractID());
        assertEquals(exchangeRate, receipt.exchangeRate());
        assertEquals(topicID, receipt.topicID());
        assertEquals(TOPIC_SEQUENCE_NUMBER, receipt.topicSequenceNumber());
        assertEquals(topicRunningHash, receipt.topicRunningHash());
        assertEquals(tokenID, receipt.tokenID());
        assertEquals(NEW_TOTAL_SUPPLY, receipt.newTotalSupply());
        assertEquals(scheduleID, receipt.scheduleID());
        assertEquals(scheduledTransactionID, receipt.scheduledTransactionID());
        assertEquals(serialNumbers, receipt.serialNumbers());
    }

    @Test
    void testTopLevelRecordBuilder() {
        SingleTransactionRecordBuilderImpl singleTransactionRecordBuilder =
                new SingleTransactionRecordBuilderImpl(CONSENSUS_TIME);

        singleTransactionRecordBuilder.transaction(transaction);

        assertEquals(CONSENSUS_TIME, singleTransactionRecordBuilder.consensusNow());
        assertNull(singleTransactionRecordBuilder.parentConsensusTimestamp());
        assertEquals(ResponseCodeEnum.OK, singleTransactionRecordBuilder.status());

        SingleTransactionRecord singleTransactionRecord = singleTransactionRecordBuilder.build();

        assertEquals(transaction, singleTransactionRecord.transaction());
        assertEquals(
                HapiUtils.asTimestamp(CONSENSUS_TIME),
                singleTransactionRecord.transactionRecord().consensusTimestamp());
        assertNull(singleTransactionRecord.transactionRecord().parentConsensusTimestamp());
        assertEquals(
                ResponseCodeEnum.OK,
                singleTransactionRecord.transactionRecord().receipt().status());
    }

    @Test
    void testBuilderWithAddMethods() {
        SingleTransactionRecordBuilderImpl singleTransactionRecordBuilder =
                new SingleTransactionRecordBuilderImpl(CONSENSUS_TIME);

        SingleTransactionRecord singleTransactionRecord = singleTransactionRecordBuilder
                .transaction(transaction)
                .addTokenTransferList(tokenTransfer)
                .addAssessedCustomFee(assessedCustomFee)
                .addAutomaticTokenAssociation(tokenAssociation)
                .addPaidStakingReward(accountAmount)
                .addSerialNumber(1L)
                .addContractStateChanges(contractStateChanges, false)
                .addContractActions(contractActions, false)
                .addContractBytecode(contractBytecode, false)
                .build();

        assertEquals(transaction, singleTransactionRecord.transaction());
        assertEquals(
                HapiUtils.asTimestamp(CONSENSUS_TIME),
                singleTransactionRecord.transactionRecord().consensusTimestamp());
        assertNull(singleTransactionRecord.transactionRecord().parentConsensusTimestamp());
        assertEquals(
                ResponseCodeEnum.OK,
                singleTransactionRecord.transactionRecord().receipt().status());
        assertEquals(
                List.of(tokenTransfer),
                singleTransactionRecord.transactionRecord().tokenTransferLists());
        assertEquals(
                List.of(assessedCustomFee),
                singleTransactionRecord.transactionRecord().assessedCustomFees());
        assertEquals(
                List.of(tokenAssociation),
                singleTransactionRecord.transactionRecord().automaticTokenAssociations());
        assertEquals(
                List.of(accountAmount),
                singleTransactionRecord.transactionRecord().paidStakingRewards());
        assertEquals(
                List.of(1L),
                singleTransactionRecord.transactionRecord().receipt().serialNumbers());

        final var expectedTransactionSidecarRecords = List.of(
                new TransactionSidecarRecord(
                        HapiUtils.asTimestamp(CONSENSUS_TIME),
                        false,
                        new OneOf<>(
                                TransactionSidecarRecord.SidecarRecordsOneOfType.STATE_CHANGES, contractStateChanges)),
                new TransactionSidecarRecord(
                        HapiUtils.asTimestamp(CONSENSUS_TIME),
                        false,
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.ACTIONS, contractActions)),
                new TransactionSidecarRecord(
                        HapiUtils.asTimestamp(CONSENSUS_TIME),
                        false,
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.BYTECODE, contractBytecode)));
        assertEquals(expectedTransactionSidecarRecords, singleTransactionRecord.transactionSidecarRecords());
    }
}
