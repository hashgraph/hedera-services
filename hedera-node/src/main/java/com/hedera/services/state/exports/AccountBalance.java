package com.hedera.services.state.exports;

/*-
 * ‌
 * Hedera Services Node
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

import com.google.common.base.MoreObjects;

import java.util.Comparator;

public class AccountBalance implements Comparable<AccountBalance> {
	private static final Comparator<AccountBalance> CANONICAL_ORDER = Comparator
			.comparingLong(AccountBalance::getShard)
			.thenComparingLong(AccountBalance::getRealm)
			.thenComparingLong(AccountBalance::getNum);

	private long num;
	private long shard;
	private long realm;
	private long balance;
	private String b64TokenBalances = "";

	public AccountBalance(
			long shard,
			long realm,
			long num,
			long balance
	) {
		this.num = num;
		this.shard = shard;
		this.realm = realm;
		this.balance = balance;
	}

	@Override
	public String toString() {
		var helper = MoreObjects.toStringHelper(this)
				.add("account", String.format("%d.%d.%d", shard, realm, num))
				.add("balance", balance);
		if (b64TokenBalances.length() > 0) {
			helper.add("b64TokenBalances", b64TokenBalances);
		}
		return helper.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || o.getClass() != AccountBalance.class) {
			return false;
		}
		AccountBalance that = (AccountBalance) o;
		return this.num == that.num
				&& this.realm == that.realm
				&& this.shard == that.shard
				&& this.b64TokenBalances.equals(that.b64TokenBalances);
	}

	@Override
	public int compareTo(AccountBalance that) {
		return CANONICAL_ORDER.compare(this, that);
	}

	public long getShard() {
		return shard;
	}

	public void setShard(long shard) {
		this.shard = shard;
	}

	public long getRealm() {
		return realm;
	}

	public void setRealm(long realm) {
		this.realm = realm;
	}

	public long getNum() {
		return num;
	}

	public void setNum(long num) {
		this.num = num;
	}

	public long getBalance() {
		return balance;
	}

	public void setBalance(long balance) {
		this.balance = balance;
	}

	public void setB64TokenBalances(String b64TokenBalances) {
		this.b64TokenBalances = b64TokenBalances;
	}

	public String getB64TokenBalances() {
		return b64TokenBalances;
	}
}
