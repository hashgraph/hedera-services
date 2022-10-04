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
package com.hedera.services.state.merkle;

import static com.hedera.services.ServicesState.EMPTY_HASH;
import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.contracts.execution.BlockMetaSource.UNAVAILABLE_BLOCK_HASH;
import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.state.serdes.IoUtils.readNullable;
import static com.hedera.services.state.serdes.IoUtils.writeNullable;
import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static com.hedera.services.utils.MiscUtils.safeResetThrottles;
import static com.hedera.services.utils.Units.HBARS_TO_TINYBARS;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.merkle.internals.BytesElement;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.state.tasks.SystemTaskManager;
import com.hedera.services.sysfiles.domain.KnownBlockValues;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.fcqueue.FCQueue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;

public class MerkleNetworkContext extends PartialMerkleLeaf implements MerkleLeaf {
    private static final long MAX_PENDING_REWARDS = 50_000_000_000L * HBARS_TO_TINYBARS;
    private static final Logger log = LogManager.getLogger(MerkleNetworkContext.class);

    private static final int NUM_BLOCK_HASHES_TO_KEEP = 256;
    private static final String LINE_WRAP = "\n    ";
    private static final String NOT_EXTANT = "<NONE>";
    private static final String NOT_AVAILABLE = "<N/A>";
    private static final String NOT_AVAILABLE_SUFFIX = "<N/A>";

    public static final byte LAST_PRE_EXISTING_ENTITY_NOT_SCANNED = 0;
    public static final byte LAST_PRE_EXISTING_ENTITY_SCANNED = 1;
    public static final byte ALL_PRE_EXISTING_ENTITIES_SCANNED = 2;

    public static final int UPDATE_FILE_HASH_LEN = 48;
    public static final int UNRECORDED_STATE_VERSION = -1;
    public static final int NUM_BLOCKS_TO_LOG_AFTER_RENUMBERING = 5;
    public static final long NO_PREPARED_UPDATE_FILE_NUM = -1;
    public static final byte[] NO_PREPARED_UPDATE_FILE_HASH = new byte[0];
    public static final DeterministicThrottle.UsageSnapshot NO_GAS_THROTTLE_SNAPSHOT =
            new DeterministicThrottle.UsageSnapshot(-1, Instant.EPOCH);
    static final DeterministicThrottle.UsageSnapshot NEVER_USED_SNAPSHOT =
            new DeterministicThrottle.UsageSnapshot(0L, null);

    static final int RELEASE_0240_VERSION = 7;
    static final int RELEASE_0260_VERSION = 8;
    static final int RELEASE_0270_VERSION = 9;
    static final int RELEASE_0300_VERSION = 10;
    static final int RELEASE_0310_VERSION = 11;
    static final int CURRENT_VERSION = RELEASE_0310_VERSION;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0x8d4aa0f0a968a9f3L;
    static final Instant[] NO_CONGESTION_STARTS = new Instant[0];
    static final DeterministicThrottle.UsageSnapshot[] NO_SNAPSHOTS =
            new DeterministicThrottle.UsageSnapshot[0];

    static Supplier<ExchangeRates> ratesSupplier = ExchangeRates::new;
    static Supplier<SequenceNumber> seqNoSupplier = SequenceNumber::new;

    private static int blocksToLog = 0;

    private int stateVersion = UNRECORDED_STATE_VERSION;
    private Instant[] congestionLevelStarts = NO_CONGESTION_STARTS;
    private ExchangeRates midnightRates;
    @Nullable private Instant consensusTimeOfLastHandledTxn = null;
    private SequenceNumber seqNo;
    private int nextTaskTodo;
    private long lastScannedEntity;
    private long seqNoPostUpgrade;
    private long lastScannedPostUpgrade;
    private long entitiesScannedThisSecond = 0L;
    private long entitiesTouchedThisSecond = 0L;
    private long preparedUpdateFileNum = NO_PREPARED_UPDATE_FILE_NUM;
    private byte preExistingEntityScanStatus;
    private byte[] preparedUpdateFileHash = NO_PREPARED_UPDATE_FILE_HASH;
    private boolean migrationRecordsStreamed;
    @Nullable private FeeMultiplierSource multiplierSource = null;
    @Nullable private FunctionalityThrottling throttling = null;
    @Nullable private ExpiryThrottle expiryThrottle = null;
    private DeterministicThrottle.UsageSnapshot gasThrottleUsageSnapshot = NO_GAS_THROTTLE_SNAPSHOT;
    private DeterministicThrottle.UsageSnapshot[] usageSnapshots = NO_SNAPSHOTS;
    private DeterministicThrottle.UsageSnapshot expiryUsageSnapshot = NEVER_USED_SNAPSHOT;
    private long blockNo = 0L;
    private Instant firstConsTimeOfCurrentBlock = null;
    private FCQueue<BytesElement> blockHashes = new FCQueue<>();
    private boolean stakingRewardsActivated;
    private long totalStakedRewardStart;
    private long totalStakedStart;
    private long pendingRewards;

