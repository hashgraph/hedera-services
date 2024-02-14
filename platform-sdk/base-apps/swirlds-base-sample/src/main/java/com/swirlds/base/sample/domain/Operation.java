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

package com.swirlds.base.sample.domain;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigDecimal;
import java.util.List;

public record Operation(
        @Nullable String uuid,
        @NonNull List<OperationDetail> details,
        @NonNull Long timestamp,
        @NonNull OperationType type) {

    public record OperationDetail(
            @NonNull String itemId,
            @NonNull Integer amount,
            @NonNull BigDecimal unitaryPrice,
            @Nullable StockHandlingMode mode) {}
}
