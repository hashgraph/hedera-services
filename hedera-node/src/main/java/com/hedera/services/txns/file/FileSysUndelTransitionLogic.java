package com.hedera.services.txns.file;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.files.HederaFs;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

@Singleton
public class FileSysUndelTransitionLogic implements TransitionLogic {
	private static final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_RUBBER_STAMP = ignore -> OK;

	private final HederaFs hfs;
	private final TransactionContext txnCtx;
	private final Map<EntityId, Long> expiries;

	@Inject
	public FileSysUndelTransitionLogic(
			HederaFs hfs,
			Map<EntityId, Long> expiries,
			TransactionContext txnCtx
	) {
		this.hfs = hfs;
		this.expiries = expiries;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		/* --- Extract from gRPC --- */
		final var op = txnCtx.accessor().getTxn().getSystemUndelete();
		final var tbu = op.getFileID();
		final var entity = EntityId.fromGrpcFileId(tbu);

		/* --- Perform validations --- */
		validateFalse(!expiries.containsKey(entity) || !hfs.exists(tbu), INVALID_FILE_ID);
		final var info = hfs.getattr(tbu);
		validateTrue(info.isDeleted(), INVALID_FILE_ID);

		/* --- Do the business logic --- */
		final var oldExpiry = expiries.get(entity);
		if (oldExpiry <= txnCtx.consensusTime().getEpochSecond()) {
			hfs.rm(tbu);
		} else {
			info.setDeleted(false);
			info.setExpiry(oldExpiry);
			hfs.sudoSetattr(tbu, info);
		}
		expiries.remove(entity);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return txn -> txn.hasSystemUndelete() && txn.getSystemUndelete().hasFileID();
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_RUBBER_STAMP;
	}
}
