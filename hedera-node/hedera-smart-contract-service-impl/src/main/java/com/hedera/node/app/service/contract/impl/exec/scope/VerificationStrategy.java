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

package com.hedera.node.app.service.contract.impl.exec.scope;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Predicate;

/**
 * A strategy interface to allow a dispatcher to optionally set the verification status of a
 * "simple" {@link Key.KeyOneOfType#CONTRACT_ID},
 * {@link Key.KeyOneOfType#DELEGATABLE_CONTRACT_ID}, or
 * even cryptographic key.
 *
 * <p>The strategy has the option to delegate back to the cryptographic verifications
 * already computed by the app in pre-handle and/or handle workflows by returning
 * {@link Decision#DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION}.
 *
 * <p>Because it possible for the {@code tokenTransfer()} system contract to need to amend
 * its dispatched transaction based on the results of signature verifications, the strategy
 * also has the option to return an amended transaction body when this occurs.
 */
public interface VerificationStrategy {
    enum Decision {
        VALID,
        INVALID,
        DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION
    }

    /**
     * Returns a decision on whether to verify the signature of a transaction, given a key and
     * the role that its "parent" key structure plays in the transaction.
     *
     * @param key the key to verify
     * @return a decision on whether to verify the signature, or delegate back to the crypto engine results
     */
    Decision decideFor(@NonNull Key key);

    /**
     * Returns a predicate that tests whether a given key is a valid signature for a given key
     * given this strategy within the given {@link HandleContext}.
     *
     * @param context the context in which this strategy will be used
     * @return a predicate that tests whether a given key is a valid signature for a given key
     */
    default Predicate<Key> asSignatureTestIn(@NonNull final HandleContext context) {
        return key -> switch (decideFor(key)) {
            case VALID -> true;
            case INVALID -> false;
            case DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION -> context.verificationFor(key)
                    .passed();
        };
    }
}
