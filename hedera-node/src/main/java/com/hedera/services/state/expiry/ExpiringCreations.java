package com.hedera.services.state.expiry;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.fcmap.FCMap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.state.submerkle.EntityId.fromGrpcScheduleId;
import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

public class ExpiringCreations implements EntityCreator {
	private RecordCache recordCache;

	private final ExpiryManager expiries;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;
	private final ServicesContext ctx;

	public ExpiringCreations(
			ExpiryManager expiries,
			GlobalDynamicProperties dynamicProperties,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			ServicesContext ctx) {
		this.accounts = accounts;
		this.expiries = expiries;
		this.dynamicProperties = dynamicProperties;
		this.ctx = ctx;
	}

	@Override
	public void setRecordCache(RecordCache recordCache) {
		this.recordCache = recordCache;
	}

	@Override
	public ExpirableTxnRecord saveExpiringRecord(
			AccountID payer,
			ExpirableTxnRecord record,
			long now,
			long submittingMember
	) {
		long expiry = now + dynamicProperties.cacheRecordsTtl();
		record.setExpiry(expiry);
		record.setSubmittingMember(submittingMember);

		if (dynamicProperties.shouldKeepRecordsInState()) {
			final var key = MerkleEntityId.fromAccountId(payer);
			addToState(key, record);
			expiries.trackRecordInState(payer, record.getExpiry());
		} else {
			recordCache.trackForExpiry(record);
		}

		return record;
	}

	private void addToState(MerkleEntityId key, ExpirableTxnRecord record) {
		final var currentAccounts = accounts.get();
		final var mutableAccount = currentAccounts.getForModify(key);
		mutableAccount.records().offer(record);
		currentAccounts.replace(key, mutableAccount);
	}

	@Override
	public ExpirableTxnRecord.Builder buildExpiringRecord(
			long otherNonThresholdFees,
			ByteString hash,
			TxnAccessor accessor,
			Timestamp consensusTimestamp,
			TransactionReceipt receipt) {

		long amount = ctx.charging().totalFeesChargedToPayer() + otherNonThresholdFees;
		TransferList transfersList = ctx.ledger().netTransfersInTxn();
		List<TokenTransferList> tokenTransferList = ctx.ledger().netTokenTransfersInTxn();

		var builder = ExpirableTxnRecord.newBuilder()
				.setReceipt(TxnReceipt.fromGrpc(receipt))
				.setTxnHash(hash.toByteArray())
				.setTxnId(TxnId.fromGrpc(accessor.getTxnId()))
				.setConsensusTimestamp(RichInstant.fromGrpc(consensusTimestamp))
				.setMemo(accessor.getTxn().getMemo())
				.setFee(amount)
				.setTransferList(!transfersList.getAccountAmountsList().isEmpty() ? CurrencyAdjustments.fromGrpc(
						transfersList) : null)
				.setScheduleRef(accessor.isTriggeredTxn() ? fromGrpcScheduleId(accessor.getScheduleRef()) : null);
		builder = setTokensAndTokenAdjustments(builder, tokenTransferList);
		return builder;
	}

	private ExpirableTxnRecord.Builder setTokensAndTokenAdjustments(ExpirableTxnRecord.Builder builder,
			List<TokenTransferList> tokenTransferList) {
		List<EntityId> tokens = new ArrayList<>();
		List<CurrencyAdjustments> tokenAdjustments = new ArrayList<>();
		if (tokenTransferList.size() > 0) {
			for (TokenTransferList tokenTransfers : tokenTransferList) {
				tokens.add(EntityId.fromGrpcTokenId(tokenTransfers.getToken()));
				tokenAdjustments.add(CurrencyAdjustments.fromGrpc(tokenTransfers.getTransfersList()));
			}
		}
		builder.setTokens(tokens)
				.setTokenAdjustments(tokenAdjustments);
		return builder;
	}

	@Override
	public ExpirableTxnRecord.Builder buildFailedExpiringRecord(TxnAccessor accessor, Instant consensusTimestamp) {
		var txnId = accessor.getTxnId();

		return ExpirableTxnRecord.newBuilder()
				.setTxnId(TxnId.fromGrpc(txnId))
				.setReceipt(TxnReceipt.fromGrpc(TransactionReceipt.newBuilder().setStatus(FAIL_INVALID).build()))
				.setMemo(accessor.getTxn().getMemo())
				.setTxnHash(accessor.getHash().toByteArray())
				.setConsensusTimestamp(RichInstant.fromGrpc(asTimestamp(consensusTimestamp)))
				.setScheduleRef(accessor.isTriggeredTxn() ? fromGrpcScheduleId(accessor.getScheduleRef()) : null);
	}
}
