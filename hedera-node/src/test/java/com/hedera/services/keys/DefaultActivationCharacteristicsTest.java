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
package com.hedera.services.keys;

import static com.hedera.services.keys.DefaultActivationCharacteristics.DEFAULT_ACTIVATION_CHARACTERISTICS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.JThresholdKey;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultActivationCharacteristicsTest {
    JKeyList l;
    JThresholdKey t;

    KeyActivationCharacteristics subject = DEFAULT_ACTIVATION_CHARACTERISTICS;

    @BeforeEach
    void setup() throws Exception {
        l = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey().getKeyList();
        t = TxnHandlingScenario.LONG_THRESHOLD_KT.asJKey().getThresholdKey();
    }

    @Test
    void defaultsToListLength() {
        // expect:
        assertEquals(l.getKeysList().size(), subject.sigsNeededForList(l));
    }

    @Test
    void defaultsToThresholdReq() {
        // expect:
        assertEquals(t.getThresholdKey().getThreshold(), subject.sigsNeededForThreshold(t));
    }
}
