package com.hedera.services.bdd.suites.utils.contracts.precompile;

import java.util.List;
import org.hyperledger.besu.datatypes.Address;

public record HederaToken(
    String name,
    String symbol,
    Address treasury,
    String memo,
    boolean tokenSupplyType,
    long maxSupply,
    boolean freezeDefault,
    List<TokenKey> tokenKeys,
    Expiry expiry) {}