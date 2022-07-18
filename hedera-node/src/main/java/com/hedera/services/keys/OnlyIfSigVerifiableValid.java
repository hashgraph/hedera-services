/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.keys;

import static com.swirlds.common.crypto.VerificationStatus.INVALID;
import static com.swirlds.common.crypto.VerificationStatus.VALID;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiPredicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OnlyIfSigVerifiableValid implements BiPredicate<JKey, TransactionSignature> {
    private static final Logger log = LogManager.getLogger(OnlyIfSigVerifiableValid.class);

    private final SyncVerifier syncVerifier;

    public OnlyIfSigVerifiableValid(SyncVerifier syncVerifier) {
        this.syncVerifier = syncVerifier;
    }

    @Override
    public boolean test(final JKey ignoredKey, final TransactionSignature sig) {
        var statusUnknown = true;
        try {
            sig.waitForFuture().get();
            statusUnknown = false;
        } catch (final InterruptedException ignore) {
            log.warn(
                    "Interrupted while validating signature, this will be fatal outside reconnect");
            Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
            log.error("Erred while validating signature, this is likely fatal", e);
        }
        if (statusUnknown) {
            return false;
        }
        var status = sig.getSignatureStatus();
        if (status == VALID) {
            return true;
        } else if (status == INVALID) {
            return false;
        } else {
            // This case happens when we expanded a signature during preHandle() using a key no
            // longer
            // linked to the transaction (e.g. the key of an account that did a key rotation earlier
            // in
            // this round)---we have no choice but to verify the signature synchronously
            try {
                syncVerifier.verifySync(List.of(sig));
                // Only try one sync verification
                return sig.getSignatureStatus() == VALID;
            } catch (CryptographyException ignore) {
                return false;
            }
        }
    }
}
