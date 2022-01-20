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

import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.platform.state.DualStateImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.state.submerkle.RichInstant.fromJava;

public class MerkleNetworkContext extends AbstractMerkleLeaf {
	private static final Logger log = LogManager.getLogger(MerkleNetworkContext.class);

	private static final String LINE_WRAP = "\n    ";
	private static final String NOT_EXTANT = "<NONE>";
	private static final String NOT_AVAILABLE = "<N/A>";
	private static final String NOT_AVAILABLE_SUFFIX = " <N/A>";

	public static final int UPDATE_FILE_HASH_LEN = 48;
	public static final int UNRECORDED_STATE_VERSION = -1;
	public static final long NO_PREPARED_UPDATE_FILE_NUM = -1;
	public static final byte[] NO_PREPARED_UPDATE_FILE_HASH = new byte[0];
	public static final DeterministicThrottle.UsageSnapshot NO_GAS_THROTTLE_SNAPSHOT =
			new DeterministicThrottle.UsageSnapshot(-1, Instant.EPOCH);

	static final int RELEASE_0130_VERSION = 2;
	static final int RELEASE_0140_VERSION = 3;
	static final int RELEASE_0150_VERSION = 4;
	static final int RELEASE_0190_VERSION = 5;
	static final int RELEASE_0200_VERSION = 6;
	static final int CURRENT_VERSION = RELEASE_0200_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8d4aa0f0a968a9f3L;
	static final Instant[] NO_CONGESTION_STARTS = new Instant[0];
	static final DeterministicThrottle.UsageSnapshot[] NO_SNAPSHOTS = new DeterministicThrottle.UsageSnapshot[0];

	public static final Instant NULL_CONSENSUS_TIME = null;

	static DomainSerdes serdes = new DomainSerdes();
	static Supplier<ExchangeRates> ratesSupplier = ExchangeRates::new;
	static Supplier<SequenceNumber> seqNoSupplier = SequenceNumber::new;

	private int stateVersion = UNRECORDED_STATE_VERSION;
	private Instant[] congestionLevelStarts = NO_CONGESTION_STARTS;
	private ExchangeRates midnightRates;
	private Instant lastMidnightBoundaryCheck = null;
	private Instant consensusTimeOfLastHandledTxn = NULL_CONSENSUS_TIME;
	private SequenceNumber seqNo;
	private long lastScannedEntity;
	private long entitiesScannedThisSecond = 0L;
	private long entitiesTouchedThisSecond = 0L;
	private long preparedUpdateFileNum = NO_PREPARED_UPDATE_FILE_NUM;
	private byte[] preparedUpdateFileHash = NO_PREPARED_UPDATE_FILE_HASH;
	private FeeMultiplierSource multiplierSource = null;
	private FunctionalityThrottling throttling = null;
	private DeterministicThrottle.UsageSnapshot[] usageSnapshots = NO_SNAPSHOTS;
	private DeterministicThrottle.UsageSnapshot gasThrottleUsageSnapshot = NO_GAS_THROTTLE_SNAPSHOT;

	public MerkleNetworkContext() {
		/* No-op for RuntimeConstructable facility; will be followed by a call to deserialize. */
	}

	/* Used at network genesis only */
	public MerkleNetworkContext(
			Instant consensusTimeOfLastHandledTxn,
			SequenceNumber seqNo,
			long lastScannedEntity,
			ExchangeRates midnightRates
	) {
		this.consensusTimeOfLastHandledTxn = consensusTimeOfLastHandledTxn;
		this.seqNo = seqNo;
		this.lastScannedEntity = lastScannedEntity;
		this.midnightRates = midnightRates;
	}

	public MerkleNetworkContext(MerkleNetworkContext that) {
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
	}

	/* --- Helpers that reset the received argument based on the network context */
	public void resetMultiplierSourceFromSavedCongestionStarts(FeeMultiplierSource feeMultiplierSource) {
		if (congestionLevelStarts.length > 0) {
			feeMultiplierSource.resetCongestionLevelStarts(congestionLevelStarts);
		}
	}

