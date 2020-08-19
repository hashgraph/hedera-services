package com.hedera.services.txns.network;

import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class UncheckedSubmitTransitionLogic implements TransitionLogic {
	private static final Function<TransactionBody, ResponseCodeEnum> SYNTAX_RUBBER_STAMP = ignore -> OK;

	@Override
	public void doStateTransition() {
		/* No-op. */
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasUncheckedSubmit;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_RUBBER_STAMP;
	}
}
