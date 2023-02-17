/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.grpc.marshalling;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.Test;

class AssessedCustomFeeWrapperTest {
    private static final EntityId AN_ACCOUNT = new EntityId(0, 0, 1111);
    private static final EntityId A_TOKEN = new EntityId(0, 0, 2222);

    @Test
    void managesTokenMetaAsExpected() {
        final var hbarSubject =
                new AssessedCustomFeeWrapper(AN_ACCOUNT, 1L, new AccountID[] {AN_ACCOUNT.toGrpcAccountId()});
        final var tokenSubject =
                new AssessedCustomFeeWrapper(AN_ACCOUNT, A_TOKEN, 1L, new AccountID[] {AN_ACCOUNT.toGrpcAccountId()});
        assertTrue(hbarSubject.isForHbar());
        assertFalse(tokenSubject.isForHbar());
        assertEquals(A_TOKEN, tokenSubject.token());
    }
}
