package com.hedera.services.bdd.suites.utils.contracts.precompile;

import java.util.Arrays;
import java.util.Objects;
import org.hyperledger.besu.datatypes.Address;

public record KeyValue(
    boolean inheritAccountKey,
    Address contractId,
    byte[] ed25519,
    byte[] ECDSA_secp256k1,
    Address delegatableContractId) {

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KeyValue keyValue = (KeyValue) o;
    return inheritAccountKey == keyValue.inheritAccountKey
        && Objects.equals(contractId, keyValue.contractId)
        && Arrays.equals(ed25519, keyValue.ed25519)
        && Arrays.equals(ECDSA_secp256k1, keyValue.ECDSA_secp256k1)
        && Objects.equals(delegatableContractId, keyValue.delegatableContractId);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(inheritAccountKey, contractId, delegatableContractId);
    result = 31 * result + Arrays.hashCode(ed25519);
    result = 31 * result + Arrays.hashCode(ECDSA_secp256k1);
    return result;
  }

  @Override
  public String toString() {
    return "KeyValue{"
        + "inheritAccountKey="
        + inheritAccountKey
        + ", contractId="
        + contractId
        + ", ed25519="
        + Arrays.toString(ed25519)
        + ", ECDSA_secp256k1="
        + Arrays.toString(ECDSA_secp256k1)
        + ", delegatableContractId="
        + delegatableContractId
        + '}';
  }
}