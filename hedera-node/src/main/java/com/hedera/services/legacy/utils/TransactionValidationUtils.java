package com.hedera.services.legacy.utils;

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

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeResponse;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractGetRecordsResponse;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsResponse;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.legacy.logic.ProtectedEntities;
import com.swirlds.common.Platform;
import com.swirlds.fcmap.FCMap;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.builder.RequestBuilder.convertProtoTimeStamp;

@Deprecated
public class TransactionValidationUtils {
	private static final Logger log = LogManager.getLogger(TransactionValidationUtils.class);

	private static final int MESSAGE_MAX_DEPTH = 50;

	public static void transactionResponse(
			StreamObserver<TransactionResponse> responseObserver,
			TxnValidityAndFeeReq preCheckResult
	) {
		responseObserver.onNext(TransactionResponse.newBuilder()
				.setNodeTransactionPrecheckCode(preCheckResult.getValidity())
				.setCost(preCheckResult.getRequiredFee())
				.build());
		responseObserver.onCompleted();
	}

	public static boolean validateTxDepth(Transaction transaction) {
		return getDepth(transaction) <= MESSAGE_MAX_DEPTH;
	}

	public static boolean validateTxBodyDepth(TransactionBody transactionBody) {
		return getDepth(transactionBody) < MESSAGE_MAX_DEPTH;
	}

	/**
	 * Get the depth of message, return 0 if it doesn't have any nesting message
	 */
	public static int getDepth(final GeneratedMessageV3 message) {
		Map<Descriptors.FieldDescriptor, Object> fields = message.getAllFields();
		int depth = 0;
		for (Descriptors.FieldDescriptor descriptor : fields.keySet()) {
			Object field = fields.get(descriptor);
			if (field instanceof GeneratedMessageV3) {
				GeneratedMessageV3 fieldMessage = (GeneratedMessageV3) field;
				depth = Math.max(depth, getDepth(fieldMessage) + 1);
			} else if (field instanceof List) {
				for (Object ele : (List) field) {
					if (ele instanceof GeneratedMessageV3) {
						depth = Math.max(depth, getDepth((GeneratedMessageV3) ele) + 1);
					}
				}
			}
		}
		return depth;
	}

	public static boolean validateTxSize(Transaction transaction) {
		return transaction.toByteArray().length <= Platform.getTransactionMaxBytes();
	}

	public static boolean validateQueryHeader(QueryHeader queryHeader, boolean hasPayment) {
		boolean returnFlag = true;
		if (queryHeader == null || queryHeader.getResponseType() == null) {
			returnFlag = false;
		} else if (hasPayment) {
			returnFlag = queryHeader.hasPayment();
		}
		return returnFlag;
	}

	public static ResponseCodeEnum validateTxSpecificBody(
			TransactionBody txn,
			OptionValidator validator) {
		if (txn.hasContractCreateInstance()) {
			return validateContractCreateTransactionBody(txn, validator);
		} else if (txn.hasContractCall()) {
			return validateContractCallTransactionBody(txn);
		} else if (txn.hasContractUpdateInstance()) {
			return validateContractUpdateTransactionBody(txn, validator);
		}

		return OK;
	}

	private static ResponseCodeEnum validateContractCreateTransactionBody(
			TransactionBody trBody,
			OptionValidator validator
	) {
		ContractCreateTransactionBody body = trBody.getContractCreateInstance();
		long autoRenewPeriod = body.getAutoRenewPeriod().getSeconds();
		if (!body.hasAutoRenewPeriod() || (autoRenewPeriod < 1)) {
			if (log.isDebugEnabled()) {
				log.debug("Non-positive auto renewal period given, trBody=" + trBody);
			}
			return ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
		}

		ResponseCodeEnum durationValidationResponse =
				validator.isValidAutoRenewPeriod(body.getAutoRenewPeriod()) ? OK : AUTORENEW_DURATION_NOT_IN_RANGE;
		if (durationValidationResponse != OK) {
			return durationValidationResponse;
		}
		if (body.getGas() < 0L) {
			if (log.isDebugEnabled()) {
				log.debug("negative gas for a contract." + trBody);
			}
			return ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
		}
		if (body.getInitialBalance() < 0L) {
			if (log.isDebugEnabled()) {
				log.debug("negative initial balance for a contract." + trBody);
			}
			return ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
		}

		return OK;
	}

	private static ResponseCodeEnum validateContractCallTransactionBody(TransactionBody trBody) {
		ContractCallTransactionBody body = trBody.getContractCall();
		if (body.getGas() < 0L) {
			if (log.isDebugEnabled()) {
				log.debug("negative gas for a contract." + trBody);
			}
			return ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
		}
		if (body.getAmount() < 0L) {
			if (log.isDebugEnabled()) {
				log.debug("negative amount for a contract." + trBody);
			}
			return ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
		}

		return OK;
	}

	private static ResponseCodeEnum validateContractUpdateTransactionBody(
			TransactionBody txn,
			OptionValidator validator
	) {
		ContractUpdateTransactionBody body = txn.getContractUpdateInstance();
		long autoRenewPeriod = body.getAutoRenewPeriod().getSeconds();
		if (body.hasAutoRenewPeriod()) {
			if (autoRenewPeriod < 1) {
				if (log.isDebugEnabled()) {
					log.debug("Non-positive auto renewal period given, trBody=" + txn);
				}
				return ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
			}
			ResponseCodeEnum durationValidationResponse =
					validator.isValidAutoRenewPeriod(body.getAutoRenewPeriod()) ? OK : AUTORENEW_DURATION_NOT_IN_RANGE;
			if (durationValidationResponse != OK) {
				return durationValidationResponse;
			}
		}
		return OK;
	}


