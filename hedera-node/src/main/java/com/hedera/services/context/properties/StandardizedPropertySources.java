/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.properties;

import static com.hedera.services.context.properties.BootstrapProperties.BOOTSTRAP_PROP_NAMES;

import com.hedera.services.context.annotations.BootstrapProps;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements a {@link PropertySources} that re-resolves every property reference by delegating to a
 * supplier.
 */
public class StandardizedPropertySources implements PropertySources {
    public static final Logger log = LogManager.getLogger(StandardizedPropertySources.class);

    private final PropertySource bootstrapProps;
    private final ScreenedSysFileProps dynamicGlobalProps;
    private final ScreenedNodeFileProps nodeProps;

    @Inject
    public StandardizedPropertySources(
            @BootstrapProps PropertySource bootstrapProps,
            ScreenedSysFileProps dynamicGlobalProps,
            ScreenedNodeFileProps nodeProps) {
        this.nodeProps = nodeProps;
        this.bootstrapProps = bootstrapProps;
        this.dynamicGlobalProps = dynamicGlobalProps;
    }

    @Override
    public void reloadFrom(ServicesConfigurationList config) {
        log.info(
                "Reloading global dynamic properties from {} candidates",
                config.getNameValueCount());
        dynamicGlobalProps.screenNew(config);
    }

    @Override
    public PropertySource asResolvingSource() {
        final var bootstrap = new SupplierMapPropertySource(sourceMap());
        final var bootstrapPlusNodeProps = new ChainedSources(nodeProps, bootstrap);
        return new ChainedSources(dynamicGlobalProps, bootstrapPlusNodeProps);
    }

    private Map<String, Supplier<Object>> sourceMap() {
        final Map<String, Supplier<Object>> source = new HashMap<>();

        /* Bootstrap properties, which must include defaults for every system property. */
        BOOTSTRAP_PROP_NAMES.forEach(
                name -> source.put(name, () -> bootstrapProps.getProperty(name)));

        return source;
    }

    ScreenedNodeFileProps getNodeProps() {
        return nodeProps;
    }
}
