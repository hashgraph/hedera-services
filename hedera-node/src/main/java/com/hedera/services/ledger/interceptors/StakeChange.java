package com.hedera.services.ledger.interceptors;

import com.hedera.services.utils.EntityNum;

public record StakeChange(EntityNum stakedAccount, long adjustment, boolean nodeStaked) {
}