	public static void constructGetBySolidityIDErrorResponse(
			StreamObserver<Response> responseObserver, ResponseCodeEnum validationCode, long scheduledFee) {
		ResponseHeader responseHeader = RequestBuilder.getResponseHeader(
				validationCode, scheduledFee, ResponseType.ANSWER_ONLY, ByteString.EMPTY);
		responseObserver.onNext(Response.newBuilder().setGetBySolidityID(
				GetBySolidityIDResponse.newBuilder().setHeader(responseHeader)).build());
		responseObserver.onCompleted();
	}

	public static void constructContractGetInfoErrorResponse(
			StreamObserver<Response> responseObserver, ResponseCodeEnum validationCode, long scheduledFee) {
		ResponseHeader responseHeader = RequestBuilder.getResponseHeader(
				validationCode, scheduledFee, ResponseType.ANSWER_ONLY, ByteString.EMPTY);
		responseObserver.onNext(Response.newBuilder().setContractGetInfo(
				ContractGetInfoResponse.newBuilder().setHeader(responseHeader)).build());
		responseObserver.onCompleted();
	}

	public static void constructContractGetBytecodeInfoErrorResponse(
			StreamObserver<Response> responseObserver, ResponseCodeEnum validationCode, long scheduledFee) {
		ResponseHeader responseHeader = RequestBuilder.getResponseHeader(
				validationCode, scheduledFee, ResponseType.ANSWER_ONLY, ByteString.EMPTY);
		responseObserver.onNext(Response.newBuilder().setContractGetBytecodeResponse(
				ContractGetBytecodeResponse.newBuilder().setHeader(responseHeader)).build());
		responseObserver.onCompleted();
	}

	public static void constructGetAccountRecordsErrorResponse(
			StreamObserver<Response> responseObserver, ResponseCodeEnum validationCode, long scheduledFee) {
		ResponseHeader responseHeader = RequestBuilder
				.getResponseHeader(validationCode, scheduledFee, ResponseType.ANSWER_ONLY, ByteString.EMPTY);
		responseObserver.onNext(Response.newBuilder().setCryptoGetAccountRecords(
				CryptoGetAccountRecordsResponse.newBuilder().setHeader(responseHeader)).build());
		responseObserver.onCompleted();
	}

	public static void constructContractGetRecordsErrorResponse(
			StreamObserver<Response> responseObserver, ResponseCodeEnum validationCode, long scheduledFee) {
		ResponseHeader responseHeader = RequestBuilder.getResponseHeader(
				validationCode, scheduledFee, ResponseType.ANSWER_ONLY, ByteString.EMPTY);
		responseObserver.onNext(Response.newBuilder().setContractGetRecordsResponse(
				ContractGetRecordsResponse.newBuilder().setHeader(responseHeader)).build());
		responseObserver.onCompleted();
	}

	public static void constructContractCallLocalErrorResponse(
			StreamObserver<Response> responseObserver, ResponseCodeEnum validationCode, long scheduledFee) {
		ResponseHeader responseHeader = RequestBuilder.getResponseHeader(
				validationCode, scheduledFee, ResponseType.ANSWER_ONLY, ByteString.EMPTY);
		responseObserver.onNext(Response.newBuilder().setContractCallLocal(
				ContractCallLocalResponse.newBuilder().setHeader(responseHeader)).build());
		responseObserver.onCompleted();
	}

	public static void logAndConstructResponseWhenCreateTxFailed(
			Logger log,
			StreamObserver<Response> responseObserver,
			String methodMsg,
			AccountID accountID
	) {
		ResponseCodeEnum responseCode = ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
		if (methodMsg.startsWith("getBySolidityID")) {
			TransactionValidationUtils.constructGetBySolidityIDErrorResponse(responseObserver,
					responseCode, 0);
		} else if (methodMsg.startsWith("getContractInfo")) {
			TransactionValidationUtils.constructContractGetInfoErrorResponse(responseObserver,
					responseCode, 0);
		} else if (methodMsg.startsWith("getContractBytecode")) {
			TransactionValidationUtils.constructContractGetBytecodeInfoErrorResponse(responseObserver,
					responseCode, 0);
		} else if (methodMsg.startsWith("contractCallLocalMethod")) {
			TransactionValidationUtils.constructContractCallLocalErrorResponse(responseObserver,
					responseCode, 0);
		} else if (methodMsg.startsWith("getTxRecordByContractID")) {
			TransactionValidationUtils.constructContractGetRecordsErrorResponse(responseObserver,
					responseCode, 0);
		} else if (methodMsg.startsWith("contractGetBytecode")) {
			TransactionValidationUtils
					.constructContractGetBytecodeInfoErrorResponse(responseObserver, responseCode, 0);
		}
	}

	public static void logAndConstructResponseWhenCreateTxFailed(
			Logger log,
			StreamObserver<TransactionResponse> responseObserver
	) {
		TxnValidityAndFeeReq responseCode = new TxnValidityAndFeeReq(ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED);
		transactionResponse(responseObserver, responseCode);
	}
}
