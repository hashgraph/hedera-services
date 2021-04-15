package com.hedera.services.legacy.unit.utils;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class DummyHapiPermissions extends HapiOpPermissions {
	final ResponseCodeEnum always;

	public DummyHapiPermissions() {
		super(new MockAccountNumbers());
		always = OK;
	}

	public DummyHapiPermissions(ResponseCodeEnum always) {
		super(new MockAccountNumbers());
		this.always = always;
	}

	@Override
	public ResponseCodeEnum permissibilityOf(HederaFunctionality function, AccountID givenPayer) {
		return always;
	}
}
