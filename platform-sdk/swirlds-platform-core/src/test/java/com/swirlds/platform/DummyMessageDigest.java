/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import com.swirlds.common.crypto.DigestType;
import java.security.MessageDigest;
import java.util.Arrays;

public class DummyMessageDigest extends MessageDigest {
    private static final byte mdHash[] = new byte[DigestType.SHA_384.digestLength()];

    static {
        Arrays.fill(mdHash, (byte) 1);
    }

    public DummyMessageDigest() {
        super(DigestType.SHA_384.algorithmName());
    }

    public static byte[] getDummyHashValue() {
        return mdHash.clone();
    }

    @Override
    public void update(byte arg0) {}

    @Override
    public void update(byte[] arg0) {}

    @Override
    public byte[] digest() {
        return mdHash.clone();
    }

    @Override
    public byte[] digest(byte[] arg0) {
        return mdHash.clone();
    }

    @Override
    protected void engineUpdate(byte arg0) {}

    @Override
    protected void engineUpdate(byte[] arg0, int arg1, int arg2) {}

    @Override
    protected byte[] engineDigest() {
        return null;
    }

    @Override
    protected void engineReset() {}
}
