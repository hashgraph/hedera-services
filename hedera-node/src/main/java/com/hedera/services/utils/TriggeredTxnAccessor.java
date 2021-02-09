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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;

public class TriggeredTxnAccessor extends SignedTxnAccessor {
    private final AccountID payer;
    private final ScheduleID scheduleRef;

    public TriggeredTxnAccessor(byte[] signedTxnBytes, AccountID payer, ScheduleID scheduleRef) throws InvalidProtocolBufferException {
        super(signedTxnBytes);
        this.payer = payer;
        this.scheduleRef = scheduleRef;
    }

    @Override
    public boolean isTriggeredTxn() {
        return true;
    }

    @Override
    public AccountID getPayer() { return payer; }

    @Override
    public ScheduleID getScheduleRef() {
        return scheduleRef;
    }
}
