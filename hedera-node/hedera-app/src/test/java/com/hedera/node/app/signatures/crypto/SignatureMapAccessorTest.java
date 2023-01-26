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
package com.hedera.node.app.signatures.crypto;

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.SignatureMap;
import org.junit.jupiter.api.Test;

class SignatureMapAccessorTest {
    private final byte[] MOCK_PUBLIC_KEY = new byte[0];

    private final SignatureMapAccessor subject =
            new SignatureMapAccessor(SignatureMap.getDefaultInstance());

    @Test
    void nothingIsImplemented() {
        assertThrows(AssertionError.class, () -> subject.getSignatureByPublicKey(MOCK_PUBLIC_KEY));
        assertThrows(AssertionError.class, () -> subject.getSignatureByEvmAddress(MOCK_PUBLIC_KEY));
        assertThrows(AssertionError.class, subject::getRemainingExplicitSignatures);
    }
}
