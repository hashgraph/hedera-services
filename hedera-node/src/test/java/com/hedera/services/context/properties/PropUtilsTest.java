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
package com.hedera.services.context.properties;

import static com.hedera.services.context.properties.PropUtils.loadOverride;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

class PropUtilsTest {
    @Test
    void shouldLoadOverride() throws IOException {
        final var loc = "";
        final var intoProps = mock(Properties.class);
        final var fin = mock(InputStream.class);
        final var fileStreamProvider = mock(ThrowingStreamProvider.class);
        given(fileStreamProvider.newInputStream(loc)).willReturn(fin).willThrow(new IOException());
        final var log = mock(Logger.class);

        loadOverride(loc, intoProps, fileStreamProvider, log);
        verify(intoProps).load(fin);

        loadOverride(loc, intoProps, fileStreamProvider, log);
        verify(log).info("No overrides present at {}.", loc);
    }
}
