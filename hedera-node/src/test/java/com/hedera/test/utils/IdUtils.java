package com.hedera.test.utils;

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

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenBalance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hedera.services.state.merkle.MerkleEntityId;

import java.util.stream.Stream;

public class IdUtils {

	public static TokenID tokenWith(long num) {
		return TokenID.newBuilder()
				.setShardNum(0)
				.setRealmNum(0)
				.setTokenNum(num)
				.build();
	}

	public static TopicID asTopic(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return TopicID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setTopicNum(nativeParts[2])
				.build();
	}

	public static AccountID asAccount(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return AccountID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setAccountNum(nativeParts[2])
				.build();
	}

	public static AccountID fromKey(MerkleEntityId mk) {
		return asAccount(String.format("%d.%d.%d", mk.getShard(), mk.getRealm(), mk.getNum()));
	}

	public static ContractID asContract(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return ContractID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setContractNum(nativeParts[2])
				.build();
	}

	public static FileID asFile(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return FileID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setFileNum(nativeParts[2])
				.build();
	}

	public static TokenID asToken(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return TokenID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setTokenNum(nativeParts[2])
				.build();
	}

	public static ScheduleID asSchedule(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return ScheduleID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setScheduleNum(nativeParts[2])
				.build();
	}

	static long[] asDotDelimitedLongArray(String s) {
		String[] parts = s.split("[.]");
		return Stream.of(parts).mapToLong(Long::valueOf).toArray();
	}

	public static String asAccountString(AccountID account) {
		return String.format("%d.%d.%d", account.getShardNum(), account.getRealmNum(), account.getAccountNum());
	}

	public static TokenBalance tokenBalanceWith(long id, long balance) {
		return TokenBalance.newBuilder()
				.setTokenId(IdUtils.asToken("0.0." + id))
				.setBalance(balance)
				.build();
	}

	public static TokenBalance tokenBalanceWith(TokenID id, long balance) {
		return TokenBalance.newBuilder()
				.setTokenId(id)
				.setBalance(balance)
				.build();
	}

	public static AccountAmount adjustFrom(AccountID account, long amount) {
		return AccountAmount.newBuilder()
				.setAccountID(account)
				.setAmount(amount)
				.build();
	}
}
