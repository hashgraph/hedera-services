package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import org.hyperledger.besu.datatypes.Address;

public class EvmKey {

  private Address contractId;

  private byte[] ed25519;

  private byte[] ECDSA_secp256k1;

  private Address delegatableContractId;

  public EvmKey(Address contractId, byte[] ed25519, byte[] ECDSA_secp256k1,
      Address delegatableContractId) {
    this.contractId = contractId;
    this.ed25519 = ed25519;
    this.ECDSA_secp256k1 = ECDSA_secp256k1;
    this.delegatableContractId = delegatableContractId;
  }

}
