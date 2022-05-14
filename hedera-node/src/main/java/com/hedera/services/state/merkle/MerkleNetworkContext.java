package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.sysfiles.domain.KnownBlockValues;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.common.utility.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.state.serdes.IoUtils.readNullable;
import static com.hedera.services.state.serdes.IoUtils.writeNullable;
import static com.hedera.services.state.submerkle.RichInstant.fromJava;

public class MerkleNetworkContext extends AbstractMerkleLeaf {
	private static final Logger log = LogManager.getLogger(MerkleNetworkContext.class);

	private static final int NUM_BLOCK_HASHES_TO_KEEP = 256;
	private static final String LINE_WRAP = "\n    ";
	private static final String NOT_EXTANT = "<NONE>";
	private static final String NOT_AVAILABLE = "<N/A>";
	private static final String NOT_AVAILABLE_SUFFIX = " <N/A>";

	public static final int UPDATE_FILE_HASH_LEN = 48;
	public static final int UNRECORDED_STATE_VERSION = -1;
	public static final int NUM_BLOCKS_TO_LOG_AFTER_RENUMBERING = 5;
	public static final long NO_PREPARED_UPDATE_FILE_NUM = -1;
	public static final byte[] NO_PREPARED_UPDATE_FILE_HASH = new byte[0];
	public static final DeterministicThrottle.UsageSnapshot NO_GAS_THROTTLE_SNAPSHOT =
			new DeterministicThrottle.UsageSnapshot(-1, Instant.EPOCH);
	public static final org.hyperledger.besu.datatypes.Hash UNAVAILABLE_BLOCK_HASH =
			ethHashFrom(new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]));

	static final int RELEASE_0200_VERSION = 6;
	static final int RELEASE_0240_VERSION = 7;
	static final int RELEASE_0260_VERSION = 8;
	static final int CURRENT_VERSION = RELEASE_0260_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8d4aa0f0a968a9f3L;
	static final Instant[] NO_CONGESTION_STARTS = new Instant[0];
	static final DeterministicThrottle.UsageSnapshot[] NO_SNAPSHOTS = new DeterministicThrottle.UsageSnapshot[0];

	static Supplier<ExchangeRates> ratesSupplier = ExchangeRates::new;
	static Supplier<SequenceNumber> seqNoSupplier = SequenceNumber::new;

	private static int blocksToLog = 0;

	private int stateVersion = UNRECORDED_STATE_VERSION;
	private Instant[] congestionLevelStarts = NO_CONGESTION_STARTS;
	private ExchangeRates midnightRates;
	@Nullable
	private Instant lastMidnightBoundaryCheck = null;
	@Nullable
	private Instant consensusTimeOfLastHandledTxn = null;
	private SequenceNumber seqNo;
	private long lastScannedEntity;
	private long entitiesScannedThisSecond = 0L;
	private long entitiesTouchedThisSecond = 0L;
	private long preparedUpdateFileNum = NO_PREPARED_UPDATE_FILE_NUM;
	private byte[] preparedUpdateFileHash = NO_PREPARED_UPDATE_FILE_HASH;
	private boolean migrationRecordsStreamed;
	@Nullable
	private FeeMultiplierSource multiplierSource = null;
	@Nullable
	private FunctionalityThrottling throttling = null;
	private DeterministicThrottle.UsageSnapshot gasThrottleUsageSnapshot = NO_GAS_THROTTLE_SNAPSHOT;
	private DeterministicThrottle.UsageSnapshot[] usageSnapshots = NO_SNAPSHOTS;
	private long blockNo = Long.MIN_VALUE;
	private Instant firstConsTimeOfCurrentBlock = null;
	private SortedMap<Long, org.hyperledger.besu.datatypes.Hash> blockHashes = new TreeMap<>();

	public MerkleNetworkContext() {
		// No-op for RuntimeConstructable facility; will be followed by a call to deserialize
	}

	// Used at network genesis only
	public MerkleNetworkContext(
			final Instant consensusTimeOfLastHandledTxn,
			final SequenceNumber seqNo,
			final long lastScannedEntity,
			final ExchangeRates midnightRates
	) {
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
		this.gasThrottleUsageSnapshot = that.gasThrottleUsageSnapshot;
		this.congestionLevelStarts = that.congestionLevelStarts;
		this.stateVersion = that.stateVersion;
		this.entitiesScannedThisSecond = that.entitiesScannedThisSecond;
		this.entitiesTouchedThisSecond = that.entitiesTouchedThisSecond;
		this.lastMidnightBoundaryCheck = that.lastMidnightBoundaryCheck;
		this.preparedUpdateFileNum = that.preparedUpdateFileNum;
		this.preparedUpdateFileHash = that.preparedUpdateFileHash;
		this.migrationRecordsStreamed = that.migrationRecordsStreamed;
		this.firstConsTimeOfCurrentBlock = that.firstConsTimeOfCurrentBlock;
		this.blockNo = that.blockNo;
		this.blockHashes = new TreeMap<>(that.blockHashes);
	}

	// Helpers that reset the received argument based on the network context
	public void resetMultiplierSourceFromSavedCongestionStarts(FeeMultiplierSource feeMultiplierSource) {
		if (congestionLevelStarts.length > 0) {
			feeMultiplierSource.resetCongestionLevelStarts(congestionLevelStarts);
		}
	}

	public void resetThrottlingFromSavedSnapshots(FunctionalityThrottling throttling) {
		var activeThrottles = throttling.allActiveThrottles();

		if (activeThrottles.size() != usageSnapshots.length) {
			log.warn("There are {} active throttles, but {} usage snapshots from saved state. " +
					"Not performing a reset!", activeThrottles.size(), usageSnapshots.length);
			return;
		}

		reset(activeThrottles, throttling.gasLimitThrottle());
	}

	/**
	 * Given the block number that should go with a given hash, renumbers the context's block hashes to match
	 * (assuming the given hash is found in the trailing 256), and updates the current block number.
	 *
	 * @param knownBlockValues
	 * 		the known block values to use for the renumbering
	 */
	public void renumberBlocksToMatch(final KnownBlockValues knownBlockValues) {
		if (knownBlockValues.isMissing()) {
			return;
		}
		final var matchIndex = matchIndexOf(knownBlockValues.hash());
		if (matchIndex == -1) {
			log.info("None of {} trailing block hashes matched '{}'",
					blockHashes::size, () -> CommonUtils.hex(knownBlockValues.hash()));
		} else {
			final SortedMap<Long, org.hyperledger.besu.datatypes.Hash> renumberedBlockHashes = new TreeMap<>();
			long nextKey = knownBlockValues.number() - matchIndex;
			for (final var entry : blockHashes.entrySet()) {
				renumberedBlockHashes.put(nextKey++, entry.getValue());
			}
			blockNo = nextKey;
			blockHashes = renumberedBlockHashes;
			log.info("Renumbered {} trailing block hashes given '0x{}@{}'",
					blockHashes::size, () -> CommonUtils.hex(knownBlockValues.hash()), knownBlockValues::number);
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

	public void syncMultiplierSource(FeeMultiplierSource multiplierSource) {
		this.multiplierSource = multiplierSource;
	}

	public void setConsensusTimeOfLastHandledTxn(Instant consensusTimeOfLastHandledTxn) {
		throwIfImmutable("Cannot set consensus time of last transaction on an immutable context");
		this.consensusTimeOfLastHandledTxn = consensusTimeOfLastHandledTxn;
	}

	public void setLastMidnightBoundaryCheck(Instant lastMidnightBoundaryCheck) {
		throwIfImmutable("Cannot update last midnight boundary check on an immutable context");
		this.lastMidnightBoundaryCheck = lastMidnightBoundaryCheck;
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

	public long finishBlock(final org.hyperledger.besu.datatypes.Hash ethHash, final Instant firstConsTimeOfNewBlock) {
		if (blocksToLog > 0) {
			log.info("""
							--- BLOCK UPDATE ---
							  Finished: #{} @ {} with hash {}
							  Starting: #{} @ {}""",
					blockNo, firstConsTimeOfCurrentBlock, ethHash, blockNo + 1, firstConsTimeOfNewBlock);
			blocksToLog--;
		}

		if (blockHashes.size() == NUM_BLOCK_HASHES_TO_KEEP) {
			blockHashes.remove(blockHashes.firstKey());
		}
		blockHashes.put(blockNo++, ethHash);
		firstConsTimeOfCurrentBlock = firstConsTimeOfNewBlock;
		return blockNo;
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

		setImmutable(true);

		return new MerkleNetworkContext(this);
	}

	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		final var lastHandleTime = readNullable(in, RichInstant::from);
		consensusTimeOfLastHandledTxn = (lastHandleTime == null) ? null : lastHandleTime.toJava();

		seqNo = seqNoSupplier.get();
		seqNo.deserialize(in);
		midnightRates = in.readSerializable(true, ratesSupplier);

		// Added in 0.13
		readCongestionControlData(in);
		// Added in 0.14
		whenVersionHigherOrEqualTo0140(in);
		// Added in 0.15
		whenVersionHigherOrEqualTo0150(in);
		// Added in 0.19
		preparedUpdateFileNum = in.readLong();
		preparedUpdateFileHash = in.readByteArray(UPDATE_FILE_HASH_LEN);
		// Added in 0.20
		final var gasUsed = in.readLong();
		final var lastGasUsage = readNullable(in, RichInstant::from);
		gasThrottleUsageSnapshot = new DeterministicThrottle.UsageSnapshot(
				gasUsed, (lastGasUsage == null) ? null : lastGasUsage.toJava());
		if (version >= RELEASE_0240_VERSION) {
			migrationRecordsStreamed = in.readBoolean();
		}
		if (version >= RELEASE_0260_VERSION) {
			final var firstBlockTime = readNullable(in, RichInstant::from);
			firstConsTimeOfCurrentBlock = firstBlockTime == null ? null : firstBlockTime.toJava();
			blockNo = in.readLong();
		}
	}

	private void readCongestionControlData(final SerializableDataInputStream in) throws IOException {
		int numUsageSnapshots = in.readInt();
		if (numUsageSnapshots > 0) {
			usageSnapshots = new DeterministicThrottle.UsageSnapshot[numUsageSnapshots];
			for (int i = 0; i < numUsageSnapshots; i++) {
				final var used = in.readLong();
				final var lastUsed = readNullable(in, RichInstant::from);
				usageSnapshots[i] = new DeterministicThrottle.UsageSnapshot(
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

	private void whenVersionHigherOrEqualTo0140(final SerializableDataInputStream in) throws IOException {
		lastScannedEntity = in.readLong();
		entitiesScannedThisSecond = in.readLong();
		entitiesTouchedThisSecond = in.readLong();
		stateVersion = in.readInt();
	}

	private void whenVersionHigherOrEqualTo0150(final SerializableDataInputStream in) throws IOException {
		final var lastBoundaryCheck = readNullable(in, RichInstant::from);
		lastMidnightBoundaryCheck = (lastBoundaryCheck == null) ? null : lastBoundaryCheck.toJava();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
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
		writeNullable(fromJava(lastMidnightBoundaryCheck), out, RichInstant::serialize);
		out.writeLong(preparedUpdateFileNum);
		out.writeByteArray(preparedUpdateFileHash);
		out.writeLong(gasThrottleUsageSnapshot.used());
		writeNullable(fromJava(gasThrottleUsageSnapshot.lastDecisionTime()), out, RichInstant::serialize);
		out.writeBoolean(migrationRecordsStreamed);
		writeNullable(fromJava(firstConsTimeOfCurrentBlock), out, RichInstant::serialize);
		out.writeLong(blockNo);
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
	public int getMinimumSupportedVersion() {
		return RELEASE_0200_VERSION;
	}

	public String summarized() {
		return summarizedWith(null);
	}

	public String summarizedWith(DualStateAccessor dualStateAccessor) {
		final var isDualStateAvailable = dualStateAccessor != null && dualStateAccessor.getDualState() != null;
		final var freezeTime = isDualStateAvailable
				? dualStateAccessor.getDualState().getFreezeTime()
				: null;
		final var pendingUpdateDesc = currentPendingUpdateDesc();
		final var pendingMaintenanceDesc = freezeTimeDesc(freezeTime, isDualStateAvailable) + pendingUpdateDesc;

		return "The network context (state version " +
				(stateVersion == UNRECORDED_STATE_VERSION ? NOT_AVAILABLE : stateVersion) +
				") is," +
				"\n  Consensus time of last handled transaction :: " +
				reprOf(consensusTimeOfLastHandledTxn) +
				"\n  Pending maintenance                        :: " +
				pendingMaintenanceDesc +
				"\n  Midnight rate set                          :: " +
				midnightRates.readableRepr() +
				"\n  Last midnight boundary check               :: " +
				reprOf(lastMidnightBoundaryCheck) +
				"\n  Next entity number                         :: " +
				seqNo.current() +
				"\n  Last scanned entity                        :: " +
				lastScannedEntity +
				"\n  Entities scanned last consensus second     :: " +
				entitiesScannedThisSecond +
				"\n  Entities touched last consensus second     :: " +
				entitiesTouchedThisSecond +
				"\n  Throttle usage snapshots are               ::" +
				usageSnapshotsDesc() +
				"\n  Congestion level start times are           ::" +
				congestionStartsDesc() +
				"\n  Block number is                            :: " +
				blockNo +
				"\n  Block timestamp is                         :: " +
				reprOf(firstConsTimeOfCurrentBlock);
	}

	public long getAlignmentBlockNo() {
		return blockNo;
	}

	public org.hyperledger.besu.datatypes.Hash getBlockHashByNumber(final long reqBlockNo) {
		if (reqBlockNo < 0) {
			return UNAVAILABLE_BLOCK_HASH;
		}
		return blockHashes.getOrDefault(reqBlockNo, UNAVAILABLE_BLOCK_HASH);
	}

	public Instant firstConsTimeOfCurrentBlock() {
		return (firstConsTimeOfCurrentBlock == null) ? Instant.EPOCH : firstConsTimeOfCurrentBlock;
	}

	private String usageSnapshotsDesc() {
		if (usageSnapshots.length == 0) {
			return NOT_AVAILABLE_SUFFIX;
		} else {
			final var sb = new StringBuilder();
			for (var snapshot : usageSnapshots) {
				sb.append(LINE_WRAP).append(snapshot.used())
						.append(" used (last decision time ")
						.append(reprOf(snapshot.lastDecisionTime())).append(")");
			}
			sb.append(LINE_WRAP)
					.append(gasThrottleUsageSnapshot.used())
					.append(" gas used (last decision time ")
					.append(reprOf(gasThrottleUsageSnapshot.lastDecisionTime())).append(")");
			return sb.toString();
		}
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
				+ " # " + CommonUtils.hex(preparedUpdateFileHash).substring(0, 8);
	}

	private String freezeTimeDesc(@Nullable Instant freezeTime, boolean isDualStateAvailable) {
		final var nmtDescSkip = LINE_WRAP;
		if (freezeTime == null) {
			return (isDualStateAvailable ? NOT_EXTANT : NOT_AVAILABLE) + nmtDescSkip;
		}
		return freezeTime + nmtDescSkip;
	}

	/* --- Getters --- */
	public long getEntitiesScannedThisSecond() {
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

	public Instant lastMidnightBoundaryCheck() {
		return lastMidnightBoundaryCheck;
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

	/* --- Internal helpers --- */
	void updateSnapshotsFrom(FunctionalityThrottling throttling) {
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

	void updateCongestionStartsFrom(FeeMultiplierSource feeMultiplierSource) {
		throwIfImmutable("Cannot update congestion starts on an immutable context");
		final var congestionStarts = feeMultiplierSource.congestionLevelStarts();
		if (null == congestionStarts) {
			congestionLevelStarts = NO_CONGESTION_STARTS;
		} else {
			congestionLevelStarts = congestionStarts;
		}
	}

	private void reset(List<DeterministicThrottle> throttles, GasLimitDeterministicThrottle gasLimitThrottle) {
		var currUsageSnapshots = throttles.stream()
				.map(DeterministicThrottle::usageSnapshot)
				.toList();
		for (int i = 0, n = usageSnapshots.length; i < n; i++) {
			var savedUsageSnapshot = usageSnapshots[i];
			var throttle = throttles.get(i);
			try {
				throttle.resetUsageTo(savedUsageSnapshot);
				log.info("Reset {} with saved usage snapshot", throttle);
			} catch (Exception e) {
				log.warn(
						"Saved usage snapshot # {} was not compatible with the corresponding active throttle ( {}) " +
								"not" +
								" " +
								"performing a reset !",
						(i + 1), e.getMessage());
				resetUnconditionally(throttles, currUsageSnapshots);
				break;
			}
		}

		var currGasThrottleUsageSnapshot = gasLimitThrottle.usageSnapshot();
		try {
			gasLimitThrottle.resetUsageTo(gasThrottleUsageSnapshot);
			log.info("Reset {} with saved gas throttle usage snapshot", gasThrottleUsageSnapshot);
		} catch (IllegalArgumentException e) {
			log.warn(String.format("Saved gas throttle usage snapshot was not compatible " +
					"with the corresponding active throttle (%s); not performing a reset!", e.getMessage()));
			gasLimitThrottle.resetUsageTo(currGasThrottleUsageSnapshot);
		}
	}

	private void resetUnconditionally(
			List<DeterministicThrottle> throttles,
			List<DeterministicThrottle.UsageSnapshot> knownCompatible
	) {
		for (int i = 0, n = knownCompatible.size(); i < n; i++) {
			throttles.get(i).resetUsageTo(knownCompatible.get(i));
		}
	}

	private String reprOf(Instant consensusTime) {
		return consensusTime == null ? NOT_AVAILABLE : consensusTime.toString();
	}

	private int matchIndexOf(final byte[] blockHash) {
		var matchIndex = -1;
		var offsetFromOldest = 0;
		for (final var hash : blockHashes.values()) {
			if (Arrays.equals(blockHash, hash.toArrayUnsafe())) {
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

	public void markMigrationRecordsStreamed() {
		this.migrationRecordsStreamed = true;
	}

	public void markMigrationRecordsNotYetStreamed() {
		this.migrationRecordsStreamed = false;
	}

	/* Only used for unit tests */
	public void setUsageSnapshots(final DeterministicThrottle.UsageSnapshot[] usageSnapshots) {
		this.usageSnapshots = usageSnapshots;
	}

	public void setCongestionLevelStarts(final Instant[] congestionLevelStarts) {
		this.congestionLevelStarts = congestionLevelStarts;
	}

	Instant[] getCongestionLevelStarts() {
		return congestionLevelStarts;
	}

	Instant getConsensusTimeOfLastHandledTxn() {
		return consensusTimeOfLastHandledTxn;
	}

	DeterministicThrottle.UsageSnapshot[] usageSnapshots() {
		return usageSnapshots;
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

	public void setGasThrottleUsageSnapshot(DeterministicThrottle.UsageSnapshot gasThrottleUsageSnapshot) {
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
	public void setFirstConsTimeOfCurrentBlock(final Instant firstConsTimeOfCurrentBlock) {
		this.firstConsTimeOfCurrentBlock = firstConsTimeOfCurrentBlock;
	}

	@VisibleForTesting
	public Map<Long, org.hyperledger.besu.datatypes.Hash> getBlockHashCache() {
		return blockHashes;
	}

	@VisibleForTesting
	int getBlocksToLog() {
		return blocksToLog;
	}

	@VisibleForTesting
	void setBlockNo(final long blockNo) {
		this.blockNo = blockNo;
	}
}
