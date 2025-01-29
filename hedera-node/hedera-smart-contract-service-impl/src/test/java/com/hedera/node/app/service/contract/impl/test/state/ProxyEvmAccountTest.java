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

package com.hedera.node.app.service.contract.impl.test.state;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarallowance.HbarAllowanceTranslator.HBAR_ALLOWANCE_PROXY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ACCOUNT_CALL_REDIRECT_CONTRACT_BINARY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ADDRESS_BYTECODE_PATTERN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.contract.impl.state.DispatchingEvmFrameState;
import com.hedera.node.app.service.contract.impl.state.ProxyEvmAccount;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProxyEvmAccountTest {
    private static final long ACCOUNT_NUM = 0x9abcdefabcdefbbbL;
    private static final AccountID ACCOUNT_ID =
            AccountID.newBuilder().accountNum(ACCOUNT_NUM).build();
    private static final Bytes SOME_PRETEND_CODE = Bytes.wrap("<NOT-REALLY-CODE>");

    @Mock
    private DispatchingEvmFrameState state;

    @Mock
    private ProxyEvmAccount subject;

    @BeforeEach
    void setUp() {
        subject = new ProxyEvmAccount(ACCOUNT_ID, state);
    }

    @Test
    void notTokenFacade() {
        assertFalse(subject.isTokenFacade());
    }

    @Test
    void notScheduleTxnFacade() {
        assertFalse(subject.isScheduleTxnFacade());
    }

    @Test
    void returnsEvmCodeOfProxy() {
        final var accountInHex = String.format("%040X", ACCOUNT_NUM);
        final var expected = org.apache.tuweni.bytes.Bytes.fromHexString(
                ACCOUNT_CALL_REDIRECT_CONTRACT_BINARY.replace(ADDRESS_BYTECODE_PATTERN, accountInHex));
        given(state.getAddress(ACCOUNT_ID)).willReturn(Address.fromHexString(accountInHex));
        given(state.getAccountRedirectCode(Address.fromHexString(accountInHex))).willCallRealMethod();

        assertEquals(
                CodeFactory.createCode(expected, 0, false),
                subject.getEvmCode(org.apache.tuweni.bytes.Bytes.wrap(HBAR_ALLOWANCE_PROXY.selector())));
    }

    @Test
    void returnsEvmCodeOfEmptyBytes() {
        given(state.getAccountRedirectCode(null)).willCallRealMethod();

        assertEquals(
                CodeFactory.createCode(org.apache.tuweni.bytes.Bytes.EMPTY, 0, false),
                subject.getEvmCode(org.apache.tuweni.bytes.Bytes.wrap(SOME_PRETEND_CODE.toByteArray())));
    }

    @Test
    void returnsEvmCodeHashOfProxy() {
        final var accountInHex = String.format("%040X", ACCOUNT_NUM);
        final var expected = org.apache.tuweni.bytes.Bytes.fromHexString(
                ACCOUNT_CALL_REDIRECT_CONTRACT_BINARY.replace(ADDRESS_BYTECODE_PATTERN, accountInHex));
        given(state.getAddress(ACCOUNT_ID)).willReturn(Address.fromHexString(accountInHex));
        given(state.getAccountRedirectCode(Address.fromHexString(accountInHex))).willCallRealMethod();
        given(state.getAccountRedirectCodeHash(Address.fromHexString(accountInHex)))
                .willCallRealMethod();

        final var expectedHash = CodeFactory.createCode(expected, 0, false).getCodeHash();

        subject.getEvmCode(org.apache.tuweni.bytes.Bytes.wrap(HBAR_ALLOWANCE_PROXY.selector()));
        final var hash = subject.getCodeHash();

        assertEquals(expectedHash, hash);
    }

    @Test
    void returnsEvmCodeHashOfEmptyBytes() {
        given(state.getAccountRedirectCode(null)).willCallRealMethod();
        given(state.getAccountRedirectCodeHash(null)).willCallRealMethod();

        final var expectedHash = CodeFactory.createCode(org.apache.tuweni.bytes.Bytes.EMPTY, 0, false)
                .getCodeHash();

        subject.getEvmCode(org.apache.tuweni.bytes.Bytes.wrap(SOME_PRETEND_CODE.toByteArray()));
        final var hash = subject.getCodeHash();

        assertEquals(expectedHash, hash);
    }
}
