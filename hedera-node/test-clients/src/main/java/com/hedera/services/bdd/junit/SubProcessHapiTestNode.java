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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    /** The Hedera instance we are testing */
    private ProcessHandle handle;
    /** The ID of the node */
    private final long nodeId;
    /** The directory in which the config.txt, settings.txt, and other files live. */
    private final Path workingDir;
    /** The port on which the grpc server will be listening */
    private final int grpcPort;

    /**
     * Create a new sub-process node.
     *
     * @param workingDir The working directory. Must already be created and setup with all the files.
     * @param nodeId The node ID
     * @param grpcPort The grpc port to configure the server with.
     */
    public SubProcessHapiTestNode(@NonNull final Path workingDir, final long nodeId, final int grpcPort) {
        this.workingDir = requireNonNull(workingDir);
        this.nodeId = nodeId;
        this.grpcPort = grpcPort;
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
                            "-Dfile.encoding=UTF-8",
                            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + (nodeId == 0 ? "n" : "n")
                                    + ",address=*:" + (5005 + nodeId),
                            "-Dhedera.workflows.enabled=true",
                            "-classpath",
                            classPath,
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
    public void waitForActive(long seconds) {
        final var waitUntil = System.currentTimeMillis() + (seconds * 1000);
        final var log = workingDir.resolve("output").resolve("hgcaa.log");
        while (handle != null) {
            if (System.currentTimeMillis() > waitUntil) {
                throw new RuntimeException(
                        "node " + nodeId + ": Waited " + seconds + " seconds, but node did not become active!");
            }

            try {
                if (Files.exists(log)) {
                    try (final var in = Files.newBufferedReader(log)) {
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.contains("ACTIVE")) {
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("node " + nodeId + ": Unable to read from the hgcaa log file " + log, e);
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

    public void stop() {
        if (handle != null) {
            handle.destroy();
            handle = null;
        }
    }

    public void terminate() {
        if (handle != null) {
            handle.destroyForcibly();
            handle = null;
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
}
