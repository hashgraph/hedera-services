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
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.real.DeterministicThrottle;
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
	List<DeterministicThrottle.UsageSnapshot> throttleUsages = Collections.emptyList();

	/* Non-null iff {@code this} is mutable. */
	FunctionalityThrottling throttling;

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
		throw new AssertionError("Not implemented!");
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
		this.consensusTimeOfLastHandledTxn = RichInstant.fromJava(consensusTimeOfLastHandledTxn);
	}

	public MerkleNetworkContext copy() {
		if (throttling == null) {
			throw new IllegalStateException("Copy called on immutable network context " + this);
		}

		var mutableCopy = new MerkleNetworkContext(consensusTimeOfLastHandledTxn, seqNo.copy(), midnightRates.copy());
		var activeThrottles = throttling.activeThrottles();
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
					var snapshot = new DeterministicThrottle.UsageSnapshot(
						in.readLong(),
						in.readLong(),
						RichInstant.from(in).toJava());
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
			out.writeLong(usageSnapshot.capacity());
			RichInstant.fromJava(usageSnapshot.lastDecisionTime()).serialize(out);
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

	FunctionalityThrottling getThrottling() {
		return throttling;
	}

	List<DeterministicThrottle.UsageSnapshot> getThrottleUsages() {
		return throttleUsages;
	}
}
