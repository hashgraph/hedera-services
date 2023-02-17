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
package com.hedera.node.app.service.token.impl.test;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.token.impl.components.DaggerTokenComponent;
import com.hedera.node.app.service.token.impl.components.TokenComponent;
import org.junit.jupiter.api.Test;

class TokenComponentTest {
    @Test
    void objectGraphRootsAreAvailable() {
        // given:
        TokenComponent subject = DaggerTokenComponent.factory().create();

        // expect:
        assertNotNull(subject.cryptoAddLiveHashHandler());
        assertNotNull(subject.cryptoApproveAllowanceHandler());
        assertNotNull(subject.cryptoCreateHandler());
        assertNotNull(subject.cryptoDeleteAllowanceHandler());
        assertNotNull(subject.cryptoDeleteHandler());
        assertNotNull(subject.cryptoDeleteLiveHashHandler());
        assertNotNull(subject.cryptoGetAccountBalanceHandler());
        assertNotNull(subject.cryptoGetAccountInfoHandler());
        assertNotNull(subject.cryptoGetAccountRecordsHandler());
        assertNotNull(subject.cryptoGetLiveHashHandler());
        assertNotNull(subject.cryptoGetStakersHandler());
        assertNotNull(subject.cryptoTransferHandler());
        assertNotNull(subject.cryptoUpdateHandler());
        assertNotNull(subject.tokenAccountWipeHandler());
        assertNotNull(subject.tokenAssociateToAccountHandler());
        assertNotNull(subject.tokenBurnHandler());
        assertNotNull(subject.tokenCreateHandler());
        assertNotNull(subject.tokenDeleteHandler());
        assertNotNull(subject.tokenDissociateFromAccountHandler());
        assertNotNull(subject.tokenFeeScheduleUpdateHandler());
        assertNotNull(subject.tokenFreezeAccountHandler());
        assertNotNull(subject.tokenGetAccountNftInfosHandler());
        assertNotNull(subject.tokenGetInfoHandler());
        assertNotNull(subject.tokenGetNftInfoHandler());
        assertNotNull(subject.tokenGetNftInfosHandler());
        assertNotNull(subject.tokenGrantKycToAccountHandler());
        assertNotNull(subject.tokenMintHandler());
        assertNotNull(subject.tokenPauseHandler());
        assertNotNull(subject.tokenRevokeKycFromAccountHandler());
        assertNotNull(subject.tokenUnfreezeAccountHandler());
        assertNotNull(subject.tokenUnpauseHandler());
        assertNotNull(subject.tokenUpdateHandler());
    }
}
