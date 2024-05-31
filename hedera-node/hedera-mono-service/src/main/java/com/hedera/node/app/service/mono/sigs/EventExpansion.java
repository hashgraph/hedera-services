/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.sigs;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.context.StateChildrenProvider;
import com.hedera.node.app.service.mono.sigs.order.SigReqsManager;
import com.hedera.node.app.service.mono.txns.prefetch.PrefetchProcessor;
import com.hedera.node.app.service.mono.txns.span.ExpandHandleSpan;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.Transaction;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class EventExpansion {
    private static final Logger log = LogManager.getLogger(EventExpansion.class);

    private final Cryptography engine;
    private final ExpandHandleSpan expandHandleSpan;
    private final PrefetchProcessor prefetchProcessor;
    private final Provider<SigReqsManager> sigReqsManagerProvider;

    @Inject
    public EventExpansion(
            final Cryptography engine,
            final ExpandHandleSpan expandHandleSpan,
            final PrefetchProcessor prefetchProcessor,
            final Provider<SigReqsManager> sigReqsManagerProvider) {
        this.engine = engine;
        this.expandHandleSpan = expandHandleSpan;
        this.prefetchProcessor = prefetchProcessor;
        this.sigReqsManagerProvider = sigReqsManagerProvider;
    }

    public void expandAllSigs(final Event event, final StateChildrenProvider provider) {
        final var eventSigReqsManager = sigReqsManagerProvider.get();
        event.forEachTransaction(txn -> expandSingle(txn, eventSigReqsManager, provider));
    }

    public void expandSingle(
            final Transaction txn, final SigReqsManager sigReqsManager, final StateChildrenProvider provider) {
        try {
            final var accessor = expandHandleSpan.track(txn);
            // Submit the transaction for any pre-handle processing that can be
            // performed asynchronously; for
            // example, pre-fetching of contract bytecode; should start before
            // synchronous signature expansion
            prefetchProcessor.submit(accessor);
            sigReqsManager.expandSigs(provider, accessor);
            engine.verifySync(accessor.getCryptoSigs());
        } catch (final InvalidProtocolBufferException e) {
            log.warn("Event contained a non-GRPC transaction", e);
        } catch (final Exception race) {
            log.warn("Unable to expand signatures, will be verified synchronously in" + " handleTransaction", race);
        }
    }
}
