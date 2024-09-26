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

import static com.hedera.services.bdd.junit.extensions.NetworkTargetingExtension.REPEATABLE_KEY_GENERATOR;
import static com.hedera.services.bdd.junit.extensions.NetworkTargetingExtension.SHARED_NETWORK;

import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedNetwork;
import com.hedera.services.bdd.junit.hedera.subprocess.SubProcessNetwork;
import com.hedera.services.bdd.spec.infrastructure.HapiClients;
import com.hedera.services.bdd.spec.keys.RepeatableKeyGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/**
 * Registers a {@link TestExecutionListener} when the {@link LauncherSession} is opened to
 * start the shared test network before the test plan is executed; and stop it after test
 * plan execution finishes.
 */
public class SharedNetworkLauncherSessionListener implements LauncherSessionListener {
    public static final int CLASSIC_HAPI_TEST_NETWORK_SIZE = 1;

    @Override
    public void launcherSessionOpened(@NonNull final LauncherSession session) {
        session.getLauncher().registerTestExecutionListeners(new SharedNetworkExecutionListener());
    }

    private static class SharedNetworkExecutionListener implements TestExecutionListener {
        private boolean isEmbedded = false;

        @Override
        public void testPlanExecutionStarted(@NonNull final TestPlan testPlan) {
            isEmbedded = embeddedModeRequested() || repeatableModeRequested();
            final HederaNetwork targetNetwork;
            if (isEmbedded) {
                targetNetwork = EmbeddedNetwork.newSharedNetwork();
            } else {
                final var initialPortProperty = System.getProperty("hapi.spec.initial.port");
                if (!initialPortProperty.isBlank()) {
                    final var initialPort = Integer.parseInt(initialPortProperty);
                    SubProcessNetwork.initializeNextPortsForNetwork(CLASSIC_HAPI_TEST_NETWORK_SIZE, initialPort);
                }
                targetNetwork = SubProcessNetwork.newSharedNetwork(CLASSIC_HAPI_TEST_NETWORK_SIZE);
            }
            targetNetwork.start();
            SHARED_NETWORK.set(targetNetwork);
            REPEATABLE_KEY_GENERATOR.set(new RepeatableKeyGenerator());
        }

        @Override
        public void testPlanExecutionFinished(@NonNull final TestPlan testPlan) {
            if (!isEmbedded) {
                HapiClients.tearDown();
            }
            SHARED_NETWORK.get().terminate();
        }
    }

    private static boolean embeddedModeRequested() {
        return "true".equalsIgnoreCase(System.getProperty("hapi.spec.embedded.mode"));
    }

    public static boolean repeatableModeRequested() {
        return "true".equalsIgnoreCase(System.getProperty("hapi.spec.repeatable.mode"));
    }
}
