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

package com.hedera.node.app.signature.impl;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * This benchmark measures the time it takes to find the matching {@link SignaturePair}.
 */
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class VerificationBenchmark extends AppTestBase implements Scenarios {
    @Param({"1", "2", "5", "10"})
    public int numSigPairs;

    @Param({"key", "keyList", "thresholdKey", "deepTree", "hollow"})
    public String scenario;

    private Key key;
    private List<SignaturePair> sigPairs;
    private Bytes fakeSignedBytes;
    private SignatureVerifierImpl subject;

    @Setup(Level.Invocation)
    public void setUp() {
        key = createKey(scenario);
        sigPairs = createSigPairs(numSigPairs);
        final var fakeCryptoEngine = new FakeCryptoEngine();
        fakeSignedBytes = Bytes.wrap(new byte[] {1, 2, 3, 4, 5});
        subject = new SignatureVerifierImpl(fakeCryptoEngine);
    }

    @Benchmark
    public void singleKeySingleSignature(Blackhole blackhole) {
        //        blackhole.consume(subject.match(key, fakeSignedBytes, sigPairs));
    }

    private Key createKey(String scenario) {
        return FAKE_ED25519_KEY_INFOS[0].publicKey();
    }

    private List<SignaturePair> createSigPairs(int numSigPairs) {
        final var pairs = new ArrayList<SignaturePair>();
        for (int i = 0; i < numSigPairs; i++) {
            pairs.add(SignaturePair.newBuilder()
                    .ed25519(randomBytes(64))
                    .pubKeyPrefix(randomBytes(32))
                    .build());
        }
        return pairs;
    }
}
