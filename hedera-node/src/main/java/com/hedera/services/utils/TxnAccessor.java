package com.hedera.services.utils;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;

/**
 * Defines a type that gives access to several commonly referenced
 * parts of a Hedera Services gRPC {@link Transaction}.
 */
public interface TxnAccessor {
    int sigMapSize();
    int numSigPairs();
    SignatureMap getSigMap();

    long getOfferedFee();
    AccountID getPayer();
    TransactionID getTxnId();
    HederaFunctionality getFunction();

    byte[] getMemoUtf8Bytes();
    String getMemo();

    byte[] getHash();
    byte[] getTxnBytes();
    byte[] getSignedTxnWrapperBytes();
    Transaction getSignedTxn4Log();
    Transaction getSignedTxnWrapper();
    TransactionBody getTxn();

    boolean canTriggerTxn();
    boolean isTriggeredTxn();
    ScheduleID getScheduleRef();

    default SwirldTransaction getPlatformTxn() {
        throw new UnsupportedOperationException();
    }
}
