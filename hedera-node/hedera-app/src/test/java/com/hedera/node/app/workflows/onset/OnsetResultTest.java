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

package com.hedera.node.app.workflows.onset;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import org.junit.jupiter.api.Test;

class OnsetResultTest {

    @SuppressWarnings("ConstantConditions")
    @Test
    void checkConstructorWithIllegalArguments() {
        // given
        final var txn = Transaction.newBuilder().build();
        final var txBody = TransactionBody.newBuilder().build();
        final var sigMap = SignatureMap.newBuilder().build();
        final var function = HederaFunctionality.NONE;

        // then
        assertThatThrownBy(() -> new OnsetResult(txn, null, OK, sigMap, function))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OnsetResult(txn, txBody, null, sigMap, function))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OnsetResult(txn, txBody, OK, null, function))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OnsetResult(txn, txBody, OK, sigMap, null))
                .isInstanceOf(NullPointerException.class);
    }
}
