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

package com.hedera.node.app.workflows.prehandle;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A transaction that captures a node due diligence failure; including the
 * associated status and (if it was parseable) the information on the
 * offending transaction.
 */
public class DueDiligenceException extends PreCheckException {
    @Nullable
    private final TransactionInfo transactionInfo;

    public DueDiligenceException(
            @NonNull final ResponseCodeEnum responseCode, @Nullable TransactionInfo transactionInfo) {
        super(responseCode);
        this.transactionInfo = transactionInfo;
    }

    @Nullable
    public TransactionInfo txInfo() {
        return transactionInfo;
    }
}
