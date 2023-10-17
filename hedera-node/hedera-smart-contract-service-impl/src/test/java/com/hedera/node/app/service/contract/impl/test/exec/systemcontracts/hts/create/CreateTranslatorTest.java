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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CreateTranslatorTest {

    @Mock
    private HtsCallAttempt attempt;

    private CreateDecoder decoder = new CreateDecoder();

    private CreateTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new CreateTranslator(decoder);
    }

    @Test
    void matchesCreateFungibleTokenV1() {
        given(attempt.selector()).willReturn(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesCreateFungibleTokenV2() {
        given(attempt.selector()).willReturn(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V2.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesCreateFungibleTokenV3() {
        given(attempt.selector()).willReturn(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V3.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesCreateFungibleTokenWithCustomFeesV1() {
        given(attempt.selector()).willReturn(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesCreateFungibleTokenWithCustomFeesV2() {
        given(attempt.selector()).willReturn(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesCreateFungibleTokenWithCustomFeesV3() {
        given(attempt.selector()).willReturn(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesCreateNonFungibleTokenV1() {
        given(attempt.selector()).willReturn(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesCreateNonFungibleTokenV2() {
        given(attempt.selector()).willReturn(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesCreateNonFungibleTokenV3() {
        given(attempt.selector()).willReturn(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesCreateNonFungibleTokenWithCustomFeesV1() {
        given(attempt.selector()).willReturn(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesCreateNonFungibleTokenWithCustomFeesV2() {
        given(attempt.selector()).willReturn(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesCreateNonFungibleTokenWithCustomFeesV3() {
        given(attempt.selector()).willReturn(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }
}
