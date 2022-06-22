package com.hedera.services.store.contracts;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Map;

public interface HederaEvmWorldUpdater extends WorldUpdater {

    /**
     * Tracks how much Gas should be refunded to the sender account for the TX. SBH price is refunded for the first
     * allocation of new contract storage in order to prevent double charging the client.
     *
     * @return the amount of Gas to refund;
     */
    long getSbhRefund();

    Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> getFinalStateChanges();
}
