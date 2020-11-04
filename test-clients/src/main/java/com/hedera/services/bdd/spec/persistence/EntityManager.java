package com.hedera.services.bdd.spec.persistence;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class EntityManager {
	static final Logger log = LogManager.getLogger(EntityManager.class);

	private final HapiApiSpec spec;

	public EntityManager(HapiApiSpec spec) {
		this.spec = spec;
	}

	List<Entity> entities = new ArrayList<>();

	public boolean init() {
		var entitiesDir = new File(spec.setup().persistentEntitiesDirPath());
		if (!entitiesDir.exists() || !entitiesDir.isDirectory()) {
			return true;
		}
		File[] yaml = entitiesDir.listFiles(f -> f.getAbsolutePath().endsWith(".yaml"));
		var yamlIn = new Yaml(new Constructor(Entity.class));
		for (File manifest : yaml) {
			try {
				log.info("Attempting to register an entity from '{}'", manifest.getPath());
				Entity entity = yamlIn.load(Files.newInputStream(manifest.toPath()));
				entity.registerWhatIsKnown(spec);
				entities.add(entity);
			} catch (IOException e) {
				log.error("Could not deserialize entity from '{}'!", manifest.getPath(), e);
				return false;
			}
		}
		return true;
	}

	public List<HapiSpecOperation> requiredCreations() {
		return entities.stream()
				.filter(Entity::needsCreation)
				.map(Entity::createOp)
				.collect(toList());
	}
}
