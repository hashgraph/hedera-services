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
package com.hedera.node.app.spi.test.meta;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionMetadataTest {
    @Mock AccountKeyLookup lookup;
    final List<HederaKey> requiredKeys = new ArrayList<>();
    final TransactionBody txnBody = TransactionBody.getDefaultInstance();
    final AccountID payer = AccountID.getDefaultInstance();

    @Test
    void testCopy() {
        final var metadata = new SigTransactionMetadata(txnBody, payer, OK, requiredKeys);
        final var copy = metadata.copy(lookup).build();
        assertNotNull(copy);
        assertEquals(payer, copy.payer());
        assertEquals(requiredKeys, copy.requiredKeys());
        assertEquals(txnBody, copy.txnBody());
        assertEquals(OK, copy.status());
        assertEquals(payer, copy.payer());
    }
}