    public MerkleNetworkContext() {
        // No-op for RuntimeConstructable facility; will be followed by a call to deserialize
    }

    // Used at network genesis only
    public MerkleNetworkContext(
            @Nullable final Instant consensusTimeOfLastHandledTxn,
            final SequenceNumber seqNo,
            final long lastScannedEntity,
            final ExchangeRates midnightRates) {
        this.consensusTimeOfLastHandledTxn = consensusTimeOfLastHandledTxn;
        this.seqNo = seqNo;
        this.lastScannedEntity = lastScannedEntity;
        this.midnightRates = midnightRates;
    }

    public MerkleNetworkContext(final MerkleNetworkContext that) {
        this.consensusTimeOfLastHandledTxn = that.consensusTimeOfLastHandledTxn;
        this.seqNo = that.seqNo.copy();
        this.lastScannedEntity = that.lastScannedEntity;
        this.midnightRates = that.midnightRates.copy();
        this.usageSnapshots = that.usageSnapshots;
        this.expiryUsageSnapshot = that.expiryUsageSnapshot;
        this.gasThrottleUsageSnapshot = that.gasThrottleUsageSnapshot;
        this.congestionLevelStarts = that.congestionLevelStarts;
        this.stateVersion = that.stateVersion;
        this.entitiesScannedThisSecond = that.entitiesScannedThisSecond;
        this.entitiesTouchedThisSecond = that.entitiesTouchedThisSecond;
        this.preparedUpdateFileNum = that.preparedUpdateFileNum;
        this.preparedUpdateFileHash = that.preparedUpdateFileHash;
        this.migrationRecordsStreamed = that.migrationRecordsStreamed;
        this.firstConsTimeOfCurrentBlock = that.firstConsTimeOfCurrentBlock;
        this.blockNo = that.blockNo;
        this.blockHashes = that.blockHashes.copy();
        this.stakingRewardsActivated = that.stakingRewardsActivated;
        this.totalStakedRewardStart = that.totalStakedRewardStart;
        this.totalStakedStart = that.totalStakedStart;
        this.pendingRewards = that.pendingRewards;
        this.nextTaskTodo = that.nextTaskTodo;
        this.seqNoPostUpgrade = that.seqNoPostUpgrade;
        this.lastScannedPostUpgrade = that.lastScannedPostUpgrade;
        this.preExistingEntityScanStatus = that.preExistingEntityScanStatus;
    }

    // Helpers that reset the received argument based on the network context
    public void resetMultiplierSourceFromSavedCongestionStarts(
            FeeMultiplierSource feeMultiplierSource) {
        if (congestionLevelStarts.length > 0) {
            feeMultiplierSource.resetCongestionLevelStarts(congestionLevelStarts);
        }
    }

    public void resetThrottlingFromSavedSnapshots(final FunctionalityThrottling throttling) {
        var activeThrottles = throttling.allActiveThrottles();
        if (activeThrottles.size() != usageSnapshots.length) {
            log.warn(
                    "There are {} active throttles, but {} usage snapshots from saved state. "
                            + "Not performing a reset!",
                    activeThrottles.size(),
                    usageSnapshots.length);
            return;
        }
        reset(activeThrottles, throttling.gasLimitThrottle());
    }

    public void resetExpiryThrottleFromSavedSnapshot(final ExpiryThrottle expiryThrottle) {
        expiryThrottle.resetToSnapshot(expiryUsageSnapshot);
    }

    /**
     * Given the block number that should go with a given hash, renumbers the context's block hashes
     * to match (assuming the given hash is found in the trailing 256), and updates the current
     * block number.
     *
     * @param knownBlockValues the known block values to use for the renumbering
     */
    @SuppressWarnings("java:S2696")
    public void renumberBlocksToMatch(final KnownBlockValues knownBlockValues) {
        throwIfImmutable("Cannot renumber blocks in an immutable context");
        if (knownBlockValues.isMissing()) {
            return;
        }
        final var matchIndex = matchIndexOf(knownBlockValues.hash());
        if (matchIndex == -1) {
            log.info(
                    "None of {} trailing block hashes matched '{}'",
                    blockHashes::size,
                    () -> CommonUtils.hex(knownBlockValues.hash()));
        } else {
            blockNo = knownBlockValues.number() + (blockHashes.size() - matchIndex);
            log.info(
                    "Renumbered {} trailing block hashes given '0x{}@{}'",
                    blockHashes::size,
                    () -> CommonUtils.hex(knownBlockValues.hash()),
                    knownBlockValues::number);
            blocksToLog = NUM_BLOCKS_TO_LOG_AFTER_RENUMBERING;
        }
    }

    /* --- Mutators that change this network context --- */
    public void clearAutoRenewSummaryCounts() {
        throwIfImmutable("Cannot reset auto-renew summary counts on an immutable context");
        entitiesScannedThisSecond = 0L;
        entitiesTouchedThisSecond = 0L;
    }

