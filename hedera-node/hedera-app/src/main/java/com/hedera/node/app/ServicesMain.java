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

import static com.hedera.node.app.spi.config.PropertyNames.WORKFLOWS_ENABLED;

import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main entry point.
 *
 * <p>This class simply delegates to either {@link MonoServicesMain} or {@link Hedera} depending on
 * the value of the {@code hedera.services.functions.workflows.enabled} property. If *any* workflows
 * are enabled, then {@link Hedera} is used; otherwise, {@link MonoServicesMain} is used.
 */
public class ServicesMain implements SwirldMain {
    private static final Logger logger = LogManager.getLogger(ServicesMain.class);

    /**
     * The {@link SwirldMain} to actually use, depending on whether workflows are enabled.
     */
    private SwirldMain delegate;

    /** Create a new instance */
    public ServicesMain() {
        final var bootstrapProps = new BootstrapProperties(false);
        final var enabledWorkflows = bootstrapProps.getFunctionsProperty(WORKFLOWS_ENABLED);
        if (enabledWorkflows.isEmpty()) {
            logger.info("No workflows enabled, using mono-service");
            delegate = new MonoServicesMain();
        } else {
            logger.info("One or more workflows enabled, using Hedera");
            delegate = new Hedera(ConstructableRegistry.getInstance(), bootstrapProps);
        }
    }

    /** {@inheritDoc} */
    @Override
    public SoftwareVersion getSoftwareVersion() {
        return delegate.getSoftwareVersion();
    }

    /** {@inheritDoc} */
    @Override
    public void init(final Platform ignored, final NodeId nodeId) {
        delegate.init(ignored, nodeId);
    }

    /** {@inheritDoc} */
    @Override
    public SwirldState2 newState() {
        return (SwirldState2) delegate.newState();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        delegate.run();
    }
}
