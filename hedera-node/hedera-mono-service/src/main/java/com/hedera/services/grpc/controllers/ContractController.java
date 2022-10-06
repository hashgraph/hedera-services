/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.grpc.controllers;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetBytecode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetBySolidityID;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;

import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.contract.ContractAnswers;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

public class ContractController extends SmartContractServiceGrpc.SmartContractServiceImplBase {
    /* Transactions */
    public static final String CALL_CONTRACT_METRIC = "contractCallMethod";
    public static final String CREATE_CONTRACT_METRIC = "createContract";
    public static final String UPDATE_CONTRACT_METRIC = "updateContract";
    public static final String DELETE_CONTRACT_METRIC = "deleteContract";
    public static final String CALL_ETHEREUM_METRIC = "callEthereum";
    /* Queries */
    public static final String GET_CONTRACT_INFO_METRIC = "getContractInfo";
    public static final String LOCALCALL_CONTRACT_METRIC = "contractCallLocalMethod";
    public static final String GET_CONTRACT_RECORDS_METRIC = "getTxRecordByContractID";
    public static final String GET_CONTRACT_BYTECODE_METRIC = "ContractGetBytecode";
    public static final String GET_SOLIDITY_ADDRESS_INFO_METRIC = "getBySolidityID";

    private final ContractAnswers contractAnswers;
    private final TxnResponseHelper txnHelper;
    private final QueryResponseHelper queryHelper;

    @Inject
    public ContractController(
            ContractAnswers contractAnswers,
            TxnResponseHelper txnHelper,
            QueryResponseHelper queryHelper) {
        this.txnHelper = txnHelper;
        this.queryHelper = queryHelper;
        this.contractAnswers = contractAnswers;
    }

    @Override
    public void createContract(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, ContractCreate);
    }

    @Override
    public void updateContract(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, ContractUpdate);
    }

    @Override
    public void contractCallMethod(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, ContractCall);
    }

    @Override
    public void getContractInfo(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, contractAnswers.getContractInfo(), ContractGetInfo);
    }

    @Override
    public void contractCallLocalMethod(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, contractAnswers.contractCallLocal(), ContractCallLocal);
    }

    @Override
    public void contractGetBytecode(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, contractAnswers.getBytecode(), ContractGetBytecode);
    }

    @Override
    public void getBySolidityID(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(query, observer, contractAnswers.getBySolidityId(), GetBySolidityID);
    }

    @Override
    public void getTxRecordByContractID(Query query, StreamObserver<Response> observer) {
        queryHelper.answer(
                query, observer, contractAnswers.getContractRecords(), ContractGetRecords);
    }

    @Override
    public void deleteContract(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, ContractDelete);
    }

    @Override
    public void systemDelete(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, SystemDelete);
    }

    @Override
    public void systemUndelete(
            Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, SystemUndelete);
    }

    @Override
    public void callEthereum(Transaction signedTxn, StreamObserver<TransactionResponse> observer) {
        txnHelper.submit(signedTxn, observer, EthereumTransaction);
    }
}
