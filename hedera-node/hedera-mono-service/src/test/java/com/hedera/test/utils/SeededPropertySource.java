/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.test.utils;

import static com.hedera.services.state.merkle.internals.BitPackUtils.numFromCode;
import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static com.hedera.services.state.submerkle.TxnId.USER_TRANSACTION_NONCE;
import static com.hedera.services.state.virtual.KeyPackingUtils.computeNonZeroBytes;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.EntityType;
import com.hedera.services.context.properties.SerializableSemVers;
import com.hedera.services.legacy.core.jproto.*;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.*;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.merkle.internals.BytesElement;
import com.hedera.services.state.submerkle.*;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.state.virtual.entities.OnDiskAccount;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NftNumPair;
import com.hederahashgraph.api.proto.java.*;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.utility.CommonUtils;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;

public class SeededPropertySource {
    private static final long BASE_SEED = 4_242_424L;

    private final SplittableRandom SEEDED_RANDOM;

    public SeededPropertySource(final SplittableRandom SEEDED_RANDOM) {
        this.SEEDED_RANDOM = SEEDED_RANDOM;
    }

    public static SeededPropertySource forSerdeTest(final int version, final int testCaseNo) {
        return new SeededPropertySource(new SplittableRandom(version * BASE_SEED + testCaseNo));
    }

    public EvmFnResult nextEvmResult() {
        final var contractId = nextEntityId();
        final var result = nextBytes(32);
        final var error = nextString(64);
        final var bloom = nextBytes(256);
        final var gasUsed = nextUnsignedLong();
        final var logs = nextEvmLogs(3);
        final var createdContractIds = nextEntityIds(2);
        final var evmAddress = nextBytes(20);
        // call nextStateChanges(), even though state changes are not part of EvmFnResult anymore,
        // in order to advance SEEDED_RANDOM and get correct values for subsequent fields
        nextStateChanges(2, 5);
        return new EvmFnResult(
                contractId,
                result,
                error,
                bloom,
                gasUsed,
                logs,
                createdContractIds,
                evmAddress,
                nextUnsignedLong(),
                nextUnsignedLong(),
                nextBytes(128),
                nextEntityId());
    }

    public MerkleTopic nextTopic() {
        final var seeded = new MerkleTopic();
        if (nextBoolean()) {
            seeded.setMemo(nextString(100));
        }
        seeded.setAdminKey(nextNullableKey());
        seeded.setSubmitKey(nextNullableKey());
        seeded.setAutoRenewDurationSeconds(nextUnsignedLong());
        if (nextBoolean()) {
            seeded.setAutoRenewAccountId(nextEntityId());
        }
        seeded.setExpirationTimestamp(nextNullableRichInstant());
        seeded.setKey(nextNum());
        seeded.setDeleted(nextBoolean());
        seeded.setSequenceNumber(nextUnsignedLong());
        if (nextBoolean()) {
            seeded.setRunningHash(nextBytes(48));
        }
        return seeded;
    }

    public MerkleToken nextToken() {
        final var initialSupply = SEEDED_RANDOM.nextLong(100_000_000);
        final var seeded =
                new MerkleToken(
                        nextUnsignedLong(),
                        initialSupply,
                        nextUnsignedInt(),
                        nextString(24),
                        nextString(48),
                        nextBoolean(),
                        nextBoolean(),
                        nextEntityId(),
                        nextInt());
        seeded.setMemo(nextString(36));
        seeded.setDeleted(nextBoolean());
        seeded.setFeeSchedule(nextCustomFeeSchedule());
        seeded.setAutoRenewPeriod(nextUnsignedLong());
        seeded.setTokenType(nextTokenType());
        seeded.setSupplyType(nextTokenSupplyType());
        seeded.setLastUsedSerialNumber(nextUnsignedLong());
        seeded.setMaxSupply(initialSupply + SEEDED_RANDOM.nextLong(100_000_000));
        seeded.setPaused(nextBoolean());
        seeded.setAdminKey(nextNullableKey());
        seeded.setFreezeKey(nextNullableKey());
        seeded.setKycKey(nextNullableKey());
        seeded.setWipeKey(nextNullableKey());
        seeded.setSupplyKey(nextNullableKey());
        seeded.setFeeScheduleKey(nextNullableKey());
        seeded.setPauseKey(nextNullableKey());
        seeded.setKey(nextNum());
        return seeded;
    }

    public List<FcCustomFee> nextCustomFeeSchedule() {
        return IntStream.range(0, SEEDED_RANDOM.nextInt(10))
                .mapToObj(i -> nextCustomFee())
                .toList();
    }

    public FcCustomFee nextCustomFee() {
        final var type = nextFeeType();
        final FcCustomFee release17Type =
                switch (type) {
                    case FIXED_FEE -> FcCustomFee.fixedFee(
                            nextUnsignedLong(), nextEntityId(), nextEntityId(), false);
                    case ROYALTY_FEE -> {
                        final var denom = 1 + nextNonZeroInt(100);
                        final var numer = nextNonZeroInt(denom - 1);
                        if (nextBoolean()) {
                            yield FcCustomFee.royaltyFee(numer, denom, null, nextEntityId(), false);
                        } else {
                            yield FcCustomFee.royaltyFee(
                                    numer, denom, nextFixedFeeSpec(), nextEntityId(), false);
                        }
                    }
                    case FRACTIONAL_FEE -> {
                        final var denom = 1 + nextNonZeroInt(100);
                        final var numer = nextNonZeroInt(denom - 1);
                        final var minUnits = nextNonZeroInt(100);
                        final var maxUnits = minUnits + nextNonZeroInt(100);
                        yield FcCustomFee.fractionalFee(
                                numer,
                                denom,
                                minUnits,
                                maxUnits,
                                nextBoolean(),
                                nextEntityId(),
                                false);
                    }
                };
        release17Type.setAllCollectorsAreExempt(nextBoolean());
        return release17Type;
    }

