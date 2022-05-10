package com.hedera.services.store.contracts.precompile;

import com.google.common.primitives.Longs;
import com.hedera.services.fees.HbarCentExchange;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Instant;

import static com.hedera.services.calc.OverflowCheckingCalc.tinycentsToTinybars;
import static com.hedera.services.store.contracts.precompile.ExchangeRateContract.GAS_REQUIREMENT;
import static com.hedera.services.store.contracts.precompile.ExchangeRateContract.TO_TINYBARS_SELECTOR;
import static com.hedera.services.store.contracts.precompile.ExchangeRateContract.TO_TINYCENTS_SELECTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ExchangeRateContractTest {
	@Mock
	private MessageFrame frame;
	@Mock
	private HbarCentExchange exchange;
	@Mock
	private GasCalculator gasCalculator;

	private ExchangeRateContract subject;

	@BeforeEach
	void setUp() {
		subject = new ExchangeRateContract(gasCalculator, exchange, () -> now);
	}

	@Test
	void hasExpectedGasRequirement() {
		assertSame(GAS_REQUIREMENT, subject.gasRequirement(argOf(123)));
	}

	@Test
	void convertsPositiveNumberToTinybarsAsExpected() {
		givenRate(someRate);

		final var someInput = unpackedBytesFor(TO_TINYBARS_SELECTOR, someTinycentAmount);
		final var result = subject.compute(someInput, frame);

		assertEquals(unpackedBytesFor(someTinybarAmount), result);
	}

	@Test
	void convertsPositiveNumberToTinycentsAsExpected() {
		givenRate(someRate);

		final var someInput = unpackedBytesFor(TO_TINYCENTS_SELECTOR, someTinybarAmount);
		final var result = subject.compute(someInput, frame);

		assertEquals(unpackedBytesFor(someTinycentAmount), result);
	}

	@Test
	void convertsNegativeNumberToTinybarsAsExpected() {
		givenRate(someRate);

		final var someInput = unpackedBytesFor(TO_TINYBARS_SELECTOR, -someTinycentAmount);
		final var result = subject.compute(someInput, frame);

		assertEquals(unpackedBytesFor(-someTinybarAmount), result);
	}

	@Test
	void convertsZeroToTinybarsAsExpected() {
		givenRate(someRate);

		final var someInput = Bytes.wrap(new byte[] { TO_TINYBARS_SELECTOR });
		final var result = subject.compute(someInput, frame);

		assertEquals(unpackedBytesFor(0), result);
	}

	@Test
	void inputCannotBeOutOfRange() {
		final var outOfRangeInput = Bytes.concatenate(
				Bytes.of(TO_TINYBARS_SELECTOR),
				Bytes.wrap(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TEN).toByteArray()));

		assertNull(subject.compute(outOfRangeInput, frame));
	}

	@Test
	void selectorMustBeValid() {
		final var unrecognizedSelector = Bytes.of(0xab);
		assertNull(subject.compute(unrecognizedSelector, frame));
	}

	private static Bytes unpackedBytesFor(final long amount) {
		return unpackedBytesFor((byte) 0, amount);
	}

	private static Bytes unpackedBytesFor(final byte selector, final long amount) {
		final var word = new byte[32];
		final var bytes = Longs.toByteArray(amount);
		System.arraycopy(bytes, 0, word, 24, 8);
		if (selector != 0) {
			word[23] = selector;
		}
		return Bytes32.wrap(word);
	}

	private static Bytes argOf(final long amount) {
		return Bytes.wrap(Longs.toByteArray(amount));
	}

	private void givenRate(final ExchangeRate rate) {
		given(exchange.activeRate(now)).willReturn(rate);
	}

	private static final int someHbarEquiv = 120;
	private static final int someCentEquiv = 100;
	private static final int someTinycentAmount = 123_456_000;
	private static final ExchangeRate someRate = ExchangeRate.newBuilder()
			.setHbarEquiv(someHbarEquiv)
			.setCentEquiv(someCentEquiv)
			.build();
	private static final long someTinybarAmount = tinycentsToTinybars(someTinycentAmount, someRate);
	private static final Instant now = Instant.ofEpochSecond(1_234_567, 890);
}