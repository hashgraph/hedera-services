package com.hedera.services.store.contracts.precompile;

import com.esaulpaugh.headlong.abi.BigIntegerType;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.HbarCentExchange;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.Supplier;

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
public class ExchangeRatePrecompiledContract extends AbstractPrecompiledContract {
	private static final String PRECOMPILE_NAME = "ExchangeRate";
	private static final BigIntegerType WORD_DECODER = TypeFactory.create("uint256");

	static final Bytes INVALID_CALL_REVERT_REASON = Bytes.of("Invalid call".getBytes());

	//tinycentsToTinybars(uint256)
	static final int TO_TINYBARS_SELECTOR = 0x2e3cff6a;
	//tinybarsToTinycents(uint256)
	static final int TO_TINYCENTS_SELECTOR = 0x43a88229;

	public static final String EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS = "0x21d4";

	private final HbarCentExchange exchange;
	private final Supplier<Instant> consensusNow;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public ExchangeRatePrecompiledContract(
			final GasCalculator gasCalculator,
			final HbarCentExchange exchange,
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<Instant> consensusNow
	) {
		super(PRECOMPILE_NAME, gasCalculator);
		this.exchange = exchange;
		this.consensusNow = consensusNow;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public Gas gasRequirement(Bytes bytes) {
		return dynamicProperties.exchangeRateGasReq();
	}

	@Override
	public Bytes compute(final Bytes input, final MessageFrame frame) {
		final var output = internalCompute(input);
		if (output == null) {
			frame.setRevertReason(INVALID_CALL_REVERT_REASON);
		}
		return output;
	}

	@Nullable
	private Bytes internalCompute(final Bytes input) {
		try {
			final var selector = input.getInt(0);
			final var amount = biValueFrom(input);
			final var activeRate = exchange.activeRate(consensusNow.get());
			return switch (selector) {
				case TO_TINYBARS_SELECTOR -> padded(
						fromAToB(amount, activeRate.getHbarEquiv(), activeRate.getCentEquiv()));
				case TO_TINYCENTS_SELECTOR -> padded(
						fromAToB(amount, activeRate.getCentEquiv(), activeRate.getHbarEquiv()));
				default -> null;
			};
		} catch (Exception ignore) {
			return null;
		}
	}

	private BigInteger fromAToB(final BigInteger aAmount, final int bEquiv, final int aEquiv) {
		return aAmount.multiply(BigInteger.valueOf(bEquiv)).divide(BigInteger.valueOf(aEquiv));
	}

	private BigInteger biValueFrom(final Bytes input) {
		return WORD_DECODER.decode(input.slice(4).toArrayUnsafe());
	}

	private Bytes padded(final BigInteger result) {
		return Bytes32.leftPad(Bytes.wrap(result.toByteArray()));
	}
}
