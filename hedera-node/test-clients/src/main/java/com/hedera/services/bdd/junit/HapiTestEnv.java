/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.junit.HapiTestEnv.HapiTestNodesType.IN_PROCESS_ALICE;

import com.hedera.hapi.node.base.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiTestEnv {
    private static final Logger logger = LogManager.getLogger(HapiTestEnv.class);
    private static final String[] NODE_NAMES = new String[] {"Alice", "Bob", "Carol", "Dave"};
    private static final int FIRST_GOSSIP_PORT = 60000;
    private static final int FIRST_GOSSIP_TLS_PORT = 60001;
    private static final int FIRST_GRPC_PORT = 50211;
    private static final int CAPTIVE_NODE_STARTUP_TIME_LIMIT = 300;
    private final List<HapiTestNode> nodes = new ArrayList<>();
    private final List<String> nodeHosts = new ArrayList<>();
    private boolean started = false;

    public HapiTestEnv(
            @NonNull final String testName, final int nodeCount, @NonNull final HapiTestNodesType nodesType) {
        try {
            final var sb = new StringBuilder();
            sb.append("swirld, ")
                    .append(testName)
                    .append("\n")
                    .append("\n# This next line is, hopefully, ignored.\n")
                    .append("app, HederaNode.jar\n\n#The following nodes make up this network\n");
            for (int nodeId = 0; nodeId < nodeCount; nodeId++) {
                final var nodeName = NODE_NAMES[nodeId];
                final var firstChar = nodeName.charAt(0);
                final var account = "0.0." + (3 + nodeId);
                sb.append("address, ")
                        .append(nodeId)
                        .append(", ")
                        .append(firstChar)
                        .append(", ")
                        .append(nodeName)
                        .append(", 1, 127.0.0.1, ")
                        .append(FIRST_GOSSIP_PORT + (nodeId * 2))
                        .append(", 127.0.0.1, ")
                        .append(FIRST_GOSSIP_TLS_PORT + (nodeId * 2))
                        .append(", ")
                        .append(account)
                        .append("\n");
                nodeHosts.add("127.0.0.1:" + (FIRST_GRPC_PORT + (nodeId * 2)) + ":" + account);
            }
            sb.append("\nnextNodeId, ").append(nodeCount).append("\n");
            final String configText = sb.toString();

            for (int nodeId = 0; nodeId < nodeCount; nodeId++) {
                final Path workingDir =
                        Path.of("./build/hapi-test/node" + nodeId).normalize();
                setupWorkingDirectory(workingDir, configText);
                final String nodeName = NODE_NAMES[nodeId];
                final AccountID acct =
                        AccountID.newBuilder().accountNum(3L + nodeId).build();
                boolean currentNodeAlice = nodeId == 0;
                if (IN_PROCESS_ALICE == nodesType && currentNodeAlice) {
                    nodes.add(new InProcessHapiTestNode(nodeName, nodeId, acct, workingDir, FIRST_GRPC_PORT));
                } else {
                    nodes.add(new SubProcessHapiTestNode(
                            nodeName, nodeId, acct, workingDir, FIRST_GRPC_PORT + (nodeId * 2)));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Defines the types of nodes(InProcessHapiTestNode or SubProcessHapiTestNode) for the test executing nodes.
    enum HapiTestNodesType {
        // Makes the fist starting node(Alice, id=0) to use InProcessHapiTestNode. This gives us the ability to debug
        IN_PROCESS_ALICE,
        OUT_OF_PROCESS_ALICE
    }

    /**
     * Starts all nodes in the environment.
     */
    public void start() throws TimeoutException {
        started = true;
        for (final var node : nodes) {
            logger.info("Starting node {}", node.getName());
            try {
                node.start();
            } catch (RuntimeException e) {
                logger.error(
                        "Node {} failed to start within {} seconds", node.getName(), CAPTIVE_NODE_STARTUP_TIME_LIMIT);
                throw e;
            }
        }
        for (final var node : nodes) {
            try {
                node.waitForActive(CAPTIVE_NODE_STARTUP_TIME_LIMIT);
            } catch (TimeoutException e) {
                logger.error(
                        "Node {} failed to ACTIVE within {} seconds", node.getName(), CAPTIVE_NODE_STARTUP_TIME_LIMIT);
                throw e;
            }
        }
    }

    /**
     * Forcibly terminates all nodes in the environment. Once terminated, an environment can be started again.
     */
    public void terminate() {
        for (final var node : nodes) {
            node.terminate();
        }
        started = false;
    }

    /**
     * Gets whether this environment has been started and not terminated.
     */
    public boolean started() {
        return started;
    }

    /**
     * Gets node info suitable for the HAPI test system's configuration
     */
    public String getNodeInfo() {
        return String.join(",", nodeHosts);
    }

    /**
     * Gets the list of nodes that make up this test environment.
     */
    public List<HapiTestNode> getNodes() {
        return nodes;
    }

    private void setupWorkingDirectory(@NonNull final Path workingDir, @NonNull final String configText) {
        try {
            if (Files.exists(workingDir)) {
                Files.walk(workingDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }

            Files.createDirectories(workingDir);
            Files.createDirectories(workingDir.resolve("data").resolve("keys"));
            Files.createDirectories(workingDir.resolve("data").resolve("config"));

            final var configTextFile = workingDir.resolve("config.txt");
            Files.writeString(configTextFile, configText);

            final var configDir =
                    Path.of("../configuration/dev").toAbsolutePath().normalize();
            Files.walk(configDir).filter(file -> !file.equals(configDir)).forEach(file -> {
                try {
                    if (file.getFileName().toString().contains(".properties")) {
                        Files.copy(
                                file,
                                workingDir
                                        .resolve("data")
                                        .resolve("config")
                                        .resolve(file.getFileName().toString()));
                    } else {
                        Files.copy(file, workingDir.resolve(file.getFileName().toString()));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Update the log4j2.xml, so it contains absolute paths to the files instead of relative paths,
            // because node0 is always relative to the test-clients base dir instead of the appropriate node0
            // dir in the build directory
            final var log4j2File = workingDir.resolve("log4j2.xml");
            final var logConfig = Files.readString(log4j2File);
            final var updatedLogConfig = logConfig
                    .replace(
                            "</Appenders>\n" + "  <Loggers>",
                            """
                                      <RollingFile name="TestClientRollingFile" fileName="output/test-clients.log"
                                        filePattern="output/test-clients-%d{yyyy-MM-dd}-%i.log.gz">
                                        <PatternLayout>
                                          <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p %-4L %c{1} - %m{nolookups}%n</pattern>
                                        </PatternLayout>
                                        <Policies>
                                          <TimeBasedTriggeringPolicy/>
                                          <SizeBasedTriggeringPolicy size="100 MB"/>
                                        </Policies>
                                        <DefaultRolloverStrategy max="10">
                                          <Delete basePath="output" maxDepth="3">
                                            <IfFileName glob="test-clients-*.log.gz">
                                              <IfLastModified age="P3D"/>
                                            </IfFileName>
                                          </Delete>
                                        </DefaultRolloverStrategy>
                                      </RollingFile>
                                    </Appenders>
                                    <Loggers>

                                      <Logger name="com.hedera.services.bdd" level="info" additivity="false">
                                        <AppenderRef ref="Console"/>
                                        <AppenderRef ref="TestClientRollingFile"/>
                                      </Logger>
                                    """)
                    .replace(
                            "output/",
                            workingDir.resolve("output").toAbsolutePath().normalize() + "/");

            Files.writeString(log4j2File, updatedLogConfig, StandardOpenOption.WRITE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
