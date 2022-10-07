/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.queries.crypto;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class CryptoAnswersTest {
    @Test
    void getsQueryBalance() {
        final var liveHash = mock(GetLiveHashAnswer.class);
        final var stakers = mock(GetStakersAnswer.class);
        final var accountInfo = mock(GetAccountInfoAnswer.class);
        final var accountBalance = mock(GetAccountBalanceAnswer.class);
        final var accountRecords = mock(GetAccountRecordsAnswer.class);
        final var subject =
                new CryptoAnswers(liveHash, stakers, accountInfo, accountBalance, accountRecords);

        assertSame(liveHash, subject.getLiveHash());
        assertSame(stakers, subject.getStakers());
        assertSame(accountInfo, subject.getAccountInfo());
        assertSame(accountBalance, subject.getAccountBalance());
        assertSame(accountRecords, subject.getAccountRecords());
    }
}
