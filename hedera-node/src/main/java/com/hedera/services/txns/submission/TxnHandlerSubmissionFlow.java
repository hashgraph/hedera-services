package com.hedera.services.txns.submission;

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
import com.hedera.services.context.ServicesNodeType;
import com.hedera.services.txns.SubmissionFlow;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.legacy.handler.TransactionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.context.ServicesNodeType.ZERO_STAKE_NODE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import java.util.Optional;
import java.util.function.Function;

public class TxnHandlerSubmissionFlow implements SubmissionFlow {
	private static final Logger log = LogManager.getLogger(TxnHandlerSubmissionFlow.class);

	static final Function<TransactionBody, ResponseCodeEnum> FALLBACK_SYNTAX_CHECK = ignore -> NOT_SUPPORTED;

	private final ServicesNodeType nodeType;
	private final TransactionHandler legacyTxnHandler;
	private final TransitionLogicLookup transitionLogic;
	private final PlatformSubmissionManager submissionManager;

	public TxnHandlerSubmissionFlow(
			ServicesNodeType nodeType,
			TransactionHandler legacyTxnHandler,
			TransitionLogicLookup transitionLogic,
			PlatformSubmissionManager submissionManager
	) {
		this.nodeType = nodeType;
		this.legacyTxnHandler = legacyTxnHandler;
		this.transitionLogic = transitionLogic;
		this.submissionManager = submissionManager;
	}

	@Override
	public TransactionResponse submit(Transaction signedTxn) {
		if (nodeType == ZERO_STAKE_NODE) {
			return responseWith(INVALID_NODE_ACCOUNT);
		}

		try {
			SignedTxnAccessor accessor = new SignedTxnAccessor(signedTxn);

			TxnValidityAndFeeReq metaValidity = metaValidityOf(accessor);
			if (metaValidity.getValidity() != OK) {
				return responseWith(metaValidity.getValidity(), metaValidity.getRequiredFee());
			}

			Optional<TransitionLogic> logic = transitionLogic.lookupFor(accessor.getFunction(), accessor.getTxn());
			Function<TransactionBody, ResponseCodeEnum> syntaxCheck = logic
					.map(TransitionLogic::syntaxCheck)
					.orElse(FALLBACK_SYNTAX_CHECK);
			ResponseCodeEnum validity = syntaxCheck.apply(accessor.getTxn());
			if (validity != OK) {
				return responseWith(validity);
			}

			return responseWith(submissionManager.trySubmission(accessor));
		} catch (InvalidProtocolBufferException impossible) {
			return responseWith(INVALID_TRANSACTION_BODY);
		}
	}

	private TxnValidityAndFeeReq metaValidityOf(SignedTxnAccessor accessor) {
		return legacyTxnHandler.validateTransactionPreConsensus(accessor.getBackwardCompatibleSignedTxn(), false);
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
