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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.serdes.DomainSerdesTest;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.state.expiry.NoopExpiringCreations.NOOP_EXPIRING_CREATIONS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpiringCreationsTest {
	private final int cacheTtl = 180;
	private final long now = 1_234_567L;
	private final long submittingMember = 1L;
	private final long expectedExpiry = now + cacheTtl;

	private final AccountID effPayer = IdUtils.asAccount("0.0.75231");
	private final TransactionRecord record = DomainSerdesTest.recordOne().asGrpc();

	@Mock
	private RecordCache recordCache;
	@Mock
	private ExpiryManager expiries;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	private ExpirableTxnRecord expectedRecord;

	private ExpiringCreations subject;

	@BeforeEach
	void setup() {
		subject = new ExpiringCreations(expiries, dynamicProperties, () -> accounts);

		expectedRecord = ExpirableTxnRecord.fromGprc(record);
		expectedRecord.setExpiry(expectedExpiry);
		expectedRecord.setSubmittingMember(submittingMember);

		subject.setRecordCache(recordCache);
	}

	@Test
	void ifNotCreatingStatePayerRecordsDirectlyTracksWithCache() {
		given(dynamicProperties.shouldKeepRecordsInState()).willReturn(false);
		given(dynamicProperties.cacheRecordsTtl()).willReturn(cacheTtl);

		// when:
		var actual = subject.createExpiringRecord(effPayer, record, now, submittingMember);

		// then:
		verify(recordCache).trackForExpiry(expectedRecord);
		// and:
		verify(expiries, never()).trackRecordInState(effPayer, expectedExpiry);
		// and:
		assertEquals(expectedRecord, actual);
	}

	@Test
	void addsToPayerRecordsAndTracks() {
		// setup:
		final var key = MerkleEntityId.fromAccountId(effPayer);
		final var payerAccount = new MerkleAccount();

		given(accounts.getForModify(key)).willReturn(payerAccount);
		given(dynamicProperties.shouldKeepRecordsInState()).willReturn(true);
		given(dynamicProperties.cacheRecordsTtl()).willReturn(cacheTtl);

		// when:
		var actual = subject.createExpiringRecord(effPayer, record, now, submittingMember);

		// then:
		assertEquals(expectedRecord, actual);
		// and:
		verify(accounts).replace(key, payerAccount);
		verify(expiries).trackRecordInState(effPayer, expectedExpiry);
		assertEquals(expectedRecord, payerAccount.records().peek());
	}

	@Test
	void noopFormDoesNothing() {
		// expect:
		Assertions.assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.createExpiringRecord(
						null, null, 0L, submittingMember));
	}
}
