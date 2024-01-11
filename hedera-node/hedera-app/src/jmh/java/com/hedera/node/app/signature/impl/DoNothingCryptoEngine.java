/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.signature.impl;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Message;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.io.SelfSerializable;
import java.util.List;
import java.util.concurrent.Future;

public class DoNothingCryptoEngine implements Cryptography {
    @Override
    public void digestAsync(Message message) {
        // fake crypto engine does nothing
    }

    @Override
    public void digestAsync(List<Message> list) {
        // fake crypto engine does nothing
    }

    @Override
    public Future<Hash> digestAsync(byte[] bytes, DigestType digestType) {
        return null;
    }

    @Override
    public Hash digestSync(Message message) {
        return null;
    }

    @Override
    public void digestSync(List<Message> list) {
        // fake crypto engine does nothing
    }

    @Override
    public Hash digestSync(byte[] bytes, DigestType digestType) {
        return null;
    }

    @Override
    public Hash digestSync(SelfSerializable selfSerializable, DigestType digestType) {
        return null;
    }

    @Override
    public Hash digestSync(SerializableHashable serializableHashable, DigestType digestType, boolean b) {
        return null;
    }

    @Override
    public Hash getNullHash(DigestType digestType) {
        return null;
    }

    @Override
    public void verifyAsync(TransactionSignature transactionSignature) {
        // fake crypto engine does nothing
    }

    @Override
    public void verifyAsync(List<TransactionSignature> list) {
        // fake crypto engine does nothing
    }

    @Override
    public Future<Boolean> verifyAsync(byte[] bytes, byte[] bytes1, byte[] bytes2, SignatureType signatureType) {
        return null;
    }

    @Override
    public boolean verifySync(TransactionSignature transactionSignature) {
        return false;
    }

    @Override
    public boolean verifySync(List<TransactionSignature> list) {
        return false;
    }

    @Override
    public boolean verifySync(byte[] bytes, byte[] bytes1, byte[] bytes2, SignatureType signatureType) {
        return false;
    }

    @Override
    public Hash calcRunningHash(Hash hash, Hash hash1, DigestType digestType) {
        return null;
    }
}
