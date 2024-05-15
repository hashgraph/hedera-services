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

package com.hedera.services.bdd.suites;

import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class HelloWorldLauncherSessionListener implements LauncherSessionListener {
    private static final Logger log = LogManager.getLogger(HelloWorldLauncherSessionListener.class);

    @Override
    public void launcherSessionOpened(@NonNull final LauncherSession session) {
        log.info("Gracious, isn't it?");
        session.getLauncher().registerTestExecutionListeners(new HwExecutionListener());
    }

    @Override
    public void launcherSessionClosed(@NonNull final LauncherSession session) {
        log.info("Goodbye, World!");
    }

    private static class HwExecutionListener implements TestExecutionListener {
        private static final String SHARED_NETWORK = "sharedNetwork";
        private HederaNetwork network;

        @Override
        public void testPlanExecutionStarted(@NonNull final TestPlan testPlan) {
            log.info("Started the test plan");
            visitTests(testPlan, testIdentifier -> {
                log.info("Hello, {}!", testIdentifier.getDisplayName());
            });
            network = HederaNetwork.newLiveNetwork(SHARED_NETWORK, 2);
            network.startWithin(Duration.ofSeconds(30));
            log.info("Network started");
            network.waitForReady();
            log.info("Network is ready");
        }

        @Override
        public void testPlanExecutionFinished(@NonNull final TestPlan testPlan) {
            log.info("Finished the test plan");
            network.terminate();
        }

        @Override
        public void dynamicTestRegistered(TestIdentifier testIdentifier) {
            log.info("Goodness, found this {}!", testIdentifier.getDisplayName());
        }

        private void visitTests(@NonNull final TestPlan testPlan, @NonNull final Consumer<TestIdentifier> visitor) {
            testPlan.getRoots().forEach(root -> visitTests(testPlan, root, visitor));
        }

        private void visitTests(
                @NonNull final TestPlan testPlan,
                @NonNull final TestIdentifier parent,
                @NonNull final Consumer<TestIdentifier> visitor) {
            testPlan.getChildren(parent).forEach(child -> {
                if (child.isTest()) {
                    visitor.accept(child);
                } else {
                    log.info("Found a container: {} ({})", child.getDisplayName(), child.getType());
                }
                if (child.isContainer()) {
                    visitTests(testPlan, child, visitor);
                }
            });
        }
    }
}
