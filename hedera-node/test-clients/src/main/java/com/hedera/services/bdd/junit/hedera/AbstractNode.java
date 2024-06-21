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

package com.hedera.services.bdd.junit.hedera;

import static com.hedera.services.bdd.junit.hedera.subprocess.ProcessUtils.STREAMS_DIR;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.APPLICATION_PROPERTIES;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.CONFIG_DIR;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.CONFIG_TXT;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.CURRENT_DIR;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.DATA_DIR;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.GENESIS_PROPERTIES;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.OUTPUT_DIR;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.UPGRADE_DIR;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;

public abstract class AbstractNode implements HederaNode {
    private static final String HGCAA_LOG = "hgcaa.log";
    private static final String LOG4J2_XML = "log4j2.xml";

    protected final NodeMetadata metadata;

    protected AbstractNode(@NonNull final NodeMetadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public String getHost() {
        return metadata.host();
    }

    @Override
    public int getPort() {
        return metadata.grpcPort();
    }

    @Override
    public long getNodeId() {
        return metadata.nodeId();
    }

    @Override
    public String getName() {
        return metadata.name();
    }

    @Override
    public AccountID getAccountId() {
        return metadata.accountId();
    }

    @Override
    public Path getExternalPath(@NonNull ExternalPath path) {
        requireNonNull(path);
        final var workingDir = requireNonNull(metadata.workingDir());
        return switch (path) {
            case APPLICATION_LOG -> workingDir.resolve(OUTPUT_DIR).resolve(HGCAA_LOG);
            case ADDRESS_BOOK -> workingDir.resolve(CONFIG_TXT);
            case GENESIS_PROPERTIES -> workingDir
                    .resolve(DATA_DIR)
                    .resolve(CONFIG_DIR)
                    .resolve(GENESIS_PROPERTIES);
            case APPLICATION_PROPERTIES -> workingDir
                    .resolve(DATA_DIR)
                    .resolve(CONFIG_DIR)
                    .resolve(APPLICATION_PROPERTIES);
            case LOG4J2_XML -> workingDir.resolve(LOG4J2_XML);
            case STREAMS_DIR -> workingDir
                    .resolve(DATA_DIR)
                    .resolve(STREAMS_DIR)
                    .resolve("record0.0." + getAccountId().accountNumOrThrow());
            case UPGRADE_ARTIFACTS_DIR -> workingDir
                    .resolve(DATA_DIR)
                    .resolve(UPGRADE_DIR)
                    .resolve(CURRENT_DIR);
        };
    }
}
