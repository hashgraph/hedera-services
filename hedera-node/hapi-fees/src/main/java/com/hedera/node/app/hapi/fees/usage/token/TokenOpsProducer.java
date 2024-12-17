/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.fees.usage.token;

import com.hederahashgraph.api.proto.java.SubType;

/**
 * A functional interface for creating an object of type {@code R}.
 * @param <R> the type of object to create
 */
@FunctionalInterface
public interface TokenOpsProducer<R> {
    /**
     * Creates an object of type {@code R}.
     * @param bpt the base price of the transaction
     * @param subType the subType of the transaction
     * @param transferRecordRb the record bytes for the transfer
     * @param serialNumsCount the serial number count
     * @return
     */
    R create(int bpt, SubType subType, long transferRecordRb, int serialNumsCount);
}
