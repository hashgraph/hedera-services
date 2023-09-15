/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.failure;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

/**
 * Some {@link ExceptionalHaltReason}s that are not part of the Besu core.
 */
public enum CustomExceptionalHaltReason implements ExceptionalHaltReason {
    TOO_MANY_CHILD_RECORDS("CONTRACT_EXECUTION_EXCEPTION", CONTRACT_EXECUTION_EXCEPTION),
    ACCOUNTS_LIMIT_REACHED("CONTRACT_EXECUTION_EXCEPTION", CONTRACT_EXECUTION_EXCEPTION),
    INVALID_VALUE_TRANSFER("Value transfer not allowed to system or expired accounts", CONTRACT_EXECUTION_EXCEPTION);

    /**
     * The default status returned for halted contract executions, by tradition.
     */
    private static final ResponseCodeEnum DEFAULT_STATUS = CONTRACT_EXECUTION_EXCEPTION;
    /**
     * The default error message returned for halted contract executions, by tradition.
     */
    private static final String DEFAULT_ERROR_MESSAGE = DEFAULT_STATUS.protoName();

    private final String errorMessage;
    private final ResponseCodeEnum status;

    CustomExceptionalHaltReason(String errorMessage, ResponseCodeEnum status) {
        this.errorMessage = errorMessage;
        this.status = status;
    }

    @Override
    public String getDescription() {
        return errorMessage;
    }

    /**
     * Returns the error message corresponding to this halt reason.
     *
     * @return the error message
     */
    public String errorMessage() {
        return status.protoName();
    }

    /**
     * Returns the status code corresponding to this halt reason.
     *
     * @return the status code
     */
    public ResponseCodeEnum correspondingStatus() {
        return status;
    }
}
