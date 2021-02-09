package com.hedera.services.legacy.regression;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;

import java.util.concurrent.Callable;

/**
 * Creates a Thread and waits for specific micro second for submitting transaction to network,
 * for testing same transaction is submitted to network simultaneously
 *
 * @author Tirupathi Mandala Created on 2019-08-29
 */
public class ParallelSubmit implements Callable<TransactionResponse> {

    private Transaction transaction;
    private SmartContractServiceGrpc.SmartContractServiceBlockingStub contractStub;
    private long startMicroSecond= 500;
    public ParallelSubmit(){}

    public ParallelSubmit(Transaction transaction, SmartContractServiceGrpc.SmartContractServiceBlockingStub contractStub){
        this.transaction = transaction;
        this.contractStub = contractStub;
    }
    public TransactionResponse call() throws Exception {
        System.out.println(String.format("Starting task thread %s",
                Thread.currentThread().getName()));
        TransactionResponse response = null;
        long executionMicroSecond = getNextMillisecondsForMatch(startMicroSecond);
        //Wait for specific millisecond for submitting transaction
        System.out.println("******Waiting -- "+Thread.currentThread().getName()+", Current Time: "+System.currentTimeMillis());
        while (executionMicroSecond == System.currentTimeMillis()) {
			;
		}
        System.out.println("******Done -- "+Thread.currentThread().getName()+", Completed TIme "+System.currentTimeMillis());
        response = contractStub.createContract(transaction);
        return response;
    }

    private long getNextMillisecondsForMatch(long milliSeconds) {
        long nextMilliseconds;
        if( System.currentTimeMillis()  > milliSeconds  ){
            nextMilliseconds = System.currentTimeMillis() - milliSeconds;
        } else {
            nextMilliseconds = System.currentTimeMillis() + milliSeconds;
        }
        return nextMilliseconds;
    }

}
