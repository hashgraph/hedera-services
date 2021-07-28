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
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static java.util.stream.Collectors.toList;

public class MerkleNetworkContext extends AbstractMerkleLeaf {
	private static final Logger log = LogManager.getLogger(MerkleNetworkContext.class);

	static final int UNRECORDED_STATE_VERSION = -1;

	static final int PRE_RELEASE_0130_VERSION = 1;
	static final int RELEASE_0130_VERSION = 2;
	static final int RELEASE_0140_VERSION = 3;
	static final int RELEASE_0150_VERSION = 4;
	static final int MERKLE_VERSION = RELEASE_0150_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8d4aa0f0a968a9f3L;
	static final Instant[] NO_CONGESTION_STARTS = new Instant[0];
	static final DeterministicThrottle.UsageSnapshot[] NO_SNAPSHOTS = new DeterministicThrottle.UsageSnapshot[0];

	public static final Instant UNKNOWN_CONSENSUS_TIME = null;

	static DomainSerdes serdes = new DomainSerdes();
	static Supplier<ExchangeRates> ratesSupplier = ExchangeRates::new;
	static Supplier<SequenceNumber> seqNoSupplier = SequenceNumber::new;

	private int stateVersion = UNRECORDED_STATE_VERSION;
	private Instant[] congestionLevelStarts = NO_CONGESTION_STARTS;
	private ExchangeRates midnightRates;
	private Instant lastMidnightBoundaryCheck = null;
	private Instant consensusTimeOfLastHandledTxn = UNKNOWN_CONSENSUS_TIME;
	private SequenceNumber seqNo;
	private long lastScannedEntity;
	private long entitiesScannedThisSecond = 0L;
	private long entitiesTouchedThisSecond = 0L;
	private DeterministicThrottle.UsageSnapshot[] usageSnapshots = NO_SNAPSHOTS;

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

	/* Used by copy() */
	public MerkleNetworkContext(
			Instant consensusTimeOfLastHandledTxn,
			SequenceNumber seqNo,
			long lastScannedEntity,
			ExchangeRates midnightRates,
			DeterministicThrottle.UsageSnapshot[] usageSnapshots,
			Instant[] congestionStartPeriods,
			int stateVersion,
			long entitiesScannedThisSecond,
			long entitiesTouchedThisSecond,
			Instant lastMidnightBoundaryCheck
	) {
		this.consensusTimeOfLastHandledTxn = consensusTimeOfLastHandledTxn;
		this.seqNo = seqNo;
		this.lastScannedEntity = lastScannedEntity;
		this.midnightRates = midnightRates;
		this.usageSnapshots = usageSnapshots;
		this.congestionLevelStarts = congestionStartPeriods;
		this.stateVersion = stateVersion;
		this.entitiesScannedThisSecond = entitiesScannedThisSecond;
		this.entitiesTouchedThisSecond = entitiesTouchedThisSecond;
		this.lastMidnightBoundaryCheck = lastMidnightBoundaryCheck;
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

		reset(activeThrottles);
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

	public void updateSnapshotsFrom(FunctionalityThrottling throttling) {
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
	}

	public void updateCongestionStartsFrom(FeeMultiplierSource feeMultiplierSource) {
		throwIfImmutable("Cannot update congestion starts on an immutable context");
		final var congestionStarts = feeMultiplierSource.congestionLevelStarts();
		if (null == congestionStarts) {
			congestionLevelStarts = NO_CONGESTION_STARTS;
		} else {
			congestionLevelStarts = congestionStarts;
		}
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

	/* --- MerkleLeaf --- */
	@Override
	public MerkleNetworkContext copy() {
		setImmutable(true);
		return new MerkleNetworkContext(
				consensusTimeOfLastHandledTxn,
				seqNo.copy(),
				lastScannedEntity,
				midnightRates.copy(),
				usageSnapshots,
				congestionLevelStarts,
				stateVersion,
				entitiesScannedThisSecond,
				entitiesTouchedThisSecond,
				lastMidnightBoundaryCheck);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		final var lastHandleTime = serdes.readNullableInstant(in);
		consensusTimeOfLastHandledTxn = (lastHandleTime == null) ? null : lastHandleTime.toJava();

		seqNo = seqNoSupplier.get();
		seqNo.deserialize(in);
		midnightRates = in.readSerializable(true, ratesSupplier);

		readCongestionControlData(in);

		if (version >= RELEASE_0140_VERSION) {
			whenVersionHigherOrEqualTo0140(in);
		}
		if (version >= RELEASE_0150_VERSION) {
			whenVersionHigherOrEqualTo0150(in);
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
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public String toString() {
		var sb = new StringBuilder("The network context (state version ")
				.append(stateVersion == UNRECORDED_STATE_VERSION ? "<N/A>" : stateVersion)
				.append(") is,")
				.append("\n  Consensus time of last handled transaction :: ")
				.append(reprOf(consensusTimeOfLastHandledTxn))
				.append("\n  Midnight rate set                          :: ")
				.append(midnightRates.readableRepr())
				.append("\n  Last midnight boundary check               :: ")
				.append(reprOf(lastMidnightBoundaryCheck))
				.append("\n  Next entity number                         :: ")
				.append(seqNo.current())
				.append("\n  Last scanned entity                        :: ")
				.append(lastScannedEntity)
				.append("\n  Entities scanned last consensus second     :: ")
				.append(entitiesScannedThisSecond)
				.append("\n  Entities touched last consensus second     :: ")
				.append(entitiesTouchedThisSecond);
		sb.append("\n  Throttle usage snapshots are               ::");
		for (var snapshot : usageSnapshots) {
			sb.append("\n    ").append(snapshot.used())
					.append(" used (last decision time ")
					.append(reprOf(snapshot.lastDecisionTime())).append(")");
		}
		sb.append("\n  Congestion level start times are           ::");
		for (var start : congestionLevelStarts) {
			sb.append("\n    ").append(reprOf(start));
		}
		return sb.toString();
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
	private void reset(List<DeterministicThrottle> throttles) {
		var currUsageSnapshots = throttles.stream()
				.map(DeterministicThrottle::usageSnapshot)
				.collect(toList());
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
		return consensusTime == null ? "<N/A>" : consensusTime.toString();
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

	DeterministicThrottle.UsageSnapshot[] getUsageSnapshots() {
		return usageSnapshots;
	}

	void setUsageSnapshots(DeterministicThrottle.UsageSnapshot[] usageSnapshots) {
		this.usageSnapshots = usageSnapshots;
	}

	DeterministicThrottle.UsageSnapshot[] usageSnapshots() {
		return usageSnapshots;
	}
}
