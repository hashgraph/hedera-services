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

import com.hedera.services.bdd.junit.hedera.HederaNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class WorkingDirUtils {
    private static final int FIRST_GOSSIP_PORT = 60000;
    private static final int FIRST_GOSSIP_TLS_PORT = 60001;

    private WorkingDirUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Creates an address book (i.e., <i>config.txt</i>) for the given
     * network name and nodes.
     *
     * @param networkName the name of the network
     * @param nodes the nodes of the network
     * @return the address book
     */
    public static String configTxtFor(@NonNull final String networkName, @NonNull final List<HederaNode> nodes) {
        final var sb = new StringBuilder();
        sb.append("swirld, ")
                .append(networkName)
                .append("\n")
                .append("\n# This next line is, hopefully, ignored.\n")
                .append("app, HederaNode.jar\n\n#The following nodes make up this network\n");
        for (final var node : nodes) {
            sb.append("address, ")
                    .append(node.getId())
                    .append(", ")
                    .append(node.getName().charAt(0))
                    .append(", ")
                    .append(node.getName())
                    .append(", 1, 127.0.0.1, ")
                    .append(FIRST_GOSSIP_PORT + (node.getId() * 2))
                    .append(", 127.0.0.1, ")
                    .append(FIRST_GOSSIP_TLS_PORT + (node.getId() * 2))
                    .append(", ")
                    .append("0.0.")
                    .append(node.getAccountId().accountNumOrThrow())
                    .append("\n");
        }
        sb.append("\nnextNodeId, ").append(nodes.size()).append("\n");
        return sb.toString();
    }

    /**
     * Recursively deletes the given path.
     *
     * @param path the path to delete
     */
    public static void rm(@NonNull final Path path) {
        if (Files.exists(path)) {
            try (Stream<Path> paths = Files.walk(path)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
