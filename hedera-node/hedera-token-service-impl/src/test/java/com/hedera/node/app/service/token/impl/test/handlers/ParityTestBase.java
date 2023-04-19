/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import org.junit.jupiter.api.BeforeEach;

public class ParityTestBase {
    protected ReadableAccountStore readableAccountStore;
    protected ReadableTokenStore readableTokenStore;
    protected TokenID token = TokenID.newBuilder().tokenNum(1).build();

    @BeforeEach
    void setUp() {
        readableAccountStore = SigReqAdapterUtils.wellKnownAccountStoreAt();
        readableTokenStore = SigReqAdapterUtils.wellKnownTokenStoreAt();
    }

    protected TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return toPbj(scenario.platformTxn().getTxn());
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
