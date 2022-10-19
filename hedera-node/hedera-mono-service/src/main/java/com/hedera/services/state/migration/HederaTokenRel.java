package com.hedera.services.state.migration;

import com.hedera.services.utils.EntityNumPair;

public interface HederaTokenRel {
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
