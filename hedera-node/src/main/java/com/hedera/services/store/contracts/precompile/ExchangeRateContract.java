package com.hedera.services.store.contracts.precompile;

import com.google.common.primitives.Longs;
import com.hedera.services.fees.HbarCentExchange;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Supplier;

import static com.hedera.services.calc.OverflowCheckingCalc.tinybarsToTinycents;
import static com.hedera.services.calc.OverflowCheckingCalc.tinycentsToTinybars;

/**
 * System contract to interconvert tinybars and tinycents at the active exchange rate.
 * The ABI consists of 1 to 9 packed bytes where,
 * <ol>
 *    <li>The first byte is either {@code 0xbb}, when converting to tinybars; or
 *    {@code 0xcc}, when converting to tinycents.</li>
 *    <li>The remaining 0 to 8 bytes are (logically) left-padded with zeros to
 *    form an eight-byte big-endian representation of a {@code long} value.</li>
 * </ol>
 *
 * <p> When the input {@code Bytes} take this form, <i>and</i> the given value can
 * be converted to the requested denomination without over-flowing an eight-byte
 * value, the contract returns the conversion result. Otherwise, it returns null.
 */
@Singleton
public class ExchangeRateContract extends AbstractPrecompiledContract {
	private static final String PRECOMPILE_NAME = "ExchangeRate";
	static final Gas GAS_REQUIREMENT = Gas.of(100L);
	static final byte TO_TINYBARS_SELECTOR = (byte) 0xbb;
	static final byte TO_TINYCENTS_SELECTOR = (byte) 0xcc;
	public static final String EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS = "0x21d4";

	private final HbarCentExchange exchange;
	private final Supplier<Instant> consensusNow;

	@Inject
	public ExchangeRateContract(
			final GasCalculator gasCalculator,
			final HbarCentExchange exchange,
			final Supplier<Instant> consensusNow
	) {
		super(PRECOMPILE_NAME, gasCalculator);
		this.exchange = exchange;
		this.consensusNow = consensusNow;
	}

	@Override
	public Gas gasRequirement(Bytes bytes) {
		return GAS_REQUIREMENT;
	}

	@Override
	public Bytes compute(final Bytes bytes, final MessageFrame messageFrame) {
		try {
			final var input = bytes.trimLeadingZeros();
			return switch (input.get(0)) {
				case TO_TINYBARS_SELECTOR ->
						padded(tinycentsToTinybars(input.slice(1).toLong(), exchange.activeRate(consensusNow.get())));
				case TO_TINYCENTS_SELECTOR ->
						padded(tinybarsToTinycents(input.slice(1).toLong(), exchange.activeRate(consensusNow.get())));
				default -> null;
			};
		} catch (IllegalArgumentException ignore) {
			return null;
		}
	}

	private Bytes padded(final long primitive) {
		return Bytes32.leftPad(Bytes.wrap(Longs.toByteArray(primitive)));
	}
}
