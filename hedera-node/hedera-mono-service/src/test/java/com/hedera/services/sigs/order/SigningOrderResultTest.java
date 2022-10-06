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
package com.hedera.services.sigs.order;

import static java.util.Collections.EMPTY_LIST;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SigningOrderResultTest {
    @Test
    void representsErrorsAccurately() {
        // given:
        SigningOrderResult<String> subject = new SigningOrderResult<>("NOPE!");

        // expect:
        assertEquals("SigningOrderResult{outcome=FAILURE, details=NOPE!}", subject.toString());
    }

    @Test
    void representsSuccessAccurately() {
        // given:
        SigningOrderResult<String> subject = new SigningOrderResult<>(EMPTY_LIST);

        // expect:
        assertEquals("SigningOrderResult{outcome=SUCCESS, keys=[]}", subject.toString());
    }
}
