/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.transaction.GetByKeyQuery;
import com.hedera.hapi.node.transaction.GetByKeyResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkGetByKeyHandler;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.app.spi.workflows.WorkflowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NetworkGetByKeyHandlerTest {
    @Mock
    private QueryContext context;

    private NetworkGetByKeyHandler subject;

    @BeforeEach
    void setUp() {
        subject = new NetworkGetByKeyHandler();
    }

    @Test
    void extractsHeader() {
        final var data = GetByKeyQuery.newBuilder()
                .header(QueryHeader.newBuilder().build())
                .build();
        final var query = Query.newBuilder().getByKey(data).build();
        final var header = subject.extractHeader(query);
        final var op = query.getByKeyOrThrow();
        assertThat(op.header()).isEqualTo(header);
    }

    @Test
    void createsEmptyResponse() {
        final var responseHeader = ResponseHeader.newBuilder().build();
        final var response = subject.createEmptyResponse(responseHeader);
        final var expectedResponse = Response.newBuilder()
                .getByKey(GetByKeyResponse.newBuilder().header(responseHeader))
                .build();
        assertThat(expectedResponse).isEqualTo(response);
    }

    @Test
    void validateThrowsPreCheck() {
        assertThatThrownBy(() -> subject.validate(context))
                .isInstanceOf(WorkflowException.class)
                .has(responseCode(NOT_SUPPORTED));
    }

    @Test
    void findResponseThrowsUnsupported() {
        final var responseHeader = ResponseHeader.newBuilder().build();
        assertThatThrownBy(() -> subject.findResponse(context, responseHeader))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
