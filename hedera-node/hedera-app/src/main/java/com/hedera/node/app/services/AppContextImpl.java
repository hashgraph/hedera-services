// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.services;

import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.fees.FeeCharging;
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
 *
 * @param instantSource The source of the current instant.
 * @param signatureVerifier The verifier of signatures.
 * @param gossip The gossip interface.
 * @param configSupplier supplies the app's configuration
 * @param selfNodeInfoSupplier supplies the app's self-node info
 * @param metricsSupplier supplies the app's metrics instance
 * @param feeChargingSupplier supplies the app's fee charging strategy implementation
 * @param throttleFactory The throttle factory
 */
public record AppContextImpl(
        @NonNull InstantSource instantSource,
        @NonNull SignatureVerifier signatureVerifier,
        @NonNull Gossip gossip,
        @NonNull Supplier<Configuration> configSupplier,
        @NonNull Supplier<NodeInfo> selfNodeInfoSupplier,
        @NonNull Supplier<Metrics> metricsSupplier,
        @NonNull Supplier<FeeCharging> feeChargingSupplier,
        @NonNull Throttle.Factory throttleFactory)
        implements AppContext {}
