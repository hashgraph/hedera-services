package com.hedera.services.files.sysfiles;

import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class CurrencyCallbacksTest {
	ExchangeRates curMidnightRates;

	@Mock
	FeeCalculator fees;
	@Mock
	HbarCentExchange exchange;
	@Mock
	Supplier<ExchangeRates> midnightRates;

	CurrencyCallbacks subject;

	@BeforeEach
	void setUp() {
		subject = new CurrencyCallbacks(fees, exchange, midnightRates);
	}

	@Test
	void ratesCbAsExpectedWithExistingMidnightRates() {
		// setup:
		curMidnightRates = new ExchangeRates(
				1, 120, 1_234_567L,
				1, 150, 2_345_678L);
		// and:
		var rates = new ExchangeRates(
				1, 12, 1_234_567L,
				1, 15, 2_345_678L);
		var grpcRates = rates.toGrpc();

		given(midnightRates.get()).willReturn(curMidnightRates);

		// when:
		subject.exchangeRatesCb().accept(grpcRates);

		// then:
		verify(exchange).updateRates(grpcRates);
		assertNotEquals(curMidnightRates, rates);
	}

	@Test
	void ratesCbAsExpectedWithMissingMidnightRates() {
		// setup:
		curMidnightRates = new ExchangeRates();
		// and:
		var rates = new ExchangeRates(
				1, 12, 1_234_567L,
				1, 15, 2_345_678L);
		var grpcRates = rates.toGrpc();

		given(midnightRates.get()).willReturn(curMidnightRates);

		// when:
		subject.exchangeRatesCb().accept(grpcRates);

		// then:
		verify(exchange).updateRates(grpcRates);
		assertEquals(curMidnightRates, rates);
	}

	@Test
	void feesCbJustDelegates() {
		// when:
		subject.feeSchedulesCb().accept(CurrentAndNextFeeSchedule.getDefaultInstance());

		// then:
		verify(fees).init();
	}
}
