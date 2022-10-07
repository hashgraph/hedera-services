/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.sigs;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ServicesState;
import com.hedera.services.sigs.order.SigReqsManager;
import com.hedera.services.txns.prefetch.PrefetchProcessor;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.system.events.Event;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
            final PrefetchProcessor prefetchProcessor) {
        this.engine = engine;
        this.sigReqsManager = sigReqsManager;
        this.expandHandleSpan = expandHandleSpan;
        this.prefetchProcessor = prefetchProcessor;
    }

    public void expandAllSigs(final Event event, final ServicesState sourceState) {
        event.forEachTransaction(
                txn -> {
                    try {
                        final var accessor = expandHandleSpan.track(txn);
                        // Submit the transaction for any pre-handle processing that can be
                        // performed asynchronously; for
                        // example, pre-fetching of contract bytecode; should start before
                        // synchronous signature expansion
                        prefetchProcessor.submit(accessor);
                        sigReqsManager.expandSigs(sourceState, accessor);
                        engine.verifyAsync(txn.getSignatures());
                    } catch (final InvalidProtocolBufferException e) {
                        log.warn("Event contained a non-GRPC transaction", e);
                    } catch (final Exception race) {
                        log.warn(
                                "Unable to expand signatures, will be verified synchronously in"
                                        + " handleTransaction",
                                race);
                    }
                });
    }
}
