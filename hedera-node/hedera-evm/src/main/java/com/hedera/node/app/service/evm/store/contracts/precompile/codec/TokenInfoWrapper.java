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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

public record TokenInfoWrapper<T>(T token, long serialNumber) {
    private static final long INVALID_SERIAL_NUMBER = -1;

    public static <T> TokenInfoWrapper<T> forNonFungibleToken(
            final T token, final long serialNumber) {
        return new TokenInfoWrapper<>(token, serialNumber);
    }

    public static <T> TokenInfoWrapper<T> forFungibleToken(final T token) {
        return initializeWithTokenId(token);
    }

    public static <T> TokenInfoWrapper<T> forToken(final T token) {
        return initializeWithTokenId(token);
    }

    private static <T> TokenInfoWrapper<T> initializeWithTokenId(final T token) {
        return new TokenInfoWrapper<>(token, INVALID_SERIAL_NUMBER);
    }
}
