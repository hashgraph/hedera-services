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
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MerkleNetworkContext extends AbstractMerkleLeaf {
	private static final Logger log = LogManager.getLogger(MerkleNetworkContext.class);

	public static final RichInstant UNKNOWN_CONSENSUS_TIME = null;

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8d4aa0f0a968a9f3L;

	static DomainSerdes serdes = new DomainSerdes();
	static Supplier<ExchangeRates> ratesSupplier = ExchangeRates::new;
	static Supplier<SequenceNumber> seqNoSupplier = SequenceNumber::new;

	static class ThrottleInternals {
		private final long used;
		private final String name;
		private final Instant lastUsed;

		public ThrottleInternals(long used, String name, Instant lastUsed) {
			this.used = used;
			this.name = name;
			this.lastUsed = lastUsed;
		}

		public long getUsed() {
			return used;
		}

		public String getName() {
			return name;
		}

		public Instant getLastUsed() {
			return lastUsed;
		}
	}

	RichInstant consensusTimeOfLastHandledTxn;
	SequenceNumber seqNo;
	ExchangeRates midnightRates;
	List<ThrottleInternals> throttleInternals = Collections.emptyList();

	/* Exactly one instance of {@code MerkleNetworkContext} (the instance
	associated to the mutable state) will keep a reference to the active
	throttles; and use their snapshots to serialize itself to a save state. */
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
		var mutableCopy = new MerkleNetworkContext(consensusTimeOfLastHandledTxn, seqNo.copy(), midnightRates.copy());

		throttleInternals = throttling.currentThrottles().entrySet().stream()
				.sorted(Comparator.comparing(Map.Entry::getKey))
				.map(entry -> Pair.of(entry.getKey(), entry.getValue().snapshot()))
				.map(pair ->
						new ThrottleInternals(pair.getRight().getUsed(), pair.getLeft(), pair.getRight().getLastUsed()))
				.collect(Collectors.toList());
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
		/* TODO: read the throttle snapshots from the saved network state */
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		serdes.writeNullableInstant(consensusTimeOfLastHandledTxn, out);
		seqNo.serialize(out);
		out.writeSerializable(midnightRates, true);

		/* TODO - behave differently if we aren't the special network context in the mutable state */
		var current = throttling.currentThrottles();
		out.writeInt(current.size());
		current.entrySet().stream()
				.sorted(Comparator.comparing(Map.Entry::getKey))
				.forEach(entry -> {
					try {
						out.writeNormalisedString(entry.getKey());
						var snapshot = entry.getValue().snapshot();
						out.writeLong(snapshot.getUsed());
						RichInstant.fromJava(snapshot.getLastUsed()).serialize(out);
					} catch (IOException e) {
						throw new AssertionError("Not implemented!");
					}
				});
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

	public FunctionalityThrottling getThrottling() {
		return throttling;
	}

	public List<ThrottleInternals> getThrottleInternals() {
		return throttleInternals;
	}
}
