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
package com.hedera.services.bdd.spec.queries.meta;

import static com.hedera.services.bdd.spec.assertions.AssertUtils.rethrowSummaryError;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asDebits;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTokenId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.isEndOfStakingPeriodRecord;
import static com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate.correspondingScheduledTxnId;
import static com.hedera.services.bdd.suites.HapiApiSuite.HBAR_TOKEN_SENTINEL;
import static com.hedera.services.bdd.suites.crypto.CryptoTransferSuite.sdec;
import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ErroringAsserts;
import com.hedera.services.bdd.spec.assertions.ErroringAssertsProvider;
import com.hedera.services.bdd.spec.assertions.SequentialID;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.AssessedCustomFee;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.solidity.Abi;
import org.junit.jupiter.api.Assertions;

public class HapiGetTxnRecord extends HapiQueryOp<HapiGetTxnRecord> {
    private static final Logger LOG = LogManager.getLogger(HapiGetTxnRecord.class);

    private static final TransactionID defaultTxnId = TransactionID.getDefaultInstance();
    public static final int MAX_PSEUDORANDOM_BYTES_LENGTH = 48;

    private String txn;
    private boolean scheduled = false;
    private boolean assertNothing = false;
    private boolean useDefaultTxnId = false;
    private boolean requestDuplicates = false;
    private boolean requestChildRecords = false;
    private boolean includeStakingRecordsInCount = true;
    private boolean shouldBeTransferFree = false;
    private boolean assertOnlyPriority = false;
    private boolean assertNothingAboutHashes = false;
    private boolean lookupScheduledFromRegistryId = false;
    private boolean omitPaymentHeaderOnCostAnswer = false;
    private boolean validateStakingFees = false;

    private boolean stakingFeeExempted = false;
    private List<Pair<String, Long>> accountAmountsToValidate = new ArrayList<>();
    private List<Triple<String, String, Long>> tokenAmountsToValidate = new ArrayList<>();
    private List<AssessedNftTransfer> assessedNftTransfersToValidate = new ArrayList<>();
    private List<Triple<String, String, Long>> assessedCustomFeesToValidate = new ArrayList<>();
    private List<Pair<String, String>> newTokenAssociations = new ArrayList<>();
    private OptionalInt assessedCustomFeesSize = OptionalInt.empty();
    private Optional<TransactionID> explicitTxnId = Optional.empty();
    private Optional<TransactionRecordAsserts> priorityExpectations = Optional.empty();
    private Optional<TransactionID> expectedParentId = Optional.empty();
    private Optional<List<TransactionRecordAsserts>> childRecordsExpectations = Optional.empty();
    private Optional<BiConsumer<TransactionRecord, Logger>> format = Optional.empty();
    private Optional<String> creationName = Optional.empty();
    private Optional<String> saveTxnRecordToRegistry = Optional.empty();
    private Optional<String> registryEntry = Optional.empty();
    private Optional<String> topicToValidate = Optional.empty();
    private Optional<byte[]> lastMessagedSubmitted = Optional.empty();
    private Optional<LongConsumer> priceConsumer = Optional.empty();
    private Optional<Map<AccountID, Long>> expectedDebits = Optional.empty();
    private Optional<Consumer<Map<AccountID, Long>>> debitsConsumer = Optional.empty();
    private Optional<ErroringAssertsProvider<List<TransactionRecord>>> duplicateExpectations =
            Optional.empty();
    private OptionalInt childRecordsCount = OptionalInt.empty();
    private Optional<Consumer<TransactionRecord>> observer = Optional.empty();

    private Optional<Integer> pseudorandomNumberRange = Optional.empty();

    private boolean pseudorandomBytesExpected = false;

    private boolean noPseudoRandomData = false;
    private List<Pair<String, Long>> paidStakingRewards = new ArrayList<>();

    private Consumer<List<?>> eventDataObserver;
    private Predicate<Abi.Event> eventMatcher;
    private String contractResultAbi = null;

    public static ByteString sha384HashOf(final Transaction transaction) {
        if (transaction.getSignedTransactionBytes().isEmpty()) {
            return ByteString.copyFrom(noThrowSha384HashOf(transaction.toByteArray()));
        }

        return ByteString.copyFrom(
                noThrowSha384HashOf(transaction.getSignedTransactionBytes().toByteArray()));
    }

