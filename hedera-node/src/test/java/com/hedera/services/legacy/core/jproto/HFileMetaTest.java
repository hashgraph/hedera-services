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
package com.hedera.services.legacy.core.jproto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.files.HFileMeta;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HFileMetaTest {
    private long expiry = 1_234_567L;
    private JKey wacl = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asJKeyUnchecked().getKeyList();
    private String memo = "Remember me?";
    private boolean deleted = true;

    HFileMeta subject;

    @BeforeEach
    void setUp() {
        subject = new HFileMeta(deleted, wacl, expiry, memo);
    }

    @Test
    void toStringWorks() {
        // given:
        var expected =
                "HFileMeta{"
                        + "memo="
                        + memo
                        + ", "
                        + "wacl="
                        + MiscUtils.describe(wacl)
                        + ", "
                        + "expiry="
                        + expiry
                        + ", "
                        + "deleted="
                        + deleted
                        + "}";

        // when:
        var actual = subject.toString();

        // then:
        assertEquals(expected, actual);
    }
}