    public void updateAutoRenewSummaryCounts(int numScanned, int numTouched) {
        throwIfImmutable("Cannot update auto-renew summary counts on an immutable context");
        entitiesScannedThisSecond += numScanned;
        entitiesTouchedThisSecond += numTouched;
    }

    public void updateLastScannedEntity(long lastScannedEntity) {
        throwIfImmutable("Cannot update last scanned entity on an immutable context");
        this.lastScannedEntity = lastScannedEntity;
    }

    public void syncThrottling(FunctionalityThrottling throttling) {
        this.throttling = throttling;
    }

    public void syncExpiryThrottle(ExpiryThrottle expiryThrottle) {
        this.expiryThrottle = expiryThrottle;
    }

    public void syncMultiplierSource(FeeMultiplierSource multiplierSource) {
        this.multiplierSource = multiplierSource;
    }

    public void setConsensusTimeOfLastHandledTxn(Instant consensusTimeOfLastHandledTxn) {
        throwIfImmutable("Cannot set consensus time of last transaction on an immutable context");
        this.consensusTimeOfLastHandledTxn = consensusTimeOfLastHandledTxn;
    }

    public void setStateVersion(int stateVersion) {
        throwIfImmutable("Cannot set state version on an immutable context");
        this.stateVersion = stateVersion;
    }

    public boolean hasPreparedUpgrade() {
        return preparedUpdateFileNum != NO_PREPARED_UPDATE_FILE_NUM;
    }

    public void recordPreparedUpgrade(FreezeTransactionBody op) {
        throwIfImmutable("Cannot record a prepared upgrade on an immutable context");
        preparedUpdateFileNum = op.getUpdateFile().getFileNum();
        preparedUpdateFileHash = op.getFileHash().toByteArray();
    }

    public boolean isPreparedFileHashValidGiven(MerkleSpecialFiles specialFiles) {
        if (preparedUpdateFileNum == NO_PREPARED_UPDATE_FILE_NUM) {
            return true;
        }
        final var fid = STATIC_PROPERTIES.scopedFileWith(preparedUpdateFileNum);
        return specialFiles.hashMatches(fid, preparedUpdateFileHash);
    }

    public void discardPreparedUpgradeMeta() {
        throwIfImmutable("Cannot rollback a prepared upgrade on an immutable context");
        preparedUpdateFileNum = NO_PREPARED_UPDATE_FILE_NUM;
        preparedUpdateFileHash = NO_PREPARED_UPDATE_FILE_HASH;
    }

    @SuppressWarnings("java:S2696")
    public long finishBlock(
            final org.hyperledger.besu.datatypes.Hash ethHash,
            final Instant firstConsTimeOfNewBlock) {
        throwIfImmutable("Cannot finish a block in an immutable context");
        if (blocksToLog > 0) {
            log.info(
                    """
							--- BLOCK UPDATE ---
							  Finished: #{} @ {} with hash {}
							  Starting: #{} @ {}""",
                    blockNo,
                    firstConsTimeOfCurrentBlock,
                    ethHash,
                    blockNo + 1,
                    firstConsTimeOfNewBlock);
            blocksToLog--;
        }

        if (blockHashes.size() == NUM_BLOCK_HASHES_TO_KEEP) {
            blockHashes.poll();
        }
        blockHashes.add(new BytesElement(ethHash.toArrayUnsafe()));
        blockNo++;
        firstConsTimeOfCurrentBlock = firstConsTimeOfNewBlock;
        return blockNo;
    }

    public void setTotalStakedRewardStart(final long totalStakedRewardStart) {
        throwIfImmutable("Cannot update Total StakedRewardStart on an immutable context");
        this.totalStakedRewardStart = totalStakedRewardStart;
    }

    public void setTotalStakedStart(final long totalStakedStart) {
        throwIfImmutable("Cannot update Total StakedStart on an immutable context");
        this.totalStakedStart = totalStakedStart;
    }

