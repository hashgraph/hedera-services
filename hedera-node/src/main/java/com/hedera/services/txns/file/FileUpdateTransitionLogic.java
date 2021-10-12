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

import com.hedera.services.config.EntityNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HederaFs;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
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
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static java.lang.Math.max;

@Singleton
public class FileUpdateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(FileUpdateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final HederaFs hfs;
	private final EntityNumbers entityNums;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;

	@Inject
	public FileUpdateTransitionLogic(
			HederaFs hfs,
			EntityNumbers entityNums,
			OptionValidator validator,
			TransactionContext txnCtx
	) {
		this.hfs = hfs;
		this.entityNums = entityNums;
		this.txnCtx = txnCtx;
		this.validator = validator;
	}

	@Override
	public void doStateTransition() {
		/* --- Extract from gRPC --- */
		final var op = txnCtx.accessor().getTxn().getFileUpdate();
		final var target = op.getFileID();

		/* --- Perform validations --- */
		validateFalse(!op.hasFileID() || !hfs.exists(op.getFileID()), INVALID_FILE_ID);
		validateFalse(op.hasKeys() && !validator.hasGoodEncoding(wrapped(op.getKeys())), BAD_ENCODING);

		final var attr = hfs.getattr(target);
		validateFalse(attr.isDeleted(), FILE_DELETED);

		/* --- Authorize to process file --- */
		if (attr.getWacl().isEmpty() && (op.hasKeys() || !op.getContents().isEmpty())) {
				/* The transaction is trying to update an immutable file; in general, not a legal operation,
				but the semantics change for a superuser (i.e., sysadmin or treasury) updating a system file. */
			var isSysFile = entityNums.isSystemFile(target);
			var isSysAdmin = entityNums.accounts().isSuperuser(txnCtx.activePayer().getAccountNum());
			validateTrue(isSysAdmin && isSysFile, UNAUTHORIZED);
		}

		/* --- Do the business logic --- */
		if(!op.getContents().isEmpty()) {
			hfs.overwrite(target, op.getContents().toByteArray());
		}

		attr.setExpiry(max(op.getExpirationTime().getSeconds(), attr.getExpiry()));

		if (op.hasKeys()) {
			attr.setWacl(asFcKeyUnchecked(wrapped(op.getKeys())));
		}
		if (op.hasMemo()) {
			attr.setMemo(op.getMemo().getValue());
		}

		hfs.setattr(target, attr);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasFileUpdate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	private ResponseCodeEnum validate(TransactionBody fileUpdateTxn) {
		var op = fileUpdateTxn.getFileUpdate();

		var memoValidity = !op.hasMemo() ? OK : validator.memoCheck(op.getMemo().getValue());
		if (memoValidity != OK) {
			return memoValidity;
		}

		if (op.hasExpirationTime()) {
			var effectiveDuration = Duration.newBuilder()
					.setSeconds(
							op.getExpirationTime().getSeconds() -
									fileUpdateTxn.getTransactionID().getTransactionValidStart().getSeconds())
					.build();
			if (!validator.isValidAutoRenewPeriod(effectiveDuration)) {
				return AUTORENEW_DURATION_NOT_IN_RANGE;
			}
		}

		return OK;
	}

	static Key wrapped(KeyList wacl) {
		return Key.newBuilder().setKeyList(wacl).build();
	}
}
