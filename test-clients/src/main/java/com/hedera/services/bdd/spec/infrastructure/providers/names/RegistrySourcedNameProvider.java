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
package com.hedera.services.bdd.spec.infrastructure.providers.names;

import static java.util.Collections.EMPTY_SET;

import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.infrastructure.RegistryChangeContext;
import com.hedera.services.bdd.spec.infrastructure.listeners.PresenceTrackingListener;
import com.hedera.services.bdd.suites.regression.RegressionProviderFactory;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RegistrySourcedNameProvider<T> implements EntityNameProvider<T> {
    static final Logger log = LogManager.getLogger(RegressionProviderFactory.class);

    private final Class<T> type;
    private final PresenceTrackingListener<T> listener;
    private final BiFunction<Set<String>, Set<String>, Optional<String>> selector;

    public RegistrySourcedNameProvider(
            Class<T> type,
            HapiSpecRegistry registry,
            BiFunction<Set<String>, Set<String>, Optional<String>> selector) {
        this.type = type;
        this.selector = selector;
        this.listener = new PresenceTrackingListener<>(type, registry);
    }

    public RegistrySourcedNameProvider(
            Class<T> type,
            HapiSpecRegistry registry,
            BiFunction<Set<String>, Set<String>, Optional<String>> selector,
            Predicate<RegistryChangeContext<T>> filter) {
        this.type = type;
        this.selector = selector;
        this.listener = new PresenceTrackingListener<>(type, registry, filter);
    }

    @Override
    public Class<T> forType() {
        return type;
    }

    @Override
    public Optional<String> getQualifying() {
        return selector.apply(listener.allKnown(), EMPTY_SET);
    }

    @Override
    public Optional<String> getQualifyingExcept(Set<String> ineligible) {
        return selector.apply(listener.allKnown(), ineligible);
    }

    public int numPresent() {
        return listener.allKnown().size();
    }
}
