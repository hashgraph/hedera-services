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

import static com.hedera.services.bdd.spec.persistence.Entity.UNUSED_KEY;
import static com.hedera.services.bdd.spec.persistence.PemKey.RegistryForms.under;
import static com.hedera.services.bdd.spec.persistence.PemKey.submitKeyFor;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;

public class Topic {
	private PemKey adminKey = UNUSED_KEY;
	private PemKey submitKey = UNUSED_KEY;

	public void registerWhatIsKnown(HapiApiSpec spec, String name, Optional<EntityId> entityId) {
		if (adminKey != UNUSED_KEY) {
			adminKey.registerWith(spec, under(name));
		}
		if (submitKey != UNUSED_KEY) {
			submitKey.registerWith(spec, under(submitKeyFor(name)));
		}
		entityId.ifPresent(id -> {
			spec.registry().saveTopicId(name, id.asTopic());
		});
	}

	public HapiSpecOperation createOp(String name) {
		var op = createTopic(name)
				.advertisingCreation();

		if (adminKey != UNUSED_KEY) {
			op.adminKeyName(name);
		}
		if (submitKey != UNUSED_KEY) {
			op.submitKeyName(submitKeyFor(name));
		}

		return op;
	}

	public PemKey getAdminKey() {
		return adminKey;
	}

	public void setAdminKey(PemKey adminKey) {
		this.adminKey = adminKey;
	}

	public PemKey getSubmitKey() {
		return submitKey;
	}

	public void setSubmitKey(PemKey submitKey) {
		this.submitKey = submitKey;
	}
}
