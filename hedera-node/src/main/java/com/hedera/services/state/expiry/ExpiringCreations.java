package com.hedera.services.state.expiry;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import java.util.function.Function;
import java.util.function.ObjLongConsumer;
import java.util.function.ToLongBiFunction;

public class ExpiringCreations implements EntityCreator {
	private TransactionRecord currentRecord = null;
	private ExpirableTxnRecord currentExpirableRecord = null;

	private HederaLedger ledger;
	private RecordCache recordCache;
	private final ExpiryManager expiries;
	private final PropertySource properties;
	private final GlobalDynamicProperties dynamicProperties;

	private ObjLongConsumer<AccountID> payerTracker;
	private ObjLongConsumer<AccountID> historicalTracker;
	private ToLongBiFunction<AccountID, ExpirableTxnRecord> payerRecordFn;
	private ToLongBiFunction<AccountID, ExpirableTxnRecord> historicalRecordFn;

	public ExpiringCreations(
			ExpiryManager expiries,
			PropertySource properties,
			GlobalDynamicProperties dynamicProperties
	) {
		this.expiries = expiries;
		this.properties = properties;
		this.dynamicProperties = dynamicProperties;

		payerTracker = this::trackPayerRecord;
		historicalTracker = expiries::trackHistoricalRecord;
	}

	@Override
	public void setRecordCache(RecordCache recordCache) {
		this.recordCache = recordCache;
	}

	@Override
	public void setLedger(HederaLedger ledger) {
		this.ledger = ledger;
		payerRecordFn = this::updatePayerRecord;
		historicalRecordFn = ledger::addRecord;
	}

	private void trackPayerRecord(AccountID effectivePayer, long expiry) {
		if (dynamicProperties.shouldCreatePayerRecords()) {
			expiries.trackPayerRecord(effectivePayer, expiry);
		}
	}

	private long updatePayerRecord(AccountID id, ExpirableTxnRecord record) {
		if (dynamicProperties.shouldCreatePayerRecords()) {
			return ledger.addPayerRecord(id, record);
		} else {
			recordCache.trackForExpiry(record);
			return 0L;
		}
	}

	@Override
	public ExpirableTxnRecord createExpiringPayerRecord(
			AccountID id,
			TransactionRecord record,
			long now,
			long submittingMember
	) {
		return createExpiringRecord(
				now + dynamicProperties.cacheRecordsTtl(),
				submittingMember,
				id,
				record,
				payerTracker,
				payerRecordFn,
				ExpirableTxnRecord::fromGprc);
	}

	@Override
	public void createExpiringHistoricalRecord(
			AccountID id,
			TransactionRecord record,
			long now,
			long submittingMember
	) {
		createExpiringRecord(
				now + properties.getIntProperty("ledger.records.ttl"),
				submittingMember,
				id,
				record,
				historicalTracker,
				historicalRecordFn,
				this::expirableRecordWithReuse);
	}

	private ExpirableTxnRecord expirableRecordWithReuse(TransactionRecord record) {
		if (record != currentRecord) {
			currentExpirableRecord = ExpirableTxnRecord.fromGprc(record);
			currentRecord = record;
		}
		return currentExpirableRecord;
	}

	private ExpirableTxnRecord createExpiringRecord(
			long expiry,
			long submittingMember,
			AccountID id,
			TransactionRecord record,
			ObjLongConsumer<AccountID> tracker,
			ToLongBiFunction<AccountID, ExpirableTxnRecord> adder,
			Function<TransactionRecord, ExpirableTxnRecord> fromGrpc
	) {
		var expiringRecord = fromGrpc.apply(record);
		expiringRecord.setExpiry(expiry);
		expiringRecord.setSubmittingMember(submittingMember);
		adder.applyAsLong(id, expiringRecord);
		tracker.accept(id, expiry);
		return expiringRecord;
	}
}
