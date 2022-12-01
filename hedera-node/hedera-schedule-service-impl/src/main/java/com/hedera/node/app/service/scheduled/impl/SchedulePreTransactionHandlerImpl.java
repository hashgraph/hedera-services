/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.scheduled.impl;

import com.hedera.node.app.service.scheduled.SchedulePreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.NotImplementedException;

/**
 * A {@link SchedulePreTransactionHandler} implementation that pre-computes the required signing keys
 * (but not the candidate signatures) for each schedule operation.
 */
public class SchedulePreTransactionHandlerImpl implements SchedulePreTransactionHandler {

    @Override
    public TransactionMetadata preHandleCreateSchedule(TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleSignSchedule(TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleDeleteSchedule(TransactionBody txn) {
        throw new NotImplementedException();
    }
}
