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

package com.swirlds.platform.hcm.api.pairings;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An interface representing objects that can be serialized into a byte array.
 *
 * <H2>Usage Example:</H2>
 * <pre>
 * {@code
 * public class MyObject implements ByteRepresentable {
 *     private int value;
 *
 *     public MyObject(int value) {
 *         this.value = value;
 *     }
 *
 *     {@literal @}Override
 *     public byte[] toBytes() {
 *         // Convert the value to a byte array
 *         ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
 *         buffer.putInt(value);
 *         return buffer.array();
 *     }
 * }
 *
 * MyObject obj = new MyObject(123);
 * byte[] bytes = obj.toBytes();
 * }
 * </pre>
 */
public interface ByteRepresentable {

    /**
     * Serializes the field element to bytes
     *
     * @return the byte array representing the element
     */
    @NonNull
    byte[] toBytes();
}
