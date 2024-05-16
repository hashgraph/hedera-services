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

package com.hedera.services.bdd.junit.hedera.live;

import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ProcessUtils {
    private static final int FIRST_AGENT_PORT = 5005;
    private static final long NODE_ID_TO_SUSPEND = -1;
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    public static final String OVERRIDE_RECORD_STREAM_FOLDER = "recordStreams";

    private ProcessUtils() {
        throw new UnsupportedOperationException("Utility Class");
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
        final var builder = new ProcessBuilder();
        final var environment = builder.environment();
        environment.put("LC_ALL", "en.UTF-8");
        environment.put("LANG", "en_US.UTF-8");
        environment.put("grpc.port", Integer.toString(metadata.grpcPort()));
        try {
            return builder.command(
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
                            "-Dhedera.recordStream.logDir=data/" + OVERRIDE_RECORD_STREAM_FOLDER,
                            "-Dhedera.profiles.active=DEV",
                            "-Dhedera.workflows.enabled=true",
                            "com.hedera.node.app.ServicesMain",
                            "-local",
                            Long.toString(metadata.nodeId()))
                    .directory(metadata.workingDir().toFile())
                    .inheritIO()
                    .start()
                    .toHandle();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