    private FixedFeeSpec nextFixedFeeSpec() {
        return new FixedFeeSpec(nextNonZeroInt(1_000_000), nextEntityId());
    }

    public FcCustomFee.FeeType nextFeeType() {
        // size of FcCustomFee.FeeType.class.getEnumConstants() in 0.25
        return nextFeeType(3);
    }

    public FcCustomFee.FeeType nextFeeType(final int range) {
        final var choices = FcCustomFee.FeeType.class.getEnumConstants();
        return choices[SEEDED_RANDOM.nextInt(range)];
    }

    public TokenType nextTokenType() {
        // size of TokenType.class.getEnumConstants() in 0.25
        return nextTokenType(2);
    }

    public TokenType nextTokenType(final int range) {
        final var choices = TokenType.class.getEnumConstants();
        return choices[SEEDED_RANDOM.nextInt(range)];
    }

    public TokenSupplyType nextTokenSupplyType() {
        // size of TokenSupplyType.class.getEnumConstants() in 0.25
        return nextTokenSupplyType(2);
    }

    public TokenSupplyType nextTokenSupplyType(final int range) {
        final var choices = TokenSupplyType.class.getEnumConstants();
        return choices[SEEDED_RANDOM.nextInt(range)];
    }

    public byte[] nextSerializedLegacyScheduleCreateTransactionBody() {
        return TransactionBody.newBuilder()
                .setTransactionID(nextTxnId().toGrpc())
                .setMemo(nextString(50))
                .setScheduleCreate(
                        ScheduleCreateTransactionBody.newBuilder()
                                .setAdminKey(
                                        Key.newBuilder()
                                                .setECDSASecp256K1(
                                                        ByteString.copyFrom(nextBytes(20))))
                                .setMemo(nextString(20))
                                .setPayerAccountID(nextEntityId().toGrpcAccountId())
                                .setScheduledTransactionBody(
                                        SchedulableTransactionBody.newBuilder()
                                                .setMemo(nextString(20))
                                                .setTransactionFee(nextLong())))
                .build()
                .toByteArray();
    }

    public MerkleUniqueToken next0260UniqueToken() {
        final var ownerCode = SEEDED_RANDOM.nextInt(1234);
        final var spenderCode = SEEDED_RANDOM.nextInt(1234);
        final var packedCreationTime = packedTime(nextInRangeLong(), nextInt());
        final var metadata = nextBytes(10);
        final var numbers = SEEDED_RANDOM.nextLong(1234);
        final var prev =
                NftNumPair.fromLongs(SEEDED_RANDOM.nextLong(1234), SEEDED_RANDOM.nextLong(1234));
        final var next =
                NftNumPair.fromLongs(SEEDED_RANDOM.nextLong(1234), SEEDED_RANDOM.nextLong(1234));

        final var subject = new MerkleUniqueToken(ownerCode, metadata, packedCreationTime, numbers);
        subject.setSpender(new EntityId(0, 0, spenderCode));
        subject.setPrev(prev);
        subject.setNext(next);
        return subject;
    }

    /**
     * Provides a current {@link MerkleAccountState} that has the same "value" as a previously
     * serialized instance.
     *
     * @return the "modernized" account state
     */
    public MerkleAccountState next0242AccountState() {
        final var key = nextKey();
        final var expiry = nextUnsignedLong();
        final var balance = nextUnsignedLong();
        final var autoRenewSecs = nextUnsignedLong();
        final var memo = nextString(100);
        final var isDeleted = nextBoolean();
        final var isSmartContract = nextBoolean();
        final var isReceiverSigReq = nextBoolean();
        final var proxy = nextEntityId();
        final var num = nextInt();
        final var autoAssocMeta = nextUnsignedInt();
        final var alias = nextByteString(36);
        final var kvPairs = nextUnsignedInt();
        // Preserve same seeded values, but these will be ignored
        nextGrantedCryptoAllowances(10);
        nextGrantedFungibleAllowances(10);
        nextApprovedForAllAllowances(10);

        final var newMaxAutoAssociations =
                BitPackUtils.getMaxAutomaticAssociationsFrom(autoAssocMeta);
        final var newUsedAutoAssociations =
                BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom(autoAssocMeta);
        final var seeded = new MerkleAccountState();
        seeded.setAccountKey(key);
        seeded.setExpiry(expiry);
        seeded.setHbarBalance(balance);
        seeded.setAutoRenewSecs(autoRenewSecs);
        seeded.setMemo(memo);
        seeded.setDeleted(isDeleted);
        seeded.setSmartContract(isSmartContract);
        seeded.setReceiverSigRequired(isReceiverSigReq);
        seeded.setProxy(proxy);
        seeded.setNumber(num);
        seeded.setUsedAutomaticAssociations(newUsedAutoAssociations);
        seeded.setMaxAutomaticAssociations(newMaxAutoAssociations);
        seeded.setAlias(alias);
        seeded.setNumContractKvPairs(kvPairs);

        return seeded;
    }

