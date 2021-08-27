package com.hedera.services.store.tokens.views.internals;

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

import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;

/**
 * An integer whose {@code hashCode()} implementation vastly reduces
 * the risk of hash collisions in structured data using this type,
 * when compared to the {@code java.lang.Integer} boxed type.
 *
 * May no longer be necessary after {@link com.swirlds.fchashmap.FCOneToManyRelation}
 * improves internal hashing.
 */
public class PermHashInteger {
	private final int value;

	public PermHashInteger(int value) {
		this.value = value;
	}

	public static PermHashInteger fromInt(int i) {
		return new PermHashInteger(i);
	}

	public static PermHashInteger fromLong(long i) {
		return new PermHashInteger((int) i);
	}

	public static PermHashInteger fromAccountId(AccountID grpc) {
		return fromLong(grpc.getAccountNum());
	}

	public static PermHashInteger fromTokenId(TokenID grpc) {
		return fromLong(grpc.getTokenNum());
	}

	public static PermHashInteger fromTopicId(TopicID grpc) {
		return fromLong(grpc.getTopicNum());
	}

	public static PermHashInteger fromContractId(ContractID grpc) {
		return fromLong(grpc.getContractNum());
	}

	public static PermHashInteger fromScheduleId(ScheduleID grpc) {
		return fromLong(grpc.getScheduleNum());
	}

	public ScheduleID asGrpcScheduleId() {
		throw new AssertionError("Not implemented!");
	}

	public AccountID asGrpcAccountId() {
		throw new AssertionError("Not implemented!");
	}

	public TokenID asGrpcTokenId() {
		throw new AssertionError("Not implemented!");
	}

	public String asAbbrevString() {
		throw new AssertionError("Not implemented!");
	}

	@Override
	public int hashCode() {
		return (int) MiscUtils.perm64(value);
	}

	public int getValue() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || PermHashInteger.class != o.getClass()) {
			return false;
		}

		var that = (PermHashInteger) o;

		return this.value == that.value;
	}
}
