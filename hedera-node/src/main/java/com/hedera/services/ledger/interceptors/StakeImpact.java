package com.hedera.services.ledger.interceptors;

import com.hedera.services.utils.EntityNum;

public record StakeImpact(EntityNum stakedAccount, long adjustment) {
}