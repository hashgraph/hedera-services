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

package com.hedera.services.bdd.junit.hedera.subprocess;

import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.DATA_DIR;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class ProcessUtils {
    private static final Logger log = LogManager.getLogger(ProcessUtils.class);

    private static final int FIRST_AGENT_PORT = 5005;
    private static final long NODE_ID_TO_SUSPEND = -1;
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    public static final String STREAMS_DIR = "recordStreams";
    private static final long WAIT_SLEEP_MILLIS = 100L;

    public static final Executor EXECUTOR = Executors.newCachedThreadPool();

    private ProcessUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Waits for the given node to reach the specified status within the given timeout.
     * Throws an assertion error if the status is not reached within the timeout.
     *
     * @param node the node to wait for
     * @param status the status to wait for
     * @param timeout the timeout duration
     */
    public static void awaitStatus(
            @NonNull final HederaNode node, @NonNull final PlatformStatus status, @NonNull final Duration timeout) {
        final AtomicReference<NodeStatus> lastStatus = new AtomicReference<>();
        log.info("Waiting for node '{}' to be {} within {}", node.getName(), status, timeout);
        try {
            node.statusFuture(status, lastStatus::set).get(timeout.toMillis(), MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Assertions.fail("Node '" + node.getName() + "' did not reach status " + status + " within " + timeout
                    + "\n  Final status: " + lastStatus.get()
                    + "\n  Cause       : " + e);
        }
        log.info("Node '{}' is {}", node.getName(), status);
    }

    /**
     * Destroys any process that appears to be a node started from the given metadata, based on the
     * process command being {@code java} and having a last argument matching the node ID.
     *
     * @param nodeId the id of the node whose processes should be destroyed
     */
    public static void destroyAnySubProcessNodeWithId(final long nodeId) {
        ProcessHandle.allProcesses()
                .filter(p -> p.info().command().orElse("").contains("java"))
                .filter(p -> endsWith(p.info().arguments().orElse(EMPTY_STRING_ARRAY), Long.toString(nodeId)))
                .forEach(ProcessHandle::destroyForcibly);
    }

    /**
     * Starts a sub-process node from the given metadata and returns its {@link ProcessHandle}.
     *
     * @param metadata the metadata of the node to start
     * @return the {@link ProcessHandle} of the started node
     */
    public static ProcessHandle startSubProcessNodeFrom(@NonNull final NodeMetadata metadata) {
        // By default tell java to start the ServicesMain class
        return startSubProcessNodeFrom(metadata, "com.hedera.node.app.ServicesMain");
    }

    /**
     * Starts a sub-process node from the given metadata and main class reference, and returns its {@link ProcessHandle}.
     *
     * @param metadata the metadata of the node to start
     * @param mainClassRef the main class reference to start
     * @return the {@link ProcessHandle} of the started node
     */
    public static ProcessHandle startSubProcessNodeFrom(
            @NonNull final NodeMetadata metadata, @NonNull String... mainClassRef) {
        final var builder = new ProcessBuilder();
        final var environment = builder.environment();
        environment.put("LC_ALL", "en.UTF-8");
        environment.put("LANG", "en_US.UTF-8");
        environment.put("grpc.port", Integer.toString(metadata.grpcPort()));
        try {
            return builder.command(Stream.of(
                                    baseJavaCmdLine(metadata),
                                    List.of(mainClassRef),
                                    List.of("-local", Long.toString(metadata.nodeId())))
                            .flatMap(List::stream)
                            .toList())
                    .directory(metadata.workingDir().toFile())
                    .inheritIO()
                    .start()
                    .toHandle();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> baseJavaCmdLine(@NonNull final NodeMetadata metadata) {
        return List.of(
                // Use the same java command that started this process
                ProcessHandle.current().info().command().orElseThrow(),
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend="
                        + (metadata.nodeId() == NODE_ID_TO_SUSPEND ? "y" : "n") + ",address=*:"
                        + (FIRST_AGENT_PORT + metadata.nodeId()),
                "-classpath",
                // Use the same classpath that started this process, excluding test-clients
                currentNonTestClientClasspath(),
                // JVM system
                "-Dfile.encoding=UTF-8",
                "-Dprometheus.endpointPortNumber=" + metadata.prometheusPort(),
                "-Dhedera.recordStream.logDir=" + DATA_DIR + "/" + STREAMS_DIR,
                "-Dhedera.profiles.active=DEV",
                "-Dhedera.workflows.enabled=true");
    }

    /**
     * Returns a future that resolves when the given condition is true.
     *
     * @param condition the condition to wait for
     * @return a future that resolves when the condition is true or the timeout is reached
     */
    public static CompletableFuture<Void> conditionFuture(@NonNull final BooleanSupplier condition) {
        return conditionFuture(condition, () -> WAIT_SLEEP_MILLIS);
    }

    /**
     * Returns a future that resolves when the given condition is true, backing off checking
     * the condition by the number of milliseconds returned by the given supplier.
     *
     * @param condition the condition to wait for
     * @param checkBackoffMs the supplier of the number of milliseconds to back off between checks
     * @return a future that resolves when the condition is true or the timeout is reached
     */
    public static CompletableFuture<Void> conditionFuture(
            @NonNull final BooleanSupplier condition, @NonNull final LongSupplier checkBackoffMs) {
        requireNonNull(condition);
        requireNonNull(checkBackoffMs);
        return CompletableFuture.runAsync(
                () -> {
                    while (!condition.getAsBoolean()) {
                        try {
                            MILLISECONDS.sleep(checkBackoffMs.getAsLong());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while waiting for condition", e);
                        }
                    }
                },
                EXECUTOR);
    }

    private static String currentNonTestClientClasspath() {
        // Could have been launched with -cp, or -classpath, or @/path/to/classpathFile.txt, or maybe module path?
        final var args = ProcessHandle.current().info().arguments().orElse(EMPTY_STRING_ARRAY);

        String classpath = "";
        for (int i = 0; i < args.length; i++) {
            final var arg = args[i];
            if (arg.startsWith("@")) {
                try {
                    final var fileContents = Files.readString(Path.of(arg.substring(1)));
                    classpath = fileContents.substring(fileContents.indexOf('/'));
                    break;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else if (arg.equals("-cp") || arg.equals("-classpath")) {
                classpath = args[i + 1];
                break;
            }
        }
        if (classpath.isBlank()) {
            throw new IllegalStateException("Cannot discover the classpath. Was --module-path used instead?");
        }
        return Arrays.stream(classpath.split(":"))
                .filter(s -> !s.contains("test-clients"))
                .collect(Collectors.joining(":"));
    }

    private static boolean endsWith(final String[] args, final String lastArg) {
        return args.length > 0 && args[args.length - 1].equals(lastArg);
    }
}
