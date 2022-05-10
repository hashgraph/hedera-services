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
						wrapped(tinycentsToTinybars(input.slice(1).toLong(), exchange.activeRate(consensusNow.get())));
				case TO_TINYCENTS_SELECTOR ->
						wrapped(tinybarsToTinycents(input.slice(1).toLong(), exchange.activeRate(consensusNow.get())));
				default -> null;
			};
		} catch (IllegalArgumentException ignore) {
			return null;
		}
	}

	private Bytes wrapped(final long primitive) {
		return Bytes32.leftPad(Bytes.wrap(Longs.toByteArray(primitive)));
	}
}
