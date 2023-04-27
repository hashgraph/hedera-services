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

package com.hedera.node.app.signature.hapi;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class VerificationBenchmark implements Scenarios {

    @Param({"100", "200", "300", "500", "1000"})
    public int iterations;

    public Key key;
    public List<SignaturePair> sigPairs;
    public Cryptography fakeCryptoEngine;
    public Bytes fakeSignedBytes;
    public SignatureVerifierImpl subject;

    @Setup(Level.Invocation)
    public void setUp() {
        key = FAKE_ED25519_KEY_INFOS[0].publicKey();
        sigPairs = List.of(SignaturePair.newBuilder()
                .pubKeyPrefix(key.ed25519OrThrow().slice(0, 10))
                .build());
        fakeCryptoEngine = new FakeCryptoEngine();
        fakeSignedBytes = Bytes.wrap(new byte[] {1, 2, 3, 4, 5});
        subject = new SignatureVerifierImpl(fakeCryptoEngine);
    }

    @Benchmark
    public void singleKey_singleSignature(Blackhole blackhole) {
        blackhole.consume(subject.verify(key, fakeSignedBytes, sigPairs));
    }
}
