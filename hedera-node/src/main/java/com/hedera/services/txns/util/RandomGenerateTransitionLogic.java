package com.hedera.services.txns.util;

import com.hedera.services.ServicesState;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.utils.MiscUtils.asBinaryString;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RANDOM_GENERATE_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class RandomGenerateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(RandomGenerateTransitionLogic.class);

	private final TransactionContext txnCtx;
	private final SideEffectsTracker sideEffectsTracker;
	private final Supplier<RecordsRunningHashLeaf> runningHashLeafSupplier;

	@Inject
	public RandomGenerateTransitionLogic(final TransactionContext txnCtx,
			final SideEffectsTracker sideEffectsTracker,
			final Supplier<RecordsRunningHashLeaf> runningHashLeafSupplier) {
		this.txnCtx = txnCtx;
		this.sideEffectsTracker = sideEffectsTracker;
		this.runningHashLeafSupplier = runningHashLeafSupplier;
	}

	@Override
	public void doStateTransition() {
		final var op = txnCtx.accessor().getTxn().getRandomGenerate();

		// Use n-3 running hash instead of n-1 running hash for processing transactions quickly
		final var nMinus3RunningHash = runningHashLeafSupplier.get().getNMinus3RunningHash();
		if (nMinus3RunningHash == null || nMinus3RunningHash.getHash() == null) {
			log.info("No n-3 record running hash available to generate random number");
			return;
		}

        //generate binary string from the running hash of records
		final var pseudoRandomBytes = nMinus3RunningHash.getHash().getValue();
		var binaryVal = asBinaryString(pseudoRandomBytes);

		final var range = op.getRange();
		if (range > 0) {
			// generate pseudorandom number in the given range
			final var initialBitsValue = Integer.parseUnsignedInt(binaryVal.substring(0, 32), 2);
			int pseudoRandomNumber = Math.abs((int) ((range * (long) initialBitsValue) >>> 32));
			sideEffectsTracker.trackRandomNumber(pseudoRandomNumber);
		} else {
			sideEffectsTracker.trackRandomBitString(binaryVal);
		}

		txnCtx.setStatus(SUCCESS);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasRandomGenerate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	private ResponseCodeEnum validate(final TransactionBody randomGenerateTxn) {
		final var range = randomGenerateTxn.getRandomGenerate().getRange();
		if (range < 0) {
			return INVALID_RANDOM_GENERATE_RANGE;
		}
		return OK;
	}
}
