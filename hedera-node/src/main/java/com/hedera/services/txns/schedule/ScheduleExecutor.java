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
import com.hedera.services.context.TransactionContext;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.accessors.TriggeredTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Defines a final class to handle scheduled transaction execution once the scheduled transaction is signed by the
 * required number of parties.
 */
@Singleton
public final class ScheduleExecutor {
	@Inject
	public ScheduleExecutor() {
		// Default constructor
	}

	/**
	 * Given a {@link ScheduleID}, {@link ScheduleStore}, {@link TransactionContext} it first checks if the underlying
	 * transaction is already executed/deleted before attempting to execute and then returns response code after
	 * triggering the underlying transaction. A ResponseEnumCode of OK is returned upon successful trigger of the
	 * inner transaction. The arguments cannot be null, the return type would always be a proper ResponseEnumCode.
	 *
	 * @param id
	 * 		the id of the scheduled transaction
	 * @param store
	 * 		the relevant store of schedule entities
	 * @param txnCtx
	 * 		the active (parent) transaction context
	 * @return the result {@link ResponseCodeEnum} of triggering the scheduled entity
	 */
	ResponseCodeEnum processExecution(
			@Nonnull ScheduleID id,
			@Nonnull ScheduleStore store,
			@Nonnull TransactionContext txnCtx
	) throws InvalidProtocolBufferException {
		Objects.requireNonNull(id, "The id of the scheduled transaction cannot be null");
		Objects.requireNonNull(store, "The schedule entity store cannot be null");
		Objects.requireNonNull(txnCtx, "The active transaction context cannot be null");

		final var now = txnCtx.consensusTime();
		final var executionStatus = store.markAsExecuted(id, now);
		if (executionStatus != OK) {
			return executionStatus;
		}

		final var schedule = store.get(id);
		final var transaction = schedule.asSignedTxn();
		txnCtx.trigger(
				new TriggeredTxnAccessor(
						transaction.toByteArray(),
						schedule.effectivePayer().toGrpcAccountId(),
						id));
		return OK;
	}
}

