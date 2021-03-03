package com.hedera.services.bdd.spec.persistence;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;

import java.util.Optional;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;

public class Entity implements Comparable<Entity> {
	enum Type {
		ACCOUNT, TOKEN, SCHEDULE, TOPIC, UNKNOWN
	}
	private static final Token UNSPECIFIED_TOKEN = null;
	private static final Topic UNSPECIFIED_TOPIC = null;
	private static final Account UNSPECIFIED_ACCOUNT = null;
	private static final Schedule UNSPECIFIED_SCHEDULE = null;
	private static final EntityId UNCREATED_ENTITY_ID = null;

	static final SpecKey UNUSED_KEY = null;
	private static final HapiTxnOp<?> UNNEEDED_CREATE_OP = null;

	private String name;
	private String manifestAbsPath = "<N/A>";
	private EntityId id = UNCREATED_ENTITY_ID;
	private Topic topic = UNSPECIFIED_TOPIC;
	private Token token = UNSPECIFIED_TOKEN;
	private Account account = UNSPECIFIED_ACCOUNT;
	private Schedule schedule = UNSPECIFIED_SCHEDULE;
	private HapiTxnOp<?> createOp = UNNEEDED_CREATE_OP;

	public static Entity newTokenEntity(String name, Token token) {
		var it = new Entity();
		it.setName(name);
		it.setToken(token);
		return it;
	}

	public static Entity newAccountEntity(String name, Account account) {
		var it = new Entity();
		it.setName(name);
		it.setAccount(account);
		return it;
	}

	@Override
	public int compareTo(Entity that) {
		return Integer.compare(this.specifiedEntityPriority(), that.specifiedEntityPriority());
	}

	public HapiQueryOp<?> existenceCheck() {
		if (token != UNSPECIFIED_TOKEN) {
			return token.existenceCheck(name);
		} else if (account != UNSPECIFIED_ACCOUNT) {
			return account.existenceCheck(name);
		} else if (schedule != UNSPECIFIED_SCHEDULE) {
			return schedule.existenceCheck(name);
		} else if (topic != UNSPECIFIED_TOPIC) {
			return topic.existenceCheck(name);
		} else {
			throw new IllegalStateException("Only accounts and tokens are currently supported!");
		}
	}

	private int specifiedEntityPriority() {
		return specified().ordinal();
	}

	private Type specified() {
		if (token != UNSPECIFIED_TOKEN) {
			return Type.TOKEN;
		} else if (account != UNSPECIFIED_ACCOUNT) {
			return Type.ACCOUNT;
		} else if (schedule != UNSPECIFIED_SCHEDULE) {
			return Type.SCHEDULE;
		} else if (topic != UNSPECIFIED_TOPIC) {
			return Type.TOPIC;
		} else {
			return Type.UNKNOWN;
		}
	}

	public void setManifestAbsPath(String manifestAbsPath) {
		this.manifestAbsPath = manifestAbsPath;
	}

	public String getManifestAbsPath() {
		return manifestAbsPath;
	}

	void registerWhatIsKnown(HapiApiSpec spec) {
		if (token != UNSPECIFIED_TOKEN) {
			token.registerWhatIsKnown(spec, name, Optional.ofNullable(id));
		} else if (topic != UNSPECIFIED_TOPIC) {
			topic.registerWhatIsKnown(spec, name, Optional.ofNullable(id));
		} else if (schedule != UNSPECIFIED_SCHEDULE) {
			schedule.registerWhatIsKnown(spec, name, Optional.ofNullable(id));
		} else if (account != UNSPECIFIED_ACCOUNT) {
			account.registerWhatIsKnown(spec, name, Optional.ofNullable(id));
		}
	}

	HapiSpecOperation createOp() {
		if (token != UNSPECIFIED_TOKEN) {
			return (createOp = token.createOp(name));
		} else if (topic != UNSPECIFIED_TOPIC) {
			return (createOp = topic.createOp(name));
		} else if (account != UNSPECIFIED_ACCOUNT) {
			return (createOp = account.createOp(name));
		} else if (schedule != UNSPECIFIED_SCHEDULE) {
			return (createOp = schedule.createOp(name));
		}
		return assertionsHold((spec, opLog) -> {});
	}

	boolean needsCreation() {
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

	public Topic getTopic() {
		return topic;
	}

	public void setTopic(Topic topic) {
		this.topic = topic;
	}

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	HapiTxnOp<?> getCreateOp() {
		return createOp;
	}

	public Schedule getSchedule() {
		return schedule;
	}

	public void setSchedule(Schedule schedule) {
		this.schedule = schedule;
	}

	void clearCreateOp() {
		this.createOp = UNNEEDED_CREATE_OP;
	}
}
