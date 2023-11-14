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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.Hedera;
import com.swirlds.base.state.Stoppable;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.platform.PlatformBuilder;
import com.swirlds.platform.util.BootstrapUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeoutException;

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
    /** The name of the node, such as Alice or Bob */
    private final String name;
    /** The ID of the node. This is probably always 0. */
    private final long nodeId;
    /** The account ID of the node, such as 0.0.3 */
    private final AccountID accountId;
    /** The directory in which the config.txt, settings.txt, and other files live. */
    private final Path workingDir;
    /** The port on which the grpc server will be listening */
    private final int grpcPort;

    /**
     * Create a new in-process node.
     *
     * @param name the name of the node, like Alice, Bob
     * @param nodeId The node ID
     * @param accountId The account ID of the node, such as 0.0.3.
     * @param workingDir The working directory. Must already be created and setup with all the files.
     * @param grpcPort   The grpc port to configure the server with.
     */
    public InProcessHapiTestNode(
            @NonNull final String name,
            final long nodeId,
            @NonNull final AccountID accountId,
            @NonNull final Path workingDir,
            final int grpcPort) {
        this.name = requireNonNull(name);
        this.nodeId = nodeId;
        this.accountId = requireNonNull(accountId);
        this.workingDir = requireNonNull(workingDir);
        this.grpcPort = grpcPort;
    }

    @Override
    public long getId() {
        return nodeId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public AccountID getAccountId() {
        return accountId;
    }

    @Override
    public String toString() {
        return "InProcessHapiTestNode{" + "name='"
                + name + '\'' + ", nodeId="
                + nodeId + ", accountId="
                + accountId + '}';
    }

    @Override
    public void start() {
        if (th != null && th.hedera.isActive()) {
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
    public void waitForActive(long seconds) throws TimeoutException {
        final var waitUntil = System.currentTimeMillis() + (seconds * 1000);
        while (System.currentTimeMillis() < waitUntil) {
            if (th != null && th.hedera != null && th.hedera.isActive()) {
                // Actually try to open a connection with the node, to make sure it is really up and running.
                // The platform may be active, but the node may not be listening.
                try {
                    final var url = new URL("http://localhost:" + grpcPort + "/");
                    final var connection = url.openConnection();
                    connection.connect();
                    return;
                } catch (MalformedURLException e) {
                    throw new RuntimeException("Should never happen", e);
                } catch (SocketTimeoutException ignored) {
                    // This is expected, the node is not up yet.
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                        "node " + nodeId + ": Interrupted while sleeping in waitForActive busy loop", e);
            }
        }

        // We timed out.
        throw new TimeoutException(
                "node " + nodeId + ": Waited " + seconds + " seconds, but node did not become active!");
    }

    @Override
    public void shutdown() {
        if (th != null && (th.hedera.isFrozen() || th.hedera.isActive())) {
            if (th.hedera != null) {
                th.hedera.shutdown();
            }
            th.interrupt();

            // This is a hack, but it's the best I can do without classloader isolation and without a systematic
            // way to shut down a node. Normally, nodes are shutdown by existing the JVM. However, we don't want to
            // do that here, because we're in-process! So what I'm going to do is:
            //  1. Search through all the threads in the JVM to find all of them with a callstack with "WorkerThread"
            //     in the stack. This means they were called by the WorkerThread. Only objects that are part of the
            //     node will be in this callstack.
            //  2. For each such thread, stop it. Thread.stop is deprecated and discouraged, because it is almost
            //     always the wrong thing. In our case though, NOTHING in the Junit JVM is working with any locks or
            //     semaphores, etc. in the node. So we should be safe.
            //  3. There are some places in the node software that uses statics -- like MerkleDb. I've got to try to
            //     reset those.
            //
            // This is an error-prone approach, because at any time someone might add new static state to the node and
            // fail to update this code accordingly. But it's the best we can do without removing all static state and
            // having a clean shutdown procedure for the node. Fixing that should be a priority, allowing us to simplify
            // this code and make it more foolproof.

            //noinspection deprecation
            final var threadsToStop = Thread.getAllStackTraces().entrySet().stream()
                    .filter(entry -> {
                        for (final var stackTraceElement : entry.getValue()) {
                            final var className = stackTraceElement.getClassName();
                            if (className.contains("WorkerThread") || className.contains("com.swirlds")) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .map(Map.Entry::getKey)
                    .toList();

            threadsToStop.forEach(th -> {
                if (th instanceof Stoppable s) {
                    s.stop();
                }
            });
            threadsToStop.forEach(Thread::interrupt);
            threadsToStop.forEach(Thread::stop);

            MerkleDb.setDefaultPath(null);
            ConstructableRegistry.getInstance().reset();
        }
    }

    @Override
    public void waitForShutdown(long seconds) throws TimeoutException {
        final var waitUntil = System.currentTimeMillis() + (seconds * 1000);
        while (th != null && th.hedera != null && th.hedera.isActive()) {
            if (System.currentTimeMillis() > waitUntil) {
                throw new TimeoutException(
                        "node " + nodeId + ": Waited " + seconds + " seconds, but node did not shut down!");
            }

            try {
                MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                        "node " + nodeId + ": Interrupted while sleeping in waitForShutdown busy loop", e);
            }
        }
    }

    @Override
    public void waitForFreeze(long seconds) throws TimeoutException {
        final var waitUntil = System.currentTimeMillis() + (seconds * 1000);
        while (th != null && !th.hedera.isFrozen()) {
            if (System.currentTimeMillis() > waitUntil) {
                throw new TimeoutException(
                        "node " + nodeId + ": Waited " + seconds + " seconds, but node did not freeze!");
            }

            try {
                MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                        "node " + nodeId + ": Interrupted while sleeping in waitForFreeze busy loop", e);
            }
        }
    }

    @Override
    public void terminate() {
        // There really isn't anything better I can do without classloader isolation
        shutdown();
    }

    @Override
    public void clearState() {
        if (th != null && th.hedera.isActive()) {
            throw new IllegalStateException("Cannot clear state from a running node. At least, not yet.");
        }

        final var saved = workingDir.resolve("data/saved").toAbsolutePath().normalize();
        try {
            if (Files.exists(saved)) {
                Files.walk(saved)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not delete saved state " + saved, e);
        }
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
                    .withValue("metrics.csvOutputFolder", path("."))
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
