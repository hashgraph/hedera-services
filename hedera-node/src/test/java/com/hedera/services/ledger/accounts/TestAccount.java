package com.hedera.services.ledger.accounts;

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
import com.hedera.services.ledger.TokenViewMergeable;

public class TestAccount implements TokenViewMergeable<TestAccount> {
	private static final long DEFAULT_TOKEN_THING = 123L;

	public long value;
	public long tokenThing;
	public Object thing;
	public boolean flag;

	public TestAccount() {
		tokenThing = DEFAULT_TOKEN_THING;
	}

	public TestAccount(long value, Object thing, boolean flag, long tokenThing) {
		this.value = value;
		this.thing = thing;
		this.flag = flag;
		this.tokenThing = tokenThing;
	}

	public TestAccount(long value, Object thing, boolean flag) {
		this(value, thing, flag, DEFAULT_TOKEN_THING);
	}


	public Object getThing() {
		return thing;
	}

	public void setThing(Object thing) {
		this.thing = thing;
	}

	public boolean isFlag() {
		return flag;
	}

	public void setFlag(boolean flag) {
		this.flag = flag;
	}

	public long getValue() {
		return value;
	}

	public void setValue(long value) {
		this.value = value;
	}

	public long getTokenThing() {
		return tokenThing;
	}

	public void setTokenThing(long tokenThing) {
		this.tokenThing = tokenThing;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof TestAccount)) {
			return false;
		}
		TestAccount that = (TestAccount) o;
		return thing.equals(that.thing)
				&& (flag == that.flag)
				&& (value == that.value)
				&& this.tokenThing == that.tokenThing;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("flag", flag)
				.add("thing", thing)
				.add("value", value)
				.add("tokenThing", tokenThing)
				.toString();
	}

	@Override
	public void mergeTokenPropertiesFrom(TestAccount viewSoFar) {
		this.tokenThing = viewSoFar.tokenThing;
	}

	@Override
	public String readableTokenRelationships() {
		return "OK(" + tokenThing + ")";
	}
}
