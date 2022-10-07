/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.init;

import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_LAST_THROTTLE_EXEMPT;
import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.ServicesState;
import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.files.FileUpdateInterceptor;
import com.hedera.services.files.HederaFs;
import com.hedera.services.stream.RecordStreamManager;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class StateInitializationFlow {
    private static final Logger log = LogManager.getLogger(StateInitializationFlow.class);

    private final HederaFs hfs;
    private final HederaNumbers hederaNums;
    private final RecordStreamManager recordStreamManager;
    private final MutableStateChildren workingState;
    private final Set<FileUpdateInterceptor> fileUpdateInterceptors;

    @Inject
    public StateInitializationFlow(
            final HederaFs hfs,
            final HederaNumbers hederaNums,
            final RecordStreamManager recordStreamManager,
            final MutableStateChildren workingState,
            final Set<FileUpdateInterceptor> fileUpdateInterceptors) {
        this.hfs = hfs;
        this.hederaNums = hederaNums;
        this.workingState = workingState;
        this.recordStreamManager = recordStreamManager;
        this.fileUpdateInterceptors = fileUpdateInterceptors;
    }

    public void runWith(
            final ServicesState activeState, final BootstrapProperties bootstrapProperties) {
        final var lastThrottleExempt =
                bootstrapProperties.getLongProperty(ACCOUNTS_LAST_THROTTLE_EXEMPT);
        // The last throttle-exempt account is configurable to make it easy to start dev networks
        // without throttling
        numberConfigurer.configureNumbers(hederaNums, lastThrottleExempt);

        workingState.updateFrom(activeState);
        log.info("Context updated with working state");

        final var activeHash = activeState.runningHashLeaf().getRunningHash().getHash();
        recordStreamManager.setInitialHash(activeHash);
        log.info("Record running hash initialized");

        if (hfs.numRegisteredInterceptors() == 0) {
            fileUpdateInterceptors.forEach(hfs::register);
            log.info("Registered {} file update interceptors", fileUpdateInterceptors.size());
        }
    }

    interface NumberConfigurer {
        void configureNumbers(HederaNumbers numbers, long lastThrottleExempt);
    }

    private static NumberConfigurer numberConfigurer = STATIC_PROPERTIES::configureNumbers;

    /* --- Only used by unit tests --- */
    @VisibleForTesting
    static void setNumberConfigurer(NumberConfigurer numberConfigurer) {
        StateInitializationFlow.numberConfigurer = numberConfigurer;
    }
}
