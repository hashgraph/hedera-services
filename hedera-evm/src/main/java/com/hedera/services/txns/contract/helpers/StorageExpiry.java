package com.hedera.services.txns.contract.helpers;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.evm.frame.MessageFrame;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

/**
 * Helper to efficiently get the expiration time for new storage being allocated in a {@link MessageFrame}.
 *
 * <p>Note the allocating contract is given by {@link MessageFrame#getRecipientAddress()}. Now there are
 * two cases: either the contract allocating the storage <i>already existed</i> at the start of the EVM
 * transaction; or we have created the allocating contract in the ongoing EVM transaction.
 *
 * <ol>
 *     <li>In the first case, the storage expiry is just the same as the (well-known) expiry of
 *     the allocating contract---which must be available in the accounts {@code MerkleMap}.</li>
 *     <li>In the second case, the expiry of the newly-created, allocating contract is determined by its
 *     sponsor contract.
 * </ol>
 * Note we say "sponsor <i>chain</i>"---and not simply "sponsor"---because the sponsor of the allocating
 * contract might have itself been created in the ongoing EVM transaction! So we have to walk up the
 * {@code MessageFrame} stack until we find a {@code recipient} contract that already existed; this will
 * be the source of expiries for the whole sponsor chain.
 *
 * <p><b>IMPORTANT:</b> if our stack walk never finds a {@code recipient} that already exists, the
 * of the sponsor chain must begin with a contract that is <i>itself</i> being created this EVM
 * transaction. In this case, the HAPI {@code cryptoCreate} should be the ultimate source of the
 * storage expiry. So we need to option to configure the oracle with a {@code fallbackExpiryFromHapi}.
 */
@Singleton
public class StorageExpiry {
    private static final Logger log = LogManager.getLogger(StorageExpiry.class);

    private static final long UNAVAILABLE_EXPIRY = 0;

    private final Oracle callOracle = new Oracle(UNAVAILABLE_EXPIRY);
    private final UnusableStaticOracle unusableStaticOracle = new UnusableStaticOracle();

    private final AliasManager aliasManager;
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> contracts;

    @Inject
    public StorageExpiry(
            final AliasManager aliasManager,
            final Supplier<MerkleMap<EntityNum, MerkleAccount>> contracts
    ) {
        this.aliasManager = aliasManager;
        this.contracts = contracts;
    }

    public Oracle hapiCreationOracle(final long hapiExpiry) {
        return new Oracle(hapiExpiry);
    }

    public Oracle hapiCallOracle() {
        return callOracle;
    }

    public Oracle hapiStaticCallOracle() {
        return unusableStaticOracle;
    }

    public class Oracle {
        private final long fallbackExpiry;

        private Oracle(long fallbackExpiryFromHapi) {
            this.fallbackExpiry = fallbackExpiryFromHapi;
        }

        /**
         * Returns the effective expiry for storage being allocated in the current frame.
         *
         * @param frame the active message frame
         * @return the effective expiry for allocated storage
         */
        public long storageExpiryIn(final MessageFrame frame) {
            final var curContracts = contracts.get();
            var expiry = effExpiryGiven(frame, curContracts);

            if (expiry == UNAVAILABLE_EXPIRY) {
                // The first frame in the deque is the top of the stack
                final var iter = frame.getMessageFrameStack().iterator();
                while (iter.hasNext() && expiry == UNAVAILABLE_EXPIRY ) {
                    final var nextFrame = iter.next();
                    expiry = effExpiryGiven(nextFrame, curContracts);
                }
            }

            final var answer = (expiry == UNAVAILABLE_EXPIRY) ? fallbackExpiry : expiry;
            if (answer == UNAVAILABLE_EXPIRY) {
                log.warn("Using 0 as expiry for storage allocated by contract {}", frame::getRecipientAddress);
            }
            return answer;
        }

        private long effExpiryGiven(final MessageFrame frame, final MerkleMap<EntityNum, MerkleAccount> curContracts) {
            final var recipientAddress = frame.getRecipientAddress().toArrayUnsafe();
            final var recipientNum = aliasManager.isMirror(recipientAddress)
                    ? EntityNum.fromMirror(recipientAddress)
                    : aliasManager.lookupIdBy(ByteString.copyFrom(recipientAddress));
            if (curContracts.containsKey(recipientNum)) {
                final var recipient = curContracts.get(recipientNum);
                return recipient.getExpiry();
            } else {
                return UNAVAILABLE_EXPIRY;
            }
        }
    }

    private class UnusableStaticOracle extends Oracle {
        public UnusableStaticOracle() {
            super(UNAVAILABLE_EXPIRY);
        }

        @Override
        public long storageExpiryIn(final MessageFrame frame) {
            return UNAVAILABLE_EXPIRY;
        }
    }
}
