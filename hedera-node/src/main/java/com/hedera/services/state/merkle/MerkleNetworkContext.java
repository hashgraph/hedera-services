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
import com.hedera.services.state.submerkle.RichInstant;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static java.util.stream.Collectors.toList;

public class MerkleNetworkContext extends AbstractMerkleLeaf {
	static Logger log = LogManager.getLogger(MerkleNetworkContext.class);

	static final int PRE_RELEASE_0130_VERSION = 1;
	static final int RELEASE_0130_VERSION = 2;
	static final int MERKLE_VERSION = RELEASE_0130_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8d4aa0f0a968a9f3L;
	static final RichInstant[] NO_CONGESTION_STARTS = new RichInstant[0];
	static final DeterministicThrottle.UsageSnapshot[] NO_SNAPSHOTS = new DeterministicThrottle.UsageSnapshot[0];

	public static final RichInstant UNKNOWN_CONSENSUS_TIME = null;

	static DomainSerdes serdes = new DomainSerdes();
	static Supplier<ExchangeRates> ratesSupplier = ExchangeRates::new;
	static Supplier<SequenceNumber> seqNoSupplier = SequenceNumber::new;

	RichInstant consensusTimeOfLastHandledTxn;
	RichInstant[] congestionLevelStarts = NO_CONGESTION_STARTS;
	ExchangeRates midnightRates;
	SequenceNumber seqNo;
	DeterministicThrottle.UsageSnapshot[] usageSnapshots = NO_SNAPSHOTS;

	public MerkleNetworkContext() {
		/* RuntimeConstructable */
	}

	public MerkleNetworkContext(
			RichInstant consensusTimeOfLastHandledTxn,
			SequenceNumber seqNo,
			ExchangeRates midnightRates
	) {
		this.consensusTimeOfLastHandledTxn = consensusTimeOfLastHandledTxn;
		this.seqNo = seqNo;
		this.midnightRates = midnightRates;
	}

	public MerkleNetworkContext(
			RichInstant consensusTimeOfLastHandledTxn,
			SequenceNumber seqNo,
			ExchangeRates midnightRates,
			DeterministicThrottle.UsageSnapshot[] usageSnapshots,
			RichInstant[] congestionStartPeriods
	) {
		this.consensusTimeOfLastHandledTxn = consensusTimeOfLastHandledTxn;
		this.seqNo = seqNo;
		this.midnightRates = midnightRates;
		this.usageSnapshots = usageSnapshots;
		this.congestionLevelStarts = congestionStartPeriods;
	}

	public void updateSnapshotsFrom(FunctionalityThrottling throttling) {
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
		var congestionStarts = feeMultiplierSource.congestionLevelStarts();
		if (congestionStarts.length == 0) {
			congestionLevelStarts = NO_CONGESTION_STARTS;
		} else {
			congestionLevelStarts = new RichInstant[congestionStarts.length];
			for (int i = 0; i < congestionStarts.length; i++) {
				congestionLevelStarts[i] = RichInstant.fromJava(congestionStarts[i]);
			}
		}
	}

