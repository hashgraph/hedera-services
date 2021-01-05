package com.hedera.services.sigs.order;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */


import com.swirlds.common.CommonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScheduledTransactionOrderResultTest {
    @Test
    public void representsErrorsAccurately() {
        // given:
        ScheduledTransactionOrderResult<String> subject = new ScheduledTransactionOrderResult<>("NOPE!");

        // expect:
        assertEquals(
                "ScheduledTransactionOrderResult{outcome=FAILURE, details=NOPE!}",
                subject.toString());
    }

    @Test
    public void representsSuccessAccurately() {
        // given:
        var tx = "tx_body".getBytes();
        var hexTx = CommonUtils.hex(tx);
        ScheduledTransactionOrderResult<String> subject = new ScheduledTransactionOrderResult<>(tx);

        // expect:
        assertEquals(
                String.format("ScheduledTransactionOrderResult{outcome=SUCCESS, transactionBody=%s}", hexTx),
                subject.toString());
    }
}
