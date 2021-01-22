package com.hedera.services.usage.schedule.entities;

/*
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RICH_INSTANT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.KEY_SIZE;

public enum ScheduleEntitySizes {
	SCHEDULE_ENTITY_SIZES;

	/* { deleted } */
	static int NUM_FLAGS_IN_BASE_SCHEDULE_REPRESENTATION = 1;
	/* { payer, schedulingAccount } */
	static int NUM_ENTITY_ID_FIELDS_IN_BASE_SCHEDULE_REPRESENTATION = 2;
	/* { schedulingTXValidStart } */
	static int NUM_RICH_INSTANT_FIELDS_IN_BASE_SCHEDULE_REPRESENTATION = 1;

	public int fixedBytesInScheduleRepr() {
		return NUM_FLAGS_IN_BASE_SCHEDULE_REPRESENTATION * BOOL_SIZE
				+ NUM_ENTITY_ID_FIELDS_IN_BASE_SCHEDULE_REPRESENTATION * BASIC_ENTITY_ID_SIZE
				+ NUM_RICH_INSTANT_FIELDS_IN_BASE_SCHEDULE_REPRESENTATION * BASIC_RICH_INSTANT_SIZE;
	}

	public int bytesInBaseReprGiven(byte[] transactionBody, ByteString memo) {
		return fixedBytesInScheduleRepr() + transactionBody.length + memo.size();
	}

	/**
	 * Signature map is not stored in state, we only need it for bpt
	 */
	public int bptScheduleReprGiven(SignatureMap sigMap) {
		return sigMap.toByteArray().length;
	}

	/**
	 * For a given Scheduled Entity, a set of simple JKEYs are stored
	 */
	public int sigBytesInScheduleReprGiven(SignatureMap sigMap) {
		return sigMap.getSigPairCount() * KEY_SIZE;
	}
}