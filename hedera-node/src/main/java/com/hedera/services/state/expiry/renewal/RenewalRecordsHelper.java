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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.stream.RecordStreamObject;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.crypto.RunningHash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;

@Singleton
public class RenewalRecordsHelper {
	private static final Logger log = LogManager.getLogger(RenewalRecordsHelper.class);

	private static final Transaction EMPTY_SIGNED_TXN = Transaction.getDefaultInstance();

	private final RecordStreamManager recordStreamManager;
	private final GlobalDynamicProperties dynamicProperties;
	private final Consumer<RunningHash> updateRunningHash;

	private int consensusNanosIncr = 0;
	private Instant cycleStart = null;
	private AccountID funding = null;

	@Inject
	public RenewalRecordsHelper(
			RecordStreamManager recordStreamManager,
			GlobalDynamicProperties dynamicProperties,
			Consumer<RunningHash> updateRunningHash
	) {
		this.updateRunningHash = updateRunningHash;
		this.recordStreamManager = recordStreamManager;
		this.dynamicProperties = dynamicProperties;
	}

	public void beginRenewalCycle(Instant now) {
		cycleStart = now;
		consensusNanosIncr = 1;
		funding = dynamicProperties.fundingAccount();
	}

	public void streamCryptoRemoval(
			PermHashInteger id,
			List<EntityId> tokens,
			List<CurrencyAdjustments> tokenAdjustments
	) {
		assertInCycle();

		final var eventTime = cycleStart.plusNanos(consensusNanosIncr++);
		final var grpcId = id.asGrpcAccountId();
		final var memo = "Entity " + id.asAbbrevString() + " was automatically deleted.";
		final var record = forCrypto(grpcId, eventTime)
				.setMemo(memo)
				.setTokens(tokens)
				.setTokenAdjustments(tokenAdjustments)
				.build();
		stream(record, eventTime);

		log.debug("Streamed crypto removal record {}", record);
	}

	public void streamCryptoRenewal(PermHashInteger id, long fee, long newExpiry) {
		assertInCycle();

		final var eventTime = cycleStart.plusNanos(consensusNanosIncr++);
		final var grpcId = id.asGrpcAccountId();
		final var memo = "Entity " +
				id.asAbbrevString() +
				" was automatically renewed. New expiration time: " +
				newExpiry +
				".";

		final var record = forCrypto(grpcId, eventTime)
				.setMemo(memo)
				.setTransferList(feeXfers(fee, grpcId))
				.setFee(fee)
				.build();
		stream(record, eventTime);

		log.debug("Streamed crypto renewal record {}", record);
	}

	private void stream(ExpirableTxnRecord expiringRecord, Instant at) {
		final var rso = new RecordStreamObject(expiringRecord, EMPTY_SIGNED_TXN, at);
		updateRunningHash.accept(rso.getRunningHash());
		recordStreamManager.addRecordStreamObject(rso);
	}

	public void endRenewalCycle() {
		cycleStart = null;
		consensusNanosIncr = 0;
	}

	private CurrencyAdjustments feeXfers(long amount, AccountID payer) {
		return new CurrencyAdjustments(
				new long[] { amount, -amount },
				List.of(EntityId.fromGrpcAccountId(funding), EntityId.fromGrpcAccountId(payer))
		);
	}

	private ExpirableTxnRecord.Builder forCrypto(AccountID accountId, Instant consensusTime) {
		final var at = RichInstant.fromJava(consensusTime);
		final var id = EntityId.fromGrpcAccountId(accountId);
		final var receipt = new TxnReceipt();
		receipt.setAccountId(id);

		return ExpirableTxnRecord.newBuilder()
				.setTxnId(new TxnId(EntityId.fromGrpcAccountId(accountId), MISSING_INSTANT, false))
				.setReceipt(receipt)
				.setConsensusTime(at);
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
