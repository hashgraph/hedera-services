/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.legacy.core.jproto;

import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;

/**
 * This is a special type of key, which _does not_ map to proto Key. It's used only internally.
 *
 * <p>This key represents an unknown/wildcard ECDSA key, whose actual key bytes are unknown,
 * but the _evm address_ that is derived from the key is known. When such a key is added to the required keys list,
 * we can try to find its corresponding ECDSA key in the sig map and replace it.
 *
 * <p>This can happen in the following 2 scenarios:
 * <ul>
 *     <li>When we have a transaction that needs a signature from a hollow account. The hollow account holds only
 *     an evm address, so the actual ECDSA key that needs to have signed is unknown to us.</li>
 *     <li>When we have a CryptoCreate with an evm address alias, which is derived from a key, different than the
 *     key we are setting for the new account. Proof-of-ownership dictates that the key, from which the evm address
 *     is derived, must sign the transaction in order for it to be valid. However, we only have access to the evm address</li>
 * </ul>
 *
 * The {@link JWildcardECDSAKey#isForHollowAccount} flag is needed for {@link com.hedera.node.app.service.mono.sigs.Rationalization}
 * and {@link com.hedera.node.app.service.mono.sigs.Expansion} to know whether to add a {@link com.hedera.node.app.service.mono.utils.PendingCompletion}
 * for a hollow account if a corresponding ECDSA key is found for this wildcard key. Therefore, when an instance of this class
 * is constructed for a hollow account key, this flag should be set to {@code true}. In all other cases, it should be {@code false}
 */
public class JWildcardECDSAKey extends JKey {
    private final boolean isForHollowAccount;
    private final byte[] evmAddress;

    public byte[] getEvmAddress() {
        return evmAddress;
    }

    public JWildcardECDSAKey(final byte[] evmAddress, boolean isForHollowAccount) {
        this.evmAddress = evmAddress;
        this.isForHollowAccount = isForHollowAccount;
    }

    public boolean isForHollowAccount() {
        return isForHollowAccount;
    }

    @Override
    public boolean isEmpty() {
        return ((null == evmAddress) || (0 == evmAddress.length));
    }

    @Override
    public boolean isValid() {
        return !(isEmpty() || (evmAddress.length != EVM_ADDRESS_SIZE));
    }

    @Override
    public boolean hasWildcardECDSAKey() {
        return true;
    }

    @Override
    public JWildcardECDSAKey getWildcardECDSAKey() {
        return this;
    }
}
