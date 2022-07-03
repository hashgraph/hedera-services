package com.hedera.services.txns.util;

import com.google.common.math.LongMath;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.Hash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PRNG_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

@Singleton
public class PrngLogic {
	private static final Logger log = LogManager.getLogger(PrngLogic.class);

	public static final byte[] MISSING_BYTES = new byte[0];
	private final Supplier<RecordsRunningHashLeaf> runningHashLeafSupplier;
	private final SideEffectsTracker sideEffectsTracker;
	private final GlobalDynamicProperties properties;

	@Inject
	public PrngLogic(
			final GlobalDynamicProperties properties,
			final Supplier<RecordsRunningHashLeaf> runningHashLeafSupplier,
			final SideEffectsTracker sideEffectsTracker
	) {
		this.properties = properties;
		this.runningHashLeafSupplier = runningHashLeafSupplier;
		this.sideEffectsTracker = sideEffectsTracker;
	}

	public void generatePseudoRandom(final int range) {
		if (!properties.isPrngEnabled()) {
			return;
		}

		final byte[] pseudoRandomBytes = getNMinus3RunningHashBytes();
		if (pseudoRandomBytes == null || pseudoRandomBytes.length == 0) {
			return;
		}
		if (range > 0) {
			// generate pseudorandom number in the given range
			final int pseudoRandomNumber = randomNumFromBytes(pseudoRandomBytes, range);
			sideEffectsTracker.trackRandomNumber(pseudoRandomNumber);
		} else {
			sideEffectsTracker.trackRandomBytes(pseudoRandomBytes);
		}
	}

	public ResponseCodeEnum validateSemantics(final TransactionBody prngTxn) {
		final var range = prngTxn.getPrng().getRange();
		if (range < 0) {
			return INVALID_PRNG_RANGE;
		}
		return OK;
	}

	public final int randomNumFromBytes(final byte[] pseudoRandomBytes, final int range) {
		final var initialBitsValue = ByteBuffer.wrap(pseudoRandomBytes, 0, 4).getInt();
		return LongMath.mod(initialBitsValue, range);
	}

	public final byte[] getNMinus3RunningHashBytes() {
		Hash nMinus3RunningHash;
		try {
			// Use n-3 running hash instead of n-1 running hash for processing transactions quickly
			nMinus3RunningHash = runningHashLeafSupplier.get().nMinusThreeRunningHash();
			if (nMinus3RunningHash == null) {
				log.info("No n-3 record running hash available to generate random number");
				return MISSING_BYTES;
			}
			//generate binary string from the running hash of records
			return nMinus3RunningHash.getValue();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted when computing n-3 running hash");
		}
	}
}
