package com.hedera.services.bdd.spec.assertions;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;

import static java.util.Arrays.*;

import java.nio.charset.Charset;

public class ContractLogAsserts extends BaseErroringAssertsProvider<ContractLoginfo> {
	public static ContractLogAsserts logWith() {
		return new ContractLogAsserts();
	}

	public ContractLogAsserts utf8data(String data) {
		registerProvider((spec, o) -> {
			String actual = new String(dataFrom(o), Charset.forName("UTF-8"));
			Assert.assertEquals("Wrong UTF-8 data!", data, actual);
		});
		return this;
	}

	public ContractLogAsserts accountAtBytes(String account, int start) {
		registerProvider((spec, o) -> {
			byte[] data = dataFrom(o);
			System.out.println("Length of event: " + data.length);
			AccountID expected = spec.registry().getAccountID(account);
			AccountID actual = accountFromBytes(data, start);
			Assert.assertEquals("Bad account in log data, starting at byte " + start, expected, actual);
		});
		return this;
	}

	public ContractLogAsserts longAtBytes(long expected, int start) {
		registerProvider((spec, o) -> {
			byte[] data = dataFrom(o);
			long actual = ByteUtil.byteArrayToLong(copyOfRange(data, start, start + 8));
			Assert.assertEquals("Bad long value in log data, starting at byte " + start, expected, actual);
		});
		return this;
	}

	static AccountID accountFromBytes(byte[] data, int start) {
		long shard = ByteUtil.byteArrayToLong(copyOfRange(data, start, start + 4));
		long realm = ByteUtil.byteArrayToLong(copyOfRange(data, start + 4, start + 12));
		long seq = ByteUtil.byteArrayToLong(copyOfRange(data, start + 12, start + 20));
		return AccountID.newBuilder().setAccountNum(seq).setRealmNum(realm).setShardNum(shard).build();
	}

	static byte[] dataFrom(Object o) {
		ContractLoginfo entry = (ContractLoginfo) o;
		return entry.getData().toByteArray();
	}
}