    private record ExpectedChildInfo(String aliasingKey, long pendingRewards) {}

    private Map<Integer, ExpectedChildInfo> childExpectations = new HashMap<>();

    public HapiGetTxnRecord(String txn) {
        this.txn = txn;
    }

    public HapiGetTxnRecord(TransactionID txnId) {
        this.explicitTxnId = Optional.of(txnId);
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TransactionGetRecord;
    }

    @Override
    protected HapiGetTxnRecord self() {
        return this;
    }

    public HapiGetTxnRecord exposingTo(final Consumer<TransactionRecord> observer) {
        this.observer = Optional.of(observer);
        return this;
    }

    public HapiGetTxnRecord exposingFilteredCallResultVia(
            final String abi,
            final Predicate<Abi.Event> eventMatcher,
            final Consumer<List<?>> dataObserver) {
        this.contractResultAbi = abi;
        this.eventMatcher = eventMatcher;
        this.eventDataObserver = dataObserver;

        return this;
    }

    public HapiGetTxnRecord scheduled() {
        scheduled = true;
        return this;
    }

    public HapiGetTxnRecord assertingOnlyPriority() {
        assertOnlyPriority = true;
        return this;
    }

    public HapiGetTxnRecord omittingAnyPaymentForCostAnswer() {
        omitPaymentHeaderOnCostAnswer = true;
        return this;
    }

    public HapiGetTxnRecord scheduledBy(String creation) {
        scheduled = true;
        creationName = Optional.of(creation);
        lookupScheduledFromRegistryId = true;
        return this;
    }

    public HapiGetTxnRecord andAnyDuplicates() {
        requestDuplicates = true;
        return this;
    }

    public HapiGetTxnRecord andAllChildRecords() {
        requestChildRecords = true;
        return this;
    }

    public HapiGetTxnRecord assertingNothingAboutHashes() {
        assertNothingAboutHashes = true;
        return this;
    }

    public HapiGetTxnRecord hasChildRecordCount(int count) {
        requestChildRecords = true;
        childRecordsCount = OptionalInt.of(count);
        return this;
    }

    public HapiGetTxnRecord hasNonStakingChildRecordCount(int count) {
        requestChildRecords = true;
        includeStakingRecordsInCount = false;
        childRecordsCount = OptionalInt.of(count);
        return this;
    }

    public HapiGetTxnRecord hasPaidStakingRewards(List<Pair<String, Long>> rewards) {
        paidStakingRewards = rewards;
        return this;
    }

    public HapiGetTxnRecord hasAliasInChildRecord(final String aliasingKey, final int childIndex) {
        requestChildRecords = true;
        childExpectations.put(childIndex, new ExpectedChildInfo(aliasingKey, 0L));
        return this;
    }

    public HapiGetTxnRecord assertingNothing() {
        assertNothing = true;
        return this;
    }

    public HapiGetTxnRecord hasExactDebits(Map<AccountID, Long> expected) {
        expectedDebits = Optional.of(expected);
        return this;
    }

    public HapiGetTxnRecord revealingDebitsTo(Consumer<Map<AccountID, Long>> observer) {
        debitsConsumer = Optional.of(observer);
        return this;
    }

    public HapiGetTxnRecord providingFeeTo(LongConsumer priceConsumer) {
        this.priceConsumer = Optional.of(priceConsumer);
        return this;
    }

    public HapiGetTxnRecord showsNoTransfers() {
        shouldBeTransferFree = true;
        return this;
    }

    public HapiGetTxnRecord saveCreatedContractListToRegistry(String registryEntry) {
        this.registryEntry = Optional.of(registryEntry);
        return this;
    }

    public HapiGetTxnRecord saveTxnRecordToRegistry(String txnRecordEntry) {
        this.saveTxnRecordToRegistry = Optional.of(txnRecordEntry);
        return this;
    }

    public HapiGetTxnRecord useDefaultTxnId() {
        useDefaultTxnId = true;
        return this;
    }

    public HapiGetTxnRecord hasPriority(TransactionRecordAsserts provider) {
        priorityExpectations = Optional.of(provider);
        return this;
    }

    public HapiGetTxnRecord hasOnlyPseudoRandomBytes() {
        pseudorandomBytesExpected = true;
        pseudorandomNumberRange = Optional.empty();
        return this;
    }

