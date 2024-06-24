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

package com.hedera.node.app.service.contract.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.contract.ContractGetBytecodeQuery;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetBytecodeHandler;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ContractGetBytecodeHandlerTest {
    @Mock
    private QueryContext context;

    @Mock
    private ContractGetBytecodeQuery contractGetBytecodeQuery;

    @Mock
    private QueryHeader header;

    @Mock
    private ResponseHeader responseHeader;

    @Mock
    private Query query;

    @Mock
    private ContractID contractID;

    @Mock
    private ReadableAccountStore store;

    @Mock
    private ContractStateStore contractStore;

    @Mock
    private Account contract;

    @Mock
    private AccountID accountID;

    private final ContractGetBytecodeHandler subject = new ContractGetBytecodeHandler();

    @Test
    void extractHeaderTest() {
        // given:
        given(query.contractGetBytecodeOrThrow()).willReturn(contractGetBytecodeQuery);
        given(contractGetBytecodeQuery.header()).willReturn(header);

        // when:
        var header = subject.extractHeader(query);

        // then:
        assertThat(header).isNotNull();
    }

    @Test
    void createEmptyResponseTest() {
        // when:
        var response = subject.createEmptyResponse(responseHeader);

        // then:
        assertThat(response).isNotNull();
    }

    @Test
    void validatePositiveTest() {
        // given
        given(context.createStore(ReadableAccountStore.class)).willReturn(store);
        given(context.query()).willReturn(query);
        given(query.contractGetBytecodeOrThrow()).willReturn(contractGetBytecodeQuery);
        given(contractGetBytecodeQuery.contractIDOrElse(ContractID.DEFAULT)).willReturn(contractID);
        given(store.getContractById(contractID)).willReturn(contract);
        given(contract.smartContract()).willReturn(true);

        // when:
        assertThatCode(() -> subject.validate(context)).doesNotThrowAnyException();
    }

    @Test
    void validateFailsIfNoContractIdTest() {
        // given
        given(context.createStore(ReadableAccountStore.class)).willReturn(store);
        given(context.query()).willReturn(query);
        given(query.contractGetBytecodeOrThrow()).willReturn(contractGetBytecodeQuery);
        given(contractGetBytecodeQuery.contractIDOrElse(ContractID.DEFAULT)).willReturn(null);

        // when:
        assertThatThrownBy(() -> subject.validate(context)).isInstanceOf(PreCheckException.class);
    }

    @Test
    void findResponsePositiveTest() {
        // given
        given(responseHeader.nodeTransactionPrecheckCode()).willReturn(OK);
        given(responseHeader.responseType()).willReturn(ANSWER_ONLY);

        given(context.createStore(ReadableAccountStore.class)).willReturn(store);
        given(context.query()).willReturn(query);
        given(query.contractGetBytecodeOrThrow()).willReturn(contractGetBytecodeQuery);
        given(contractGetBytecodeQuery.contractIDOrElse(ContractID.DEFAULT)).willReturn(contractID);
        given(store.getContractById(contractID)).willReturn(contract);
        given(contract.smartContract()).willReturn(true);

        given(context.createStore(ContractStateStore.class)).willReturn(contractStore);
        given(contract.accountIdOrThrow()).willReturn(accountID);
        final var expectedResult = Bytes.wrap(new byte[] {1, 2, 3, 4, 5});
        final var bytecode = Bytecode.newBuilder().code(expectedResult).build();
        given(contractStore.getBytecode(any())).willReturn(bytecode);

        // when:
        var response = subject.findResponse(context, responseHeader);

        assertThat(response.contractGetBytecodeResponse().header()).isEqualTo(responseHeader);
        assertThat(response.contractGetBytecodeResponse().bytecode()).isEqualTo(expectedResult);
    }
}
