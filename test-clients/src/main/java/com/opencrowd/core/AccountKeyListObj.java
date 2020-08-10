package com.opencrowd.core;

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

import com.opencrowd.core.KeyPairObj;
import com.hederahashgraph.api.proto.java.AccountID;

import java.io.Serializable;
import java.util.List;

public class AccountKeyListObj implements Serializable {
	private static final long serialVersionUID = -4429672793456228453L;

	private AccountID accountId;
	private List<KeyPairObj> keyPairList;

	public AccountKeyListObj(AccountID accountId, List<KeyPairObj> keyPairList) {
		this.accountId = accountId;
		this.keyPairList = keyPairList;
	}

	public List<KeyPairObj> getKeyPairList() {
		return keyPairList;
	}

	public void setKeyPairList(List<KeyPairObj> keyPairList) {
		this.keyPairList = keyPairList;
	}

	public AccountID getAccountId() {
		return accountId;
	}

	public void setAccountId(AccountID accountId) {
		this.accountId = accountId;
	}
}