    public HapiGetTxnRecord hasOnlyPseudoRandomNumberInRange(int range) {
        pseudorandomBytesExpected = false;
        pseudorandomNumberRange = Optional.of(range);
        return this;
    }

    public HapiGetTxnRecord hasNoPseudoRandomData() {
        noPseudoRandomData = true;
        return this;
    }

    public HapiGetTxnRecord hasChildRecords(TransactionRecordAsserts... providers) {
        requestChildRecords = true;
        childRecordsExpectations = Optional.of(Arrays.asList(providers));
        return this;
    }

    public HapiGetTxnRecord hasChildRecords(
            TransactionID parentId, TransactionRecordAsserts... providers) {
        expectedParentId = Optional.of(parentId);
        childRecordsExpectations = Optional.of(Arrays.asList(providers));
        return this;
    }

    public HapiGetTxnRecord hasDuplicates(
            ErroringAssertsProvider<List<TransactionRecord>> provider) {
        duplicateExpectations = Optional.of(provider);
        return this;
    }

    public HapiGetTxnRecord hasCorrectRunningHash(String topic, byte[] lastMessage) {
        topicToValidate = Optional.of(topic);
        lastMessagedSubmitted = Optional.of(lastMessage);
        return this;
    }

    public HapiGetTxnRecord hasCorrectRunningHash(String topic, String lastMessage) {
        hasCorrectRunningHash(topic, lastMessage.getBytes());
        return this;
    }

    public HapiGetTxnRecord loggedWith(BiConsumer<TransactionRecord, Logger> customFormat) {
        super.logged();
        format = Optional.of(customFormat);
        return this;
    }

    public HapiGetTxnRecord hasNewTokenAssociation(final String token, final String account) {
        newTokenAssociations.add(Pair.of(token, account));
        return this;
    }

    public HapiGetTxnRecord hasHbarAmount(final String account, final long amount) {
        accountAmountsToValidate.add(Pair.of(account, amount));
        return this;
    }

    public HapiGetTxnRecord hasTokenAmount(
            final String token, final String account, final long amount) {
        tokenAmountsToValidate.add(Triple.of(token, account, amount));
        return this;
    }

    public HapiGetTxnRecord hasNftTransfer(
            final String token, final String sender, final String receiver, final long serial) {
        assessedNftTransfersToValidate.add(
                new AssessedNftTransfer(token, sender, receiver, serial));
        return this;
    }

    public HapiGetTxnRecord hasAssessedCustomFee(String token, String account, long amount) {
        assessedCustomFeesToValidate.add(Triple.of(token, account, amount));
        return this;
    }

    public HapiGetTxnRecord hasAssessedCustomFeesSize(final int size) {
        assessedCustomFeesSize = OptionalInt.of(size);
        return this;
    }

    public TransactionRecord getResponseRecord() {
        return response.getTransactionGetRecord().getTransactionRecord();
    }

    public TransactionRecord getChildRecord(final int i) {
        return response.getTransactionGetRecord().getChildTransactionRecords(i);
    }

    public List<TransactionRecord> getChildRecords() {
        return response.getTransactionGetRecord().getChildTransactionRecordsList();
    }

    public HapiGetTxnRecord hasStakingFeesPaid() {
        validateStakingFees = true;
        return this;
    }

    public HapiGetTxnRecord stakingFeeExempted() {
        stakingFeeExempted = true;
        return this;
    }

    @SuppressWarnings("java:S5960")
    private void assertPriority(HapiApiSpec spec, TransactionRecord actualRecord) throws Throwable {
        if (priorityExpectations.isPresent()) {
            ErroringAsserts<TransactionRecord> asserts =
                    priorityExpectations.get().assertsFor(spec);
            List<Throwable> errors = asserts.errorsIn(actualRecord);
            rethrowSummaryError(LOG, "Bad priority record!", errors);
        }
        expectedDebits.ifPresent(
                debits -> assertEquals(debits, asDebits(actualRecord.getTransferList())));
    }

