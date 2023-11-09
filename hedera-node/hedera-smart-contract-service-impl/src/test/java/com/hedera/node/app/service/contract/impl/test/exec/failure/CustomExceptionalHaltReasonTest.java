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

package com.hedera.node.app.service.contract.impl.test.exec.failure;

import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.CONTRACT_ENTITY_LIMIT_REACHED;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_SIGNATURE;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.SELF_DESTRUCT_TO_SELF;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.statusFor;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.Test;

class CustomExceptionalHaltReasonTest {
    @Test
    void translatesSameStatusesAsMonoService() {
        assertEquals(ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID, statusFor(SELF_DESTRUCT_TO_SELF));
        assertEquals(ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS, statusFor(INVALID_SOLIDITY_ADDRESS));
        assertEquals(ResponseCodeEnum.INVALID_SIGNATURE, statusFor(INVALID_SIGNATURE));
        assertEquals(ResponseCodeEnum.INSUFFICIENT_GAS, statusFor(ExceptionalHaltReason.INSUFFICIENT_GAS));
        assertEquals(
                ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION,
                statusFor(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
        assertEquals(
                ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED,
                statusFor(CONTRACT_ENTITY_LIMIT_REACHED));
        assertEquals(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION, statusFor(ExceptionalHaltReason.PRECOMPILE_ERROR));
    }

    @Test
    void usesToStringForErrorMessages() {
        assertEquals("INVALID_SOLIDITY_ADDRESS", CustomExceptionalHaltReason.errorMessageFor(INVALID_SOLIDITY_ADDRESS));
    }
}
