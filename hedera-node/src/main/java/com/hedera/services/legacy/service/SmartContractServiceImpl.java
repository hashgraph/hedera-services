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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hedera.services.context.ServicesNodeType;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.legacy.handler.SmartContractRequestHandler;
import com.hedera.services.txns.submission.PlatformSubmissionManager;
import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.contract.ContractAnswers;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractGetRecordsQuery;
import com.hederahashgraph.api.proto.java.ContractGetRecordsResponse;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.services.stats.HederaNodeStats;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.legacy.utils.TransactionValidationUtils;
import com.swirlds.common.Platform;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.context.ServicesNodeType.STAKED_NODE;
import static com.hedera.services.context.ServicesNodeType.ZERO_STAKE_NODE;
import static com.hedera.services.legacy.utils.TransactionValidationUtils.logAndConstructResponseWhenCreateTxFailed;
import static com.hedera.services.utils.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * This implements execution logic of smart contract api calls.
 * <p>
 * Created by Akshay Pitale on 2018-29-06.
 */

public class SmartContractServiceImpl extends SmartContractServiceGrpc.SmartContractServiceImplBase {
  private SmartContractFeeBuilder feeBuilder = new SmartContractFeeBuilder();
  private static final Logger log = LogManager.getLogger(SmartContractServiceImpl.class);

  public static final String GET_BYTECODE_METRIC = "ContractGetBytecode";
  public static final String GET_CONTRACT_INFO_METRIC = "getContractInfo";

  private Platform platform;
  private TransactionHandler txHandler;
  private SmartContractRequestHandler smartContractHandler;
  private HederaNodeStats hederaNodeStats;
  private UsagePricesProvider usagePrices;
  private HbarCentExchange exchange;
  private ServicesNodeType nodeType;
  private PlatformSubmissionManager submissionManager;
  private ContractAnswers contractAnswers;
  private QueryResponseHelper queryHelper;

  public SmartContractServiceImpl(
          TransactionHandler txHandler,
          SmartContractRequestHandler smartContractHandler,
          HederaNodeStats hederaNodeStats,
          UsagePricesProvider usagePrices,
          HbarCentExchange exchange,
          ServicesNodeType nodeType,
          PlatformSubmissionManager submissionManager,
          ContractAnswers contractAnswers,
          QueryResponseHelper queryHelper
  ) {
    this.txHandler = txHandler;
    this.smartContractHandler = smartContractHandler;
    this.hederaNodeStats = hederaNodeStats;
    this.usagePrices = usagePrices;
    this.exchange = exchange;
    this.nodeType = nodeType;
    this.submissionManager = submissionManager;
    this.contractAnswers = contractAnswers;
    this.queryHelper = queryHelper;
  }

  public long getContractCallLocalGasPriceInTinyBars(Timestamp at) {
    FeeData prices = usagePrices.pricesGiven(ContractCallLocal, at);
    long feeInTinyCents = prices.getNodedata().getGas() / 1000;
    ExchangeRate exchangeRate = exchange.rate(at);
    long feeInTinyBars = FeeBuilder.getTinybarsFromTinyCents(exchangeRate, feeInTinyCents);
    return Math.max(1L, feeInTinyBars);
  }

