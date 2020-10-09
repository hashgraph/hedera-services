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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hedera.services.config.EntityNumbers;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.txns.submission.PlatformSubmissionManager;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.FreezeServiceGrpc;
import com.swirlds.common.Platform;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.legacy.logic.ApplicationConstants.DEFAULT_FILE_REALM;
import static com.hedera.services.legacy.logic.ApplicationConstants.DEFAULT_FILE_SHARD;
import static com.hedera.services.legacy.utils.TransactionValidationUtils.transactionResponse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.builder.RequestBuilder.getTransactionReceipt;

/**
 * Freeze Service Implementation
 *
 * @author Qian
 */

public class FreezeServiceImpl extends FreezeServiceGrpc.FreezeServiceImplBase {
	private static final Logger log = LogManager.getLogger(FreezeServiceImpl.class);

	private final FileID softwareUpdateZipFid;
	private TransactionHandler txHandler;
	private PlatformSubmissionManager submissionManager;

	public FreezeServiceImpl(
			FileNumbers fileNums,
			TransactionHandler transactionHandler,
			PlatformSubmissionManager submissionManager
	) {
		this.txHandler = transactionHandler;
		this.submissionManager = submissionManager;

		softwareUpdateZipFid = fileNums.toFid(fileNums.softwareUpdateZip());
	}

	/**
	 * Validates startHour, startMin, endHour, and endMin in FreezeTransactionBody
	 */
	public TxnValidityAndFeeReq validateFreezeTxBody(FreezeTransactionBody body) {
		int startHour = body.getStartHour();
		int startMin = body.getStartMin();
		int endHour = body.getEndHour();
		int endMin = body.getEndMin();
		if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23 ||
				startMin < 0 || startMin > 59 || endMin < 0 || endMin > 59) {
			return new TxnValidityAndFeeReq(ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY);
		}
		if (body.hasUpdateFile()) {
			if (!body.getUpdateFile().equals(softwareUpdateZipFid)) {
				return new TxnValidityAndFeeReq(ResponseCodeEnum.INVALID_FILE_ID);
			}
			if (body.getFileHash() == null || body.getFileHash().isEmpty()) {
				log.error("Missing file hash when update file ID is present");
				return new TxnValidityAndFeeReq(INVALID_FREEZE_TRANSACTION_BODY);
			}
		}
		log.debug("FreezeTransactionBody is valid: \n {} \n", () -> TextFormat.shortDebugString(body));
		return new TxnValidityAndFeeReq(OK);
	}

	@Override
	public void freeze(Transaction request, StreamObserver<TransactionResponse> responseObserver) {
		// Check if fee payer is 0.0.55 is included in validateApiPermission() in this step
		TxnValidityAndFeeReq precheckResult = txHandler.validateTransactionPreConsensus(request, false);
		if (precheckResult.getValidity() != ResponseCodeEnum.OK) {
			String errorMsg = "Pre-check validation failed. " + precheckResult;
			logErrorAndResponse(errorMsg, precheckResult, log, responseObserver);
			return;
		}
		try {
			var accessor = new SignedTxnAccessor(request);
			if (!accessor.getTxn().hasFreeze()) {
				logErrorAndResponse("FreezeTransactionBody is missing. ",
						new TxnValidityAndFeeReq(ResponseCodeEnum.FREEZE_TRANSACTION_BODY_NOT_FOUND),
						log, responseObserver);
				return;
			}
			precheckResult = validateFreezeTxBody(accessor.getTxn().getFreeze());
			if (precheckResult.getValidity() != ResponseCodeEnum.OK) {
				String errorMsg = "FreezeTransactionBody is invalid. " + precheckResult.getValidity().name();
				logErrorAndResponse(errorMsg, precheckResult, log, responseObserver);
				return;
			}
			transactionResponse(
					responseObserver,
					new TxnValidityAndFeeReq(submissionManager.trySubmission(accessor)));
		} catch (InvalidProtocolBufferException ex) {
			transactionResponse(responseObserver,
					new TxnValidityAndFeeReq(ResponseCodeEnum.INVALID_TRANSACTION_BODY));
			return;
		}
	}

	public void logErrorAndResponse(String errorMsg, TxnValidityAndFeeReq precheckResult,
			Logger log, StreamObserver<TransactionResponse> responseObserver) {
		if (log.isDebugEnabled()) {
			log.debug(errorMsg);
		}
		transactionResponse(responseObserver, precheckResult);
	}

}
