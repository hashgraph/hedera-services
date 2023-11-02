/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.swirlds.platform.PlatformBuilder.DEFAULT_CONFIG_FILE_NAME;
import static com.swirlds.platform.PlatformBuilder.DEFAULT_SETTINGS_FILE_NAME;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.hedera.node.app.Hedera;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.PlatformBuilder;
import com.swirlds.platform.util.BootstrapUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;

/**
 * An implementation of {@link HapiTestNode} that runs the node in this JVM process. The advantage of the in-process
 * node is that it can be debugged when run through the IDE or through Gradle. Just launch JUnit using a debugger, and
 * place a breakpoint in the code for the node, and it will work!
 *
 * <p>Ideally we would host the node with classloader isolation, which would allow us to bring this node down and up
 * again. Unfortunately, the {@link ConstructableRegistry} thwarted my attempt, because it ignores the classloader
 * isolation, discovers all the classloaders (including the system classloader), and chooses its own rank order for what
 * order to look classes up in. That makes it impossible to do classloader isolation. We need to fix that. Until then,
 * in process nodes simply will not work well when stopped.
 *
 * <p>NOTE!! This class will not work generally, There are several problems that must be fixed. We must have a clean
 * way to shut things down, which we don't have today. We also need a way to specify the config properties to use
 * with the Hedera config (which is DIFFERENT from the platform config). Right now that involves setting an environment
 * variable, which we cannot do when running in process. See ConfigProviderBase.
 */
public class InProcessHapiTestNode implements HapiTestNode {
    /** The thread in which the Hedera node will run */
    private WorkerThread th;
    /** The ID of the node. This is probably always 0. */
    private final long nodeId;
    /** The directory in which the config.txt, settings.txt, and other files live. */
    private final Path workingDir;
    /** The port on which the grpc server will be listening */
    private final int grpcPort;

    /**
     * Create a new in-process node.
     *
     * @param workingDir The working directory. Must already be created and setup with all the files.
     * @param nodeId     The node ID
     * @param grpcPort   The grpc port to configure the server with.
     */
    public InProcessHapiTestNode(@NonNull final Path workingDir, final long nodeId, final int grpcPort) {
        this.workingDir = requireNonNull(workingDir);
        this.nodeId = nodeId;
        this.grpcPort = grpcPort;
    }

    @Override
    public void start() {
        if (th != null) {
            throw new IllegalStateException("Node is not stopped, cannot start it!");
        }

        try {
            th = new WorkerThread(workingDir, nodeId, grpcPort);
            th.setDaemon(false);
            th.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start the node! Check the logs", e);
        }
    }

    @Override
    public void waitForActive(long seconds) {
        final var waitUntil = System.currentTimeMillis() + (seconds * 1000);
        while (th == null || th.hedera == null || !th.hedera.isActive()) {
            if (System.currentTimeMillis() > waitUntil) {
                throw new RuntimeException(
                        "node " + nodeId + ": Waited " + seconds + " seconds, but node did not become active!");
            }

            try {
                MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                        "node " + nodeId + ": Interrupted while sleeping in waitForActive busy loop", e);
            }
        }
    }

    @Override
    public void stop() {
        if (th != null) {
            if (th.hedera != null) {
                th.hedera.shutdown();
            }
            th.interrupt();
            th = null;
        }
    }

    @Override
    public void terminate() {
        // There really isn't anything better I can do without classloader isolation
        stop();
    }

    /**
     * A helper thread class within which the node is started.
     */
    public static final class WorkerThread extends Thread {
        private Hedera hedera;
        private final long nodeId;
        private final int grpcPort;
        private final Path workingDir;

        public WorkerThread(Path workingDir, long nodeId, int grpcPort) {
            this.workingDir = workingDir;
            this.nodeId = nodeId;
            this.grpcPort = grpcPort;
        }

        public Hedera getHedera() {
            return hedera;
        }

        @Override
        public void run() {
            BootstrapUtils.setupConstructableRegistry();
            final var cr = ConstructableRegistry.getInstance();

            hedera = new Hedera(cr);

            final PlatformBuilder builder = new PlatformBuilder(
                    Hedera.APP_NAME,
                    Hedera.SWIRLD_NAME,
                    hedera.getSoftwareVersion(),
                    hedera::newState,
                    new NodeId(nodeId));

            final ConfigurationBuilder configBuilder = ConfigurationBuilder.create()
                    .withValue("paths.settingsUsedDir", path("."))
                    .withValue("paths.keysDirPath", path("data/keys"))
                    .withValue("paths.appsDirPath", path("data/apps"))
                    .withValue("paths.logPath", path("log4j2.xml"))
                    .withValue("emergencyRecoveryFileLoadDir", path("data/saved"))
                    .withValue("state.savedStateDirectory", path("data/saved"))
                    .withValue("loadKeysFromPfxFiles", "false")
                    .withValue("grpc.port", Integer.toString(grpcPort));

            builder.withConfigurationBuilder(configBuilder)
                    .withSettingsPath(Path.of(path(DEFAULT_SETTINGS_FILE_NAME)))
                    .withConfigPath(Path.of(path(DEFAULT_CONFIG_FILE_NAME)));

            final Platform platform = builder.build();
            hedera.init(platform, new NodeId(nodeId));
            platform.start();
            hedera.run();
        }

        private String path(String path) {
            return workingDir.resolve(path).toAbsolutePath().normalize().toString();
        }
    }
}