  /**
   * Issue a local call to a smart contract methid. This goes to the platform for the
   * fee transfer.
   *
   * @param request API request for contract method call
   * @param responseObserver Observer to be informed of the results
   */
  @Override
  public void contractCallLocalMethod(Query request, StreamObserver<Response> responseObserver) {
    hederaNodeStats.smartContractQueryReceived("contractCallLocalMethod");

    if (log.isDebugEnabled()) {
      log.debug("In contractCallLocalMethod :: request : " + TextFormat.shortDebugString(request));
    }
    boolean isStaked = (nodeType == STAKED_NODE);
    ResponseCodeEnum validationCode = txHandler.validateQuery(request, isStaked);
    if (OK != validationCode) {
      String errorMsg = "contractCallLocalMethod query validation failed: " + validationCode.name();
      if (log.isDebugEnabled()) {
        log.debug(errorMsg);
      }
      TransactionValidationUtils.constructContractCallLocalErrorResponse(responseObserver,
          validationCode,0);
      return;
    }

    ContractCallLocalQuery transactionContractCallLocal = request.getContractCallLocal();
    Transaction feePayment = transactionContractCallLocal.getHeader().getPayment();
    long currentTimeMs = Instant.now().getEpochSecond();
    ByteString callResult = ByteString.EMPTY;
    ResponseCodeEnum returnResponseCode = OK;

    ContractCallLocalResponse callResponse;
    if (transactionContractCallLocal.getHeader().getResponseType() == ResponseType.COST_ANSWER) {
      // Build dummy response for calculating fees
      int estimatedReturnBytes = PropertiesLoader.getlocalCallEstReturnBytes();
      ContractFunctionResult dummyResult = ContractFunctionResult.newBuilder()
          .setContractID(transactionContractCallLocal.getContractID())
          .setContractCallResult(ByteString.copyFrom(new byte[estimatedReturnBytes]))
          .build();
      ResponseHeader dummyHeader = ResponseHeader.newBuilder()
          .setNodeTransactionPrecheckCode(OK).build();
      callResponse = ContractCallLocalResponse.newBuilder().setHeader(dummyHeader)
          .setFunctionResult(dummyResult).build();
    } else {
      try {
        callResponse =
            smartContractHandler.contractCallLocal(transactionContractCallLocal, currentTimeMs);
      } catch (Exception e1) {
        String errorMsg = "contractCallLocalMethod failed: ";
        if (log.isDebugEnabled()) {
          log.debug(errorMsg);
        }
        TransactionValidationUtils.constructContractCallLocalErrorResponse(responseObserver,
            ResponseCodeEnum.INVALID_TRANSACTION,0);
        return;
      }
    }

    if (callResponse.hasHeader()
        && callResponse.getHeader().getNodeTransactionPrecheckCode() != null) {
      returnResponseCode = callResponse.getHeader().getNodeTransactionPrecheckCode();
      if (returnResponseCode == OK && callResponse.hasFunctionResult()) {
        callResult = callResponse.getFunctionResult().getContractCallResult();
      }
    }
    ContractFunctionResult callResultResponse = callResponse.getFunctionResult().toBuilder()
        .setContractID(transactionContractCallLocal.getContractID())
        .setContractCallResult(callResult).build();

    TransactionBody body;
    try {
      body = CommonUtils.extractTransactionBody(feePayment);
    } catch (InvalidProtocolBufferException e) {
      if (log.isDebugEnabled()) {
        log.debug("Transaction body parsing exception: " + e);
      }
      validationCode = ResponseCodeEnum.INVALID_TRANSACTION_BODY;
      TransactionValidationUtils.constructContractCallLocalErrorResponse(responseObserver,
          validationCode,0);
      return;
    }
    Timestamp at = body.getTransactionID().getTransactionValidStart();
    FeeData feeData = usagePrices.pricesGiven(ContractCallLocal, at);
    long gasPrice = getContractCallLocalGasPriceInTinyBars(at);

    int functionParamSize = 0;
    if (transactionContractCallLocal.getFunctionParameters() != null) {
      functionParamSize = transactionContractCallLocal.getFunctionParameters().size();
    }

    FeeData feeMatrices = feeBuilder.getContractCallLocalFeeMatrices(
            functionParamSize,
            callResultResponse,
            transactionContractCallLocal.getHeader().getResponseType());
    long scheduledFee = 0;
    long gasOffered = transactionContractCallLocal.getGas();
    long gasCost = gasPrice * gasOffered;
    long queryFee = feeBuilder.getTotalFeeforRequest(
                    feeData,
                    feeMatrices,
                    exchange.rate(body.getTransactionID().getTransactionValidStart())) + gasCost;

    if (isStaked) {
      if (transactionContractCallLocal.getHeader().getResponseType() == ResponseType.COST_ANSWER) {
        scheduledFee = 0;
      } else if (transactionContractCallLocal.getHeader().getResponseType() == ResponseType.ANSWER_ONLY) {
        scheduledFee = queryFee;
      }
    }

    validationCode = txHandler.validateScheduledFee(HederaFunctionality.ContractCallLocal, feePayment, scheduledFee);

    if (validationCode == OK) {
      if (transactionContractCallLocal.hasContractID()) {
        validationCode =
            smartContractHandler
                .validateContractExistence(transactionContractCallLocal.getContractID());
      } else {
        validationCode = ResponseCodeEnum.INVALID_CONTRACT_ID;
      }
    }

    if (OK == validationCode && scheduledFee > 0) {
      if (submissionManager.trySubmission(uncheckedFrom(feePayment)) != OK) {
        logAndConstructResponseWhenCreateTxFailed(log, responseObserver, "contractCallLocalMethod", null);
        return;
      }
      log.debug("fee has been processed successfully..!");
    } else if (scheduledFee <= 0) {
      log.debug("Schedule fee is 0, hence transaction is not created");
    } else {
      String errorMsg = "Fee Validation Error: " + validationCode.name();
      if (log.isDebugEnabled()) {
        log.debug(errorMsg);
      }
      TransactionValidationUtils.constructContractCallLocalErrorResponse(responseObserver,
          validationCode,scheduledFee);
      return;
    }
    if (callResultResponse != null) {
      ResponseHeader responseHeader = RequestBuilder.getResponseHeader(
          (OK.equals(returnResponseCode)) ? validationCode : returnResponseCode,
          queryFee, transactionContractCallLocal.getHeader().getResponseType(), ByteString.EMPTY);
      if (transactionContractCallLocal.getHeader().getResponseType() == ResponseType.COST_ANSWER) {
        responseObserver.onNext(Response.newBuilder()
            .setContractCallLocal(ContractCallLocalResponse.newBuilder().setHeader(responseHeader))
            .build());
      } else {
        responseObserver.onNext(Response.newBuilder().setContractCallLocal(ContractCallLocalResponse
            .newBuilder().setHeader(responseHeader).setFunctionResult(callResultResponse)).build());
      }
      responseObserver.onCompleted();
    } else {
      TransactionValidationUtils.constructContractCallLocalErrorResponse(responseObserver,
          validationCode,0);
    }
    hederaNodeStats.smartContractQuerySubmitted("contractCallLocalMethod");
  }

