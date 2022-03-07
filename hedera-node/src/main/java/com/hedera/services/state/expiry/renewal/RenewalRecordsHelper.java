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
import com.hedera.services.state.logic.RecordStreaming;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;

import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.hedera.services.state.submerkle.TxnId.USER_TRANSACTION_NONCE;

@Singleton
public class RenewalRecordsHelper {
	private static final Logger log = LogManager.getLogger(RenewalRecordsHelper.class);

	private static final Transaction EMPTY_SIGNED_TXN = Transaction.getDefaultInstance();

	private final RecordStreaming recordStreaming;
	private final GlobalDynamicProperties dynamicProperties;

	private int consensusNanosIncr = 0;
	private Instant cycleStart = null;
	private AccountID funding = null;

	@Inject
	public RenewalRecordsHelper(
			final RecordStreaming recordStreaming,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.recordStreaming = recordStreaming;
		this.dynamicProperties = dynamicProperties;
	}

	public void beginRenewalCycle(final Instant now) {
		cycleStart = now;
		consensusNanosIncr = 1;
		funding = dynamicProperties.fundingAccount();
	}

	public void streamCryptoRemoval(
			final EntityNum id,
			final List<EntityId> tokens,
			final List<CurrencyAdjustments> tokenAdjustments
	) {
		assertInCycle();

		final var eventTime = cycleStart.plusNanos(consensusNanosIncr++);
		final var grpcId = id.toGrpcAccountId();
		final var memo = "Entity " + id.toIdString() + " was automatically deleted.";
		final var expirableTxnRecord = forCrypto(grpcId, eventTime)
				.setMemo(memo)
				.setTokens(tokens)
				.setTokenAdjustments(tokenAdjustments)
				.build();
		stream(expirableTxnRecord, eventTime);

		log.debug("Streamed crypto removal record {}", expirableTxnRecord);
	}

	public void streamCryptoRenewal(final EntityNum id, final long fee, final long newExpiry) {
		assertInCycle();

		final var eventTime = cycleStart.plusNanos(consensusNanosIncr++);
		final var grpcId = id.toGrpcAccountId();
		final var memo = "Entity " +
				id.toIdString() +
				" was automatically renewed. New expiration time: " +
				newExpiry +
				".";

		final var expirableTxnRecord = forCrypto(grpcId, eventTime)
				.setMemo(memo)
				.setTransferList(feeXfers(fee, grpcId))
				.setFee(fee)
				.build();
		stream(expirableTxnRecord, eventTime);

		log.debug("Streamed crypto renewal record {}", expirableTxnRecord);
	}

	private void stream(final ExpirableTxnRecord expiringRecord, final Instant at) {
		final var rso = new RecordStreamObject(expiringRecord, EMPTY_SIGNED_TXN, at);
		recordStreaming.stream(rso);
	}

	public void endRenewalCycle() {
		cycleStart = null;
		consensusNanosIncr = 0;
	}

	private CurrencyAdjustments feeXfers(final long amount, final AccountID payer) {
		return new CurrencyAdjustments(
				new long[] { amount, -amount },
				List.of(EntityId.fromGrpcAccountId(funding), EntityId.fromGrpcAccountId(payer))
		);
	}

	private ExpirableTxnRecord.Builder forCrypto(final AccountID accountId, final Instant consensusTime) {
		final var at = RichInstant.fromJava(consensusTime);
		final var id = EntityId.fromGrpcAccountId(accountId);
		final var receipt = new TxnReceipt();
		receipt.setAccountId(id);

		/* FUTURE WORK - determine if, and how, the nonce should be altered here. */
		final var txnId = new TxnId(EntityId.fromGrpcAccountId(accountId), MISSING_INSTANT, false, USER_TRANSACTION_NONCE);
		return ExpirableTxnRecord.newBuilder()
				.setTxnId(txnId)
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
