// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.services;

import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.signatures.SignatureVerifier;
import com.hedera.node.app.spi.throttle.Throttle;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.InstantSource;
import java.util.function.Supplier;

/**
 * Provides the context for the application.
 * @param instantSource The source of the current instant.
 * @param signatureVerifier The verifier of signatures.
 * @param gossip The gossip interface.
 * @param configSupplier The configuration.
 * @param selfNodeInfoSupplier The supplier of the self-node info
 */
public record AppContextImpl(
        @NonNull InstantSource instantSource,
        @NonNull SignatureVerifier signatureVerifier,
        @NonNull Gossip gossip,
        @NonNull Supplier<Configuration> configSupplier,
        @NonNull Supplier<NodeInfo> selfNodeInfoSupplier,
        @NonNull Supplier<Metrics> metricsSupplier,
        @NonNull Throttle.Factory throttleFactory)
        implements AppContext {}
