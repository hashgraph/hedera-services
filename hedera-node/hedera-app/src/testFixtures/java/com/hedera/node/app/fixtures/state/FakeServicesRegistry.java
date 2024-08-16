/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.fixtures.state;

import com.hedera.node.app.services.ServicesRegistry;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.SchemaAware;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A fake implementation of the {@link ServicesRegistry} interface.
 */
public class FakeServicesRegistry implements ServicesRegistry {
    public static final ServicesRegistry.Factory FACTORY =
            (@NonNull final ConstructableRegistry registry, @NonNull final Configuration configuration) ->
                    new FakeServicesRegistry();

    private static final Logger logger = LogManager.getLogger(FakeServicesRegistry.class);
    /**
     * The set of registered services
     */
    private final SortedSet<ServicesRegistry.Registration> entries;

    /**
     * Creates a new registry.
     */
    public FakeServicesRegistry() {
        this.entries = new TreeSet<>();
    }

    /**
     * Register the given service.
     *
     * @param schemaAware The service to register.
     */
    @Override
    public void register(@NonNull final SchemaAware schemaAware) {
        final var registry = new FakeSchemaRegistry();
        schemaAware.registerSchemas(registry);

        entries.add(new FakeServicesRegistry.Registration(schemaAware, registry));
        logger.info("Registered service {}", schemaAware.getStateName());
    }

    @NonNull
    @Override
    public SortedSet<FakeServicesRegistry.Registration> registrations() {
        return Collections.unmodifiableSortedSet(entries);
    }
}
