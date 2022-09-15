package com.hedera.evm.execution;

import javax.annotation.Nullable;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.MutableAccount;

public record ChargingResult(
    MutableAccount sender, @Nullable MutableAccount relayer, Wei allowanceCharged) {}
