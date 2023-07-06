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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.asBytes;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class AliasUtils {
    private AliasUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }
    /**
     * Attempts to parse a {@code Key} from given alias {@code ByteString}. If the Key is of type
     * Ed25519 or ECDSA(secp256k1), returns true if it is a valid key; and false otherwise.
     *
     * @param alias given alias byte string
     * @return whether it parses to a valid primitive key
     */
    public static boolean isSerializedProtoKey(@NonNull final Bytes alias) {
        requireNonNull(alias);
        try (final var bais = new ByteArrayInputStream(requireNonNull(asBytes(alias)))) {
            final var stream = new ReadableStreamingData(bais);
            stream.limit(bais.available());
            final var key = Key.PROTOBUF.parse(stream);
            return (key.hasEcdsaSecp256k1() || key.hasEd25519()) && isValid(key);
        } catch (final IOException e) {
            return false;
        }
    }

    /**
     * Parse a {@code Key} from given alias {@code Bytes}. If there is a parse error, throws a
     * {@code HandleException} with {@code INVALID_ALIAS_KEY} response code.
     * @param alias given alias bytes
     * @return the parsed key
     */
    public static Key asKeyFromAlias(@NonNull Bytes alias) {
        requireNonNull(alias);
        try (final var bais = new ByteArrayInputStream(requireNonNull(asBytes(alias)))) {
            final var stream = new ReadableStreamingData(bais);
            return Key.PROTOBUF.parse(stream);
        } catch (final IOException e) {
            throw new HandleException(ResponseCodeEnum.INVALID_ALIAS_KEY);
        }
    }

    public static boolean isAlias(@NonNull final AccountID idOrAlias) {
        requireNonNull(idOrAlias);
        return !idOrAlias.hasAccountNum() && idOrAlias.hasAlias();
    }
}
