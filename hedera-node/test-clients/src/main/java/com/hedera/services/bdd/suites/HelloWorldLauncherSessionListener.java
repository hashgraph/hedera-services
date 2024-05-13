package com.hedera.services.bdd.suites;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.function.Consumer;

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
        @Override
        public void testPlanExecutionStarted(@NonNull final TestPlan testPlan) {
            log.info("Starting the test plan!");
            visitTests(testPlan, testIdentifier -> {
                log.info("Hello, {}!", testIdentifier.getDisplayName());
            });
        }

        @Override
        public void dynamicTestRegistered(TestIdentifier testIdentifier) {
            log.info("Goodness, found this {}!", testIdentifier.getDisplayName());
        }

        private void visitTests(
                @NonNull final TestPlan testPlan,
                @NonNull final Consumer<TestIdentifier> visitor) {
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
