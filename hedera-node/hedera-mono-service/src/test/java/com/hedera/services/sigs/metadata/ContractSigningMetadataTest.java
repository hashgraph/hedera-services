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
package com.hedera.services.sigs.metadata;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ContractSigningMetadataTest {
    private final JKey jkey = new JEd25519Key("jkey".getBytes(StandardCharsets.UTF_8));
    private ContractSigningMetadata subject = new ContractSigningMetadata(jkey, false);

    @Test
    void assertHasAdminKey() {
        assertTrue(subject.hasAdminKey());
    }
}
