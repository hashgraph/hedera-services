package com.hedera.evm.model;

import com.google.protobuf.ByteString;
import com.hedera.services.ethereum.EthTxSigs;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class Account {
  private static final int EVM_ADDRESS_SIZE = 20;
  private static final int ECDSA_SECP256K1_ALIAS_SIZE = 35;
  private static final ByteString ECDSA_KEY_ALIAS_PREFIX = ByteString.copyFrom(new byte[] { 0x3a, 0x21 });

  Id id;
  ByteString alias;

  public Address canonicalAddress() {
    if (alias.isEmpty()) {
      return id.asEvmAddress();
    } else {
      if (alias.size() == EVM_ADDRESS_SIZE) {
        return Address.wrap(Bytes.wrap(alias.toByteArray()));
      } else if (alias.size() == ECDSA_SECP256K1_ALIAS_SIZE
          && alias.startsWith(ECDSA_KEY_ALIAS_PREFIX)) {
        var addressBytes =
            EthTxSigs.recoverAddressFromPubKey(alias.substring(2).toByteArray());
        return addressBytes == null
            ? id.asEvmAddress()
            : Address.wrap(Bytes.wrap(addressBytes));
      } else {
        return id.asEvmAddress();
      }
    }
  }

  public Id getId() {
    return id;
  }

}
