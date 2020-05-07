package com.hedera.services.txns.submission;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.txns.SubmissionFlow;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.legacy.core.TxnValidityAndFeeReq;
import com.hedera.services.legacy.exception.PlatformTransactionCreationException;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.swirlds.common.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import java.util.Optional;
import java.util.function.Function;

public class TxnHandlerSubmissionFlow implements SubmissionFlow {
	private static final Logger log = LogManager.getLogger(TxnHandlerSubmissionFlow.class);

	static final Function<TransactionBody, ResponseCodeEnum> FALLBACK_SYNTAX_CHECK = ignore -> NOT_SUPPORTED;

	private final Platform platform;
	private final TransactionHandler legacyTxnHandler;
	private final TransitionLogicLookup transitionLogic;

	public TxnHandlerSubmissionFlow(
			Platform platform,
			TransactionHandler legacyTxnHandler,
			TransitionLogicLookup transitionLogic
	) {
		this.platform = platform;
		this.legacyTxnHandler = legacyTxnHandler;
		this.transitionLogic = transitionLogic;
	}

	@Override
	public TransactionResponse submit(Transaction signedTxn) {
		try {
			SignedTxnAccessor accessor = new SignedTxnAccessor(signedTxn);

			if (!accessor.getSignedTxn().hasBody() && accessor.getSignedTxn().getBodyBytes().isEmpty()) {
				return responseWith(INVALID_TRANSACTION_BODY);
			}

			TxnValidityAndFeeReq metaValidity = metaValidityOf(accessor);
			if (metaValidity.getValidity() != OK) {
				return responseWith(metaValidity.getValidity(), metaValidity.getFeeRequired());
			}

			Optional<TransitionLogic> logic = transitionLogic.lookupFor(accessor.getTxn());
			Function<TransactionBody, ResponseCodeEnum> syntaxCheck = logic
					.map(TransitionLogic::syntaxCheck)
					.orElse(FALLBACK_SYNTAX_CHECK);
			ResponseCodeEnum validity = syntaxCheck.apply(accessor.getTxn());
			if (validity != OK) {
				return responseWith(validity);
			}

			return submitTransaction(accessor);

		} catch (InvalidProtocolBufferException ignore) {
			log.warn(ignore.getMessage());
			return responseWith(INVALID_TRANSACTION_BODY);
		}
	}

	private TransactionResponse submitTransaction(SignedTxnAccessor accessor) {
		try {
			legacyTxnHandler.submitTransaction(platform, accessor.getSignedTxn(), accessor.getTxnId());
			return responseWith(OK);
		} catch (PlatformTransactionCreationException | InvalidProtocolBufferException ptce) {
			return responseWith(PLATFORM_TRANSACTION_NOT_CREATED);
		}
	}

	private TxnValidityAndFeeReq metaValidityOf(SignedTxnAccessor accessor) {
		return legacyTxnHandler.validateTransactionPreConsensus(accessor.getSignedTxn(), false);
	}

	private TransactionResponse responseWith(ResponseCodeEnum validity) {
		return responseWith(validity, 0);
	}

	private TransactionResponse responseWith(ResponseCodeEnum validity, long feeRequired) {
		return TransactionResponse.newBuilder()
				.setNodeTransactionPrecheckCode(validity)
				.setCost(feeRequired)
				.build();
	}
}