	public void resetFromSavedSnapshots(FunctionalityThrottling throttling) {
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

	public void resetFromSavedCongestionStarts(FeeMultiplierSource feeMultiplierSource) {
		if (congestionLevelStarts.length > 0) {
			var congestionStarts = Arrays.stream(congestionLevelStarts)
							.map(RichInstant::toJava)
							.toArray(Instant[]::new);
			feeMultiplierSource.resetCongestionLevelStarts(congestionStarts);
		}
	}

	public void setConsensusTimeOfLastHandledTxn(Instant consensusTimeOfLastHandledTxn) {
		this.consensusTimeOfLastHandledTxn = fromJava(consensusTimeOfLastHandledTxn);
	}

	public MerkleNetworkContext copy() {
		return new MerkleNetworkContext(
				consensusTimeOfLastHandledTxn,
				seqNo.copy(),
				midnightRates.copy(),
				usageSnapshots,
				congestionLevelStarts);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		consensusTimeOfLastHandledTxn = serdes.readNullableInstant(in);
		seqNo = seqNoSupplier.get();
		seqNo.deserialize(in);
		midnightRates = in.readSerializable(true, ratesSupplier);

		if (version >= RELEASE_0130_VERSION) {
			int numUsageSnapshots = in.readInt();
			if (numUsageSnapshots > 0) {
				usageSnapshots = new DeterministicThrottle.UsageSnapshot[numUsageSnapshots];
				for (int i = 0; i < numUsageSnapshots; i++) {
					var used = in.readLong();
					var lastUsed = serdes.readNullableInstant(in);
					usageSnapshots[i] = new DeterministicThrottle.UsageSnapshot(
							used,
							Optional.ofNullable(lastUsed).map(RichInstant::toJava).orElse(null));
				}
			}

			int numCongestionStarts = in.readInt();
			if (numCongestionStarts > 0) {
				congestionLevelStarts = new RichInstant[numCongestionStarts];
				for (int i = 0; i < numCongestionStarts; i++) {
					congestionLevelStarts[i] = RichInstant.from(in);
				}
			}
		}
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		serdes.writeNullableInstant(consensusTimeOfLastHandledTxn, out);
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
			congestionStart.serialize(out);
		}
	}

	public Instant consensusTimeOfLastHandledTxn() {
		return Optional.ofNullable(consensusTimeOfLastHandledTxn).map(RichInstant::toJava).orElse(null);
	}

	public SequenceNumber seqNo() {
		return seqNo;
	}

	public ExchangeRates midnightRates() {
		return midnightRates;
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
		var sb = new StringBuilder("The network context is,")
				.append("\n  Consensus time of last handled transaction :: ")
				.append(reprOf(consensusTimeOfLastHandledTxn))
				.append("\n  Midnight rate set                          :: ")
				.append(midnightRates.readableRepr())
				.append("\n  Next entity number                         :: ")
				.append(seqNo.current());
		sb.append("\n  Throttle usage snapshots are               ::");
		for (var snapshot : usageSnapshots) {
			sb.append("\n    ").append(snapshot.used())
					.append(" used (last decision time ")
					.append(reprOf(fromJava(snapshot.lastDecisionTime()))).append(")");
		}
		sb.append("\n  Congestion level start times are           ::");
		for (var start : congestionLevelStarts) {
			sb.append("\n    ").append(reprOf(start));
		}
		return sb.toString();
	}

	void setUsageSnapshots(DeterministicThrottle.UsageSnapshot[] usageSnapshots) {
		this.usageSnapshots = usageSnapshots;
	}

	DeterministicThrottle.UsageSnapshot[] usageSnapshots() {
		return usageSnapshots;
	}

	private void reset(List<DeterministicThrottle> throttles) {
		var currUsageSnapshots = throttles.stream()
				.map(DeterministicThrottle::usageSnapshot)
				.collect(toList());
		for (int i = 0, n = usageSnapshots.length; i < n; i++) {
			var savedUsageSnapshot = usageSnapshots[i];
			var throttle = throttles.get(i);
			try {
				throttle.resetUsageTo(savedUsageSnapshot);
			} catch (Exception e) {
				log.warn("Saved usage snapshot #" + (i + 1)
						+ " was not compatible with the corresponding active throttle ("
						+ e.getMessage() + "); not performing a reset!");
				resetUnconditionally(throttles, currUsageSnapshots);
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

	private String reprOf(RichInstant consensusTime) {
		return Optional.ofNullable(consensusTime)
				.map(RichInstant::toJava)
				.map(Object::toString)
				.orElse("<NEVER>");
	}

	RichInstant[] congestionLevelStarts() {
		return congestionLevelStarts;
	}
}
