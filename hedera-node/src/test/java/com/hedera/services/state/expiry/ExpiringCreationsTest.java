package com.hedera.services.state.expiry;

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.serdes.DomainSerdesTest;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.hedera.services.state.expiry.NoopExpiringCreations.NOOP_EXPIRING_CREATIONS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;

class ExpiringCreationsTest {
	int historyTtl = 90_000, cacheTtl = 180;
	long now = 1_234_567L;

	AccountID effPayer = IdUtils.asAccount("0.0.13257");
	TransactionRecord record = DomainSerdesTest.recordOne().asGrpc();

	HederaLedger ledger;
	ExpiryManager expiries;
	PropertySource properties;
	ExpiringCreations subject;

	@BeforeEach
	public void setup() {
		ledger = mock(HederaLedger.class);
		expiries = mock(ExpiryManager.class);
		properties = mock(PropertySource.class);
		given(properties.getIntProperty("ledger.records.ttl")).willReturn(historyTtl);
		given(properties.getIntProperty("cache.records.ttl")).willReturn(cacheTtl);

		subject = new ExpiringCreations(expiries, properties);
		subject.setLedger(ledger);
	}

	@Test
	public void noopFormDoesNothing() {
		// expect:
		Assertions.assertDoesNotThrow(() ->
				NOOP_EXPIRING_CREATIONS.setLedger(null));
		Assertions.assertDoesNotThrow(() ->
				NOOP_EXPIRING_CREATIONS.createExpiringPayerRecord(null, null, 0L));
		Assertions.assertDoesNotThrow(() ->
				NOOP_EXPIRING_CREATIONS.createExpiringHistoricalRecord(null, null, 0L));
	}

	@Test
	public void reusesLastExpirableIfSameRecord() {
		// setup:
		ArgumentCaptor<ExpirableTxnRecord> captor = ArgumentCaptor.forClass(ExpirableTxnRecord.class);

		// when:
		subject.createExpiringHistoricalRecord(effPayer, record, now);
		subject.createExpiringHistoricalRecord(effPayer, record, now);

		// then:
		verify(ledger, times(2)).addRecord(argThat(effPayer::equals), captor.capture());
		// and:
		Assertions.assertSame(captor.getAllValues().get(0), captor.getAllValues().get(1));
	}

	@Test
	public void addsToPayerRecordsAndTracks() {
		// setup:
		ArgumentCaptor<ExpirableTxnRecord> captor = ArgumentCaptor.forClass(ExpirableTxnRecord.class);

		// given:
		long expectedExpiry = now + cacheTtl;
		// and:
		var expected = ExpirableTxnRecord.fromGprc(record);
		expected.setExpiry(expectedExpiry);

		// when:
		var actual = subject.createExpiringPayerRecord(effPayer, record, now);

		// then:
		verify(ledger).addPayerRecord(argThat(effPayer::equals), captor.capture());
		// and:
		assertEquals(expectedExpiry, captor.getValue().getExpiry());
		Assertions.assertEquals(expected, actual);
		// and:
		verify(expiries).trackPayerRecord(effPayer, expectedExpiry);
	}

	@Test
	public void addsToHistoryRecordsAndTracks() {
		// setup:
		ArgumentCaptor<ExpirableTxnRecord> captor = ArgumentCaptor.forClass(ExpirableTxnRecord.class);

		// given:
		long expectedExpiry = now + historyTtl;

		// when:
		subject.createExpiringHistoricalRecord(effPayer, record, now);

		// then:
		verify(ledger).addRecord(argThat(effPayer::equals), captor.capture());
		// and:
		assertEquals(expectedExpiry, captor.getValue().getExpiry());
		// and:
		verify(expiries).trackHistoricalRecord(effPayer, expectedExpiry);
	}
}