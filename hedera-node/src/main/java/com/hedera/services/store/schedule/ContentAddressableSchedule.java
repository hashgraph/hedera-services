package com.hedera.services.store.schedule;

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

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.Key;

import java.util.Objects;

/**
 * Set of properties used to describe unique instance of Scheduled Transaction
 */
public class ContentAddressableSchedule {
	static final Key UNUSED_KEY = Key.getDefaultInstance();
	static final String EMPTY_MEMO = null;

	final Key adminKey;
	final String entityMemo;
	final EntityId payer;
	ByteString transactionBytes;

	ContentAddressableSchedule(
			Key adminKey,
			String entityMemo,
			EntityId payer,
			byte[] transactionBytes
	) {
		this.adminKey = adminKey;
		this.entityMemo = entityMemo;
		this.payer = payer;
		this.transactionBytes = ByteString.copyFrom(transactionBytes);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		} else if (!(o instanceof ContentAddressableSchedule)) {
			return false;
		}
		ContentAddressableSchedule that = (ContentAddressableSchedule) o;

		return Objects.equals(this.entityMemo, that.entityMemo) &&
				Objects.equals(this.payer, that.payer) &&
				Objects.equals(this.adminKey, that.adminKey) &&
				Objects.equals(this.transactionBytes, that.transactionBytes);
	}

	@Override
	public final int hashCode() {
		return Objects.hash( payer, adminKey, entityMemo, transactionBytes);
	}

	static ContentAddressableSchedule fromMerkleSchedule(MerkleSchedule schedule) {
		var memo = schedule.memo().orElse(EMPTY_MEMO);
		var adminKey = schedule.adminKey().map(MiscUtils::asKeyUnchecked).orElse(UNUSED_KEY);

		return new ContentAddressableSchedule(
				adminKey,
				memo,
				schedule.payer(),
				schedule.transactionBody());
	}
}
