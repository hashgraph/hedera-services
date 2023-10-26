/*
 * Copyright (C) 2017-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.crypto.Signature;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import java.security.PublicKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class Crypto {

    private final KeysAndCerts keysAndCerts;
    /** a pool of threads used to verify signatures and generate keys, in parallel */
    private final ExecutorService cryptoThreadPool;

    /**
     * @param keysAndCerts     keys and certificates
     * @param cryptoThreadPool the thread pool that will be used for all operations that can be done in parallel, like
     *                         signing and verifying
     */
    public Crypto(final KeysAndCerts keysAndCerts, final ExecutorService cryptoThreadPool) {
        this.keysAndCerts = keysAndCerts;
        this.cryptoThreadPool = cryptoThreadPool;
    }

    /**
     * Digitally sign the data with the private key. Return null if anything goes wrong (e.g., bad private key).
     * <p>
     * The returned signature will be at most SIG_SIZE_BYTES bytes, which is 104 for the CNSA suite parameters.
     *
     * @param data the data to sign
     * @return the signature (or null if any errors)
     */
    public Signature sign(final byte[] data) {
        return new PlatformSigner(keysAndCerts).sign(data);
    }

    /**
     * Verify the given signature for the given data. This is submitted to the thread pool so that it will be done in
     * parallel with other signature verifications and key generation operations. This method returns a Future
     * immediately. If the signature is valid, then a get() method on that Future will eventually return a Boolean which
     * is true if the signature was valid. After the thread does the validation, and before it returns, it will run
     * doLast(true) if the signature was valid, or doLast(false) if it wasn't.
     * <p>
     * This is flexible. It is OK to ignore the returned Future, and only have doLast handle the result. It is also OK
     * to pass in (Boolean b) for doLast, and handle the result of doing a .get() on the Future. Or both mechanisms can
     * be used.
     *
     * @param data      the data that was signed
     * @param signature the claimed signature of that data
     * @param publicKey the claimed public key used to generate that signature
     * @param doLast    a function that will be run after verification, and will be passed true if the signature is
     *                  valid. To do nothing, pass in (Boolean b)
     * @return validObject if the signature is valid, else returns null
     */
    public Future<Boolean> verifySignatureParallel(
            final byte[] data, final byte[] signature, final PublicKey publicKey, final Consumer<Boolean> doLast) {
        return cryptoThreadPool.submit(() -> {
            boolean result = CryptoStatic.verifySignature(data, signature, publicKey);
            doLast.accept(result);
            return result;
        });
    }

    public KeysAndCerts getKeysAndCerts() {
        return keysAndCerts;
    }
}
