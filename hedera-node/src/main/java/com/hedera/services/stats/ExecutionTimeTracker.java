package com.hedera.services.stats;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hederahashgraph.api.proto.java.TransactionID;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExecutionTimeTracker {
	private final boolean shouldNoop;
	private final TransactionContext txnCtx;

	private final Cache<TransactionID, Long> execNanosCache;

	private long startTime;

	@Inject
	public ExecutionTimeTracker(TransactionContext txnCtx, NodeLocalProperties properties) {
		this.txnCtx = txnCtx;

		final var timesToTrack = properties.numExecutionTimesToTrack();
		if (shouldNoop = (timesToTrack == 0)) {
			execNanosCache = null;
		} else {
			execNanosCache = CacheBuilder
					.newBuilder()
					.maximumSize(properties.numExecutionTimesToTrack())
					.build();
		}
	}

	public void start() {
		if (shouldNoop) {
			return;
		}
		startTime = System.nanoTime();
	}

	public void stop() {
		if (shouldNoop) {
			return;
		}
		final var execTime = System.nanoTime() - startTime;
		final var txnId = txnCtx.accessor().getTxnId();
		execNanosCache.put(txnId, execTime);
	}

	public Long getExecNanosIfPresentFor(TransactionID txnId) {
		return shouldNoop ? null : execNanosCache.getIfPresent(txnId);
	}

	/* --- Only used by unit tests --- */
	boolean isShouldNoop() {
		return shouldNoop;
	}

	Cache<TransactionID, Long> getExecNanosCache() {
		return execNanosCache;
	}
}