    public MerkleAccountState next0250AccountState() {
        final var maxAutoAssoc = SEEDED_RANDOM.nextInt(1234);
        final var usedAutoAssoc = SEEDED_RANDOM.nextInt(maxAutoAssoc + 1);
        final var numAssociations = SEEDED_RANDOM.nextInt(12345);
        final var numPositiveBalanceAssociations = SEEDED_RANDOM.nextInt(numAssociations);
        final var misorderedState =
                new MerkleAccountState(
                        nextKey(),
                        nextUnsignedLong(),
                        nextUnsignedLong(),
                        nextUnsignedLong(),
                        nextString(100),
                        nextBoolean(),
                        nextBoolean(),
                        nextBoolean(),
                        nextEntityId(),
                        nextInt(),
                        maxAutoAssoc,
                        usedAutoAssoc,
                        nextByteString(36),
                        nextUnsignedInt(),
                        nextGrantedCryptoAllowances(10),
                        nextGrantedFungibleAllowances(10),
                        nextApprovedForAllAllowances(10),
                        null,
                        (byte) 0,
                        0,
                        numAssociations,
                        numPositiveBalanceAssociations,
                        nextInRangeLong(),
                        0,
                        0,
                        null,
                        0,
                        0,
                        0,
                        -1,
                        0,
                        false,
                        -1);
        misorderedState.setNftsOwned(nextUnsignedLong());
        misorderedState.setNumTreasuryTitles(nextUnsignedInt());
        return misorderedState;
    }

    public MerklePayerRecords nextPayerRecords() {
        final var payerRecords = new MerklePayerRecords();
        payerRecords.setKey(nextNum());
        for (int i = 0, n = nextInt(3); i < n; i++) {
            payerRecords.offer(nextRecord());
        }
        return payerRecords;
    }

    public MerkleAccountState nextAccountState() {
        final var maxAutoAssoc = SEEDED_RANDOM.nextInt(1234);
        final var usedAutoAssoc = SEEDED_RANDOM.nextInt(maxAutoAssoc + 1);
        final var numAssociations = SEEDED_RANDOM.nextInt(12345);
        final var numPositiveBalanceAssociations = SEEDED_RANDOM.nextInt(numAssociations);
        final var isContract = nextBoolean();
        final var firstContractKey = isContract ? nextPackedInts(8) : null;
        final var firstKeyBytes = isContract ? computeNonZeroBytes(firstContractKey) : (byte) 0;
        return new MerkleAccountState(
                nextKey(),
                nextUnsignedLong(),
                nextUnsignedLong(),
                nextUnsignedLong(),
                nextString(100),
                nextBoolean(),
                isContract,
                nextBoolean(),
                nextEntityId(),
                nextInt(),
                maxAutoAssoc,
                usedAutoAssoc,
                nextByteString(36),
                nextUnsignedInt(),
                nextGrantedCryptoAllowances(10),
                nextGrantedFungibleAllowances(10),
                nextApprovedForAllAllowances(10),
                firstContractKey,
                firstKeyBytes,
                nextUnsignedLong(),
                numAssociations,
                numPositiveBalanceAssociations,
                nextInRangeLong(),
                nextUnsignedInt(),
                nextUnsignedLong(),
                nextEntityId(),
                nextInRangeLong(),
                nextUnsignedLong(),
                nextUnsignedLong(),
                nextUnsignedLong(),
                nextInRangeLong(),
                nextBoolean(),
                nextUnsignedLong());
    }

    public MerkleScheduledTransactionsState nextScheduledTransactionsState() {
        return new MerkleScheduledTransactionsState(nextLong());
    }

    public ExpirableTxnRecord nextRecord() {
        // Releases 0.23/4 and 0.25 all used the same fields; only serialization format changed
        final var builder =
                ExpirableTxnRecord.newBuilder()
                        .setTxnId(nextTxnId())
                        .setReceiptBuilder(nextReceiptBuilder())
                        .setConsensusTime(nextRichInstant())
                        .setFee(nextUnsignedLong())
                        .setNumChildRecords(nextUnsignedShort());
        if (nextBoolean()) {
            builder.setAlias(nextByteString(32));
        }
        if (nextBoolean()) {
            builder.setHbarAdjustments(nextCurrencyAdjustments());
        }
        if (nextBoolean()) {
            builder.setMemo(nextString(100));
        }
        if (nextBoolean()) {
            builder.setContractCallResult(nextEvmFnResult());
        } else if (nextBoolean()) {
            builder.setContractCreateResult(nextEvmFnResult());
        }
        if (nextBoolean()) {
            builder.setScheduleRef(nextEntityId());
            builder.setParentConsensusTime(nextInstant());
        }
        if (nextBoolean()) {
            nextCryptoAllowances(1, 2, 4);
        }
        if (nextBoolean()) {
            nextFungibleAllowances(1, 2, 4, 8);
        }
        if (nextBoolean()) {
            nextTokenAdjustmentsIn(builder);
        }
        if (nextBoolean()) {
            builder.setAssessedCustomFees(nextAssessedFeesList());
        }
        if (nextBoolean()) {
            builder.setNewTokenAssociations(nextTokenAssociationsList());
        }
        if (nextBoolean()) {
            builder.setStakingRewardsPaid(nextCurrencyAdjustments());
        }
        final var seeded = builder.build();
        seeded.setSubmittingMember(nextUnsignedLong());
        seeded.setExpiry(nextUnsignedLong());
        if (seeded.getContractCallResult() != null && nextBoolean()) {
            seeded.getContractCallResult().setSenderId(nextEntityId());
        }
        if (seeded.getContractCreateResult() != null && nextBoolean()) {
            seeded.getContractCreateResult().setSenderId(nextEntityId());
        }
        // added in 0.28
        if (nextBoolean()) {
            seeded.setPseudoRandomNumber(nextUnsignedInt());
        } else if (nextBoolean()) {
            seeded.setPseudoRandomBytes(nextBytes(48));
        }
        return seeded;
    }

