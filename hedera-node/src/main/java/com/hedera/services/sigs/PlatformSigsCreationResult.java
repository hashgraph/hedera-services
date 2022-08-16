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
package com.hedera.services.sigs;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Encapsulates a (mutable) result of an attempt to create {@link Signature} instances from a list
 * of public keys and a source of the cryptographic signatures associated to those keys.
 */
public class PlatformSigsCreationResult {
    private final List<TransactionSignature> platformSigs = new ArrayList<>();
    private Optional<Exception> terminatingEx = Optional.empty();

    public List<TransactionSignature> getPlatformSigs() {
        return platformSigs;
    }

    public boolean hasFailed() {
        return terminatingEx.isPresent();
    }

    public void setTerminatingEx(Exception terminatingEx) {
        this.terminatingEx = Optional.of(terminatingEx);
    }

    public Exception getTerminatingEx() {
        return terminatingEx.get();
    }

    /**
     * Represent this result as a {@link ResponseCodeEnum}.
     *
     * @return the appropriate response code
     */
    public ResponseCodeEnum asCode() {
        if (!hasFailed()) {
            return OK;
        } else if (terminatingEx.isPresent()
                && terminatingEx.get() instanceof KeyPrefixMismatchException) {
            return ResponseCodeEnum.KEY_PREFIX_MISMATCH;
        } else {
            return ResponseCodeEnum.INVALID_SIGNATURE;
        }
    }
}
