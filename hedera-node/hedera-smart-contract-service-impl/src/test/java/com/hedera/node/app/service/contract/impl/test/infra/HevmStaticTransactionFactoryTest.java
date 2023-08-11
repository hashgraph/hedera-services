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

package com.hedera.node.app.service.contract.impl.test.infra;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONTRACTS_CONFIG;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.service.contract.impl.infra.HevmStaticTransactionFactory;
import com.hedera.node.app.service.token.ReadableAccountStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HevmStaticTransactionFactoryTest {
    @Mock
    private ReadableAccountStore accountStore;

    private HevmStaticTransactionFactory subject;

    @BeforeEach
    void setUp() {
        subject = new HevmStaticTransactionFactory(DEFAULT_CONTRACTS_CONFIG, accountStore);
    }

    @Test
    void fromHapiQueryNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.fromHapiQuery(Query.DEFAULT));
    }
}
