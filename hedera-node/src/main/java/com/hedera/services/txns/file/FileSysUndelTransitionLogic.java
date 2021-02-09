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
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.files.HFileMeta;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class FileSysUndelTransitionLogic implements TransitionLogic {
	private static final Function<TransactionBody, ResponseCodeEnum> SYNTAX_RUBBER_STAMP = ignore -> OK;

	private final HederaFs hfs;
	private final TransactionContext txnCtx;
	private final Map<EntityId, Long> expiries;

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
		var op = txnCtx.accessor().getTxn().getSystemUndelete();
		var tbu = op.getFileID();
		var entity = EntityId.ofNullableFileId(tbu);
		var attr = new AtomicReference<HFileMeta>();

		var validity = tryLookup(tbu, entity, attr);
		if (validity != OK)	 {
			txnCtx.setStatus(validity);
			return;
		}

		var info = attr.get();
		var oldExpiry = expiries.get(entity);
		if (oldExpiry <= txnCtx.consensusTime().getEpochSecond()) {
			hfs.rm(tbu);
		} else {
			info.setDeleted(false);
			info.setExpiry(oldExpiry);
			hfs.sudoSetattr(tbu, info);
		}
		expiries.remove(entity);
		txnCtx.setStatus(SUCCESS);
	}

	private ResponseCodeEnum tryLookup(
			FileID tbu,
			EntityId entity,
			AtomicReference<HFileMeta> attr
	) {
		if (!expiries.containsKey(entity) || !hfs.exists(tbu)) {
			return INVALID_FILE_ID;
		}

		var info = hfs.getattr(tbu);
		if (info.isDeleted()) {
			attr.set(info);
			return OK;
		} else {
			return INVALID_FILE_ID;
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return txn -> txn.hasSystemUndelete() && txn.getSystemUndelete().hasFileID();
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_RUBBER_STAMP;
	}
}