  /**
   * Delegate Create Contract requests to the  generic handler
   *
   * @param request API request to create the contract
   * @param responseObserver Observer to be informed of results
   */
  @Override
  public void createContract(Transaction request, StreamObserver<TransactionResponse> responseObserver) {
    smartContractTransactionExecution(request, responseObserver, "createContract");
  }

  /**
   * Delegate Contract Call requests to the generic handler
   *
   * @param request API request to call the contract
   * @param responseObserver Observer to be informed of the results
   */
  @Override
  public void contractCallMethod(Transaction request,
      StreamObserver<TransactionResponse> responseObserver) {
    smartContractTransactionExecution(request, responseObserver, "contractCallMethod");
  }


  private void transactionResponse(StreamObserver<TransactionResponse> responseObserver,
      TxnValidityAndFeeReq precheckResult) {
    responseObserver.onNext(TransactionResponse.newBuilder()
        .setNodeTransactionPrecheckCodeValue(precheckResult.getValidity().getNumber())
        .setCost(precheckResult.getRequiredFee()).build());
    responseObserver.onCompleted();
  }

  /**
   * Delegate Contract Update requests to the generic handler
   *
   * @param request API request to update the contract
   * @param responseObserver Observer to be informed of the results
   */
  @Override
  public void updateContract(Transaction request,
      StreamObserver<TransactionResponse> responseObserver) {
    smartContractTransactionExecution(request, responseObserver, "updateContract");
  }

  /**
   * Not implemented
   */
  @Override
  public void getBySolidityID(Query request, StreamObserver<Response> responseObserver) {
    hederaNodeStats.smartContractQueryReceived("getBySolidityID");
    TransactionValidationUtils.constructGetBySolidityIDErrorResponse(
            responseObserver, ResponseCodeEnum.NOT_SUPPORTED,0);
    log.debug("getBySolidityID not supported");
  }

  @Override
  public void getContractInfo(Query query, StreamObserver<Response> observer) {
    queryHelper.respondToContract(query, observer, contractAnswers.getContractInfo(), GET_CONTRACT_INFO_METRIC);
  }

  @Override
  public void contractGetBytecode(Query query, StreamObserver<Response> observer) {
      queryHelper.respondToContract(query, observer, contractAnswers.getBytecode(), GET_BYTECODE_METRIC);
  }

