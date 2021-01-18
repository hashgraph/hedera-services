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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.HederaKeyActivation;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;
import static com.hedera.services.keys.HederaKeyActivation.ONLY_IF_SIG_IS_VALID;
import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static com.hedera.services.txns.validation.ScheduleChecks.checkAdminKey;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ScheduleCreateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(ScheduleCreateTransitionLogic.class);

	private static final ScheduleID NOT_YET_RESOLVED = null;

	private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

	private final ScheduleStore store;
	private final TransactionContext txnCtx;
	private final InHandleActivationHelper activationHelper;

	public ScheduleCreateTransitionLogic(
			ScheduleStore store,
			TransactionContext txnCtx,
			InHandleActivationHelper activationHelper
	) {
		this.store = store;
		this.txnCtx = txnCtx;
		this.activationHelper = activationHelper;
	}

	@Override
	public void doStateTransition() {
		try {
			transitionFor(txnCtx.accessor().getTxn().getScheduleCreate());
		} catch (Exception e) {
			e.printStackTrace();
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxn4Log(), e);
			abortWith(FAIL_INVALID);
		}
	}

	private void transitionFor(ScheduleCreateTransactionBody op) {
		var scheduleId = NOT_YET_RESOLVED;
		var scheduledPayer = op.hasPayerAccountID() ? op.getPayerAccountID() : txnCtx.activePayer();

		var sb = new StringBuilder();

		var extantId = store.lookupScheduleId(op.getTransactionBody().toByteArray(), scheduledPayer);
		if (extantId.isPresent()) {
			scheduleId = extantId.get();
		} else {
			var result = store.createProvisionally(
					op.getTransactionBody().toByteArray(),
					scheduledPayer,
					txnCtx.activePayer(),
					fromJava(txnCtx.consensusTime()),
					adminKeyFor(op));
			if (result.getCreated().isEmpty()) {
				abortWith(result.getStatus());
				return;
			}
			scheduleId = result.getCreated().get();
		}

		sb.append(" - Resolved scheduleId: ").append(readableId(scheduleId)).append("\n");
		var scheduledTxn = uncheckedParse(op.getTransactionBody());
		if (activationHelper.areScheduledPartiesActive(scheduledTxn, ONLY_IF_SIG_IS_VALID)) {
			sb.append(" - Sigs are already valid!").append("\n");
		} else {
			sb.append(" - Sigs not yet valid.").append("\n");
		}

		/* Uncomment for temporary log-based testing locally */
		if (store == CONTEXTS.lookup(0L).scheduleStore()) {
			log.info("\n>>> START ScheduleCreate >>>\n{}<<< END ScheduleCreate END <<<", sb);
		}

		if (store.isCreationPending()) {
			store.commitCreation();
		}
		txnCtx.setCreated(scheduleId);
		txnCtx.setStatus(SUCCESS);
	}

	private Optional<JKey> adminKeyFor(ScheduleCreateTransactionBody op) {
		return op.hasAdminKey() ? asUsableFcKey(op.getAdminKey()) : Optional.empty();
	}

	private TransactionBody uncheckedParse(ByteString rawScheduledTxn) {
		try {
			return TransactionBody.parseFrom(rawScheduledTxn);
		} catch (InvalidProtocolBufferException e) {
			throw new AssertionError("Not implemented!");
		}
	}

	private void abortWith(ResponseCodeEnum cause) {
		if (store.isCreationPending()) {
			store.rollbackCreation();
		}
		txnCtx.setStatus(cause);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasScheduleCreate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txn) {
		var op = txn.getScheduleCreate();
		return checkAdminKey(op.hasAdminKey(), op.getAdminKey());
	}
}
