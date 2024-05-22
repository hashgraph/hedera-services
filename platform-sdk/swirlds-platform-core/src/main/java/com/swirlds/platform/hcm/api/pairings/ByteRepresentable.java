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

/**
 * An interface representing objects that can be serialized into a byte array, deserialized from a byte array,
 * and validated for correctness.
 *
 * <H2>Usage Example:</H2>
 * <pre>
 * {@code
 * public class MyObject implements ByteRepresentable<MyObject> {
 *     private int value;
 *
 *     public MyObject(int value) {
 *         this.value = value;
 *     }
 *
 *     {@literal @}Override
 *     public byte[] toByteArray() {
 *         // Convert the value to a byte array
 *         ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
 *         buffer.putInt(value);
 *         return buffer.array();
 *     }
 *
 *     {@literal @}Override
 *     public MyObject fromBytes(byte[] bytes) {
 *         // Convert the byte array back to an integer
 *         ByteBuffer buffer = ByteBuffer.wrap(bytes);
 *         int value = buffer.getInt();
 *         return new MyObject(value);
 *     }
 * }
 *
 * MyObject obj = new MyObject(123);
 * byte[] bytes = obj.toByteArray();
 * MyObject newObj = obj.fromBytes(bytes);
 * }
 * </pre>
 */
public interface ByteRepresentable<T> {

    T fromBytes(byte[] bytes);
    /**
     * Serializes the field element to bytes
     *
     * @return the byte array representing the element
     */
    byte[] toBytes();

    /**
     * TODO: MOVED BUT NEED TO CHECK ITS USEFULNESS
     */
    boolean isValid();
}
