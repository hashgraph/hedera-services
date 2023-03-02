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

package com.swirlds.logging.payloads.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.logging.payloads.AbstractLogPayload;
import com.swirlds.logging.payloads.PayloadParsingException;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class AbstractLogPayloadTest {

    /**
     * Simple unit test for bean
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.LOGGING)
    void payloadErrors() {
        assertEquals("", AbstractLogPayload.extractPayloadType(""), "Should be empty");

        AbstractLogPayload payload = new AbstractLogPayload() {};
        assertThrows(IllegalArgumentException.class, () -> payload.setMessage("{"), "Braces should be illegal");

        assertThrows(
                PayloadParsingException.class,
                () -> AbstractLogPayload.parsePayload(AbstractLogPayload.class, ""),
                "Should throw if no data");
    }
}
