/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.hevm;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public record HydratedEthTxData(@Nullable EthTxData ethTxData, @NonNull ResponseCodeEnum status) {
    public HydratedEthTxData {
        requireNonNull(status);
        if (status == OK) {
            requireNonNull(ethTxData);
        }
    }

    public static HydratedEthTxData successFrom(@NonNull final EthTxData ethTxData) {
        return new HydratedEthTxData(ethTxData, OK);
    }

    public static HydratedEthTxData failureFrom(@NonNull final ResponseCodeEnum status) {
        return new HydratedEthTxData(null, status);
    }

    public boolean isAvailable() {
        return ethTxData != null;
    }

    public @NonNull EthTxData ethTxDataOrThrow() {
        return requireNonNull(ethTxData);
    }
}
