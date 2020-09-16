package com.hedera.services.txns.file;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.files.HederaFs;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;

public class FileDeleteTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(FileDeleteTransitionLogic.class);

	private static final Function<TransactionBody, ResponseCodeEnum> SYNTAX_RUBBER_STAMP = ignore -> OK;

	private final HederaFs hfs;
	private final TransactionContext txnCtx;

	public FileDeleteTransitionLogic(HederaFs hfs, TransactionContext txnCtx) {
		this.hfs = hfs;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		var op = txnCtx.accessor().getTxn().getFileDelete();

		try {
			var tbd = op.getFileID();
			if (!hfs.exists(tbd)) {
				txnCtx.setStatus(INVALID_FILE_ID);
				return;
			}

			var attr = hfs.getattr(tbd);
			if (attr.getWacl().isEmpty()) {
				txnCtx.setStatus(UNAUTHORIZED);
				return;
			}
			if (attr.isDeleted()) {
				txnCtx.setStatus(FILE_DELETED);
				return;
			}

			var result = hfs.delete(tbd);
			txnCtx.setStatus(result.outcome());
		} catch (Exception unknown) {
			unknown.printStackTrace();
			log.warn("Unrecognized failure handling {}!", txnCtx.accessor().getSignedTxn4Log(), unknown);
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasFileDelete;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_RUBBER_STAMP;
	}
}
