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
package com.hedera.node.app.workflows.onset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

class OnsetResultTest {

    @SuppressWarnings("ConstantConditions")
    @Test
    void checkConstructorWithIllegalArguments() {
        // given
        final var txBody = TransactionBody.getDefaultInstance();
        final var signatureMap = SignatureMap.getDefaultInstance();
        final var functionality = HederaFunctionality.NONE;

        // then
        assertThatThrownBy(() -> new OnsetResult(null, signatureMap, functionality))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OnsetResult(txBody, null, functionality))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OnsetResult(txBody, signatureMap, null))
                .isInstanceOf(NullPointerException.class);
    }
}
