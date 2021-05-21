package com.hedera.services.txns;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.utils.TxnAccessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class TransitionRunner {
	private static final Logger log = LogManager.getLogger(TransitionRunner.class);

	private final TransactionContext txnCtx;
	private final TransitionLogicLookup lookup;

	public TransitionRunner(TransactionContext txnCtx, TransitionLogicLookup lookup) {
		this.txnCtx = txnCtx;
		this.lookup = lookup;
	}

	/**
	 * Tries to find and run transition logic for the transaction wrapped by the
	 * given accessor.
	 *
	 * @param accessor the transaction accessor
	 * @return true if the logic was run to completion
	 */
	public boolean tryTransition(@NotNull TxnAccessor accessor) {
		final var txn = accessor.getTxn();
		final var logic = lookup.lookupFor(accessor.getFunction(), txn);
		if (logic.isEmpty()) {
			log.warn("Transaction w/o applicable transition logic at consensus :: {}", accessor::getSignedTxn4Log);
			txnCtx.setStatus(FAIL_INVALID);
			return false;
		} else {
			final var transition = logic.get();
			final var validity = transition.semanticCheck().apply(txn);
			if (validity != OK) {
				txnCtx.setStatus(validity);
				return false;
			}
			try {
				transition.doStateTransition();
				txnCtx.setStatus(SUCCESS);
			} catch (InvalidTransactionException ite) {
				final var code = ite.getResponseCode();
				txnCtx.setStatus(code);
				if (code == FAIL_INVALID) {
					log.warn("Avoidable failure in transition logic for {}", accessor.getSignedTxn4Log(), ite);
				}
			}
			return true;
		}
	}
}
