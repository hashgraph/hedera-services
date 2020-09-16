package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

public class MerkleNetworkContext extends AbstractMerkleNode implements MerkleLeaf {
	private static final Logger log = LogManager.getLogger(MerkleNetworkContext.class);

	public static final RichInstant UNKNOWN_CONSENSUS_TIME = null;

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8d4aa0f0a968a9f3L;

	static DomainSerdes serdes = new DomainSerdes();
	static Supplier<ExchangeRates> ratesSupplier = ExchangeRates::new;
	static Supplier<SequenceNumber> seqNoSupplier = SequenceNumber::new;

	RichInstant consensusTimeOfLastHandledTxn;
	SequenceNumber seqNo;
	ExchangeRates midnightRates;

	public MerkleNetworkContext() { }

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
		return new MerkleNetworkContext(consensusTimeOfLastHandledTxn, seqNo.copy(), midnightRates.copy());
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		consensusTimeOfLastHandledTxn = serdes.readNullableInstant(in);
		seqNo = seqNoSupplier.get();
		seqNo.deserialize(in);
		midnightRates = in.readSerializable(true, ratesSupplier);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		serdes.writeNullableInstant(consensusTimeOfLastHandledTxn, out);
		seqNo.serialize(out);
		out.writeSerializable(midnightRates, true);
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
}
