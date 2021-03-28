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

import com.hedera.services.ServicesState;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.hedera.services.state.submerkle.RichInstant.fromJava;

public class MerkleNetworkContext extends AbstractMerkleLeaf {
	private static final Logger log = LogManager.getLogger(MerkleNetworkContext.class);

	public static final RichInstant UNKNOWN_CONSENSUS_TIME = null;

	static final int PRE_RELEASE_0130_VERSION = 1;
	static final int RELEASE_0130_VERSION = 2;

	static final int MERKLE_VERSION = RELEASE_0130_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8d4aa0f0a968a9f3L;

	static DomainSerdes serdes = new DomainSerdes();
	static Supplier<ExchangeRates> ratesSupplier = ExchangeRates::new;
	static Supplier<SequenceNumber> seqNoSupplier = SequenceNumber::new;

	RichInstant consensusTimeOfLastHandledTxn;
	SequenceNumber seqNo;
	ExchangeRates midnightRates;
	volatile List<DeterministicThrottle.UsageSnapshot> throttleUsages = Collections.emptyList();

	/* Non-null iff {@code this} is mutable. */
	volatile FunctionalityThrottling throttling;

	public MerkleNetworkContext() {
	}

	/**
	 * When the {@code FunctionalityThrottling} constructed based on
	 * system file 0.0.123 changes, the {@code ServicesContext} will
	 * call this method via {@link ServicesState#networkCtx()}.
	 *
	 * @param throttling
	 * 		the throttles to sync with
	 */
	public void syncWithThrottles(FunctionalityThrottling throttling) {
		this.throttling = throttling;
	}

	/**
	 * Called only within {@link ServicesState#genesisInit(Platform, AddressBook)}
	 * or {@link com.hedera.services.ServicesMain#init(Platform, NodeId)}, because
	 * we have no guaranteed way to map saved throttle internals to the new
	 * throttles resulting from a {@code FileUpdate} to 0.0.123.
	 */
	public void updateSyncedThrottlesFromSavedState() {
		if (throttling == null) {
			throw new IllegalStateException("Cannot update throttle snapshots, no throttles are synced!");
		}
		if (throttleUsages.isEmpty()) {
			return;
		}
		var activeThrottles = throttling.allActiveThrottles();
		for (int i = 0, n = throttleUsages.size(); i < n; i++) {
			var savedUsageSnapshot = throttleUsages.get(i);
			var throttle = activeThrottles.get(i);
			throttle.resetUsageTo(savedUsageSnapshot);
		}
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

	public void setConsensusTimeOfLastHandledTxn(Instant consensusTimeOfLastHandledTxn) {
		this.consensusTimeOfLastHandledTxn = fromJava(consensusTimeOfLastHandledTxn);
	}

	public MerkleNetworkContext copy() {
		var mutableCopy = new MerkleNetworkContext(consensusTimeOfLastHandledTxn, seqNo.copy(), midnightRates.copy());
		if (throttling == null) {
			mutableCopy.throttleUsages = new ArrayList<>(throttleUsages);
			return mutableCopy;
		}

		var activeThrottles = throttling.allActiveThrottles();
		int n = activeThrottles.size();
		if (n > 0) {
			throttleUsages = new ArrayList<>();
			activeThrottles.forEach(throttle -> throttleUsages.add(throttle.usageSnapshot()));
		}

		mutableCopy.throttling = throttling;
		this.throttling = null;
		return mutableCopy;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		consensusTimeOfLastHandledTxn = serdes.readNullableInstant(in);
		seqNo = seqNoSupplier.get();
		seqNo.deserialize(in);
		midnightRates = in.readSerializable(true, ratesSupplier);

		if (version >= RELEASE_0130_VERSION) {
			int n = in.readInt();
			if (n > 0) {
				throttleUsages = new ArrayList<>();
				while (n-- > 0) {
					var used = in.readLong();
					var lastUsed = serdes.readNullableInstant(in);
					var snapshot = new DeterministicThrottle.UsageSnapshot(
							used,
							Optional.ofNullable(lastUsed).map(RichInstant::toJava).orElse(null));
					throttleUsages.add(snapshot);
				}
			}
		}
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		if (throttling != null) {
			throw new IllegalStateException("Serialize called on mutable network context " + this);
		}

		serdes.writeNullableInstant(consensusTimeOfLastHandledTxn, out);
		seqNo.serialize(out);
		out.writeSerializable(midnightRates, true);
		/* And also the throttle usage snapshots */
		int n = throttleUsages.size();
		out.writeInt(n);
		for (var usageSnapshot : throttleUsages) {
			out.writeLong(usageSnapshot.used());
			serdes.writeNullableInstant(fromJava(usageSnapshot.lastDecisionTime()), out);
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
		boolean isSynced = throttling != null;
		var header = isSynced ? "The throttle-synced network context is," : "The network context is,";
		var sb = new StringBuilder(header)
				.append("\n  Consensus time of last handled transaction :: ")
				.append(reprOf(consensusTimeOfLastHandledTxn))
				.append("\n  Midnight rate set                          :: ")
				.append(midnightRates.readableRepr())
				.append("\n  Next entity number                         :: ")
				.append(seqNo.current());
		if (isSynced) {
			addActiveThrottleUsageTo(sb);
		} else {
			addSavedUsageSnapshotsTo(sb);
		}
		return sb.toString();
	}

	private void addSavedUsageSnapshotsTo(StringBuilder sb)	{
		sb.append("\n  Throttle usage snapshots were              ::");
		for (var snapshot : throttleUsages) {
			sb.append("\n    ").append(snapshot.used())
					.append(" used (last decision time ")
					.append(reprOf(fromJava(snapshot.lastDecisionTime()))).append(")");
		}
	}

	private void addActiveThrottleUsageTo(StringBuilder sb) {
		sb.append("\n  Usage statistics of active throttles       :: ");
		for (var throttle : throttling.allActiveThrottles()) {
			var cap = throttle.capacity();
			var usage = throttle.usageSnapshot();
			sb.append("\n    ").append(throttle.name()).append(" at ")
					.append(usage.used()).append("/").append(cap)
					.append(" used (last decision time ").append(reprOf(fromJava(usage.lastDecisionTime()))).append(")");
		}
	}

	private String reprOf(RichInstant consensusTime) {
		return Optional.ofNullable(consensusTime)
				.map(RichInstant::toJava)
				.map(Object::toString)
				.orElse("<NEVER>");
	}

	FunctionalityThrottling getThrottling() {
		return throttling;
	}

	List<DeterministicThrottle.UsageSnapshot> getThrottleUsages() {
		return throttleUsages;
	}
}
