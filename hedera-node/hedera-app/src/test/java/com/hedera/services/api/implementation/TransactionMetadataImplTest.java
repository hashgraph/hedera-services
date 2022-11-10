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
package com.hedera.services.api.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

class TransactionMetadataImplTest {

    @Test
    void testSingleParameterConstructor() {
        // given
        final TransactionBody txBody = TransactionBody.getDefaultInstance();

        // when
        final var metadata = new TransactionMetadataImpl(txBody);

        // then
        assertThat(metadata.getTxn()).isEqualTo(txBody);
        assertThat(metadata.status()).isEqualTo(ResponseCodeEnum.OK);
        assertThat(metadata.getReqKeys()).isEmpty();
    }

    @Test
    void testTwoParameterConstructor() {
        // given
        final TransactionBody txBody = TransactionBody.getDefaultInstance();

        // when
        final var metadata = new TransactionMetadataImpl(txBody, ResponseCodeEnum.UNKNOWN);

        // then
        assertThat(metadata.getTxn()).isEqualTo(txBody);
        assertThat(metadata.status()).isEqualTo(ResponseCodeEnum.UNKNOWN);
        assertThat(metadata.getReqKeys()).isEmpty();
    }

    @Test
    void testIllegalParameters() {
        // given
        final TransactionBody txBody = TransactionBody.getDefaultInstance();

        // then
        assertThatThrownBy(() -> new TransactionMetadataImpl(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionMetadataImpl(null, ResponseCodeEnum.UNKNOWN))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionMetadataImpl(txBody, null))
                .isInstanceOf(NullPointerException.class);
    }
}