    /* --- MerkleLeaf --- */
    @Override
    public MerkleNetworkContext copy() {
        if (throttling != null) {
            updateSnapshotsFrom(throttling);
            throttling = null;
        }
        if (multiplierSource != null) {
            updateCongestionStartsFrom(multiplierSource);
            multiplierSource = null;
        }
        if (expiryThrottle != null) {
            updateExpirySnapshotFrom(expiryThrottle);
            expiryThrottle = null;
        }

        setImmutable(true);
        return new MerkleNetworkContext(this);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        final var lastHandleTime = readNullable(in, RichInstant::from);
        consensusTimeOfLastHandledTxn = (lastHandleTime == null) ? null : lastHandleTime.toJava();

        seqNo = seqNoSupplier.get();
        seqNo.deserialize(in);
        midnightRates = in.readSerializable(true, ratesSupplier);

        // Added in 0.13
        readCongestionControlData(in);
        // Added in 0.14
        whenVersionHigherOrEqualTo0140(in);
        // Added in 0.15 and removed in 0.27
        whenVersionHigherOrEqualTo0150AndLessThan0270(in, version);
        // Added in 0.19
        preparedUpdateFileNum = in.readLong();
        preparedUpdateFileHash = in.readByteArray(UPDATE_FILE_HASH_LEN);
        // Added in 0.20
        final var gasUsed = in.readLong();
        final var lastGasUsage = readNullable(in, RichInstant::from);
        gasThrottleUsageSnapshot =
                new DeterministicThrottle.UsageSnapshot(
                        gasUsed, (lastGasUsage == null) ? null : lastGasUsage.toJava());
        if (version >= RELEASE_0240_VERSION) {
            migrationRecordsStreamed = in.readBoolean();
        }
        if (version >= RELEASE_0260_VERSION) {
            final var firstBlockTime = readNullable(in, RichInstant::from);
            firstConsTimeOfCurrentBlock = firstBlockTime == null ? null : firstBlockTime.toJava();
            blockNo = in.readLong();
            if (version >= RELEASE_0270_VERSION) {
                readPreBlockData(in, version);
            }
            blockHashes.clear();
            in.readSerializable(true, () -> blockHashes);
        }
    }

    private void readPreBlockData(final SerializableDataInputStream in, final int version)
            throws IOException {
        stakingRewardsActivated = in.readBoolean();
        totalStakedRewardStart = in.readLong();
        totalStakedStart = in.readLong();
        pendingRewards = in.readLong();
        if (version >= RELEASE_0300_VERSION) {
            final var expiryCapacityUsed = in.readLong();
            final var lastExpiryUsage = readNullable(in, RichInstant::from);
            expiryUsageSnapshot =
                    new DeterministicThrottle.UsageSnapshot(
                            expiryCapacityUsed,
                            (lastExpiryUsage == null) ? null : lastExpiryUsage.toJava());
            if (version >= RELEASE_0310_VERSION) {
                nextTaskTodo = in.readInt();
                seqNoPostUpgrade = in.readLong();
                lastScannedPostUpgrade = in.readLong();
                preExistingEntityScanStatus = in.readByte();
            }
        }
    }

    private void readCongestionControlData(final SerializableDataInputStream in)
            throws IOException {
        int numUsageSnapshots = in.readInt();
        if (numUsageSnapshots > 0) {
            usageSnapshots = new DeterministicThrottle.UsageSnapshot[numUsageSnapshots];
            for (int i = 0; i < numUsageSnapshots; i++) {
                final var used = in.readLong();
                final var lastUsed = readNullable(in, RichInstant::from);
                usageSnapshots[i] =
                        new DeterministicThrottle.UsageSnapshot(
                                used, (lastUsed == null) ? null : lastUsed.toJava());
            }
        }
        int numCongestionStarts = in.readInt();
        if (numCongestionStarts > 0) {
            congestionLevelStarts = new Instant[numCongestionStarts];
            for (int i = 0; i < numCongestionStarts; i++) {
                final var levelStart = readNullable(in, RichInstant::from);
                congestionLevelStarts[i] = (levelStart == null) ? null : levelStart.toJava();
            }
        }
    }

    private void whenVersionHigherOrEqualTo0140(final SerializableDataInputStream in)
            throws IOException {
        lastScannedEntity = in.readLong();
        entitiesScannedThisSecond = in.readLong();
        entitiesTouchedThisSecond = in.readLong();
        stateVersion = in.readInt();
    }

