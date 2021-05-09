package com.hedera.services.state.expiry.renewal;

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

import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.stream.RecordStreamObject;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.utils.MiscUtils.asTimestamp;

public class RenewalRecordsHelper {
	private static final Logger log = LogManager.getLogger(RenewalRecordsHelper.class);

	private static final Transaction EMPTY_SIGNED_TXN = Transaction.getDefaultInstance();

	private final ServicesContext ctx;
	private final RecordStreamManager recordStreamManager;
	private final GlobalDynamicProperties dynamicProperties;

	private int consensusNanosIncr = 0;
	private Instant cycleStart = null;
	private AccountID funding = null;

	public RenewalRecordsHelper(
			ServicesContext ctx,
			RecordStreamManager recordStreamManager,
			GlobalDynamicProperties dynamicProperties
	) {
		this.ctx = ctx;
		this.recordStreamManager = recordStreamManager;
		this.dynamicProperties = dynamicProperties;
	}

	public void beginRenewalCycle(Instant now) {
		cycleStart = now;
		consensusNanosIncr = 1;
		funding = dynamicProperties.fundingAccount();
	}

	public void streamCryptoRemoval(MerkleEntityId id, List<TokenTransferList> tokensDisplaced) {
		assertInCycle();

		final var eventTime = cycleStart.plusNanos(consensusNanosIncr++);
		final var grpcId = id.toAccountId();
		final var memo = new StringBuilder("Entity ")
				.append(id.toAbbrevString())
				.append(" was automatically deleted.")
				.toString();

		final var record = forCrypto(grpcId, eventTime)
				.setMemo(memo)
				.addAllTokenTransferLists(tokensDisplaced)
				.build();
		stream(record, eventTime);

		log.debug("Streamed crypto removal record {} @ {}", record, eventTime);
	}

	public void streamCryptoRenewal(MerkleEntityId id, long fee, long newExpiry) {
		assertInCycle();

		final var eventTime = cycleStart.plusNanos(consensusNanosIncr++);
		final var grpcId = id.toAccountId();
		final var memo = new StringBuilder("Entity ")
				.append(id.toAbbrevString())
				.append(" was automatically renewed. New expiration time: ")
				.append(newExpiry)
				.append(".")
				.toString();

		final var record = forCrypto(grpcId, eventTime)
				.setMemo(memo)
				.setTransferList(feeXfers(fee, grpcId))
				.setTransactionFee(fee)
				.build();
		stream(record, eventTime);

		log.debug("Streamed crypto renewal record {} @ {}", record, eventTime);
	}

	private void stream(TransactionRecord record, Instant at) {
		final var rso = new RecordStreamObject(record, EMPTY_SIGNED_TXN, at);
		ctx.updateRecordRunningHash(rso.getRunningHash());
		recordStreamManager.addRecordStreamObject(rso);
	}

	public void endRenewalCycle() {
		cycleStart = null;
		consensusNanosIncr = 0;
	}

	private TransferList feeXfers(long amount, AccountID payer) {
		return TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAmount(amount).setAccountID(funding))
				.addAccountAmounts(AccountAmount.newBuilder().setAmount(-amount).setAccountID(payer))
				.build();
	}

	private TransactionRecord.Builder forCrypto(AccountID id, Instant at) {
		return TransactionRecord.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setAccountID(id))
				.setReceipt(TransactionReceipt.newBuilder()
						.setAccountID(id))
				.setConsensusTimestamp(asTimestamp(at));
	}

	int getConsensusNanosIncr() {
		return consensusNanosIncr;
	}

	Instant getCycleStart() {
		return cycleStart;
	}

	private void assertInCycle() {
		if (cycleStart == null) {
			throw new IllegalStateException("Cannot stream records if not in a renewal cycle!");
		}
	}
}