    public MerkleStakingInfo nextStakingInfo() {
        final var MAX_REWARD_HISTORY = 366;
        final var ans =
                new MerkleStakingInfo(
                        nextLong(),
                        nextLong(),
                        nextLong(),
                        nextLong(),
                        nextLong(),
                        nextLong(),
                        nextLong(),
                        nextLongs(MAX_REWARD_HISTORY));
        return ans;
    }

    public MerkleNetworkContext next0260NetworkContext() {
        final var numThrottles = 5;
        final var seeded = new MerkleNetworkContext();
        seeded.setBlockNo(Long.MIN_VALUE);
        seeded.setConsensusTimeOfLastHandledTxn(nextNullableInstant());
        seeded.setSeqNo(nextSeqNo());
        seeded.updateLastScannedEntity(nextInRangeLong());
        seeded.setMidnightRates(nextExchangeRates());
        seeded.setUsageSnapshots(nextUsageSnapshots(numThrottles));
        seeded.setGasThrottleUsageSnapshot(nextUsageSnapshot());
        seeded.setCongestionLevelStarts(nextNullableInstants(numThrottles));
        seeded.setStateVersion(nextUnsignedInt());
        seeded.updateAutoRenewSummaryCounts(nextUnsignedInt(), nextUnsignedInt());
        nextNullableInstant();
        seeded.setPreparedUpdateFileNum(nextInRangeLong());
        seeded.setPreparedUpdateFileHash(nextBytes(48));
        seeded.setMigrationRecordsStreamed(nextBoolean());
        final var numBlocks = nextNonZeroInt(16);
        final var anInstant = nextInstant();
        for (int i = 0; i < numBlocks; i++) {
            seeded.finishBlock(nextEthHash(), anInstant.plusSeconds(2L * i));
        }
        seeded.setStakingRewardsActivated(nextBoolean());
        seeded.setTotalStakedRewardStart(nextLong());
        seeded.setTotalStakedStart(nextLong());
        seeded.setTotalStakedStart(nextLong());
        seeded.setPendingRewards(nextLong());
        return seeded;
    }

    public MerkleNetworkContext next0270NetworkContext() {
        final var numThrottles = 5;
        final var seeded = new MerkleNetworkContext();
        seeded.setBlockNo(Long.MIN_VALUE);
        seeded.setConsensusTimeOfLastHandledTxn(nextNullableInstant());
        seeded.setSeqNo(nextSeqNo());
        seeded.updateLastScannedEntity(nextInRangeLong());
        seeded.setMidnightRates(nextExchangeRates());
        seeded.setUsageSnapshots(nextUsageSnapshots(numThrottles));
        seeded.setGasThrottleUsageSnapshot(nextUsageSnapshot());
        seeded.setCongestionLevelStarts(nextNullableInstants(numThrottles));
        seeded.setStateVersion(nextUnsignedInt());
        seeded.updateAutoRenewSummaryCounts(nextUnsignedInt(), nextUnsignedInt());
        seeded.setPreparedUpdateFileNum(nextInRangeLong());
        seeded.setPreparedUpdateFileHash(nextBytes(48));
        seeded.setMigrationRecordsStreamed(nextBoolean());
        final var numBlocks = nextNonZeroInt(16);
        final var anInstant = nextInstant();
        for (int i = 0; i < numBlocks; i++) {
            seeded.finishBlock(nextEthHash(), anInstant.plusSeconds(2L * i));
        }
        seeded.setStakingRewardsActivated(nextBoolean());
        seeded.setTotalStakedRewardStart(nextLong());
        seeded.setTotalStakedStart(nextLong());
        seeded.setTotalStakedStart(nextLong());
        seeded.setPendingRewards(nextLong());
        return seeded;
    }

    public MerkleNetworkContext next0300NetworkContext() {
        final var base = next0270NetworkContext();
        base.setExpiryUsageSnapshot(nextUsageSnapshot());
        return base;
    }

    public MerkleNetworkContext next0310NetworkContext() {
        final var base = next0300NetworkContext();
        base.setNextTaskTodo(nextInt());
        base.setLastScannedPostUpgrade(nextLong());
        base.setSeqNoPostUpgrade(nextLong());
        base.setPreExistingEntityScanStatus(nextBytes(1)[0]);
        return base;
    }

    public Instant[] nextNullableInstants(final int n) {
        return IntStream.range(0, n).mapToObj(i -> nextNullableInstant()).toArray(Instant[]::new);
    }

