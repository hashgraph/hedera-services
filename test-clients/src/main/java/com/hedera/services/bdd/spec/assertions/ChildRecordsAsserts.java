package com.hedera.services.bdd.spec.assertions;

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
import com.hedera.services.bdd.spec.queries.QueryUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.assertions.EqualityAssertsProviderFactory.shouldBe;
import static com.hedera.services.bdd.spec.assertions.EqualityAssertsProviderFactory.shouldNotBe;
import static java.util.Collections.EMPTY_LIST;

public class ChildRecordsAsserts extends TransactionRecordAsserts {
	static final Logger log = LogManager.getLogger(ChildRecordsAsserts.class);

	public static ChildRecordsAsserts recordWith() {
		return new ChildRecordsAsserts();
	}


}