    private void assertChildRecords(HapiApiSpec spec, List<TransactionRecord> actualRecords)
            throws Throwable {
        if (childRecordsExpectations.isPresent()) {
            int numStakingRecords = 0;
            if (!actualRecords.isEmpty() && isEndOfStakingPeriodRecord(actualRecords.get(0))) {
                numStakingRecords++;
            }
            final var expectedChildRecords = childRecordsExpectations.get();
            if (expectedParentId.isPresent()) {
                final var sequentialId = new SequentialID(expectedParentId.get());
                for (int i = 0; i < numStakingRecords; i++) {
                    sequentialId.nextChild();
                }
                for (final var childRecordAssert : expectedChildRecords) {
                    childRecordAssert.txnId(sequentialId.nextChild());
                }
            }

            final var numActualRecords = actualRecords.size();
            assertEquals(
                    expectedChildRecords.size(),
                    numActualRecords - numStakingRecords,
                    "Wrong # of (non-staking) child records");
            for (int i = numStakingRecords; i < numActualRecords; i++) {
                final var expectedChildRecord = expectedChildRecords.get(i - numStakingRecords);
                final var actualChildRecord = actualRecords.get(i);
                ErroringAsserts<TransactionRecord> asserts = expectedChildRecord.assertsFor(spec);
                List<Throwable> errors = asserts.errorsIn(actualChildRecord);
                rethrowSummaryError(LOG, "Bad child records!", errors);
                expectedDebits.ifPresent(
                        debits ->
                                assertEquals(
                                        debits, asDebits(actualChildRecord.getTransferList())));
            }
        }
    }

    private void assertDuplicates(HapiApiSpec spec) throws Throwable {
        if (duplicateExpectations.isPresent()) {
            var asserts = duplicateExpectations.get().assertsFor(spec);
            var errors =
                    asserts.errorsIn(
                            response.getTransactionGetRecord()
                                    .getDuplicateTransactionRecordsList());
            rethrowSummaryError(LOG, "Bad duplicate records!", errors);
        }
    }

    private void assertTransactionHash(HapiApiSpec spec, TransactionRecord actualRecord)
            throws InvalidProtocolBufferException {
        Transaction transaction = Transaction.parseFrom(spec.registry().getBytes(txn));
        assertArrayEquals(
                sha384HashOf(transaction).toByteArray(),
                actualRecord.getTransactionHash().toByteArray(),
                "Bad transaction hash!");
    }

    private void assertTopicRunningHash(HapiApiSpec spec, TransactionRecord actualRecord)
            throws IOException {
        if (topicToValidate.isPresent()) {
            if (actualRecord.getReceipt().getStatus().equals(ResponseCodeEnum.SUCCESS)) {
                var previousRunningHash = spec.registry().getBytes(topicToValidate.get());
                var payer = actualRecord.getTransactionID().getAccountID();
                var topicId = TxnUtils.asTopicId(topicToValidate.get(), spec);
                var boas = new ByteArrayOutputStream();
                try (var out = new ObjectOutputStream(boas)) {
                    out.writeObject(previousRunningHash);
                    out.writeLong(spec.setup().defaultTopicRunningHashVersion());
                    out.writeLong(payer.getShardNum());
                    out.writeLong(payer.getRealmNum());
                    out.writeLong(payer.getAccountNum());
                    out.writeLong(topicId.getShardNum());
                    out.writeLong(topicId.getRealmNum());
                    out.writeLong(topicId.getTopicNum());
                    out.writeLong(actualRecord.getConsensusTimestamp().getSeconds());
                    out.writeInt(actualRecord.getConsensusTimestamp().getNanos());
                    out.writeLong(actualRecord.getReceipt().getTopicSequenceNumber());
                    out.writeObject(noThrowSha384HashOf(lastMessagedSubmitted.get()));
                    out.flush();
                    var expectedRunningHash = noThrowSha384HashOf(boas.toByteArray());
                    var actualRunningHash = actualRecord.getReceipt().getTopicRunningHash();
                    assertArrayEquals(
                            expectedRunningHash,
                            actualRunningHash.toByteArray(),
                            "Bad running hash!");
                    spec.registry().saveBytes(topicToValidate.get(), actualRunningHash);
                }
            } else {
                if (verboseLoggingOn) {
                    LOG.warn(
                            "Cannot validate running hash for an unsuccessful submit message"
                                    + " transaction!");
                }
            }
        }
    }

    @Override
    protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
        if (assertNothing) {
            return;
        }

        final var txRecord = response.getTransactionGetRecord();
        final var actualRecord = txRecord.getTransactionRecord();
        assertCorrectRecord(spec, actualRecord);

