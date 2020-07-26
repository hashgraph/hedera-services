package com.hedera.services.legacy.service;

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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hedera.services.legacy.utils.TransactionValidationUtils.transactionResponse;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.FreezeServiceGrpc;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.legacy.utils.TransactionValidationUtils;
import com.swirlds.common.Platform;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Freeze Service Implementation
 * @author Qian
 */

public class FreezeServiceImpl extends FreezeServiceGrpc.FreezeServiceImplBase {
	private static final Logger log = LogManager.getLogger(FreezeServiceImpl.class);

	private Platform platform;
	private TransactionHandler txHandler;

	public FreezeServiceImpl(Platform platform, TransactionHandler transactionHandler) {
		this.platform = platform;
		this.txHandler = transactionHandler;
	}

	/**
	 * Validates startHour, startMin, endHour, and endMin in FreezeTransactionBody
	 */
	public static TxnValidityAndFeeReq validateFreezeTxBody(FreezeTransactionBody body) {
		int startHour = body.getStartHour();
		int startMin = body.getStartMin();
		int endHour = body.getEndHour();
		int endMin = body.getEndMin();
		if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23 ||
				startMin < 0 || startMin > 59 || endMin < 0 || endMin > 59) {
			return new TxnValidityAndFeeReq(ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY);
		}
		log.debug("FreezeTransactionBody is valid: \n {} \n", () -> TextFormat.shortDebugString(body));
		return new TxnValidityAndFeeReq(OK);
	}

	@Override
	public void freeze(Transaction request, StreamObserver<TransactionResponse> responseObserver) {
		log.debug("In freeze: request = " + request.toString());

		// Check if fee payer is 0.0.55 is included in validateApiPermission() in this step
		TxnValidityAndFeeReq precheckResult = txHandler.validateTransactionPreConsensus(request, false);

		if (precheckResult.getValidity() != ResponseCodeEnum.OK) {
			String errorMsg = "Pre-check validation failed. " + precheckResult;
			logErrorAndResponse(errorMsg, precheckResult, log, responseObserver);
			return;
		}

		try {
			TransactionBody transactionBody = CommonUtils.extractTransactionBody(request);
			if (!transactionBody.hasFreeze()) {
				logErrorAndResponse("FreezeTransactionBody is missing. ",
						new TxnValidityAndFeeReq(ResponseCodeEnum.FREEZE_TRANSACTION_BODY_NOT_FOUND),
						log, responseObserver);
				return;
			}
			precheckResult = validateFreezeTxBody(transactionBody.getFreeze());
			if (precheckResult.getValidity() != ResponseCodeEnum.OK) {
				String errorMsg = "FreezeTransactionBody is invalid. " + precheckResult.getValidity().name();
				logErrorAndResponse(errorMsg, precheckResult, log, responseObserver);
				return;
			}
			if (!txHandler.submitTransaction(platform, request, transactionBody.getTransactionID())) {
				TransactionValidationUtils.logAndConstructResponseWhenCreateTxFailed(log, responseObserver);
				return;
			}
			transactionResponse(responseObserver, new TxnValidityAndFeeReq(ResponseCodeEnum.OK));
		} catch (InvalidProtocolBufferException ex) {
			String errorMsg = "Invalid transaction body: " + ResponseCodeEnum.INVALID_TRANSACTION_BODY.name();
		      if (log.isDebugEnabled())
		        log.debug(errorMsg);
			transactionResponse(responseObserver,
					new TxnValidityAndFeeReq(ResponseCodeEnum.INVALID_TRANSACTION_BODY));
			return;
		}
	}

	public void logErrorAndResponse(String errorMsg, TxnValidityAndFeeReq precheckResult,
			Logger log, StreamObserver<TransactionResponse> responseObserver) {
      if (log.isDebugEnabled())
        log.debug(errorMsg);
		transactionResponse(responseObserver, precheckResult);
	}

}
