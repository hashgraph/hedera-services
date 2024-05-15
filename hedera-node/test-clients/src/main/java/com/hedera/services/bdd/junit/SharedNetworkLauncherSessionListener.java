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

package com.hedera.services.bdd.junit;

import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class SharedNetworkLauncherSessionListener implements LauncherSessionListener {
    private static final Logger log = LogManager.getLogger(SharedNetworkLauncherSessionListener.class);

    @Override
    public void launcherSessionOpened(@NonNull final LauncherSession session) {
        session.getLauncher().registerTestExecutionListeners(new SharedNetworkExecutionListener());
    }

    private static class SharedNetworkExecutionListener implements TestExecutionListener {
        private static final int SHARED_NETWORK_SIZE = 2;
        private static final String SHARED_NETWORK = "sharedNetwork";
        private static final Duration SHARED_NETWORK_STARTUP_TIMEOUT = Duration.ofSeconds(30);

        @Override
        public void testPlanExecutionStarted(@NonNull final TestPlan testPlan) {
            final var sharedNetwork = HederaNetwork.newLiveNetwork(SHARED_NETWORK, SHARED_NETWORK_SIZE);
            sharedNetwork.startWithin(SHARED_NETWORK_STARTUP_TIMEOUT);
            HederaNetwork.SHARED_NETWORK.set(sharedNetwork);
        }

        @Override
        public void testPlanExecutionFinished(@NonNull final TestPlan testPlan) {
            HederaNetwork.SHARED_NETWORK.get().terminate();
        }

        @Override
        public void dynamicTestRegistered(TestIdentifier testIdentifier) {
            log.info("Registered {}", testIdentifier.getDisplayName());
        }
    }
}
