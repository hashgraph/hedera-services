package com.hedera.services.sigs;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.sigs.order.SigReqsManager;
import com.hedera.services.txns.prefetch.PrefetchProcessor;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.system.events.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EventExpansion {
	private static final Logger log = LogManager.getLogger(EventExpansion.class);

	private final Cryptography engine;
	private final SigReqsManager sigReqsManager;
	private final ExpandHandleSpan expandHandleSpan;
	private final PrefetchProcessor prefetchProcessor;

	@Inject
	public EventExpansion(
			final Cryptography engine,
			final SigReqsManager sigReqsManager,
			final ExpandHandleSpan expandHandleSpan,
			final PrefetchProcessor prefetchProcessor
	) {
		this.engine = engine;
		this.sigReqsManager = sigReqsManager;
		this.expandHandleSpan = expandHandleSpan;
		this.prefetchProcessor = prefetchProcessor;
	}

	public void expandAllSigs(final Event event) {
		event.forEachTransaction(txn -> {
			try {
				final var accessor = expandHandleSpan.track(txn);
				// Submit the transaction for any pre-handle processing that can be performed asynchronously; for
				// example, pre-fetching of contract bytecode; should start before synchronous signature expansion
				prefetchProcessor.submit(accessor);
				sigReqsManager.expandSigsInto(accessor);
				engine.verifyAsync(txn.getSignatures());
			} catch (final InvalidProtocolBufferException e) {
				log.warn("Event contained a non-GRPC transaction", e);
			} catch (final Exception race) {
				log.warn("Unable to expand signatures, will be verified synchronously in handleTransaction", race);
			}
		});
	}
}
