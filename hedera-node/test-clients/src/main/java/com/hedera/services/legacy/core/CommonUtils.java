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

package com.hedera.services.legacy.core;

import static com.swirlds.common.utility.CommonUtils.hex;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.Base64;

/** Common utilities. */
public class CommonUtils {
    /**
     * Decode base64 string to bytes
     *
     * @param base64string base64 string to be decoded
     * @return decoded bytes
     */
    public static byte[] base64decode(String base64string) {
        byte[] rv = null;
        rv = Base64.getDecoder().decode(base64string);
        return rv;
    }

    /**
     * Deserialize a Java object from given bytes
     *
     * @param bytes given byte array to be deserialized
     * @return the Java object constructed after deserialization
     */
    public static Object convertFromBytes(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                ObjectInput in = new ObjectInputStream(bis)) {
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String calculateSolidityAddress(int indicator, long realmNum, long accountNum) {
        byte[] solidityByteArray = new byte[20];
        byte[] indicatorBytes = Ints.toByteArray(indicator);
        copyArray(0, solidityByteArray, indicatorBytes);
        byte[] realmNumBytes = Longs.toByteArray(realmNum);
        copyArray(4, solidityByteArray, realmNumBytes);
        byte[] accountNumBytes = Longs.toByteArray(accountNum);
        copyArray(12, solidityByteArray, accountNumBytes);
        return hex(solidityByteArray);
    }

    private static void copyArray(int startInToArray, byte[] toArray, byte[] fromArray) {
        if (fromArray == null || toArray == null) {
            return;
        }
        for (int i = 0; i < fromArray.length; i++) {
            toArray[i + startInToArray] = fromArray[i];
        }
    }
}