	public void resetThrottlingFromSavedSnapshots(FunctionalityThrottling throttling) {
		var activeThrottles = throttling.allActiveThrottles();

		if (activeThrottles.size() != usageSnapshots.length) {
			log.warn("There are " +
					activeThrottles.size() + " active throttles, but " +
					usageSnapshots.length + " usage snapshots from saved state. " +
					"Not performing a reset!");
			return;
		}

		reset(activeThrottles, throttling.gasLimitThrottle());
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
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		final var lastHandleTime = serdes.readNullableInstant(in);
		consensusTimeOfLastHandledTxn = (lastHandleTime == null) ? null : lastHandleTime.toJava();

		seqNo = seqNoSupplier.get();
		seqNo.deserialize(in);
		midnightRates = in.readSerializable(true, ratesSupplier);

		if (version >= RELEASE_0130_VERSION) {
			readCongestionControlData(in);
		}
		if (version >= RELEASE_0140_VERSION) {
			whenVersionHigherOrEqualTo0140(in);
		}
		if (version >= RELEASE_0150_VERSION) {
			whenVersionHigherOrEqualTo0150(in);
		}
		if (version >= RELEASE_0190_VERSION) {
			preparedUpdateFileNum = in.readLong();
			preparedUpdateFileHash = in.readByteArray(UPDATE_FILE_HASH_LEN);
		}
		if (version >= RELEASE_0200_VERSION) {
			var used = in.readLong();
			var lastUsed = serdes.readNullableInstant(in);
			gasThrottleUsageSnapshot = new DeterministicThrottle.UsageSnapshot(used, (lastUsed == null) ? null : lastUsed.toJava());
		}
	}

	private void readCongestionControlData(final SerializableDataInputStream in) throws IOException {
		int numUsageSnapshots = in.readInt();
		if (numUsageSnapshots > 0) {
			usageSnapshots = new DeterministicThrottle.UsageSnapshot[numUsageSnapshots];
			for (int i = 0; i < numUsageSnapshots; i++) {
				var used = in.readLong();
				var lastUsed = serdes.readNullableInstant(in);
				usageSnapshots[i] = new DeterministicThrottle.UsageSnapshot(
						used, (lastUsed == null) ? null : lastUsed.toJava());
			}
		}
		int numCongestionStarts = in.readInt();
		if (numCongestionStarts > 0) {
			congestionLevelStarts = new Instant[numCongestionStarts];
			for (int i = 0; i < numCongestionStarts; i++) {
				final var levelStart = serdes.readNullableInstant(in);
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
		final var lastBoundaryCheck = serdes.readNullableInstant(in);
		lastMidnightBoundaryCheck = (lastBoundaryCheck == null) ? null : lastBoundaryCheck.toJava();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		serdes.writeNullableInstant(fromJava(consensusTimeOfLastHandledTxn), out);
		seqNo.serialize(out);
		out.writeSerializable(midnightRates, true);
		int n = usageSnapshots.length;
		out.writeInt(n);
		for (var usageSnapshot : usageSnapshots) {
			out.writeLong(usageSnapshot.used());
			serdes.writeNullableInstant(fromJava(usageSnapshot.lastDecisionTime()), out);
		}
		n = congestionLevelStarts.length;
		out.writeInt(n);
		for (var congestionStart : congestionLevelStarts) {
			serdes.writeNullableInstant(fromJava(congestionStart), out);
		}
		out.writeLong(lastScannedEntity);
		out.writeLong(entitiesScannedThisSecond);
		out.writeLong(entitiesTouchedThisSecond);
		out.writeInt(stateVersion);
		serdes.writeNullableInstant(fromJava(lastMidnightBoundaryCheck), out);
		out.writeLong(preparedUpdateFileNum);
		out.writeByteArray(preparedUpdateFileHash);
		out.writeLong(gasThrottleUsageSnapshot.used());
		serdes.writeNullableInstant(fromJava(gasThrottleUsageSnapshot.lastDecisionTime()), out);
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	public String summarized() {
		return summarizedWith(null);
	}

	public String summarizedWith(DualStateAccessor dualStateAccessor) {
		final var isDualStateAvailable = dualStateAccessor != null && dualStateAccessor.getDualState() != null;
		final var freezeTime = isDualStateAvailable
				? ((DualStateImpl) dualStateAccessor.getDualState()).getFreezeTime()
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
				congestionStartsDesc();
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
				log.warn("Saved usage snapshot #" + (i + 1)
						+ " was not compatible with the corresponding active throttle ("
						+ e.getMessage() + "); not performing a reset!");
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

	/* Only used for unit tests */
	void setCongestionLevelStarts(Instant[] congestionLevelStarts) {
		this.congestionLevelStarts = congestionLevelStarts;
	}

	Instant[] getCongestionLevelStarts() {
		return congestionLevelStarts;
	}

	Instant getConsensusTimeOfLastHandledTxn() {
		return consensusTimeOfLastHandledTxn;
	}

	void setUsageSnapshots(DeterministicThrottle.UsageSnapshot[] usageSnapshots) {
		this.usageSnapshots = usageSnapshots;
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

	public DeterministicThrottle.UsageSnapshot getGasThrottleUsageSnapshot() {
		return gasThrottleUsageSnapshot;
	}

	public void setGasThrottleUsageSnapshot(DeterministicThrottle.UsageSnapshot gasThrottleUsageSnapshot) {
		this.gasThrottleUsageSnapshot = gasThrottleUsageSnapshot;
	}
}