  /**
   * Process a query to fetch transaction records relevant to a specified contract.
   *
   * @param request API request for transaction records
   * @param responseObserver Observer to be informed of the results
   */
  @Override
  public void getTxRecordByContractID(Query request, StreamObserver<Response> responseObserver) {
    hederaNodeStats.smartContractQueryReceived("getTxRecordByContractID");

    boolean isStaked = (nodeType == STAKED_NODE);

    if (log.isDebugEnabled()) {
      log.debug("In getTxRecordByContractID :: request : " + TextFormat.shortDebugString(request));
    }
    ResponseCodeEnum validationCode = txHandler.validateQuery(request, isStaked);
    if (OK != validationCode) {
      String errorMsg = "query validation failed: " + validationCode.name();
      if (log.isDebugEnabled()) {
        log.debug(errorMsg);
      }
      TransactionValidationUtils.constructContractGetRecordsErrorResponse(responseObserver,
          validationCode,0);
      return;
    }

    ContractGetRecordsQuery query = request.getContractGetRecords();
    // Process fee here
    Transaction feePayment = query.getHeader().getPayment();
    List<TransactionRecord> txRecord = null;
    try {
      txRecord = ExpirableTxnRecord.allToGrpc(
          txHandler.getAllTransactionRecordFCM(MerkleEntityId.fromContractId(query.getContractID())));
    } catch (ConcurrentModificationException ex) {
      TransactionValidationUtils.constructGetAccountRecordsErrorResponse(responseObserver,
          ResponseCodeEnum.RECORD_NOT_FOUND,0);
    }
    TransactionBody body;
    try {
      body = CommonUtils.extractTransactionBody(feePayment);
    } catch (InvalidProtocolBufferException e) {
      String errorMsg = "Transaction body parsing exception: " + e;
      if (log.isDebugEnabled()) {
        log.debug(errorMsg);
      }
      validationCode = ResponseCodeEnum.INVALID_TRANSACTION_BODY;
      TransactionValidationUtils.constructContractGetRecordsErrorResponse(responseObserver,
          validationCode,0);
      return;
    }
    Timestamp at = body.getTransactionID().getTransactionValidStart();
    FeeData prices = usagePrices.pricesGiven(ContractGetRecords, at);

    FeeData feeMatrices = feeBuilder.getContractRecordsQueryFeeMatrices(txRecord, query.getHeader().getResponseType());
    long scheduledFee = 0;
    long queryFee = feeBuilder.getTotalFeeforRequest(prices, feeMatrices,
        exchange.rate(body.getTransactionID().getTransactionValidStart()));

    if (isStaked) {
      if (query.getHeader().getResponseType() == ResponseType.COST_ANSWER) {
        scheduledFee = 0;
      } else if (query.getHeader().getResponseType() == ResponseType.ANSWER_ONLY) {
        scheduledFee = queryFee;
      }
    }

    validationCode = txHandler.validateScheduledFee(HederaFunctionality.ContractGetRecords, feePayment, scheduledFee);
    if (OK == validationCode && scheduledFee > 0) {
      if (submissionManager.trySubmission(uncheckedFrom(feePayment)) != OK) {
        logAndConstructResponseWhenCreateTxFailed(log, responseObserver, "getTxRecordByContractID", null);
        return;
      }
      log.debug("fee has been processed successfully..!");
    } else if (scheduledFee <= 0) {
      log.debug("Schedule fee is 0, hence transaction is not created");
    } else {
      if (log.isDebugEnabled()) {
        log.debug("fee validation failed: " + validationCode.name());
      }
      TransactionValidationUtils.constructContractGetRecordsErrorResponse(responseObserver,
          validationCode,scheduledFee);
      return;
    }

    if (query.hasContractID()) {
      validationCode = smartContractHandler
          .validateContractExistence(query.getContractID());
    } else {
      validationCode = ResponseCodeEnum.INVALID_CONTRACT_ID;
    }

    if (OK != validationCode) {
      String errorMsg = "Fail to get contract records: " + validationCode.name();
      if (log.isDebugEnabled()) {
        log.debug(errorMsg);
      }
      TransactionValidationUtils.constructContractGetRecordsErrorResponse(responseObserver,
          validationCode,scheduledFee);
      return;
    }

    ResponseHeader responseHeader = RequestBuilder.getResponseHeader(validationCode, queryFee,
        query.getHeader().getResponseType(), ByteString.EMPTY);
    if (query.getHeader().getResponseType() == ResponseType.ANSWER_ONLY) {
      responseObserver.onNext(Response.newBuilder()
          .setContractGetRecordsResponse(
              ContractGetRecordsResponse.newBuilder().setHeader(responseHeader)
                  .setContractID(query.getContractID()).addAllRecords(txRecord))
          .build());
    } else {
      responseObserver
          .onNext(
              Response
                  .newBuilder().setContractGetRecordsResponse(ContractGetRecordsResponse
                  .newBuilder().setHeader(responseHeader).setContractID(query.getContractID()))
                  .build());
    }
    responseObserver.onCompleted();

    hederaNodeStats.smartContractQuerySubmitted("getTxRecordByContractID");
  }

