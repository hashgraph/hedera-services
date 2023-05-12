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

package com.swirlds.platform.dispatch.triggers.transaction;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.platform.dispatch.types.TriggerFour;

/**
 * Sends dispatches for pre-consensus state signatures.
 */
@FunctionalInterface
public interface PreConsensusStateSignatureTrigger extends TriggerFour<Long, Long, Hash, Signature> {

    /**
     * Signal that a pre-consensus state signature is ready to be handled.
     *
     * @param round
     * 		the round that was signed
     * @param signerId
     * 		the ID of the signer
     * @param hash
     * 		the hash that was signed
     * @param signature
     * 		the signature on the hash
     */
    @Override
    void dispatch(Long round, Long signerId, Hash hash, Signature signature);
}
