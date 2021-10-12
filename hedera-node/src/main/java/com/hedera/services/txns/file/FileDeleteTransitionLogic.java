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
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;

@Singleton
public class FileDeleteTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(FileDeleteTransitionLogic.class);

	private static final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_RUBBER_STAMP = ignore -> OK;

	private final HederaFs hfs;
	private final TransactionContext txnCtx;

	@Inject
	public FileDeleteTransitionLogic(HederaFs hfs, TransactionContext txnCtx) {
		this.hfs = hfs;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		/* --- Extract from gRPC --- */
		final var op = txnCtx.accessor().getTxn().getFileDelete();
		final var tbd = op.getFileID();

		/* --- Perform validations --- */
		validateTrue(hfs.exists(tbd), INVALID_FILE_ID);
		final var attr = hfs.getattr(tbd);
		validateFalse(attr.getWacl().isEmpty(), UNAUTHORIZED);
		validateFalse(attr.isDeleted(), FILE_DELETED);

		/* --- Do the business logic --- */
		hfs.delete(tbd);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasFileDelete;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_RUBBER_STAMP;
	}
}