    private void whenVersionHigherOrEqualTo0150AndLessThan0270(
            final SerializableDataInputStream in, final int version) throws IOException {
        if (version < RELEASE_0270_VERSION) {
            readNullable(in, RichInstant::from);
        }
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        serializeNonHashData(out);
        out.writeSerializable(blockHashes, true);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public boolean isSelfHashing() {
        return true;
    }

    @Override
    public Hash getHash() {
        final var baos = new ByteArrayOutputStream();
        try (final var out = new SerializableDataOutputStream(baos)) {
            serializeNonHashData(out);
            out.write(blockHashes.getHash().getValue());
            out.writeLong(totalStakedRewardStart);
            out.writeLong(totalStakedStart);
        } catch (IOException | UncheckedIOException e) {
            log.error("Hash computation failed", e);
            return EMPTY_HASH;
        }
        return new Hash(noThrowSha384HashOf(baos.toByteArray()), DigestType.SHA_384);
    }

    @Override
    public int getMinimumSupportedVersion() {
        return RELEASE_0240_VERSION;
    }

    public String summarized() {
        return summarizedWith(null);
    }

    public String summarizedWith(DualStateAccessor dualStateAccessor) {
        final var isDualStateAvailable =
                dualStateAccessor != null && dualStateAccessor.getDualState() != null;
        final var freezeTime =
                isDualStateAvailable ? dualStateAccessor.getDualState().getFreezeTime() : null;
        final var pendingUpdateDesc = currentPendingUpdateDesc();
        final var pendingMaintenanceDesc =
                freezeTimeDesc(freezeTime, isDualStateAvailable) + pendingUpdateDesc;

        return "The network context (state version "
                + (stateVersion == UNRECORDED_STATE_VERSION ? NOT_AVAILABLE : stateVersion)
                + ") is,"
                + "\n  Consensus time of last handled transaction :: "
                + reprOf(consensusTimeOfLastHandledTxn)
                + "\n  Pending maintenance                        :: "
                + pendingMaintenanceDesc
                + "\n  Midnight rate set                          :: "
                + midnightRates.readableRepr()
                + "\n  Next entity number                         :: "
                + seqNo.current()
                + "\n  Last scanned entity                        :: "
                + lastScannedEntity
                + "\n  Entities scanned last consensus second     :: "
                + entitiesScannedThisSecond
                + "\n  Entities touched last consensus second     :: "
                + entitiesTouchedThisSecond
                + "\n  Expiry work usage snapshot is              :: "
                + expirySnapshotDesc()
                + "\n  Throttle usage snapshots are               :: "
                + usageSnapshotsDesc()
                + "\n  Congestion level start times are           :: "
                + congestionStartsDesc()
                + "\n  Block number is                            :: "
                + blockNo
                + "\n  Block timestamp is                         :: "
                + reprOf(firstConsTimeOfCurrentBlock)
                + "\n  Trailing block hashes are                  :: "
                + stringifiedBlockHashes()
                + "\n  Staking rewards activated                  :: "
                + stakingRewardsActivated
                + "\n  Total stake reward start this period       :: "
                + totalStakedRewardStart
                + "\n  Total stake start this period              :: "
                + totalStakedStart;
    }

    public long getAlignmentBlockNo() {
        return blockNo;
    }

    @SuppressWarnings("java:S125")
    public org.hyperledger.besu.datatypes.Hash getBlockHashByNumber(final long reqBlockNo) {
        if (reqBlockNo < 0) {
            return UNAVAILABLE_BLOCK_HASH;
        }
        final var firstAvailable = blockNo - blockHashes.size();
        // If blockHashes.size() == 0, then firstAvailable == blockNo; and all numbers are
        // either less than or greater than or equal to blockNo, so we return unavailable
        if (reqBlockNo < firstAvailable || reqBlockNo >= blockNo) {
            return UNAVAILABLE_BLOCK_HASH;
        } else {
            // Oldest block hash at the head of the queue (next to "roll off" the queue);
            // so iterate in reverse assuming recent blocks are of greater interest
            final var hashIter = blockHashes.reverseIterator();
            for (int i = 0, n = (int) (blockNo - 1 - reqBlockNo); i < n; i++) {
                hashIter.next();
            }
            return org.hyperledger.besu.datatypes.Hash.wrap(
                    Bytes32.wrap(hashIter.next().getData()));
        }
    }

    public Instant firstConsTimeOfCurrentBlock() {
        return (firstConsTimeOfCurrentBlock == null) ? Instant.EPOCH : firstConsTimeOfCurrentBlock;
    }

    private String expirySnapshotDesc() {
        if (expiryUsageSnapshot == NEVER_USED_SNAPSHOT) {
            return NOT_AVAILABLE_SUFFIX;
        } else {
            final var sb = new StringBuilder();
            appendDesc(sb, expiryUsageSnapshot);
            return sb.toString();
        }
    }

    private String usageSnapshotsDesc() {
        if (usageSnapshots.length == 0) {
            return NOT_AVAILABLE_SUFFIX;
        } else {
            final var sb = new StringBuilder();
            for (var snapshot : usageSnapshots) {
                appendDesc(sb, snapshot);
            }
            sb.append(LINE_WRAP)
                    .append(gasThrottleUsageSnapshot.used())
                    .append(" gas used (last decision time ")
                    .append(reprOf(gasThrottleUsageSnapshot.lastDecisionTime()))
                    .append(")");
            return sb.toString();
        }
    }

    private void appendDesc(
            final StringBuilder sb, final DeterministicThrottle.UsageSnapshot snapshot) {
        sb.append(LINE_WRAP)
                .append(snapshot.used())
                .append(" used (last decision time ")
                .append(reprOf(snapshot.lastDecisionTime()))
                .append(")");
    }

    private String congestionStartsDesc() {
        if (congestionLevelStarts.length == 0) {
            return NOT_AVAILABLE_SUFFIX;
        } else {
            final var sb = new StringBuilder();
            for (var start : congestionLevelStarts) {
                sb.append(LINE_WRAP).append(reprOf(start));
            }
            return sb.toString();
        }
    }

    private String currentPendingUpdateDesc() {
        final var nmtDescStart = "w/ NMT upgrade prepped                   :: ";
        if (preparedUpdateFileNum == NO_PREPARED_UPDATE_FILE_NUM) {
            return nmtDescStart + NOT_EXTANT;
        }
        return nmtDescStart
                + "from "
                + STATIC_PROPERTIES.scopedIdLiteralWith(preparedUpdateFileNum)
                + " # "
                + CommonUtils.hex(preparedUpdateFileHash).substring(0, 8);
    }

    private String freezeTimeDesc(@Nullable Instant freezeTime, boolean isDualStateAvailable) {
        final var nmtDescSkip = LINE_WRAP;
        if (freezeTime == null) {
            return (isDualStateAvailable ? NOT_EXTANT : NOT_AVAILABLE) + nmtDescSkip;
        }
        return freezeTime + nmtDescSkip;
    }

    /* --- Getters --- */
    public long idsScannedThisSecond() {
        return entitiesScannedThisSecond;
    }

    public long getEntitiesTouchedThisSecond() {
        return entitiesTouchedThisSecond;
    }

    public Instant consensusTimeOfLastHandledTxn() {
        return consensusTimeOfLastHandledTxn;
    }

    public SequenceNumber seqNo() {
        return seqNo;
    }

    public ExchangeRates midnightRates() {
        return midnightRates;
    }

    public long lastScannedEntity() {
        return lastScannedEntity;
    }

    public ExchangeRates getMidnightRates() {
        return midnightRates;
    }

    public int getStateVersion() {
        return stateVersion;
    }

    public boolean areMigrationRecordsStreamed() {
        return migrationRecordsStreamed;
    }

    public long getTotalStakedRewardStart() {
        return totalStakedRewardStart;
    }

    public long getTotalStakedStart() {
        return totalStakedStart;
    }

    public boolean areRewardsActivated() {
        return stakingRewardsActivated;
    }

    /* --- Internal helpers --- */
    void updateSnapshotsFrom(final FunctionalityThrottling throttling) {
        throwIfImmutable("Cannot update usage snapshots on an immutable context");
        var activeThrottles = throttling.allActiveThrottles();
        int n = activeThrottles.size();
        if (n == 0) {
            usageSnapshots = NO_SNAPSHOTS;
        } else {
            usageSnapshots = new DeterministicThrottle.UsageSnapshot[n];
            for (int i = 0; i < n; i++) {
                usageSnapshots[i] = activeThrottles.get(i).usageSnapshot();
            }
        }
        gasThrottleUsageSnapshot = throttling.gasLimitThrottle().usageSnapshot();
    }

    void updateExpirySnapshotFrom(final ExpiryThrottle expiryThrottle) {
        throwIfImmutable("Cannot update expiry usage snapshots on an immutable context");
        final var officialSnapshot = expiryThrottle.getThrottleSnapshot();
        expiryUsageSnapshot = (officialSnapshot != null) ? officialSnapshot : NEVER_USED_SNAPSHOT;
    }

    void updateCongestionStartsFrom(FeeMultiplierSource feeMultiplierSource) {
        throwIfImmutable("Cannot update congestion starts on an immutable context");
        final var congestionStarts = feeMultiplierSource.congestionLevelStarts();
        if (null == congestionStarts) {
            congestionLevelStarts = NO_CONGESTION_STARTS;
        } else {
            congestionLevelStarts = congestionStarts;
        }
    }

    void serializeNonHashData(final SerializableDataOutputStream out) throws IOException {
        writeNullable(fromJava(consensusTimeOfLastHandledTxn), out, RichInstant::serialize);
        seqNo.serialize(out);
        out.writeSerializable(midnightRates, true);
        int n = usageSnapshots.length;
        out.writeInt(n);
        for (var usageSnapshot : usageSnapshots) {
            out.writeLong(usageSnapshot.used());
            writeNullable(fromJava(usageSnapshot.lastDecisionTime()), out, RichInstant::serialize);
        }
        n = congestionLevelStarts.length;
        out.writeInt(n);
        for (var congestionStart : congestionLevelStarts) {
            writeNullable(fromJava(congestionStart), out, RichInstant::serialize);
        }
        out.writeLong(lastScannedEntity);
        out.writeLong(entitiesScannedThisSecond);
        out.writeLong(entitiesTouchedThisSecond);
        out.writeInt(stateVersion);
        out.writeLong(preparedUpdateFileNum);
        out.writeByteArray(preparedUpdateFileHash);
        out.writeLong(gasThrottleUsageSnapshot.used());
        writeNullable(
                fromJava(gasThrottleUsageSnapshot.lastDecisionTime()), out, RichInstant::serialize);
        out.writeBoolean(migrationRecordsStreamed);
        writeNullable(fromJava(firstConsTimeOfCurrentBlock), out, RichInstant::serialize);
        out.writeLong(blockNo);
        out.writeBoolean(stakingRewardsActivated);
        out.writeLong(totalStakedRewardStart);
        out.writeLong(totalStakedStart);
        out.writeLong(pendingRewards);
        out.writeLong(expiryUsageSnapshot.used());
        writeNullable(
                fromJava(expiryUsageSnapshot.lastDecisionTime()), out, RichInstant::serialize);
        out.writeInt(nextTaskTodo);
        out.writeLong(seqNoPostUpgrade);
        out.writeLong(lastScannedPostUpgrade);
        out.writeByte(preExistingEntityScanStatus);
    }

    private void reset(
            List<DeterministicThrottle> throttles, GasLimitDeterministicThrottle gasLimitThrottle) {
        safeResetThrottles(throttles, usageSnapshots, "handle");

        var currGasThrottleUsageSnapshot = gasLimitThrottle.usageSnapshot();
        try {
            gasLimitThrottle.resetUsageTo(gasThrottleUsageSnapshot);
            log.info("Reset {} with saved gas throttle usage snapshot", gasThrottleUsageSnapshot);
        } catch (IllegalArgumentException e) {
            log.warn(
                    String.format(
                            "Saved gas throttle usage snapshot was not compatible with the"
                                + " corresponding active throttle (%s); not performing a reset!",
                            e.getMessage()));
            gasLimitThrottle.resetUsageTo(currGasThrottleUsageSnapshot);
        }
    }

    private String reprOf(Instant consensusTime) {
        return consensusTime == null ? NOT_AVAILABLE : consensusTime.toString();
    }

    private String stringifiedBlockHashes() {
        final var jsonSb = new StringBuilder("[");
        final var firstAvailable = blockNo - blockHashes.size();
        final var hashIter = blockHashes.iterator();
        for (int i = 0; hashIter.hasNext(); i++) {
            final var nextBlockNo = firstAvailable + i;
            final var blockHash = hashIter.next().getData();
            jsonSb.append("{\"num\": ")
                    .append(nextBlockNo)
                    .append(", ")
                    .append("\"hash\": \"")
                    .append(CommonUtils.hex(blockHash))
                    .append("\"}")
                    .append(hashIter.hasNext() ? ", " : "");
        }
        return jsonSb.append("]").toString();
    }

    private int matchIndexOf(final byte[] blockHash) {
        var matchIndex = -1;
        var offsetFromOldest = 0;
        for (final var trailingHash : blockHashes) {
            if (Arrays.equals(blockHash, trailingHash.getData())) {
                matchIndex = offsetFromOldest;
                break;
            }
            offsetFromOldest++;
        }
        return matchIndex;
    }

    public long getPreparedUpdateFileNum() {
        return preparedUpdateFileNum;
    }

    public void setPreparedUpdateFileNum(long preparedUpdateFileNum) {
        throwIfImmutable("Cannot update prepared update file num on an immutable context");
        this.preparedUpdateFileNum = preparedUpdateFileNum;
    }

    public byte[] getPreparedUpdateFileHash() {
        return preparedUpdateFileHash;
    }

    public void setPreparedUpdateFileHash(byte[] preparedUpdateFileHash) {
        throwIfImmutable("Cannot update prepared update file hash on an immutable context");
        this.preparedUpdateFileHash = preparedUpdateFileHash;
    }

    public long pendingRewards() {
        return pendingRewards;
    }

    public void setPendingRewards(final long pendingRewards) {
        this.pendingRewards = pendingRewards;
    }

    public void increasePendingRewards(final long amount) {
        safeUpdatePendingRewards(amount, +1);
    }

    public void decreasePendingRewards(final long amount) {
        safeUpdatePendingRewards(amount, -1);
    }

    private void safeUpdatePendingRewards(final long amount, final long sigNum) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "Cannot "
                            + (sigNum < 0 ? "decrease " : "increase ")
                            + "pendingRewards="
                            + pendingRewards
                            + " by negative amount ("
                            + amount
                            + ")");
        }
        final var delta = sigNum * amount;
        var newPendingRewards = pendingRewards + delta;
        if (MAX_PENDING_REWARDS - pendingRewards < delta) {
            log.warn(
                    "Pending rewards increased by {} to an un-payable {}, fixing to 50B hbar",
                    amount,
                    newPendingRewards);
            newPendingRewards = MAX_PENDING_REWARDS;
        } else if (newPendingRewards < 0) {
            log.warn(
                    "Pending rewards decreased by {} to a meaningless {}, fixing to zero hbar",
                    amount,
                    newPendingRewards);
            newPendingRewards = 0;
        }
        pendingRewards = newPendingRewards;
    }

    public long lastScannedPostUpgrade() {
        return lastScannedPostUpgrade;
    }

    public void setLastScannedPostUpgrade(final long lastScannedPostUpgrade) {
        throwIfImmutable("Cannot set post-upgrade last scanned in immutable context");
        this.lastScannedPostUpgrade = lastScannedPostUpgrade;
    }

    public long seqNoPostUpgrade() {
        return seqNoPostUpgrade;
    }

    public void markPostUpgradeScanStatus() {
        throwIfImmutable("Cannot set post-upgrade last scanned in immutable context");
        this.seqNoPostUpgrade = seqNo.current();
        this.lastScannedPostUpgrade = this.lastScannedEntity;
        this.preExistingEntityScanStatus = LAST_PRE_EXISTING_ENTITY_NOT_SCANNED;
    }

    public byte getPreExistingEntityScanStatus() {
        return preExistingEntityScanStatus;
    }

    public void setPreExistingEntityScanStatus(final byte preExistingEntityScanStatus) {
        throwIfImmutable("Cannot update pre-existing entity scan status in immutable context");
        this.preExistingEntityScanStatus = preExistingEntityScanStatus;
    }

    public boolean areAllPreUpgradeEntitiesScanned() {
        return this.preExistingEntityScanStatus == ALL_PRE_EXISTING_ENTITIES_SCANNED;
    }

    public void markMigrationRecordsStreamed() {
        this.migrationRecordsStreamed = true;
    }

    public void markMigrationRecordsNotYetStreamed() {
        this.migrationRecordsStreamed = false;
    }

    /**
     * Gives the consensus view (from state) of which zero-indexed task number to do next.
     *
     * @return the consensus next task number
     */
    public int nextTaskTodo() {
        return nextTaskTodo;
    }

    /**
     * Updates the consensus view in state of which zero-indexed task number to do next, given the
     * total number of task types the {@link SystemTaskManager} is aware of.
     *
     * @param i the zero-indexed task number
     */
    public void setNextTaskTodo(final int i) {
        throwIfImmutable("Cannot set post-upgrade sequence number in immutable context");
        nextTaskTodo = i;
    }

    /* Only used for unit tests */

    Instant[] getCongestionLevelStarts() {
        return congestionLevelStarts;
    }

    Instant getConsensusTimeOfLastHandledTxn() {
        return consensusTimeOfLastHandledTxn;
    }

    DeterministicThrottle.UsageSnapshot[] usageSnapshots() {
        return usageSnapshots;
    }

    DeterministicThrottle.UsageSnapshot expiryUsageSnapshot() {
        return expiryUsageSnapshot;
    }

    public void setMidnightRates(ExchangeRates midnightRates) {
        this.midnightRates = midnightRates;
    }

    public void setSeqNo(SequenceNumber seqNo) {
        this.seqNo = seqNo;
    }

    FeeMultiplierSource getMultiplierSource() {
        return multiplierSource;
    }

    FunctionalityThrottling getThrottling() {
        return throttling;
    }

    DeterministicThrottle.UsageSnapshot getGasThrottleUsageSnapshot() {
        return gasThrottleUsageSnapshot;
    }

    public void setStakingRewardsActivated(boolean stakingRewardsActivated) {
        this.stakingRewardsActivated = stakingRewardsActivated;
    }

    public void setGasThrottleUsageSnapshot(
            DeterministicThrottle.UsageSnapshot gasThrottleUsageSnapshot) {
        this.gasThrottleUsageSnapshot = gasThrottleUsageSnapshot;
    }

    public void setMigrationRecordsStreamed(final boolean migrationRecordsStreamed) {
        this.migrationRecordsStreamed = migrationRecordsStreamed;
    }

    /* --- Utility methods --- */
    public static org.hyperledger.besu.datatypes.Hash ethHashFrom(final Hash hash) {
        final byte[] hashBytesToConvert = hash.getValue();
        final byte[] prefixBytes = new byte[32];
        System.arraycopy(hashBytesToConvert, 0, prefixBytes, 0, 32);
        return org.hyperledger.besu.datatypes.Hash.wrap(Bytes32.wrap(prefixBytes));
    }

    /* --- Used for tests --- */
    @VisibleForTesting
    public ExpiryThrottle getExpiryThrottle() {
        return expiryThrottle;
    }

    @VisibleForTesting
    public void setFirstConsTimeOfCurrentBlock(final Instant firstConsTimeOfCurrentBlock) {
        this.firstConsTimeOfCurrentBlock = firstConsTimeOfCurrentBlock;
    }

    @VisibleForTesting
    FCQueue<BytesElement> getBlockHashes() {
        return blockHashes;
    }

    @VisibleForTesting
    int getBlocksToLog() {
        return blocksToLog;
    }

    @VisibleForTesting
    public void setBlockNo(final long blockNo) {
        this.blockNo = blockNo;
    }

    @VisibleForTesting
    void setBlockHashes(final FCQueue<BytesElement> blockHashes) {
        this.blockHashes = blockHashes;
    }

    @VisibleForTesting
    public void setUsageSnapshots(final DeterministicThrottle.UsageSnapshot[] usageSnapshots) {
        this.usageSnapshots = usageSnapshots;
    }

    @VisibleForTesting
    public void setCongestionLevelStarts(final Instant[] congestionLevelStarts) {
        this.congestionLevelStarts = congestionLevelStarts;
    }

    @VisibleForTesting
    public void setExpiryUsageSnapshot(
            final DeterministicThrottle.UsageSnapshot expiryUsageSnapshot) {
        this.expiryUsageSnapshot = expiryUsageSnapshot;
    }

    @VisibleForTesting
    public void setSeqNoPostUpgrade(long seqNoPostUpgrade) {
        this.seqNoPostUpgrade = seqNoPostUpgrade;
    }
}
