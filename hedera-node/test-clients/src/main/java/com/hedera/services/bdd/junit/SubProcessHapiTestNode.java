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

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.hedera.hapi.node.base.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An implementation of {@link HapiTestNode} that will shell-out to a sub-process for running the node. The advantage
 * of using a sub-process node is that the node is must closer to the "real thing". Each node has its own directory
 * with its own files and logs. The disadvantage of sub-process nodes is that they are harder to debug, and they are
 * harder to control (make sure we don't leave zombie processes around!).
 *
 * <p>Normally, nodes 1, 2 and 3 are done as sub-process nodes, and node 0 is done as in-process, so you still debug.
 * The {@code stdout} and {@code stderr} files will be written into the working directory.
 */
final class SubProcessHapiTestNode implements HapiTestNode {
    private static final Pattern PROM_PLATFORM_STATUS_HELP_PATTERN =
            Pattern.compile("# HELP platform_PlatformStatus (.*)");
    private static final Pattern PROM_PLATFORM_STATUS_PATTERN =
            Pattern.compile("platform_PlatformStatus\\{.*\\} (\\d+)\\.\\d+");
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    /** The Hedera instance we are testing */
    private ProcessHandle handle;
    /** The name of the node, such as Alice or Bob */
    private final String name;
    /** The ID of the node */
    private final long nodeId;
    /** The account ID of the node, such as 0.0.3 */
    private final AccountID accountId;
    /** The directory in which the config.txt, settings.txt, and other files live. */
    private final Path workingDir;
    /** The port on which the grpc server will be listening */
    private final int grpcPort;
    /** The HTTP Request to use for accessing prometheus to get the current node status (ACTIVE, CHECKING, etc) */
    private final HttpRequest prometheusRequest;
    /** The client used to make prometheus HTTP Requests */
    private final HttpClient httpClient;

    /**
     * Create a new sub-process node.
     *
     * @param name the name of the node, like Alice, Bob
     * @param nodeId The node ID
     * @param accountId The account ID of the node, such as 0.0.3.
     * @param workingDir The working directory. Must already be created and setup with all the files.
     * @param grpcPort The grpc port to configure the server with.
     */
    public SubProcessHapiTestNode(
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

        try {
            prometheusRequest = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:" + (10000 + nodeId)))
                    .GET()
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Bad URI. Should not happen", e);
        }

        httpClient = HttpClient.newHttpClient();
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
        return "SubProcessHapiTestNode{" + "name='"
                + name + '\'' + ", nodeId="
                + nodeId + ", accountId="
                + accountId + '}';
    }

    @Override
    public void start() {
        if (handle != null) throw new IllegalStateException("Node is not stopped, cannot start it!");

        try {
            final var javaCmd = getJavaCommand();
            final var classPath = getClasspath();

            final var stdout = workingDir.resolve("stdout.log").toAbsolutePath().normalize();
            final var stderr = workingDir.resolve("stderr.log").toAbsolutePath().normalize();

            // When tests are terminated, they may leave nodes up and running. These "zombies" have to be terminated,
            // or we will fail to start due to a bind error (two servers cannot use the same port). So we will look
            // through all processes for any that were started with java and were passed this node id as their last
            // argument, and terminate them forcibly (kill -9 style).
            ProcessHandle.allProcesses()
                    .filter(p -> p.info().command().orElse("").contains("java"))
                    .filter(p -> p.info().arguments().orElse(EMPTY_STRING_ARRAY).length > 0)
                    .filter(p -> p.info()
                            .arguments()
                            .orElseThrow()[p.info().arguments().orElse(EMPTY_STRING_ARRAY).length - 1]
                            .equals(Long.toString(nodeId)))
                    .findFirst()
                    .ifPresent(ProcessHandle::destroyForcibly);

            // Now we can start the new process
            final var builder = new ProcessBuilder();
            final var environment = builder.environment();
            environment.put("LC_ALL", "en.UTF-8");
            environment.put("LANG", "en_US.UTF-8");
            environment.put("grpc.port", Integer.toString(grpcPort));
            builder.command(
                            javaCmd,
                            // You can attach to any node. Node 0 at 5005, node 1 at 5006, etc. But if you need the
                            // node to stop at startup, you can change the below line so nodeId == the node you want
                            // to suspend at startup and the first "n" to "y".
                            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + (nodeId == 0 ? "n" : "n")
                                    + ",address=*:" + (5005 + nodeId),
                            "-Dhedera.recordStream.logDir=data/recordStreams",
                            "-classpath",
                            classPath,
                            "-Dfile.encoding=UTF-8",
                            "-Dhedera.workflows.enabled=true",
                            "-Dprometheus.endpointPortNumber=" + (10000 + nodeId),
                            "com.hedera.node.app.ServicesMain",
                            "" + nodeId)
                    .directory(workingDir.toFile())
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile());

            handle = builder.start().toHandle();
        } catch (Exception e) {
            throw new RuntimeException("node " + nodeId + ": Unable to start!", e);
        }
    }

    @Override
    public void waitForActive(long seconds) throws TimeoutException {
        final var waitUntil = System.currentTimeMillis() + (seconds * 1000);
        while (handle != null) {
            if (System.currentTimeMillis() > waitUntil) {
                throw new TimeoutException(
                        "node " + nodeId + ": Waited " + seconds + " seconds, but node did not become active!");
            }

            if ("ACTIVE".equals(getPlatformStatus())) {
                // Actually try to open a connection with the node, to make sure it is really up and running.
                // The platform may be active, but the node may not be listening.
                try {
                    final var url = new URL("http://localhost:" + grpcPort + "/");
                    final var connection = url.openConnection();
                    connection.connect();
                    return;
                } catch (MalformedURLException e) {
                    throw new RuntimeException("Should never happen", e);
                } catch (IOException ignored) {
                    // This is expected, the node is not up yet.
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
    }

    public void shutdown() {
        if (handle != null) {
            handle.destroy();
            handle = null;
        }
    }

    @Override
    public void waitForShutdown(long seconds) throws TimeoutException {
        final var waitUntil = System.currentTimeMillis() + (seconds * 1000);
        while (handle != null && handle.isAlive()) {
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
        while (handle != null && handle.isAlive()) {
            if (System.currentTimeMillis() > waitUntil) {
                throw new TimeoutException(
                        "node " + nodeId + ": Waited " + seconds + " seconds, but node did not freeze!");
            }

            if ("FREEZE_COMPLETE".equals(getPlatformStatus())) {
                return;
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

    public void terminate() {
        if (handle != null) {
            handle.destroyForcibly();
            handle = null;
        }
    }

    @Override
    public void clearState() {
        if (handle != null) {
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

    private String getJavaCommand() {
        final var me = ProcessHandle.current();
        return me.info().command().orElseThrow();
    }

    private String getClasspath() {
        // Could have been launched with -cp, or -classpath, or @/path/to/classpathFile.txt, or maybe module path?
        final var me = ProcessHandle.current();
        final var args = me.info().arguments().orElse(EMPTY_STRING_ARRAY);

        String classpath = "";
        for (int i = 0; i < args.length; i++) {
            final var arg = args[i];
            if (arg.startsWith("@")) {
                try {
                    final var fileContents = Files.readString(Path.of(arg.substring(1)));
                    classpath = fileContents.substring(fileContents.indexOf('/'));
                    break;
                } catch (Exception e) {
                    throw new RuntimeException("Unable to read classpath " + arg, e);
                }
            } else if (arg.equals("-cp") || arg.equals("-classpath")) {
                classpath = args[i + 1];
                break;
            }
        }

        if (classpath.isBlank()) {
            throw new RuntimeException("Cannot discover the classpath. Was --module-path used instead?");
        }

        return Arrays.stream(classpath.split(":"))
                .filter(s -> !s.contains("test-clients"))
                .collect(Collectors.joining(":"));
    }

    private String getPlatformStatus() {
        Map<String, String> statusMap = new HashMap();
        String statusKey = "";
        try {
            final var response = httpClient.send(prometheusRequest, HttpResponse.BodyHandlers.ofString());
            final var reader = new BufferedReader(new StringReader(response.body()));
            String line;
            while ((line = reader.readLine()) != null) {
                final var helpMatcher = PROM_PLATFORM_STATUS_HELP_PATTERN.matcher(line);
                if (helpMatcher.matches()) {
                    final var kvPairs = helpMatcher.group(1).split(" ");
                    for (final var kvPair : kvPairs) {
                        final var parts = kvPair.split("=");
                        statusMap.put(parts[0], parts[1]);
                    }
                }

                final var matcher = PROM_PLATFORM_STATUS_PATTERN.matcher(line);
                if (matcher.matches()) {
                    // This line always comes AFTER the # HELP line.
                    statusKey = matcher.group(1);
                    break;
                }
            }
        } catch (IOException | InterruptedException ignored) {
        }

        return statusMap.get(statusKey);
    }
}
