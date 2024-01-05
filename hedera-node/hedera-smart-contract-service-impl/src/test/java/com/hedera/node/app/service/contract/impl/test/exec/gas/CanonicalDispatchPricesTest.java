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

package com.hedera.node.app.service.contract.impl.test.exec.gas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.service.contract.impl.exec.gas.CanonicalDispatchPrices;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CanonicalDispatchPricesTest {
    @Mock
    private AssetsLoader assetsLoader;

    private CanonicalDispatchPrices subject;

    @BeforeEach
    void setup() {}

    @Test
    void liveAssetsContainPricesForAllDispatchTypes() {
        subject = new CanonicalDispatchPrices(new AssetsLoader());
        for (DispatchType dispatchType : DispatchType.values()) {
            assertDoesNotThrow(() -> subject.canonicalPriceInTinycents(dispatchType), "No price for " + dispatchType);
        }
        // Spot check for TokenAssociate ($0.05)
        final long expectedTokenAssociateTinycentPrice = 5 * 100_000_000;
        assertEquals(expectedTokenAssociateTinycentPrice, subject.canonicalPriceInTinycents(DispatchType.ASSOCIATE));
    }

    @Test
    void propagatesAssetLoadingException() throws IOException {
        given(assetsLoader.loadCanonicalPrices()).willThrow(IOException.class);

        assertThrows(UncheckedIOException.class, () -> new CanonicalDispatchPrices(assetsLoader));
    }
}
