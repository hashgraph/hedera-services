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
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.state.logic.RecordStreaming;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;

import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.hedera.services.state.submerkle.TxnId.USER_TRANSACTION_NONCE;
import static com.hedera.services.utils.MiscUtils.synthFromBody;

@Singleton
public class RenewalRecordsHelper {
	private final RecordStreaming recordStreaming;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final GlobalDynamicProperties dynamicProperties;
	private final ConsensusTimeTracker consensusTimeTracker;

	private boolean inCycle;
	private AccountID funding = null;

	@Inject
	public RenewalRecordsHelper(
			final RecordStreaming recordStreaming,
			final SyntheticTxnFactory syntheticTxnFactory,
			final GlobalDynamicProperties dynamicProperties,
			final ConsensusTimeTracker consensusTimeTracker
	) {
		this.recordStreaming = recordStreaming;
		this.dynamicProperties = dynamicProperties;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.consensusTimeTracker = consensusTimeTracker;
	}

	public void beginRenewalCycle() {
		inCycle = true;
		funding = dynamicProperties.fundingAccount();
	}

	public void streamCryptoRemoval(
			final EntityNum entityNum,
			final List<EntityId> tokens,
			final List<CurrencyAdjustments> tokenAdjustments
	) {
		assertInCycle();
		final var eventTime = consensusTimeTracker.nextStandaloneRecordTime();
		final var grpcId = entityNum.toGrpcAccountId();
		final var memo = "Account " + entityNum.toIdString() + " was automatically deleted.";
		final var expirableTxnRecord = forTouchedAccount(grpcId, eventTime)
				.setMemo(memo)
				.setTokens(tokens)
				.setTokenAdjustments(tokenAdjustments)
				.build();
		final var synthBody = syntheticTxnFactory.synthAccountAutoRemove(entityNum);
		stream(expirableTxnRecord, synthBody, eventTime);
	}

	public void streamCryptoRenewal(
			final EntityNum entityNum,
			final long fee,
			final long newExpiry,
			final boolean isContract,
			final EntityNum payerForAutoRenew
	) {
		assertInCycle();

		final var eventTime = consensusTimeTracker.nextStandaloneRecordTime();
		final var grpcId = entityNum.toGrpcAccountId();
		final var payerId = payerForAutoRenew.toGrpcAccountId();
		final var memo = (isContract ? "Contract " : "Account ") +
				entityNum.toIdString() +
				" was automatically renewed. New expiration time: " +
				newExpiry +
				".";
		final var synthBody = isContract
				? syntheticTxnFactory.synthContractAutoRenew(entityNum, newExpiry, payerId)
				: syntheticTxnFactory.synthAccountAutoRenew(entityNum, newExpiry);
		final var expirableTxnRecord = forTouchedAccount(grpcId, eventTime)
				.setMemo(memo)
				.setHbarAdjustments(feeXfers(fee, payerId))
				.setFee(fee)
				.build();
		stream(expirableTxnRecord, synthBody, eventTime);
	}

	private void stream(
			final ExpirableTxnRecord expiringRecord,
			final TransactionBody.Builder synthBody,
			final Instant at
	) {
		final var rso = new RecordStreamObject(expiringRecord, synthFromBody(synthBody.build()), at);
		recordStreaming.streamSystemRecord(rso);
	}

	public void endRenewalCycle() {
		inCycle = false;
	}

	private CurrencyAdjustments feeXfers(final long amount, final AccountID payer) {
		return new CurrencyAdjustments(
				new long[] { amount, -amount },
				new long[] { funding.getAccountNum(), payer.getAccountNum() }
		);
	}

	private ExpirableTxnRecord.Builder forTouchedAccount(final AccountID accountId, final Instant consensusTime) {
		final var at = RichInstant.fromJava(consensusTime);
		final var id = EntityId.fromGrpcAccountId(accountId);
		final var receipt = new TxnReceipt();
		receipt.setAccountId(id);

		final var txnId = new TxnId(
				EntityId.fromGrpcAccountId(accountId), MISSING_INSTANT, false, USER_TRANSACTION_NONCE);
		return ExpirableTxnRecord.newBuilder()
				.setTxnId(txnId)
				.setReceipt(receipt)
				.setConsensusTime(at);
	}

	boolean isInCycle() {
		return inCycle;
	}

	private void assertInCycle() {
		if (!inCycle) {
			throw new IllegalStateException("Cannot stream records if not in a renewal cycle!");
		}
	}
}
