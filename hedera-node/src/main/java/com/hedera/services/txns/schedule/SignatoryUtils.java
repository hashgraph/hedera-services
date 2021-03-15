package com.hedera.services.txns.schedule;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;

public class SignatoryUtils {
	@FunctionalInterface
	interface ScheduledSigningsWitness {
		Pair<ResponseCodeEnum, Boolean> observeInScope(
				ScheduleID id,
				ScheduleStore store,
				Optional<List<JKey>> validScheduleKeys,
				InHandleActivationHelper activationHelper);
	}

	static Pair<ResponseCodeEnum, Boolean> witnessScoped(
			ScheduleID id,
			ScheduleStore store,
			Optional<List<JKey>> validScheduleKeys,
			InHandleActivationHelper activationHelper
	) {
		if (validScheduleKeys.isEmpty()) {
			return Pair.of(SOME_SIGNATURES_WERE_INVALID, false);
		}
		var status = witnessNonTriviallyScoped(validScheduleKeys.get(), id, store);
		var updatedSchedule = store.get(id);
		var isReadyToExecute = isReady(updatedSchedule, activationHelper);
		if (isReadyToExecute) {
			status = OK;
		}
		return Pair.of(status, isReadyToExecute);
	}

	private static ResponseCodeEnum witnessNonTriviallyScoped(
			List<JKey> valid,
			ScheduleID id,
			ScheduleStore store
	) {
		List<byte[]> signatories = new ArrayList<>();
		for (JKey key : valid) {
			appendIfUnique(signatories, key.getEd25519());
		}
		return witnessAnyNew(store, id, signatories) ? OK : NO_NEW_VALID_SIGNATURES;
	}

	private static void appendIfUnique(List<byte[]> l, byte[] bytes) {
		for (byte[] extant : l) {
			if (Arrays.equals(extant, bytes)) {
				return;
			}
		}
		l.add(bytes);
	}

	private static boolean isReady(MerkleSchedule schedule, InHandleActivationHelper activationHelper) {
		return activationHelper.areScheduledPartiesActive(
				schedule.ordinaryViewOfScheduledTxn(),
				(key, sig) -> schedule.hasValidEd25519Signature(key.getEd25519()));
	}

	private static boolean witnessAnyNew(ScheduleStore store, ScheduleID id, List<byte[]> signatories) {
		var witnessedNew = new AtomicBoolean(false);
		store.apply(id, schedule -> {
			for (byte[] key : signatories) {
				if (schedule.witnessValidEd25519Signature(key)) {
					witnessedNew.set(true);
				}
			}
		});
		return witnessedNew.get();
	}
}
