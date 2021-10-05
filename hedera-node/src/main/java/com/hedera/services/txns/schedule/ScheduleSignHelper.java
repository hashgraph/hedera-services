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

import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.util.List;

public final class ScheduleSignHelper {

	private static final SigMapScheduleClassifier CLASSIFIER = new SigMapScheduleClassifier();
	private static final SignatoryUtils.ScheduledSigningsWitness SCHEDULED_SIGNINGS_WITNESS = SignatoryUtils::witnessScoped;

	private ScheduleSignHelper() {
		throw new UnsupportedOperationException("Utility Class");
	}

	public static Pair<ResponseCodeEnum, Boolean> signingOutcome(
			final List<JKey> topLevelKeys,
			final SignatureMap sigMap,
			final ScheduleID scheduleId,
			final ScheduleStore store,
			@Nonnull final InHandleActivationHelper activationHelper
			) {
		var validScheduleKeys = CLASSIFIER.validScheduleKeys(
				topLevelKeys,
				sigMap,
				activationHelper.currentSigsFn(),
				activationHelper::visitScheduledCryptoSigs);

		return SCHEDULED_SIGNINGS_WITNESS.observeInScope(scheduleId, store, validScheduleKeys, activationHelper);
	}
}
