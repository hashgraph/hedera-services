package com.hedera.services.bdd.suites.utils.contracts.precompile;

import java.util.List;

public record TokenInfo(
    HederaToken token,
    long totalSupply,
    boolean deleted,
    boolean defaultKycStatus,
    boolean pauseStatus,
    List<FixedFee> fixedFees,
    List<FractionalFee> fractionalFees,
    List<RoyaltyFee> royaltyFees,
    String ledgerId) {}