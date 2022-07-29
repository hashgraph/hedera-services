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

import java.io.IOException;
import java.util.Properties;
import org.apache.logging.log4j.Logger;

public final class PropUtils {
    private PropUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static void loadOverride(
            final String loc,
            final Properties intoProps,
            final ThrowingStreamProvider fileStreamProvider,
            final Logger log) {
        try (final var fin = fileStreamProvider.newInputStream(loc)) {
            intoProps.load(fin);
        } catch (IOException ignore) {
            log.info("No overrides present at {}.", loc);
        }
    }
}
