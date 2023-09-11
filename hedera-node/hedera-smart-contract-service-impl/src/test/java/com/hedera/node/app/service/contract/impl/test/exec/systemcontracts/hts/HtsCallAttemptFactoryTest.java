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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt.REDIRECT_FOR_TOKEN;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfCall.BALANCE_OF;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_LONG_ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.bytesForRedirect;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttemptFactory;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfCall;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HtsCallAttemptFactoryTest extends HtsCallTestBase {
    @Mock
    private MessageFrame frame;

    @Mock
    private ProxyWorldUpdater updater;

    private final HtsCallAttemptFactory subject = new HtsCallAttemptFactory();

    @Test
    void instantiatesAttemptWithInContextEnhancement() {
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.enhancement()).willReturn(mockEnhancement());
        given(nativeOperations.getToken(NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow()))
                .willReturn(FUNGIBLE_TOKEN);

        final var attempt = subject.createFrom(
                Bytes.wrap(REDIRECT_FOR_TOKEN
                        .encodeCallWithArgs(asHeadlongAddress(NON_SYSTEM_LONG_ZERO_ADDRESS), BALANCE_OF.selector())
                        .array()),
                frame);

        assertSame(FUNGIBLE_TOKEN, attempt.redirectToken());
    }

    @Test
    void instantiatesCallWithInContextEnhancement() {
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.enhancement()).willReturn(mockEnhancement());
        given(nativeOperations.getToken(FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(FUNGIBLE_TOKEN);
        given(frame.getSenderAddress()).willReturn(EIP_1014_ADDRESS);

        final var input = bytesForRedirect(
                BALANCE_OF.encodeCallWithArgs(asHeadlongAddress(NON_SYSTEM_LONG_ZERO_ADDRESS)), FUNGIBLE_TOKEN_ID);
        final var call = subject.createCallFrom(input, frame);

        assertInstanceOf(BalanceOfCall.class, call);
    }
}
