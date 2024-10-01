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

package com.hedera.node.app.service.contract.impl.test.exec.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.node.app.service.contract.impl.exec.scope.QuerySystemContractOperations;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.workflows.QueryContext;
import java.time.InstantSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuerySystemContractOperationsTest {
    @Mock
    private QueryContext context;

    @Mock
    private ExchangeRateInfo exchangeRateInfo;

    private final InstantSource instantSource = InstantSource.system();

    private QuerySystemContractOperations subject;

    @BeforeEach
    void setUp() {
        subject = new QuerySystemContractOperations(context, instantSource);
    }

    @Test
    void exchangeRateTest() {
        final ExchangeRate exchangeRate = new ExchangeRate(1, 2, TimestampSeconds.DEFAULT);
        given(context.exchangeRateInfo()).willReturn(exchangeRateInfo);
        given(exchangeRateInfo.activeRate(any())).willReturn(exchangeRate);
        var result = subject.currentExchangeRate();
        assertThat(result).isEqualTo(exchangeRate);
    }
}