        final var childRecords = txRecord.getChildTransactionRecordsList();
        assertChildRecords(spec, childRecords);
    }

    private void assertCorrectRecord(HapiApiSpec spec, TransactionRecord actualRecord)
            throws Throwable {
        assertPriority(spec, actualRecord);
        if (scheduled || assertOnlyPriority) {
            return;
        }
        assertDuplicates(spec);
        if (!assertNothingAboutHashes) {
            assertTransactionHash(spec, actualRecord);
            assertTopicRunningHash(spec, actualRecord);
        }
        if (shouldBeTransferFree) {
            assertEquals(0, actualRecord.getTokenTransferListsCount(), "Unexpected transfer list!");
        }
        if (!accountAmountsToValidate.isEmpty()) {
            final var accountAmounts = actualRecord.getTransferList().getAccountAmountsList();
            accountAmountsToValidate.forEach(
                    pair ->
                            validateAccountAmount(
                                    asId(pair.getLeft(), spec), pair.getRight(), accountAmounts));
        }
        final var tokenTransferLists = actualRecord.getTokenTransferListsList();
        if (!tokenAmountsToValidate.isEmpty()) {
            tokenAmountsToValidate.forEach(
                    triple ->
                            validateTokenAmount(
                                    asTokenId(triple.getLeft(), spec),
                                    asId(triple.getMiddle(), spec),
                                    triple.getRight(),
                                    tokenTransferLists));
        }
        if (!assessedNftTransfersToValidate.isEmpty()) {
            assessedNftTransfersToValidate.forEach(
                    transfer ->
                            validateAssessedNftTransfer(
                                    asTokenId(transfer.getToken(), spec),
                                    asId(transfer.getSender(), spec),
                                    asId(transfer.getReceiver(), spec),
                                    transfer.getSerial(),
                                    tokenTransferLists));
        }
        final var actualAssessedCustomFees = actualRecord.getAssessedCustomFeesList();
        if (assessedCustomFeesSize.isPresent()) {
            assertEquals(
                    assessedCustomFeesSize.getAsInt(),
                    actualAssessedCustomFees.size(),
                    "Unexpected size of assessed_custom_fees:\n" + actualAssessedCustomFees);
        }
        if (!assessedCustomFeesToValidate.isEmpty()) {
            assessedCustomFeesToValidate.forEach(
                    triple ->
                            validateAssessedCustomFees(
                                    triple.getLeft().equals(HBAR_TOKEN_SENTINEL)
                                            ? null
                                            : asTokenId(triple.getLeft(), spec),
                                    asId(triple.getMiddle(), spec),
                                    triple.getRight(),
                                    actualAssessedCustomFees));
        }
        final var actualNewTokenAssociations = actualRecord.getAutomaticTokenAssociationsList();
        if (!newTokenAssociations.isEmpty()) {
            newTokenAssociations.forEach(
                    pair ->
                            validateNewTokenAssociations(
                                    asTokenId(pair.getLeft(), spec),
                                    asId(pair.getRight(), spec),
                                    actualNewTokenAssociations));
        }
        if (!childExpectations.isEmpty()) {
            for (final var index : childExpectations.entrySet()) {
                final var expectations = index.getValue();
                if (expectations.aliasingKey() != null) {
                    final var childRecord = childRecords.get(index.getKey());
                    final var literalKey = spec.registry().getKey(expectations.aliasingKey());
                    assertEquals(
                            literalKey.toByteString().toStringUtf8(),
                            childRecord.getAlias().toStringUtf8());
                }
            }
        }
        if (!paidStakingRewards.isEmpty()) {
            if (actualRecord.getPaidStakingRewardsList().isEmpty()) {
                Assertions.fail("PaidStakingRewards not present in the txnRecord");
            }

            for (int i = 0; i < paidStakingRewards.size(); i++) {
                final var expectedRewards = paidStakingRewards.get(i);
                final var actualPaidRewards = actualRecord.getPaidStakingRewards(i);
                validateAccountAmount(
                        asId(String.valueOf(expectedRewards.getLeft()), spec),
                        expectedRewards.getRight().longValue(),
                        List.of(actualPaidRewards));
            }
        }

        if (validateStakingFees) {
            final var actualTxnFee = actualRecord.getTransactionFee();
            final var transferList = actualRecord.getTransferList();
            final var pendingRewards =
                    actualRecord.getPaidStakingRewardsList().stream()
                            .mapToLong(AccountAmount::getAmount)
                            .sum();
            assertStakingAccountFees(transferList, actualTxnFee, pendingRewards);
        }

        if (stakingFeeExempted) {
            final var transferList = actualRecord.getTransferList();
            assertNoStakingAccountFees(transferList);
        }
        if (noPseudoRandomData) {
            final var actualByteString = actualRecord.getPrngBytes();
            final var actualRandomNum = actualRecord.getPrngNumber();
            assertEquals(
                    TransactionRecord.EntropyCase.ENTROPY_NOT_SET, actualRecord.getEntropyCase());
            assertEquals(ByteString.EMPTY, actualByteString);
            assertEquals(0, actualRandomNum);
        }
        if (pseudorandomBytesExpected) {
            final var actualByteString = actualRecord.getPrngBytes();
            final var actualRandomNum = actualRecord.getPrngNumber();
            assertEquals(MAX_PSEUDORANDOM_BYTES_LENGTH, actualByteString.size());
            assertEquals(0, actualRandomNum);
        }

        if (pseudorandomNumberRange.isPresent()) {
            final var actualByteString = actualRecord.getPrngBytes();
            final var actualRandomNum = actualRecord.getPrngNumber();
            assertTrue(actualByteString.isEmpty());
            assertTrue(actualRandomNum >= 0 && actualRandomNum < pseudorandomNumberRange.get());
        }
    }

    private void assertNoStakingAccountFees(final TransferList transferList) {
        for (final var adjust : transferList.getAccountAmountsList()) {
            final var id = adjust.getAccountID();
            if ((id.getAccountNum() == 801L || id.getAccountNum() == 800L)
                    && adjust.getAmount() > 0) {
                Assertions.fail("Staking fees present in the txnRecord");
            }
        }
    }

    private void assertStakingAccountFees(
            final TransferList transferList, long actualTxnFee, final long pendingRewards) {
        long amount = 0L;
        for (final var adjust : transferList.getAccountAmountsList()) {
            final var id = adjust.getAccountID();
            if (id.getAccountNum() == 3L || id.getAccountNum() == 98L) {
                amount += adjust.getAmount();
            }
        }
        final var stakingFee = actualTxnFee - amount;
        for (final var adjust : transferList.getAccountAmountsList()) {
            final var id = adjust.getAccountID();
            if (id.getAccountNum() == 800L) {
                assertEquals(stakingFee / 2 - pendingRewards, adjust.getAmount());
            }
            if (id.getAccountNum() == 801L) {
                assertEquals(stakingFee / 2, adjust.getAmount());
            }
        }
    }

    private void validateNewTokenAssociations(
            final TokenID token,
            final AccountID account,
            final List<TokenAssociation> newTokenAssociations) {
        for (var newTokenAssociation : newTokenAssociations) {
            if (newTokenAssociation.getTokenId().equals(token)
                    && newTokenAssociation.getAccountId().equals(account)) {
                return;
            }
        }
        Assertions.fail(
                cannotFind(token, account) + " in the new_token_associations of the txnRecord");
    }

    private void validateAssessedCustomFees(
            final TokenID tokenID,
            final AccountID accountID,
            final long amount,
            final List<AssessedCustomFee> assessedCustomFees) {
        for (var acf : assessedCustomFees) {
            if (acf.getAmount() == amount
                    && acf.getFeeCollectorAccountId().equals(accountID)
                    && (!acf.hasTokenId() || acf.getTokenId().equals(tokenID))) {
                return;
            }
        }

        Assertions.fail(
                cannotFind(tokenID, accountID, amount)
                        + " in the assessed_custom_fees of the txnRecord");
    }

    private String cannotFind(final TokenID tokenID, final AccountID accountID) {
        return "Cannot find TokenID: " + tokenID + " AccountID: " + accountID;
    }

    private void validateTokenAmount(
            final TokenID tokenID,
            final AccountID accountID,
            final long amount,
            final List<TokenTransferList> tokenTransferLists) {
        for (final var ttl : tokenTransferLists) {
            if (ttl.getToken().equals(tokenID)) {
                final var accountAmounts = ttl.getTransfersList();
                if (!accountAmounts.isEmpty()
                        && foundInAccountAmountsList(accountID, amount, accountAmounts)) {
                    return;
                }
            }
        }

        Assertions.fail(
                cannotFind(tokenID, accountID, amount)
                        + " in the tokenTransferLists of the txnRecord");
    }

    private String cannotFind(final TokenID tokenID, final AccountID accountID, final long amount) {
        return cannotFind(tokenID, accountID) + " and amount: " + amount;
    }

    private void validateAssessedNftTransfer(
            final TokenID tokenID,
            final AccountID sender,
            final AccountID receiver,
            final long serial,
            final List<TokenTransferList> tokenTransferLists) {
        for (final var ttl : tokenTransferLists) {
            if (ttl.getToken().equals(tokenID)) {
                final var nftTransferList = ttl.getNftTransfersList();
                if (!nftTransferList.isEmpty()
                        && foundInNftTransferList(sender, receiver, serial, nftTransferList)) {
                    return;
                }
            }
        }

        Assertions.fail(
                cannotFind(tokenID, sender, receiver, serial)
                        + " in the tokenTransferLists of the txnRecord");
    }

    private String cannotFind(
            final TokenID tokenID,
            final AccountID sender,
            final AccountID receiver,
            final long serial) {
        return "Cannot find TokenID: "
                + tokenID
                + " sender: "
                + sender
                + " receiver: "
                + receiver
                + " and serial: "
                + serial;
    }

    private boolean foundInNftTransferList(
            final AccountID sender,
            final AccountID receiver,
            final long serial,
            final List<NftTransfer> nftTransferList) {
        for (final var nftTransfer : nftTransferList) {
            if (nftTransfer.getSerialNumber() == serial
                    && nftTransfer.getSenderAccountID().equals(sender)
                    && nftTransfer.getReceiverAccountID().equals(receiver)) {
                return true;
            }
        }

        return false;
    }

    private void validateAccountAmount(
            final AccountID accountID,
            final long amount,
            final List<AccountAmount> accountAmountsList) {
        final var found = foundInAccountAmountsList(accountID, amount, accountAmountsList);
        assertTrue(
                found,
                "Cannot find AccountID: "
                        + accountID
                        + " and amount: "
                        + amount
                        + " in the transferList of the "
                        + "txnRecord");
    }

    private boolean foundInAccountAmountsList(
            final AccountID accountID,
            final long amount,
            final List<AccountAmount> accountAmountsList) {
        for (final var aa : accountAmountsList) {
            if (aa.getAmount() == amount && aa.getAccountID().equals(accountID)) {
                return true;
            }
        }

        return false;
    }

    @Override
    @SuppressWarnings("java:S1874")
    protected void submitWith(HapiApiSpec spec, Transaction payment)
            throws InvalidProtocolBufferException {
        Query query = getRecordQuery(spec, payment, false);
        response =
                spec.clients()
                        .getCryptoSvcStub(targetNodeFor(spec), useTls)
                        .getTxRecordByTxID(query);
        final TransactionRecord rcd = response.getTransactionGetRecord().getTransactionRecord();
        if (contractResultAbi != null) {
            exposeRequestedEventsFrom(rcd);
        }
        observer.ifPresent(obs -> obs.accept(rcd));
        childRecords = response.getTransactionGetRecord().getChildTransactionRecordsList();
        childRecordsCount.ifPresent(
                count -> {
                    if (includeStakingRecordsInCount) {
                        assertEquals(count, childRecords.size());
                    } else {
                        int observedCount = childRecords.size();
                        if (!childRecords.isEmpty()
                                && isEndOfStakingPeriodRecord(childRecords.get(0))) {
                            observedCount--;
                        }
                        assertEquals(count, observedCount, "Wrong # of non-staking records");
                    }
                });
        for (var rec : childRecords) {
            if (!rec.getAlias().isEmpty()) {
                spec.registry()
                        .saveAccountId(
                                rec.getAlias().toStringUtf8(), rec.getReceipt().getAccountID());
                spec.registry()
                        .saveKey(rec.getAlias().toStringUtf8(), Key.parseFrom(rec.getAlias()));
                LOG.info(
                        "{}  Saving alias {} to registry for Account ID {}",
                        spec::logPrefix,
                        rec.getAlias()::toStringUtf8,
                        rec.getReceipt()::getAccountID);
            }
        }

        if (verboseLoggingOn) {
            if (format.isPresent()) {
                format.get().accept(rcd, LOG);
            } else {
                var fee = rcd.getTransactionFee();
                var rates = spec.ratesProvider();
                var priceInUsd = sdec(rates.toUsdWithActiveRates(fee), 5);
                LOG.info(
                        "{}Record (charged ${}): {}", spec::logPrefix, () -> priceInUsd, () -> rcd);
                LOG.info(
                        "{}  And {} child record{}: {}",
                        spec::logPrefix,
                        childRecords::size,
                        () -> childRecords.size() > 1 ? "s" : "",
                        () -> childRecords);
                LOG.info(
                        "Duplicates: {}",
                        response.getTransactionGetRecord().getDuplicateTransactionRecordsList());
            }
        }
        if (response.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode() == OK) {
            priceConsumer.ifPresent(pc -> pc.accept(rcd.getTransactionFee()));
            debitsConsumer.ifPresent(dc -> dc.accept(asDebits(rcd.getTransferList())));
        }
        if (registryEntry.isPresent()) {
            spec.registry()
                    .saveContractList(
                            registryEntry.get() + "CreateResult",
                            rcd.getContractCreateResult().getCreatedContractIDsList());
            spec.registry()
                    .saveContractList(
                            registryEntry.get() + "CallResult",
                            rcd.getContractCallResult().getCreatedContractIDsList());
        }
        if (saveTxnRecordToRegistry.isPresent()) {
            spec.registry().saveTransactionRecord(saveTxnRecordToRegistry.get(), rcd);
        }
    }

    private void exposeRequestedEventsFrom(final TransactionRecord rcd) {
        final var abi = Abi.fromJson(contractResultAbi);
        final var matcher = abi.findEvent(eventMatcher);
        final var logs = rcd.getContractCallResult().getLogInfoList();
        for (final var log : logs) {
            final var data = log.getData().toByteArray();
            final var topics = new byte[log.getTopicCount()][];
            for (int i = 0, n = log.getTopicCount(); i < n; i++) {
                topics[i] = log.getTopic(i).toByteArray();
            }
            final var decodedLog = matcher.decode(data, topics);
            eventDataObserver.accept(decodedLog);
        }
    }

    @Override
    protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
        Query query = getRecordQuery(spec, payment, true);
        Response response =
                spec.clients()
                        .getCryptoSvcStub(targetNodeFor(spec), useTls)
                        .getTxRecordByTxID(query);
        return costFrom(response);
    }

    private Query getRecordQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
        TransactionID txnId =
                useDefaultTxnId
                        ? defaultTxnId
                        : explicitTxnId.orElseGet(() -> spec.registry().getTxnId(txn));
        if (lookupScheduledFromRegistryId) {
            txnId = spec.registry().getTxnId(correspondingScheduledTxnId(creationName.get()));
        } else {
            if (scheduled) {
                txnId = txnId.toBuilder().setScheduled(true).build();
            }
        }
        QueryHeader header;
        if (costOnly && omitPaymentHeaderOnCostAnswer) {
            header = QueryHeader.newBuilder().setResponseType(COST_ANSWER).build();
        } else {
            header = costOnly ? answerCostHeader(payment) : answerHeader(payment);
        }
        TransactionGetRecordQuery getRecordQuery =
                TransactionGetRecordQuery.newBuilder()
                        .setHeader(header)
                        .setTransactionID(txnId)
                        .setIncludeDuplicates(requestDuplicates)
                        .setIncludeChildRecords(requestChildRecords)
                        .build();
        return Query.newBuilder().setTransactionGetRecord(getRecordQuery).build();
    }

    @Override
    protected long costOnlyNodePayment(HapiApiSpec spec) throws Throwable {
        return spec.fees()
                .forOp(
                        HederaFunctionality.TransactionGetRecord,
                        cryptoFees.getCostTransactionRecordQueryFeeMatrices());
    }

    @Override
    protected boolean needsPayment() {
        return true;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        if (explicitTxnId.isPresent()) {
            return super.toStringHelper().add("explicitTxnId", true);
        } else {
            return super.toStringHelper().add("txn", txn);
        }
    }

    public class AssessedNftTransfer {
        private String token;
        private String sender;
        private String receiver;
        private long serial;

        public AssessedNftTransfer(
                final String token, final String sender, final String receiver, final long serial) {
            this.token = token;
            this.sender = sender;
            this.receiver = receiver;
            this.serial = serial;
        }

        public String getToken() {
            return token;
        }

        public String getSender() {
            return sender;
        }

        public String getReceiver() {
            return receiver;
        }

        public long getSerial() {
            return serial;
        }
    }
}
