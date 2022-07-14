/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import static org.mockito.Mockito.mock;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UnpauseLogicTest {
    private long tokenNum = 12345L;
    private TokenID tokenID = IdUtils.asToken("0.0." + tokenNum);
    private Id tokenId = new Id(0, 0, tokenNum);

    private TypedTokenStore tokenStore;
    private TransactionContext txnCtx;
    private SignedTxnAccessor accessor;
    private Token token;

    private TransactionBody tokenUnpauseTxn;
    private TokenUnpauseTransitionLogic subject;

    @BeforeEach
    private void setup() {
        tokenStore = mock(TypedTokenStore.class);
        accessor = mock(SignedTxnAccessor.class);
        token = mock(Token.class);

        txnCtx = mock(TransactionContext.class);

        UnpauseLogic unpauseLogic = new UnpauseLogic(tokenStore);
        subject = new TokenUnpauseTransitionLogic(txnCtx, unpauseLogic);
    }

    // TODO : Add tests that correspond to the class name
}
