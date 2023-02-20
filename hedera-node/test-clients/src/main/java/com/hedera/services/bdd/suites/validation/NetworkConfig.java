/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.validation;

import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NetworkConfig {
    private static final Long DEFAULT_BOOTSTRAP_ACCOUNT = 2L;
    private static final Long DEFAULT_STARTUP_NODE_ACCOUNT = 3L;
    private static final String DEFAULT_NAME = "default";
    private static final String DEFAULT_BOOTSTRAP_PEM_KEY_LOC_TPL = "%s/keys/account%d.pem";
    private static final String DEFAULT_BOOTSTRAP_PEM_KEY_PASSPHRASE = "swirlds";
    private static final String DEFAULT_PERSISTENT_ENTITIES_DIR_TPL = "%s-entities";

    private Long bootstrapAccount = DEFAULT_BOOTSTRAP_ACCOUNT;
    private Long startupNodeAccount = DEFAULT_STARTUP_NODE_ACCOUNT;
    private String name = DEFAULT_NAME;
    private String bootstrapPemKeyLoc;
    private String persistentEntitiesDir;
    private String bootstrapPemKeyPassphrase = DEFAULT_BOOTSTRAP_PEM_KEY_PASSPHRASE;
    private List<NetworkNodeConfig> nodes;

    public NetworkConfig named(String name) {
        this.name = name;
        return this;
    }

    public Map<String, String> toCustomProperties(MiscConfig miscConfig) {
        return Map.of(
                "nodes", nodes.stream().map(Object::toString).collect(joining(",")),
                "default.payer.pemKeyLoc", effBootstrapPemKeyLoc(),
                "default.payer.pemKeyPassphrase", bootstrapPemKeyPassphrase,
                "persistentEntities.dir.path", effPersistentEntitiesDir(),
                "persistentEntities.updateCreatedManifests", miscConfig.updateCreatedManifests(),
                "default.node", String.format("0.0.%d", startupNodeAccount));
    }

    private String effBootstrapPemKeyLoc() {
        return Optional.ofNullable(bootstrapPemKeyLoc)
                .orElseGet(() ->
                        String.format(DEFAULT_BOOTSTRAP_PEM_KEY_LOC_TPL, effPersistentEntitiesDir(), bootstrapAccount));
    }

    public String effPersistentEntitiesDir() {
        return Optional.ofNullable(persistentEntitiesDir)
                .orElseGet(() -> String.format(DEFAULT_PERSISTENT_ENTITIES_DIR_TPL, name));
    }

    public Long getStartupNodeAccount() {
        return startupNodeAccount;
    }

    public void setStartupNodeAccount(Long startupNodeAccount) {
        this.startupNodeAccount = startupNodeAccount;
    }

    public String getPersistentEntitiesDir() {
        return persistentEntitiesDir;
    }

    public void setPersistentEntitiesDir(String persistentEntitiesDir) {
        this.persistentEntitiesDir = persistentEntitiesDir;
    }

    public List<NetworkNodeConfig> getNodes() {
        return nodes;
    }

    public void setNodes(List<NetworkNodeConfig> nodes) {
        this.nodes = nodes;
    }

    public String getBootstrapPemKeyLoc() {
        return bootstrapPemKeyLoc;
    }

    public void setBootstrapPemKeyLoc(String bootstrapPemKeyLoc) {
        this.bootstrapPemKeyLoc = bootstrapPemKeyLoc;
    }

    public String getBootstrapPemKeyPassphrase() {
        return bootstrapPemKeyPassphrase;
    }

    public void setBootstrapPemKeyPassphrase(String bootstrapPemKeyPassphrase) {
        this.bootstrapPemKeyPassphrase = bootstrapPemKeyPassphrase;
    }

    public Long getBootstrapAccount() {
        return bootstrapAccount;
    }

    public void setBootstrapAccount(Long bootstrapAccount) {
        this.bootstrapAccount = bootstrapAccount;
    }
}
