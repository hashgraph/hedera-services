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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.update;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateDecoder.FAILURE_CUSTOMIZER;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EXPLICITLY_IMMUTABLE_FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.MUTABLE_FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_ACCOUNT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateTranslatorTest extends CallTestBase {
    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private ReadableTokenStore readableTokenStore;

    @Mock
    private ReadableAccountStore readableAccountStore;

    private UpdateTranslator subject;

    private final UpdateDecoder decoder = new UpdateDecoder();

    private final Tuple hederaToken = Tuple.of(
            "name",
            "symbol",
            NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
            "memo",
            true,
            1000L,
            false,
            // TokenKey
            new Tuple[] {},
            // Expiry
            Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L));

    @BeforeEach
    void setUp() {
        subject = new UpdateTranslator(decoder);
    }

    @Test
    void failureCustomizerDetectsImmutableTokenWithNullAdminKey() {
        final var body = updateWithTreasuryId();
        given(nativeOperations.readableTokenStore()).willReturn(readableTokenStore);
        // Has no admin key
        given(readableTokenStore.get(FUNGIBLE_TOKEN_ID)).willReturn(FUNGIBLE_TOKEN);
        final var translatedStatus = FAILURE_CUSTOMIZER.customize(body, INVALID_SIGNATURE, mockEnhancement());
        assertEquals(TOKEN_IS_IMMUTABLE, translatedStatus);
    }

    @Test
    void failureCustomizerDetectsImmutableTokenWithExplicitlyImmutableAdminKey() {
        final var body = updateWithTreasuryId();
        given(nativeOperations.readableTokenStore()).willReturn(readableTokenStore);
        // Has no admin key
        given(readableTokenStore.get(FUNGIBLE_TOKEN_ID)).willReturn(EXPLICITLY_IMMUTABLE_FUNGIBLE_TOKEN);
        final var translatedStatus = FAILURE_CUSTOMIZER.customize(body, INVALID_SIGNATURE, mockEnhancement());
        assertEquals(TOKEN_IS_IMMUTABLE, translatedStatus);
    }

    @Test
    void failureCustomizerDoesNotChangeWithMutableToken() {
        final var body = updateWithTreasuryId();
        given(nativeOperations.readableTokenStore()).willReturn(readableTokenStore);
        // Has no admin key
        given(readableTokenStore.get(FUNGIBLE_TOKEN_ID)).willReturn(MUTABLE_FUNGIBLE_TOKEN);
        final var translatedStatus = FAILURE_CUSTOMIZER.customize(body, INVALID_SIGNATURE, mockEnhancement());
        assertEquals(INVALID_SIGNATURE, translatedStatus);
    }

    @Test
    void failureCustomizerDoesNotChangeWithMissingToken() {
        final var body = updateWithTreasuryId();
        given(nativeOperations.readableTokenStore()).willReturn(readableTokenStore);
        final var translatedStatus = FAILURE_CUSTOMIZER.customize(body, INVALID_SIGNATURE, mockEnhancement());
        assertEquals(INVALID_SIGNATURE, translatedStatus);
    }

    @Test
    void failureCustomizerDetectsInvalidTreasuryAccountId() {
        final var body = updateWithTreasuryId();
        given(nativeOperations.readableAccountStore()).willReturn(readableAccountStore);
        final var translatedStatus = FAILURE_CUSTOMIZER.customize(body, INVALID_ACCOUNT_ID, mockEnhancement());
        assertEquals(INVALID_TREASURY_ACCOUNT_FOR_TOKEN, translatedStatus);
    }

    @Test
    void failureCustomizerIgnoresTreasuryAccountIdIfNotSet() {
        final var body = updateWithoutTreasuryId();
        final var translatedStatus = FAILURE_CUSTOMIZER.customize(body, INVALID_ACCOUNT_ID, mockEnhancement());
        assertEquals(INVALID_ACCOUNT_ID, translatedStatus);
    }

    @Test
    void failureCustomizerDoesNothingForInvalidTokenId() {
        final var body = updateWithoutTreasuryId();
        final var translatedStatus = FAILURE_CUSTOMIZER.customize(body, INVALID_TOKEN_ID, mockEnhancement());
        assertEquals(INVALID_TOKEN_ID, translatedStatus);
        verifyNoInteractions(nativeOperations);
    }

    @Test
    void matchesUpdateV1Test() {
        given(attempt.selector()).willReturn(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesUpdateV2Test() {
        given(attempt.selector()).willReturn(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesUpdateV3Test() {
        given(attempt.selector()).willReturn(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void callFromUpdateTest() {
        Tuple tuple = new Tuple(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, hederaToken);
        Bytes inputBytes = Bytes.wrapByteBuffer(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1.encodeCall(tuple));
        given(attempt.input()).willReturn(inputBytes);
        given(attempt.selector()).willReturn(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1.selector());
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convertSender(any())).willReturn(NON_SYSTEM_ACCOUNT_ID);
        given(addressIdConverter.convert(any())).willReturn(NON_SYSTEM_ACCOUNT_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(DispatchForResponseCodeHtsCall.class);
    }

    private TransactionBody updateWithTreasuryId() {
        final var op = TokenUpdateTransactionBody.newBuilder()
                .token(FUNGIBLE_TOKEN_ID)
                .treasury(NON_SYSTEM_ACCOUNT_ID)
                .build();
        return TransactionBody.newBuilder().tokenUpdate(op).build();
    }

    private TransactionBody updateWithoutTreasuryId() {
        final var op =
                TokenUpdateTransactionBody.newBuilder().token(FUNGIBLE_TOKEN_ID).build();
        return TransactionBody.newBuilder().tokenUpdate(op).build();
    }
}
