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

import com.hedera.services.context.ServicesNodeType;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.legacy.handler.SmartContractRequestHandler;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.legacy.utils.TransactionValidationUtils;
import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.contract.ContractAnswers;
import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.txns.submission.PlatformSubmissionManager;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetBytecode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetBySolidityID;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;

/**
 * This implements execution logic of smart contract api calls.
 * <p>
 * Created by Akshay Pitale on 2018-29-06.
 */

public class SmartContractServiceImpl extends SmartContractServiceGrpc.SmartContractServiceImplBase {
  private static final Logger log = LogManager.getLogger(SmartContractServiceImpl.class);

  private TransactionHandler txHandler;
  private SmartContractRequestHandler smartContractHandler;
  private UsagePricesProvider usagePrices;
  private HbarCentExchange exchange;
  private ServicesNodeType nodeType;
  private PlatformSubmissionManager submissionManager;
  private ContractAnswers contractAnswers;
  private QueryResponseHelper queryHelper;
  private HapiOpCounters opCounters;
  private TxnResponseHelper txnHelper;

  public SmartContractServiceImpl(
          TransactionHandler txHandler,
          SmartContractRequestHandler smartContractHandler,
          UsagePricesProvider usagePrices,
          HbarCentExchange exchange,
          ServicesNodeType nodeType,
          PlatformSubmissionManager submissionManager,
          ContractAnswers contractAnswers,
          QueryResponseHelper queryHelper,
          HapiOpCounters opCounters,
          TxnResponseHelper txnHelper
  ) {
    this.txHandler = txHandler;
    this.smartContractHandler = smartContractHandler;
    this.usagePrices = usagePrices;
    this.exchange = exchange;
    this.nodeType = nodeType;
    this.submissionManager = submissionManager;
    this.contractAnswers = contractAnswers;
    this.queryHelper = queryHelper;
    this.opCounters = opCounters;
    this.txnHelper = txnHelper;
  }

  @Override
  public void contractCallLocalMethod(Query query, StreamObserver<Response> observer) {
  	queryHelper.answer(query, observer, contractAnswers.contractCallLocal(), ContractCallLocal);
  }

  @Override
  public void createContract(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
    txnHelper.submit(signedTxn, observer, ContractCreate);
  }

  @Override
  public void contractCallMethod(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
    txnHelper.submit(signedTxn, observer, ContractCall);
  }

  @Override
  public void updateContract(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
    txnHelper.submit(signedTxn, observer, ContractUpdate);
  }

  @Override
  public void getBySolidityID(Query request, StreamObserver<Response> responseObserver) {
    opCounters.countReceived(GetBySolidityID);
    TransactionValidationUtils.constructGetBySolidityIDErrorResponse(responseObserver, ResponseCodeEnum.NOT_SUPPORTED,0);
  }

  @Override
  public void getContractInfo(Query query, StreamObserver<Response> observer) {
    queryHelper.answer(query, observer, contractAnswers.getContractInfo(), ContractGetInfo);
  }

  @Override
  public void contractGetBytecode(Query query, StreamObserver<Response> observer) {
      queryHelper.answer(query, observer, contractAnswers.getBytecode(), ContractGetBytecode);
  }

  @Override
  public void getTxRecordByContractID(Query query, StreamObserver<Response> observer) {
      queryHelper.answer(query, observer, contractAnswers.getContractRecords(), ContractGetRecords);
  }

  @Override
  public void deleteContract(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
    txnHelper.submit(signedTxn, observer, ContractDelete);
  }

  @Override
  public void systemDelete(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
    txnHelper.submit(signedTxn, observer, SystemDelete);
  }

  @Override
  public void systemUndelete(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
    txnHelper.submit(signedTxn, observer, SystemDelete);
  }
}
