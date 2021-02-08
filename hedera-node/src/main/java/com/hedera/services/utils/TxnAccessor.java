package com.hedera.services.utils;

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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;

/**
 * Defines a type that gives access to several commonly referenced
 * parts of a Hedera Services gRPC {@link Transaction}.
 */
public interface TxnAccessor {
    SignatureMap getSigMap();

    HederaFunctionality getFunction();

    Transaction getSignedTxn4Log();

    byte[] getTxnBytes();

    Transaction getBackwardCompatibleSignedTxn();

    TransactionBody getTxn();

    TransactionID getTxnId();

    AccountID getPayer();

    byte[] getBackwardCompatibleSignedTxnBytes();

    ByteString getHash();

    boolean canTriggerTxn();

    boolean isTriggeredTxn();

    ScheduleID getScheduleRef();

    default com.swirlds.common.Transaction getPlatformTxn() { throw new UnsupportedOperationException(); }
}
