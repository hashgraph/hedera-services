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

package com.swirlds.signaturescheme.api;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Contains the bytes of a cryptographic element in a signature schema, with the corresponding signature schema.
 */
public record SerializedSignatureSchemaObject(@NonNull SignatureSchema schema, @NonNull byte[] elementBytes) {
    /**
     * Convert a byte array containing a cryptographic element into a SerializedSignatureSchemaObject
     * <p>
     * The first byte of the byte array is assumed to represent the signature schema type. The rest of the bytes are
     * assumed to be the actual bytes of the element contained by the input byte array.
     *
     * @param inputBytes the byte array to convert
     * @return the SerializedSignatureSchemaObject
     */
    public static SerializedSignatureSchemaObject fromByteArray(@NonNull final byte[] inputBytes) {
        final int elementSize = inputBytes.length - 1;

        final byte[] elementBytes = new byte[elementSize];
        System.arraycopy(inputBytes, 1, elementBytes, 0, elementSize);

        return new SerializedSignatureSchemaObject(SignatureSchema.fromIdByte(inputBytes[0]), elementBytes);
    }
}
