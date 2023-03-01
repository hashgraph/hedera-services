/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.meta;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implementation of {@link TransactionMetadata} that is used when a fatal error occured
 *
 * @param status {@link ResponseCodeEnum} status of the transaction
 */
public record InvalidTransactionMetadata(@NonNull ResponseCodeEnum status)
        implements TransactionMetadata {

    public InvalidTransactionMetadata {
        requireNonNull(status);
        if (status == ResponseCodeEnum.OK) {
            throw new IllegalArgumentException("InvalidTransactionMetadata cannot have OK status");
        }
    }

    @Override
    public boolean failed() {
        return true;
    }
}