  /**
   * Single handler for four types of request: contract Create, Call, Update, and Delete.
   * Validates the transaction and then invokes the platform
   *
   * @param request API request
   * @param responseObserver Observer to be informed of results
   * @param transactionRequest Name of the transaction type
   */
  private void smartContractTransactionExecution(
          Transaction request,
          StreamObserver<TransactionResponse> responseObserver, String transactionRequest
  ) {
    if (nodeType == ZERO_STAKE_NODE) {
      transactionResponse(responseObserver, new TxnValidityAndFeeReq(ResponseCodeEnum.INVALID_NODE_ACCOUNT));
      return;
    }
    hederaNodeStats.smartContractTransactionReceived(transactionRequest);

    TransactionBody transactionBody;
    TxnValidityAndFeeReq precheckResult;
    try {
      transactionBody = CommonUtils.extractTransactionBody(request);
    } catch (InvalidProtocolBufferException e) {
      precheckResult = new TxnValidityAndFeeReq(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
      String errorMsg = "Transaction Body Parsing failed";
      if (log.isDebugEnabled()) {
        log.debug(errorMsg);
      }
      transactionResponse(responseObserver, precheckResult);
      return;
    }
    if (log.isDebugEnabled()) {
      log.debug(
          transactionRequest + " :: request : " + TextFormat.shortDebugString(transactionBody));
    }
    precheckResult = txHandler.validateTransactionPreConsensus(request, false);

    if (precheckResult.getValidity() == OK) {
      /* should check if ContractID is invalid, if so return INVALID_CONTRACT_ID */
      if (transactionBody.hasContractUpdateInstance()) {
        if (transactionBody.getContractUpdateInstance().hasContractID()) {
          precheckResult = new TxnValidityAndFeeReq(smartContractHandler
              .validateContractExistence(
                  transactionBody.getContractUpdateInstance().getContractID()));
        } else {
          precheckResult = new TxnValidityAndFeeReq(ResponseCodeEnum.INVALID_CONTRACT_ID);
        }
      } else if (transactionBody.hasContractDeleteInstance()) {
        if (transactionBody.getContractDeleteInstance().hasContractID()) {
          precheckResult = new TxnValidityAndFeeReq(smartContractHandler
              .validateContractExistence(
                  transactionBody.getContractDeleteInstance().getContractID()));
        } else {
          precheckResult = new TxnValidityAndFeeReq(ResponseCodeEnum.INVALID_CONTRACT_ID);
        }
      } else if (transactionBody.hasContractCall()) {
        if (transactionBody.getContractCall().hasContractID()) {
          precheckResult = new TxnValidityAndFeeReq(smartContractHandler
              .validateContractExistence(transactionBody.getContractCall().getContractID()));
        } else {
          precheckResult = new TxnValidityAndFeeReq(ResponseCodeEnum.INVALID_CONTRACT_ID);
        }
      }
    }

    if (precheckResult.getValidity() != OK) {
      transactionResponse(responseObserver, precheckResult);
      return;
    }

    long minimumDuration = PropertiesLoader.getMinimumAutorenewDuration();
    if (minimumDuration < Instant.MIN.getEpochSecond()) {
      minimumDuration = Instant.MIN.getEpochSecond();
    }
    long maximumDuration = PropertiesLoader.getMaximumAutorenewDuration();
    if (maximumDuration > Instant.MAX.getEpochSecond()) {
      maximumDuration = Instant.MAX.getEpochSecond();
    }
    if (transactionBody.hasContractCreateInstance()) {
      long duration = transactionBody.getContractCreateInstance().getAutoRenewPeriod().getSeconds();
      if (durationRangeCheck(responseObserver, minimumDuration, maximumDuration, duration)) {
        return;
      }
    }

    if (transactionBody.hasContractUpdateInstance()) {
      if (transactionBody.getContractUpdateInstance().hasAutoRenewPeriod()) {
        long duration = transactionBody.getContractUpdateInstance().getAutoRenewPeriod().getSeconds();
        if (durationRangeCheck(responseObserver, minimumDuration, maximumDuration, duration)) {
          return;
        }
      }

    }

    if (submissionManager.trySubmission(uncheckedFrom(request)) != OK) {
      logAndConstructResponseWhenCreateTxFailed(log, responseObserver);
      return;
    }
    TransactionValidationUtils.transactionResponse(responseObserver, new TxnValidityAndFeeReq(OK));
    hederaNodeStats.smartContractTransactionSubmitted(transactionRequest);
  }

  private boolean durationRangeCheck(
          StreamObserver<TransactionResponse> observer,
          long minimumDuration, long maximumDuration, long duration
  ) {
    if ((duration < minimumDuration) || (duration > maximumDuration)) {
      transactionResponse(observer, new TxnValidityAndFeeReq(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE));
      return true;
    }
    return false;
  }

  /**
   * Delegate Delete Contract requests to the generic handler
   *
   * @param request API request to delete the contract
   * @param responseObserver Observer to be informed of the results
   */
  @Override
  public void deleteContract(Transaction request,
      StreamObserver<TransactionResponse> responseObserver) {
    smartContractTransactionExecution(request, responseObserver, "deleteContract");
  }

  /**
   * Validate the System Delete transaction request and then invoke the platform.
   *
   * @param request API request to system-delete the contract
   * @param responseObserver Observer to be informed of the results
   */
  @Override
  public void systemDelete(Transaction request,
      StreamObserver<TransactionResponse> responseObserver) {
    hederaNodeStats.smartContractTransactionReceived("smartContractSystemDelete");
    TxnValidityAndFeeReq precheckResult = txHandler.validateTransactionPreConsensus(request, false);
    if (precheckResult.getValidity() != OK) {
      String errorMsg = "Pre-check validation failed. " + precheckResult;
      if (log.isDebugEnabled()) {
        log.debug(errorMsg);
      }
      TransactionValidationUtils.transactionResponse(responseObserver, precheckResult);
      return;
    }

    try {
      TransactionBody body = CommonUtils.extractTransactionBody(request);
      if (log.isDebugEnabled()) {
        log.debug("In systemDelete :: request : " + TextFormat.shortDebugString(body));
      }
      if (submissionManager.trySubmission(uncheckedFrom(request)) != OK) {
        logAndConstructResponseWhenCreateTxFailed(log, responseObserver);
        return;
      }
      TransactionValidationUtils.transactionResponse(responseObserver, precheckResult);
    } catch (InvalidProtocolBufferException e) {
      precheckResult = new TxnValidityAndFeeReq(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
      TransactionValidationUtils.transactionResponse(responseObserver, precheckResult);
    }
    hederaNodeStats.smartContractTransactionSubmitted("smartContractSystemDelete");
  }

  /**
   * Validate the System Undelete transaction request and then invoke the platform
   *
   * @param request API request to system-undelete the contract
   * @param responseObserver Observer to be informed of the results
   */
  @Override
  public void systemUndelete(Transaction request,
      StreamObserver<TransactionResponse> responseObserver) {
    hederaNodeStats.smartContractTransactionReceived("smartContractSystemUndelete");
    TxnValidityAndFeeReq precheckResult = txHandler.validateTransactionPreConsensus(request, false);
    if (precheckResult.getValidity() != OK) {
      String errorMsg = "Pre-check validation failed. " + precheckResult;
      if (log.isDebugEnabled()) {
        log.debug(errorMsg);
      }
      TransactionValidationUtils.transactionResponse(responseObserver, precheckResult);
      return;
    }

    try {
      TransactionBody body = CommonUtils.extractTransactionBody(request);
      if (log.isDebugEnabled()) {
        log.debug("In systemUnDelete :: request : " + TextFormat.shortDebugString(body));
      }

      if (submissionManager.trySubmission(uncheckedFrom(request)) != OK) {
        logAndConstructResponseWhenCreateTxFailed(log, responseObserver);
        return;
      }
      TransactionValidationUtils.transactionResponse(responseObserver, precheckResult);
    } catch (InvalidProtocolBufferException e) {
      precheckResult = new TxnValidityAndFeeReq(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
      TransactionValidationUtils.transactionResponse(responseObserver, precheckResult);
    }
    hederaNodeStats.smartContractTransactionSubmitted("smartContractSystemUndelete");
  }

}
