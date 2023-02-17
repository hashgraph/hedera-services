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

package com.hedera.services.bdd.spec.persistence;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static java.util.stream.Collectors.toList;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.utilops.grouping.InBlockingOrder;
import com.hedera.services.bdd.suites.validation.YamlHelper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class EntityManager {
    static final Logger log = LogManager.getLogger(EntityManager.class);

    private final HapiSpec spec;

    public EntityManager(HapiSpec spec) {
        this.spec = spec;
    }

    private List<Entity> entities = new ArrayList<>();
    private Map<String, EntityMeta> registeredEntityMeta = new HashMap<>();

    static final String KEYS_SUBDIR = "keys";
    static final String FILES_SUBDIR = "files";
    static final String TOKENS_SUBDIR = "tokens";
    static final String TOPICS_SUBDIR = "topics";
    static final String ACCOUNTS_SUBDIR = "accounts";
    static final String SCHEDULES_SUBDIR = "schedules";
    static final String CONTRACTS_SUBDIR = "contracts";
    static final String[] ALL_SUBDIRS = {
        KEYS_SUBDIR, TOKENS_SUBDIR, TOPICS_SUBDIR, ACCOUNTS_SUBDIR, SCHEDULES_SUBDIR, FILES_SUBDIR, CONTRACTS_SUBDIR
    };

    public static String accountLoc(String base, String name) {
        return typedLoc(ACCOUNTS_SUBDIR, base, name);
    }

    public static String tokenLoc(String base, String name) {
        return typedLoc(TOKENS_SUBDIR, base, name);
    }

    public static String fileLoc(String base, String name) {
        return typedLoc(FILES_SUBDIR, base, name);
    }

    public static String contractLoc(String base, String name) {
        return typedLoc(CONTRACTS_SUBDIR, base, name);
    }

    public static String topicLoc(String base, String name) {
        return typedLoc(TOPICS_SUBDIR, base, name);
    }

    public static String scheduleLoc(String base, String name) {
        return typedLoc(SCHEDULES_SUBDIR, base, name);
    }

    private static String typedLoc(String type, String base, String name) {
        return base + File.separator + type + File.separator + name;
    }

    public boolean init() {
        var parentEntitiesDir = spec.setup().persistentEntitiesDir();
        log.info("Top-level entities @ {}", parentEntitiesDir);
        if (canIgnoreDir(parentEntitiesDir)) {
            return true;
        }

        if (!parentEntitiesDir.endsWith(File.separator)) {
            parentEntitiesDir += File.separator;
        }

        var yamlIn = new Yaml(new Constructor(Entity.class));
        List<Entity> candEntities = new ArrayList<>();
        for (String subDir : ALL_SUBDIRS) {
            String entitiesDir = parentEntitiesDir + subDir;
            if (!canIgnoreDir(entitiesDir)) {
                var result = loadEntitiesFrom(entitiesDir, yamlIn, candEntities);
                if (!result) {
                    return false;
                }
            }
        }

        Collections.sort(candEntities);
        for (Entity entity : candEntities) {
            var name = entity.getName();
            if (registeredEntityMeta.containsKey(name)) {
                log.warn("Skipping entity from '{}', name '{}' already used!", entity.getManifestAbsPath(), name);
            } else {
                entity.registerWhatIsKnown(spec);
                entities.add(entity);
                registeredEntityMeta.put(name, new EntityMeta(entity, entity.needsCreation()));
            }
        }
        return true;
    }

    public void runExistenceChecks() {
        var checks = entities.stream()
                .map(Entity::existenceCheck)
                .map(HapiQueryOp::logged)
                .toArray(HapiSpecOperation[]::new);
        allRunFor(spec, checks);
    }

    private boolean loadEntitiesFrom(String entitiesLoc, Yaml yamlIn, List<Entity> candEntities) {
        var entitiesDir = new File(entitiesLoc);
        if (!entitiesDir.exists() || !entitiesDir.isDirectory()) {
            return true;
        }
        File[] manifests = entitiesDir.listFiles(f -> f.getAbsolutePath().endsWith(".yaml"));
        for (File manifest : manifests) {
            try {
                log.info("Attempting to register an entity from '{}'", manifest.getPath());
                Entity entity = yamlIn.load(Files.newInputStream(manifest.toPath()));
                entity.setManifestAbsPath(manifest.getAbsolutePath());
                if (entity.getName() == null) {
                    entity.setName(inferNameAt(manifest.getAbsolutePath()));
                }
                candEntities.add(entity);
            } catch (IOException e) {
                log.error("Could not deserialize entity from '{}'!", manifest.getPath(), e);
                return false;
            }
        }
        return true;
    }

    private String inferNameAt(String absPath) {
        int lastSlash = absPath.lastIndexOf(File.separator);
        int lastDot = absPath.lastIndexOf('.');
        return absPath.substring(lastSlash + 1, lastDot);
    }

    private boolean canIgnoreDir(String loc) {
        var dir = new File(loc);
        return !dir.exists() || !dir.isDirectory();
    }

    public void updateCreatedEntityManifests() {
        registeredEntityMeta.entrySet().stream()
                .map(Map.Entry::getValue)
                .filter(EntityMeta::neededCreation)
                .forEach(meta -> {
                    var entity = meta.getEntity();
                    var optionalCreatedId = extractCreated(entity.getCreateOp());
                    optionalCreatedId.ifPresent(createdId -> {
                        entity.setId(createdId);
                        entity.clearCreateOp();
                        YamlHelper.serializeEntity(entity, meta.getManifestLoc());
                    });
                });
    }

    private Optional<EntityId> extractCreated(HapiSpecOperation veiledCreationOp) {
        HapiTxnOp<?> creationOp;
        if (veiledCreationOp instanceof HapiTxnOp) {
            creationOp = (HapiTxnOp<?>) veiledCreationOp;
        } else {
            creationOp = (HapiTxnOp<?>) ((InBlockingOrder) veiledCreationOp).last();
        }
        var receipt = creationOp.getLastReceipt();
        EntityId createdEntityId = null;
        if (receipt.hasAccountID()) {
            createdEntityId = new EntityId(HapiPropertySource.asAccountString(receipt.getAccountID()));
        } else if (receipt.hasTokenID()) {
            createdEntityId = new EntityId(HapiPropertySource.asTokenString(receipt.getTokenID()));
        } else if (receipt.hasTopicID()) {
            createdEntityId = new EntityId(HapiPropertySource.asTopicString(receipt.getTopicID()));
        } else if (receipt.hasContractID()) {
            createdEntityId = new EntityId(HapiPropertySource.asContractString(receipt.getContractID()));
        } else if (receipt.hasFileID()) {
            createdEntityId = new EntityId(HapiPropertySource.asFileString(receipt.getFileID()));
        } else if (receipt.hasScheduleID()) {
            createdEntityId = new EntityId(HapiPropertySource.asScheduleString(receipt.getScheduleID()));
        } else if (receipt.hasContractID()) {
            createdEntityId = new EntityId(HapiPropertySource.asContractString(receipt.getContractID()));
        }
        return Optional.ofNullable(createdEntityId);
    }

    public List<HapiSpecOperation> requiredCreations() {
        return entities.stream()
                .filter(Entity::needsCreation)
                .map(Entity::createOp)
                .collect(toList());
    }

    static class EntityMeta {
        private final Entity entity;
        private final boolean needsCreation;

        public EntityMeta(Entity entity, boolean needsCreation) {
            this.entity = entity;
            this.needsCreation = needsCreation;
        }

        public String getManifestLoc() {
            return entity.getManifestAbsPath();
        }

        public boolean neededCreation() {
            return needsCreation;
        }

        public Entity getEntity() {
            return entity;
        }
    }
}
