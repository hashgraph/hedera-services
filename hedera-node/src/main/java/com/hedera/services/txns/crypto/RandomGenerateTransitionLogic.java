package com.hedera.services.txns.crypto;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RANDOM_GENERATE_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class RandomGenerateTransitionLogic implements TransitionLogic {
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
		final var range = op.getRange();
		var pseudoRandomBytes = runningHashLeafSupplier.get().getRunningHash();
		if (range > 0) {

		}

		sideEffectsTracker.trackPseudoRandomBytes(pseudoRandomBytes);
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
