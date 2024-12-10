/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hss.scheduledcreate;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateSyntheticTxnFactory.createToken;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.TRANSACTION_ID;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.create.CreateTestHelper.CREATE_FUNGIBLE_V3_TUPLE;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.create.CreateTestHelper.CREATE_NON_FUNGIBLE_V3_TUPLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.scheduledcreate.ScheduledCreateDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.scheduledcreate.ScheduledCreateTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateDecoder;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenCreateWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenExpiryWrapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledCreateDecoderTest {

    @Mock
    private CreateDecoder createDecoder;

    @Mock
    private HederaNativeOperations nativeOperations;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private HssCallAttempt attempt;

    private ScheduledCreateDecoder subject;

    private AccountID accountID = AccountID.newBuilder().accountNum(9898).build();

    @BeforeEach
    void setUp() {
        subject = new ScheduledCreateDecoder(createDecoder);
    }

    @Test
    void decodesScheduledCreateFTCorrectly() {
        // given
        final var expectedTokenCreate = new TokenCreateWrapper(
                true,
                "name",
                "symbol",
                accountID,
                "memo",
                true,
                1000L,
                5,
                10L,
                false,
                List.of(),
                new TokenExpiryWrapper(0L, accountID, Duration.DEFAULT));

        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.nativeOperations()).willReturn(nativeOperations);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(nativeOperations.getTransactionID()).willReturn(TRANSACTION_ID);
        byte[] inputBytes = ScheduledCreateTranslator.SCHEDULED_CREATE_FUNGIBLE
                .encodeCall(CREATE_FUNGIBLE_V3_TUPLE)
                .array();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(createDecoder.getTokenCreateWrapper(
                        CREATE_FUNGIBLE_V3_TUPLE.get(0),
                        true,
                        10,
                        5,
                        attempt.senderId(),
                        attempt.nativeOperations(),
                        attempt.addressIdConverter()))
                .willReturn(expectedTokenCreate);

        // when
        final var result = subject.decodeScheduledCreateFT(attempt);

        // then
        assertTrue(result.hasScheduleCreate());
        assertNotNull(result.scheduleCreate().hasScheduledTransactionBody());
        assertTrue(result.scheduleCreate().scheduledTransactionBody().hasTokenCreation());
        assertEquals(
                createToken(expectedTokenCreate).build(),
                result.scheduleCreate().scheduledTransactionBody().tokenCreation());
    }

    @Test
    void decodesScheduledCreateNFTCorrectly() {
        // given
        final var expectedTokenCreate = new TokenCreateWrapper(
                false,
                "name",
                "symbol",
                accountID,
                "memo",
                true,
                1000L,
                0,
                0L,
                false,
                List.of(),
                new TokenExpiryWrapper(0L, accountID, Duration.DEFAULT));

        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.nativeOperations()).willReturn(nativeOperations);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(nativeOperations.getTransactionID()).willReturn(TRANSACTION_ID);
        byte[] inputBytes = ScheduledCreateTranslator.SCHEDULED_CREATE_NON_FUNGIBLE
                .encodeCall(CREATE_NON_FUNGIBLE_V3_TUPLE)
                .array();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(createDecoder.getTokenCreateWrapperNonFungible(
                        CREATE_NON_FUNGIBLE_V3_TUPLE.get(0), attempt.senderId(),
                        attempt.nativeOperations(), attempt.addressIdConverter()))
                .willReturn(expectedTokenCreate);

        // when
        final var result = subject.decodeScheduledCreateNFT(attempt);

        // then
        assertTrue(result.hasScheduleCreate());
        assertNotNull(result.scheduleCreate().hasScheduledTransactionBody());
        assertTrue(result.scheduleCreate().scheduledTransactionBody().hasTokenCreation());
        assertEquals(
                createToken(expectedTokenCreate).build(),
                result.scheduleCreate().scheduledTransactionBody().tokenCreation());
    }
}
