/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *  
 */

package com.hedera.node.app.util;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MerkleTree {

    public static String sha384(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-384");
            byte[] encodedhash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if(hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String calculateMerkleRoot(List<String> elements) {
        List<String> hashList = new ArrayList<>();

        // Initial hashing of elements
        for (String element : elements) {
            hashList.add(sha384(element));

            // Perform the double hashing operation
            int i = hashList.size() - 1;
            while ((i & 1) != 0) {
                i >>= 1;
                String x = hashList.remove(i);
                String y = hashList.remove(i);
                hashList.add(i, sha384(x + y));
            }
        }

        // Calculate the Merkle root hash
        String merkleRootHash = hashList.get(hashList.size() - 1);
        for (int i = hashList.size() - 2; i >= 0; i--) {
            merkleRootHash = sha384(hashList.get(i) + merkleRootHash);
        }

        return merkleRootHash;
    }
}
