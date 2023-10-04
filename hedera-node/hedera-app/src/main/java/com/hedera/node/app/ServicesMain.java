/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app;

import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import com.swirlds.platform.PlatformBuilder;
import com.swirlds.platform.util.BootstrapUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main entry point.
 *
 * <p>This class simply delegates to either {@link MonoServicesMain} or {@link Hedera} depending on
 * the value of the {@code hedera.services.functions.workflows.enabled} property. If *any* workflows are enabled, then
 * {@link Hedera} is used; otherwise, {@link MonoServicesMain} is used.
 */
public class ServicesMain implements SwirldMain {
    private static final Logger logger = LogManager.getLogger(ServicesMain.class);

    /**
     * The {@link SwirldMain} to actually use, depending on whether workflows are enabled.
     */
    private final SwirldMain delegate;

    /** Create a new instance */
    public ServicesMain() {
        final var configProvider = new ConfigProviderImpl(false);
        final var hederaConfig = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        if (hederaConfig.workflowsEnabled().isEmpty()) {
            logger.info("No workflows enabled, using mono-service");
            delegate = new MonoServicesMain();
        } else {
            logger.info("One or more workflows enabled, using Hedera");
            delegate = new Hedera(ConstructableRegistry.getInstance());
        }
    }

    /** {@inheritDoc} */
    @Override
    public SoftwareVersion getSoftwareVersion() {
        return delegate.getSoftwareVersion();
    }

    /** {@inheritDoc} */
    @Override
    public void init(@NonNull final Platform ignored, @NonNull final NodeId nodeId) {
        delegate.init(ignored, nodeId);
    }

    /** {@inheritDoc} */
    @Override
    public SwirldState newState() {
        return delegate.newState();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        delegate.run();
    }

    /**
     * Launches the application.
     *
     * @param args First arg, if specified, will be the node ID
     */
    public static void main(final String... args) throws Exception {
        BootstrapUtils.setupConstructableRegistry();
        final var registry = ConstructableRegistry.getInstance();

        final Hedera hedera = new Hedera(registry);
        final NodeId selfId = args != null && args.length > 0 ? new NodeId(Integer.parseInt(args[0])) : new NodeId(0);

        final PlatformBuilder builder = new PlatformBuilder(
                Hedera.APP_NAME, Hedera.SWIRLD_NAME, hedera.getSoftwareVersion(), hedera::newState, selfId);

        final Platform platform = builder.build();
        hedera.init(platform, selfId);
        platform.start();
        hedera.run();
    }
}
