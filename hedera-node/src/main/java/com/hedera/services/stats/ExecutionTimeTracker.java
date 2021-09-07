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
		throw new AssertionError("Not implemented");
	}

	public boolean hasExecNanosFor(TransactionID txnId) {
		if (shouldNoop) {
			return false;
		}
		throw new AssertionError("Not implemented");
	}

	public long getExecNanosFor(TransactionID txnId) {
		if (shouldNoop) {
			throw new IllegalStateException();
		}
		throw new AssertionError("Not implemented");
	}

	/* --- Only used by unit tests --- */
	boolean isShouldNoop() {
		return shouldNoop;
	}

	Cache<TransactionID, Long> getExecNanosCache() {
		return execNanosCache;
	}
}
