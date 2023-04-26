/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.info;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implementation of {@link NetworkInfo} that delegates to the mono-service.
 */
public class MonoNetworkInfo implements NetworkInfo {

    private final com.hedera.node.app.service.mono.config.NetworkInfo delegate;

    /**
     * Constructs a {@link MonoNetworkInfo} with the given delegate.
     *
     * @param delegate the delegate
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public MonoNetworkInfo(@NonNull com.hedera.node.app.service.mono.config.NetworkInfo delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    @NonNull
    public Bytes ledgerId() {
        final var ledgerId = delegate.ledgerId();
        return ledgerId != null ? Bytes.wrap(ledgerId.toByteArray()) : Bytes.EMPTY;
    }
}