    public DeterministicThrottle.UsageSnapshot[] nextUsageSnapshots(final int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> nextUsageSnapshot())
                .toArray(DeterministicThrottle.UsageSnapshot[]::new);
    }

    public DeterministicThrottle.UsageSnapshot nextUsageSnapshot() {
        return new DeterministicThrottle.UsageSnapshot(nextUnsignedLong(), nextNullableInstant());
    }

    public SequenceNumber nextSeqNo() {
        return new SequenceNumber(nextInRangeLong());
    }

    public boolean nextBoolean() {
        return SEEDED_RANDOM.nextBoolean();
    }

    public List<FcAssessedCustomFee> nextAssessedFeesList() {
        final var numFees = nextNonZeroInt(10);
        final List<FcAssessedCustomFee> ans = new ArrayList<>();
        for (int i = 0; i < numFees; i++) {
            ans.add(nextAssessedFee());
        }
        return ans;
    }

    public List<FcTokenAssociation> nextTokenAssociationsList() {
        final var numAssociations = nextNonZeroInt(10);
        final List<FcTokenAssociation> ans = new ArrayList<>();
        for (int i = 0; i < numAssociations; i++) {
            ans.add(nextTokenAssociation());
        }
        return ans;
    }

    public FcTokenAssociation nextTokenAssociation() {
        return new FcTokenAssociation(nextInRangeLong(), nextInRangeLong());
    }

    public FcAssessedCustomFee nextAssessedFee() {
        if (nextBoolean()) {
            return new FcAssessedCustomFee(
                    nextEntityId(), nextUnsignedLong(), nextInRangeLongs(nextNonZeroInt(3)));
        } else {
            return new FcAssessedCustomFee(
                    nextEntityId(),
                    nextEntityId(),
                    nextUnsignedLong(),
                    nextInRangeLongs(nextNonZeroInt(3)));
        }
    }

    public void nextTokenAdjustmentsIn(final ExpirableTxnRecord.Builder builder) {
        final var numAdjustments = nextNonZeroInt(10);
        final List<EntityId> tokenTypes = new ArrayList<>(numAdjustments);
        final List<NftAdjustments> ownershipChanges = new ArrayList<>(numAdjustments);
        final List<CurrencyAdjustments> fungibleAdjustments = new ArrayList<>(numAdjustments);
        for (int i = 0; i < numAdjustments; i++) {
            tokenTypes.add(nextEntityId());
            ownershipChanges.add(nextOwnershipChanges());
            fungibleAdjustments.add(nextCurrencyAdjustments());
        }
        builder.setTokens(tokenTypes);
        builder.setNftTokenAdjustments(ownershipChanges);
        builder.setTokenAdjustments(fungibleAdjustments);
    }

    public NftAdjustments nextOwnershipChanges() {
        final var numOwnershipChanges = nextNonZeroInt(10);
        final List<EntityId> senders = new ArrayList<>(numOwnershipChanges);
        final List<EntityId> receivers = new ArrayList<>(numOwnershipChanges);
        final long[] serialNos = nextUnsignedLongs(numOwnershipChanges);
        return new NftAdjustments(serialNos, senders, receivers);
    }

    public int nextNonce() {
        return nextNonZeroInt(999);
    }

    public int nextInt() {
        return SEEDED_RANDOM.nextInt();
    }

    public int nextInt(int bound) {
        return SEEDED_RANDOM.nextInt(bound);
    }

    public TxnId nextTxnId() {
        if (nextBoolean()) {
            return new TxnId(nextEntityId(), nextRichInstant(), nextBoolean(), nextNonce());
        } else {
            return new TxnId(
                    nextEntityId(), nextRichInstant(), nextBoolean(), USER_TRANSACTION_NONCE);
        }
    }

    public Instant nextInstant() {
        return Instant.ofEpochSecond(nextInRangeLong(), SEEDED_RANDOM.nextInt(1_000_000));
    }

    public Instant nextNullableInstant() {
        return nextBoolean()
                ? null
                : Instant.ofEpochSecond(nextInRangeLong(), SEEDED_RANDOM.nextInt(1_000_000));
    }

    public TxnId nextScheduledTxnId() {
        return new TxnId(nextEntityId(), nextRichInstant(), true, 0);
    }

    public CurrencyAdjustments nextCurrencyAdjustments() {
        final var numAdjustments = nextNonZeroInt(10);
        final var ids = nextInRangeLongs(numAdjustments);
        final var amounts = nextLongs(numAdjustments);
        return new CurrencyAdjustments(amounts, ids);
    }

    public OnDiskAccount nextOnDiskAccount() {
        final var account = new OnDiskAccount();

        // Boolean values
        account.setIsDeleted(nextBoolean());
        account.setIsContract(nextBoolean());
        account.setIsReceiverSigRequired(nextBoolean());
        account.setIsDeclineReward(nextBoolean());
        // Int values
        account.setNumContractKvPairs(nextInt());
        account.setMaxAutoAssociations(nextInt());
        account.setUsedAutoAssociations(nextInt());
        account.setNumAssociations(nextInt());
        account.setNumPositiveBalances(nextInt());
        account.setNumTreasuryTitles(nextInt());
        // Long values
        account.setExpiry(nextLong());
        account.setHbarBalance(nextLong());
        account.setAutoRenewSecs(nextLong());
        account.setNftsOwned(nextLong());
        account.setAccountNumber(nextLong());
        account.setHeadTokenId(nextLong());
        account.setHeadNftId(nextLong());
        account.setHeadNftSerialNum(nextLong());
        account.setEthereumNonce(nextLong());
        account.setStakedToMe(nextLong());
        account.setStakePeriodStart(nextLong());
        account.setStakedNum(nextLong());
        account.setStakeAtStartOfLastRewardedPeriod(nextLong());
        account.setAutoRenewAccountNumber(nextLong());
        // Object values
        account.setKey(nextKey());
        if (nextBoolean()) {
            account.setMemo(nextString(32));
        }
        if (nextBoolean()) {
            account.setAlias(nextByteString(20));
        }
        if (nextBoolean()) {
            account.setHbarAllowances(nextGrantedCryptoAllowances(nextInt(4)));
        }
        if (nextBoolean()) {
            account.setFungibleAllowances(nextGrantedFungibleAllowances(nextInt(3)));
        }
        if (nextBoolean()) {
            account.setNftOperatorApprovals(nextApprovedForAllAllowances(2));
        }
        if (account.isContract() && nextBoolean()) {
            account.setFirstStorageKey(nextPackedInts(8));
        }

        return account;
    }

    public EvmFnResult nextEvmFnResult() {
        final var contractId = nextEntityId();
        final var result = nextBytes(128);
        final var error = nextString(32);
        final var bloom = nextBytes(32);
        final var gasUsed = nextUnsignedLong();
        final var evmAddress = nextBytes(20);
        // call nextStateChanges(), even though state changes are not part of EvmFnResult anymore,
        // in order to advance SEEDED_RANDOM and get correct values for subsequent fields
        nextStateChanges(5, 10);
        return new EvmFnResult(
                contractId,
                result,
                error,
                bloom,
                gasUsed,
                List.of(),
                List.of(),
                evmAddress,
                nextUnsignedLong(),
                nextUnsignedLong(),
                nextBytes(64),
                null);
    }

    public List<EvmLog> nextEvmLogs(final int n) {
        return IntStream.range(0, n).mapToObj(i -> nextEvmLog()).toList();
    }

    public EvmLog nextEvmLog() {
        return new EvmLog(nextEntityId(), nextBytes(256), nextLogTopics(5), nextBytes(64));
    }

    public List<byte[]> nextLogTopics(final int n) {
        return IntStream.range(0, nextNonZeroInt(n)).mapToObj(i -> nextBytes(256)).toList();
    }

    public Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> nextStateChanges(
            int n, final int changesPerAddress) {
        final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> ans = new TreeMap<>();
        while (n-- > 0) {
            final var address = nextAddress();
            final Map<Bytes, Pair<Bytes, Bytes>> changes = new TreeMap<>();
            for (int i = 0; i < changesPerAddress; i++) {
                changes.put(nextEvmWord(), nextStateChangePair());
            }
            ans.put(address, changes);
        }
        return ans;
    }

    public Pair<Bytes, Bytes> nextStateChangePair() {
        if (nextBoolean()) {
            return Pair.of(nextEvmWord(), null);
        } else {
            return Pair.of(nextEvmWord(), nextEvmWord());
        }
    }

    public TxnReceipt nextReceipt() {
        return nextReceiptBuilder().build();
    }

    public TxnReceipt.Builder nextReceiptBuilder() {
        final var builder = TxnReceipt.newBuilder();
        if (nextBoolean()) {
            final var creationType = nextEntityType();
            switch (creationType) {
                case ACCOUNT -> builder.setAccountId(nextEntityId());
                case CONTRACT -> builder.setContractId(nextEntityId());
                case FILE -> builder.setFileId(nextEntityId());
                case SCHEDULE -> {
                    builder.setScheduleId(nextEntityId());
                    builder.setScheduledTxnId(nextScheduledTxnId());
                }
                case TOKEN -> {
                    builder.setTokenId(nextEntityId());
                    if (nextBoolean()) {
                        builder.setNewTotalSupply(nextUnsignedLong());
                    } else if (nextBoolean()) {
                        builder.setSerialNumbers(nextInRangeLongs(nextNonZeroInt(10)));
                    }
                }
                case TOPIC -> {
                    builder.setTopicId(nextEntityId());
                    if (nextBoolean()) {
                        builder.setRunningHashVersion(nextUnsignedLong());
                        builder.setTopicRunningHash(nextBytes(48));
                        builder.setTopicSequenceNumber(nextUnsignedLong());
                    }
                }
            }
        }
        return builder.setExchangeRates(nextExchangeRates()).setStatus(nextStatus().toString());
    }

    public int nextNonZeroInt(final int inclusiveUpperBound) {
        return 1 + SEEDED_RANDOM.nextInt(inclusiveUpperBound);
    }

    public ResponseCodeEnum nextStatus() {
        // size of ResponseCodeEnum.class.getEnumConstants() in 0.25
        return nextStatus(265);
    }

    public ResponseCodeEnum nextStatus(final int range) {
        final var choices = ResponseCodeEnum.class.getEnumConstants();
        return choices[SEEDED_RANDOM.nextInt(range)];
    }

    public ExchangeRates nextExchangeRates() {
        return new ExchangeRates(
                nextUnsignedInt(),
                nextUnsignedInt(),
                nextUnsignedLong(),
                nextUnsignedInt(),
                nextUnsignedInt(),
                nextUnsignedLong());
    }

    public EntityType nextEntityType() {
        // size of EntityType.class.getEnumConstants() in 0.25
        return nextEntityType(6);
    }

    public EntityType nextEntityType(final int range) {
        final var choices = EntityType.class.getEnumConstants();
        return choices[SEEDED_RANDOM.nextInt(range)];
    }

    public Map<EntityNum, Map<FcTokenAllowanceId, Long>> nextFungibleAllowances(
            final int numUniqueAccounts,
            final int numUniqueTokens,
            final int numSpenders,
            final int numAllowancesPerSpender) {
        final EntityNum[] accounts = nextNums(numUniqueAccounts);
        final EntityNum[] tokens = nextNums(numUniqueTokens);

        final Map<EntityNum, Map<FcTokenAllowanceId, Long>> ans = new TreeMap<>();
        for (int i = 0; i < numSpenders; i++) {
            final var aNum = accounts[SEEDED_RANDOM.nextInt(accounts.length)];
            final var allowances = ans.computeIfAbsent(aNum, a -> new TreeMap<>());
            for (int j = 0; j < numAllowancesPerSpender; j++) {
                final var bNum = accounts[SEEDED_RANDOM.nextInt(accounts.length)];
                final var tNum = tokens[SEEDED_RANDOM.nextInt(tokens.length)];
                final var key = new FcTokenAllowanceId(tNum, bNum);
                allowances.put(key, nextUnsignedLong());
            }
        }
        return ans;
    }

    public Map<EntityNum, Map<EntityNum, Long>> nextCryptoAllowances(
            final int numUniqueAccounts, final int numSpenders, final int numAllowancesPerSpender) {
        final EntityNum[] accounts =
                IntStream.range(0, numUniqueAccounts)
                        .mapToObj(i -> nextNum())
                        .distinct()
                        .toArray(EntityNum[]::new);
        final Map<EntityNum, Map<EntityNum, Long>> ans = new TreeMap<>();
        for (int i = 0; i < numSpenders; i++) {
            final var aNum = accounts[SEEDED_RANDOM.nextInt(accounts.length)];
            final var allowances = ans.computeIfAbsent(aNum, a -> new TreeMap<>());
            for (int j = 0; j < numAllowancesPerSpender; j++) {
                final var bNum = accounts[SEEDED_RANDOM.nextInt(accounts.length)];
                allowances.put(bNum, nextUnsignedLong());
            }
        }
        return ans;
    }

    public Set<FcTokenAllowanceId> nextApprovedForAllAllowances(final int n) {
        final Set<FcTokenAllowanceId> ans = new TreeSet<>();
        for (int i = 0; i < n; i++) {
            ans.add(nextAllowanceId());
        }
        return ans;
    }

    public Map<FcTokenAllowanceId, Long> nextGrantedFungibleAllowances(final int n) {
        final Map<FcTokenAllowanceId, Long> ans = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            ans.put(nextAllowanceId(), nextUnsignedLong());
        }
        return ans;
    }

    public Map<EntityNum, Long> nextGrantedCryptoAllowances(final int n) {
        final Map<EntityNum, Long> ans = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            ans.put(nextNum(), nextUnsignedLong());
        }
        return ans;
    }

    public FcTokenAllowanceId nextAllowanceId() {
        return new FcTokenAllowanceId(nextNum(), nextNum());
    }

    public EntityNum[] nextNums(final int n) {
        return IntStream.range(0, n).mapToObj(i -> nextNum()).distinct().toArray(EntityNum[]::new);
    }

    public long[] nextInRangeLongs(final int n) {
        return IntStream.range(0, n).mapToLong(i -> nextInRangeLong()).toArray();
    }

    public long[] nextLongs(final int n) {
        return IntStream.range(0, n).mapToLong(i -> nextLong()).toArray();
    }

    public List<EntityId> nextEntityIds(final int n) {
        return IntStream.range(0, n).mapToObj(i -> nextEntityId()).toList();
    }

    public long[] nextUnsignedLongs(final int n) {
        return IntStream.range(0, n).mapToLong(i -> nextUnsignedLong()).toArray();
    }

    public EntityNum nextNum() {
        return EntityNum.fromLong(SEEDED_RANDOM.nextLong(BitPackUtils.MAX_NUM_ALLOWED));
    }

    public long nextInRangeLong() {
        return numFromCode(SEEDED_RANDOM.nextInt());
    }

    public EntityNumPair nextPair() {
        return EntityNumPair.fromLongs(nextInRangeLong(), nextInRangeLong());
    }

    public long nextUnsignedLong() {
        return SEEDED_RANDOM.nextLong(Long.MAX_VALUE);
    }

    public long nextLong() {
        return SEEDED_RANDOM.nextLong();
    }

    public Bytes nextEvmWord() {
        if (nextBoolean()) {
            return Bytes.ofUnsignedLong(nextLong()).trimLeadingZeros();
        } else {
            return Bytes.wrap(nextBytes(32)).trimLeadingZeros();
        }
    }

    public Address nextAddress() {
        final byte[] ans = new byte[20];
        if (nextBoolean()) {
            SEEDED_RANDOM.nextBytes(ans);
        } else {
            System.arraycopy(Longs.toByteArray(nextInRangeLong()), 0, ans, 12, 8);
        }
        return Address.fromHexString(CommonUtils.hex(ans));
    }

    public int nextUnsignedInt() {
        return SEEDED_RANDOM.nextInt(Integer.MAX_VALUE);
    }

    public short nextUnsignedShort() {
        return (short) SEEDED_RANDOM.nextInt(Short.MAX_VALUE);
    }

    public String nextString(final int len) {
        final var sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append((char) SEEDED_RANDOM.nextInt(0x7f));
        }
        return sb.toString();
    }

    public JKey nextNullableKey() {
        return nextBoolean() ? null : nextKey();
    }

    public JKey nextKey() {
        final var keyType = SEEDED_RANDOM.nextInt(5);
        if (keyType == 0) {
            return nextEd25519Key();
        } else if (keyType == 1) {
            return nextSecp256k1Key();
        } else if (keyType == 2) {
            return new JContractIDKey(nextContractId());
        } else if (keyType == 3) {
            return new JDelegatableContractAliasKey(
                    nextContractId().toBuilder()
                            .clearContractNum()
                            .setEvmAddress(nextByteString(20))
                            .build());
        } else {
            return new JKeyList(List.of(nextKey(), nextKey()));
        }
    }

    public JKeyList nextKeyList(final int n) {
        return new JKeyList(IntStream.range(0, n).mapToObj(i -> nextKey()).toList());
    }

    public JKey nextEd25519Key() {
        return new JEd25519Key(nextBytes(32));
    }

    public JKey nextSecp256k1Key() {
        return new JECDSASecp256k1Key(nextBytes(33));
    }

    public byte[] nextBytes(final int n) {
        final var ans = new byte[n];
        SEEDED_RANDOM.nextBytes(ans);
        return ans;
    }

    public int[] nextPackedInts(final int n) {
        final var ans = new int[n];
        for (int i = 0; i < ans.length; i++) {
            ans[i] = nextInt();
        }
        return ans;
    }

    public ByteString nextByteString(final int n) {
        return ByteString.copyFrom(nextBytes(n));
    }

    public EntityId nextEntityId() {
        return new EntityId(nextUnsignedLong(), nextUnsignedLong(), nextUnsignedLong());
    }

    public RichInstant nextRichInstant() {
        return new RichInstant(nextUnsignedLong(), SEEDED_RANDOM.nextInt(1_000_000));
    }

    public RichInstant nextNullableRichInstant() {
        return nextBoolean() ? null : nextRichInstant();
    }

    public ContractID nextContractId() {
        return ContractID.newBuilder()
                .setShardNum(nextUnsignedLong())
                .setRealmNum(nextUnsignedLong())
                .setContractNum(nextUnsignedLong())
                .build();
    }

    public byte[] nextSerializedTransactionBody() {
        return TransactionBody.newBuilder()
                .setTransactionID(nextTxnId().toGrpc())
                .setMemo(nextString(50))
                .build()
                .toByteArray();
    }

    public org.hyperledger.besu.datatypes.Hash nextEthHash() {
        return org.hyperledger.besu.datatypes.Hash.wrap(Bytes32.wrap(nextBytes(32)));
    }

    public RecordsRunningHashLeaf nextRecordsRunningHashLeaf() {
        return new RecordsRunningHashLeaf(new RunningHash(new Hash(nextBytes(48))));
    }

    public FcTokenAllowance nextFcTokenAllowance() {
        return FcTokenAllowance.from(nextBoolean(), Longs.asList(nextLongs(nextNonZeroInt(100))));
    }

    public BytesElement nextFilePart() {
        return new BytesElement(nextBytes(nextNonZeroInt(1000)));
    }

    public MerkleEntityId nextMerkleEntityId() {
        final var entityId = nextEntityId();
        return new MerkleEntityId(entityId.shard(), entityId.realm(), entityId.num());
    }

    public FileID nextFileID() {
        // By default, the shard and realm numbers are 0.
        return FileID.newBuilder()
                .setShardNum(0)
                .setRealmNum(0)
                .setFileNum(nextUnsignedLong())
                .build();
    }

    public MerkleSpecialFiles nextMerkleSpecialFiles() {
        final var seeded = new MerkleSpecialFiles();
        final int numFiles = nextNonZeroInt(100);
        for (int i = 0; i < numFiles; i++) {
            seeded.append(nextFileID(), nextBytes(nextNonZeroInt(1000)));
        }
        return seeded;
    }

    public MerkleTokenRelStatus nextMerkleTokenRelStatus() {
        final var seeded =
                new MerkleTokenRelStatus(
                        nextUnsignedLong(),
                        nextBoolean(),
                        nextBoolean(),
                        nextBoolean(),
                        nextUnsignedLong());
        seeded.setPrev(nextUnsignedLong());
        seeded.setNext(nextUnsignedLong());
        return seeded;
    }

    public SerializableSemVers nextSerializableSemVers() {
        return new SerializableSemVers(nextSemVer(), nextSemVer());
    }

    private SemanticVersion nextSemVer() {
        final var ans = SemanticVersion.newBuilder();
        ans.setMajor(nextUnsignedInt()).setMinor(nextUnsignedInt()).setPatch(nextUnsignedInt());
        if (nextBoolean()) {
            ans.setPre(nextString(8));
        }
        if (nextBoolean()) {
            ans.setBuild(nextString(8));
        }
        return ans.build();
    }

    public ContractKey nextContractKey() {
        return new ContractKey(nextUnsignedLong(), nextBytes(32));
    }

    public ContractValue nextContractValue() {
        return new ContractValue(nextBytes(32));
    }

    public VirtualBlobKey.Type nextVirtualBlobKeyType() {
        return VirtualBlobKey.Type.values()[
                nextNonZeroInt(VirtualBlobKey.Type.values().length) - 1];
    }

    public VirtualBlobKey nextVirtualBlobKey() {
        return new VirtualBlobKey(nextVirtualBlobKeyType(), nextUnsignedInt());
    }

    public VirtualBlobValue nextVirtualBlobValue() {
        return new VirtualBlobValue(nextBytes(nextNonZeroInt(100)));
    }
}
