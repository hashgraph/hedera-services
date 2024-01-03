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

package com.hedera.node.app.service.contract.impl.test.infra;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETH_DATA_WITH_TO_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hedera.node.app.service.contract.impl.infra.EthTxSigsCache;
import org.junit.jupiter.api.Test;

class EthTxSigsCacheTest {
    private final EthTxSigsCache subject = new EthTxSigsCache();

    @Test
    void reportsExpectedSignatures() {
        final var expectedSigs = EthTxSigs.extractSignatures(ETH_DATA_WITH_TO_ADDRESS);

        assertEquals(expectedSigs, subject.computeIfAbsent(ETH_DATA_WITH_TO_ADDRESS));
    }
}
