package com.hedera.services.bdd.spec.utilops.inventory;

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

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.Key;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyGenerator;
import com.hedera.services.bdd.spec.keys.KeyLabel;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hederahashgraph.api.proto.java.KeyList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType;
import static java.util.stream.Collectors.toList;

public class NewSpecKeyList extends UtilOp {
	static final Logger log = LogManager.getLogger(NewSpecKeyList.class);

	private final String name;
	private final List<String> keys;

	public NewSpecKeyList(String name, List<String> keys) {
		this.name = name;
		this.keys = keys;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		List<Key> childKeys = keys.stream()
				.map(spec.registry()::getKey)
				.collect(toList());
		Key newList = Key.newBuilder()
				.setKeyList(KeyList.newBuilder().addAllKeys(childKeys))
				.build();
		spec.registry().saveKey(name, newList);

		SigControl[] childControls = childKeys.stream()
				.map(spec.keys()::controlFor)
				.toArray(SigControl[]::new);
		spec.keys().setControl(newList, SigControl.listSigs(childControls));

		return false;
	}


	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper().add("name", name);
	}
}
