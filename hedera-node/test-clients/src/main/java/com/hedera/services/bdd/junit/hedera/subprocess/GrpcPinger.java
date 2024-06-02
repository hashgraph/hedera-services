/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.hedera.subprocess;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class GrpcPinger {
    public boolean isLive(final int port) {
        try {
            final var url = new URL("http://localhost:" + port + "/");
            final var connection = url.openConnection();
            connection.connect();
            return true;
        } catch (MalformedURLException impossible) {
            throw new IllegalStateException(impossible);
        } catch (IOException ignored) {
            // This is expected, the node is not up yet
        }
        return false;
    }
}
