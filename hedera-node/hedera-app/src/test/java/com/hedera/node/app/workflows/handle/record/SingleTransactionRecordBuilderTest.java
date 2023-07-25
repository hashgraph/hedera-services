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

import static com.ibm.icu.impl.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"removal", "DataFlowIssue"})
@ExtendWith(MockitoExtension.class)
public class SingleTransactionRecordBuilderTest {
    public static final Instant CONSENSUS_TIME = Instant.now();
    public static final Instant PARENT_CONSENSUS_TIME = Instant.now();
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
    private @Mock ContractActions contractAction;
    private @Mock ContractBytecode contractBytecode;

    @ParameterizedTest
    @EnumSource(TransactionRecord.EntropyOneOfType.class)
    public void testBuilder(TransactionRecord.EntropyOneOfType entropyOneOfType) {
        if (entropyOneOfType == TransactionRecord.EntropyOneOfType.UNSET) {
            return;
        }

        final List<TokenTransferList> tokenTransferLists = List.of(tokenTransfer);
        final List<AssessedCustomFee> assessedCustomFees = List.of(assessedCustomFee);
        final List<TokenAssociation> automaticTokenAssociations = List.of(tokenAssociation);
        final List<AccountAmount> paidStakingRewards = List.of(accountAmount);
        final List<Long> serialNumbers = List.of(1L, 2L, 3L);

        SingleTransactionRecordBuilderImpl singleTransactionRecordBuilder =
                new SingleTransactionRecordBuilderImpl(CONSENSUS_TIME, PARENT_CONSENSUS_TIME);
        assertEquals(CONSENSUS_TIME, singleTransactionRecordBuilder.consensusNow());

        singleTransactionRecordBuilder
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
                .addContractStateChanges(contractStateChanges, false)
                .addContractAction(contractAction, false)
                .addContractBytecode(contractBytecode, false);

        if (entropyOneOfType == TransactionRecord.EntropyOneOfType.PRNG_BYTES) {
            singleTransactionRecordBuilder.entropyBytes(prngBytes);
            assertEquals(
                    TransactionRecord.EntropyOneOfType.PRNG_BYTES,
                    singleTransactionRecordBuilder.entropy().kind());
            assertEquals(prngBytes, singleTransactionRecordBuilder.entropy().value());
        } else if (entropyOneOfType == TransactionRecord.EntropyOneOfType.PRNG_NUMBER) {
            singleTransactionRecordBuilder.entropyNumber(ENTROPY_NUMBER);
            assertEquals(
                    TransactionRecord.EntropyOneOfType.PRNG_NUMBER,
                    singleTransactionRecordBuilder.entropy().kind());
            assertEquals(
                    ENTROPY_NUMBER, singleTransactionRecordBuilder.entropy().value());
        } else {
            fail("Unknown entropy type");
        }

        assertEquals(status, singleTransactionRecordBuilder.status());
        assertEquals(accountID, singleTransactionRecordBuilder.accountID());
        assertEquals(tokenID, singleTransactionRecordBuilder.tokenID());
        assertEquals(topicID, singleTransactionRecordBuilder.topicID());
        assertEquals(TOPIC_SEQUENCE_NUMBER, singleTransactionRecordBuilder.topicSequenceNumber());
        assertEquals(topicRunningHash, singleTransactionRecordBuilder.topicRunningHash());
        assertEquals(serialNumbers, singleTransactionRecordBuilder.serialNumbers());

        SingleTransactionRecord singleTransactionRecord = singleTransactionRecordBuilder.build();
        assertEquals(transaction, singleTransactionRecord.transaction());

        final Bytes transactionHash;
        try {
            final MessageDigest digest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
            transactionHash = Bytes.wrap(digest.digest(transactionBytes.toByteArray()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        assertEquals(transactionHash, singleTransactionRecord.record().transactionHash());
        assertEquals(
                HapiUtils.asTimestamp(CONSENSUS_TIME),
                singleTransactionRecord.record().consensusTimestamp());
        assertEquals(transactionID, singleTransactionRecord.record().transactionID());
        assertEquals(MEMO, singleTransactionRecord.record().memo());
        assertEquals(TRANSACTION_FEE, singleTransactionRecord.record().transactionFee());
        assertEquals(transferList, singleTransactionRecord.record().transferList());
        assertEquals(tokenTransferLists, singleTransactionRecord.record().tokenTransferLists());
        assertEquals(scheduleRef, singleTransactionRecord.record().scheduleRef());
        assertEquals(assessedCustomFees, singleTransactionRecord.record().assessedCustomFees());
        assertEquals(
                automaticTokenAssociations, singleTransactionRecord.record().automaticTokenAssociations());
        assertEquals(
                HapiUtils.asTimestamp(PARENT_CONSENSUS_TIME),
                singleTransactionRecord.record().parentConsensusTimestamp());
        assertEquals(alias, singleTransactionRecord.record().alias());
        assertEquals(ethereumHash, singleTransactionRecord.record().ethereumHash());
        assertEquals(paidStakingRewards, singleTransactionRecord.record().paidStakingRewards());
        assertEquals(evmAddress, singleTransactionRecord.record().evmAddress());

        assertEquals(status, singleTransactionRecord.record().receipt().status());
        assertEquals(accountID, singleTransactionRecord.record().receipt().accountID());
        assertEquals(fileID, singleTransactionRecord.record().receipt().fileID());
        assertEquals(contractID, singleTransactionRecord.record().receipt().contractID());
        assertEquals(exchangeRate, singleTransactionRecord.record().receipt().exchangeRate());
        assertEquals(topicID, singleTransactionRecord.record().receipt().topicID());
        assertEquals(
                TOPIC_SEQUENCE_NUMBER,
                singleTransactionRecord.record().receipt().topicSequenceNumber());
        assertEquals(
                topicRunningHash, singleTransactionRecord.record().receipt().topicRunningHash());
        assertEquals(tokenID, singleTransactionRecord.record().receipt().tokenID());
        assertEquals(
                NEW_TOTAL_SUPPLY, singleTransactionRecord.record().receipt().newTotalSupply());
        assertEquals(scheduleID, singleTransactionRecord.record().receipt().scheduleID());
        assertEquals(
                scheduledTransactionID,
                singleTransactionRecord.record().receipt().scheduledTransactionID());
        assertEquals(serialNumbers, singleTransactionRecord.record().receipt().serialNumbers());
    }
}
