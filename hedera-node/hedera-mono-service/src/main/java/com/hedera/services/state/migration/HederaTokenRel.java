/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.migration;

import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.FastCopyable;

public interface HederaTokenRel extends FastCopyable {
    long getBalance();

    void setBalance(long balance);

    boolean isFrozen();

    void setFrozen(boolean frozen);

    boolean isKycGranted();

    void setKycGranted(boolean kycGranted);

    boolean isAutomaticAssociation();

    void setAutomaticAssociation(boolean automaticAssociation);

    long getRelatedTokenNum();

    EntityNumPair getKey();

    void setKey(EntityNumPair numbers);

    long getPrev();

    long getNext();

    void setPrev(final long prev);

    void setNext(final long next);
}
