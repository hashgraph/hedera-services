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

import java.util.Optional;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;

public class Entity {
	private static final Token UNSPECIFIED_TOKEN = null;
	private static final EntityId UNCREATED_ENTITY_ID = null;

	private String name;
	private EntityId id = UNCREATED_ENTITY_ID;
	private Token token = UNSPECIFIED_TOKEN;

	public void registerWhatIsKnown(HapiApiSpec spec) {
		if (token != UNSPECIFIED_TOKEN) {
			token.registerWhatIsKnown(spec, name, Optional.ofNullable(id));
		}
	}

	public HapiSpecOperation createOp() {
		if (token != UNSPECIFIED_TOKEN) {
			return token.createOp(name);
		}
		return assertionsHold((spec, opLog) -> {});
	}

	public boolean needsCreation() {
		return id == UNCREATED_ENTITY_ID;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public EntityId getId() {
		return id;
	}

	public void setId(EntityId id) {
		this.id = id;
	}

	public Token getToken() {
		return token;
	}

	public void setToken(Token token) {
		this.token = token;
	}
}
