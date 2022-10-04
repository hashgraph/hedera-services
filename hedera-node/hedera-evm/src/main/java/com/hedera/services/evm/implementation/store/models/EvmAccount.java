package com.hedera.services.evm.implementation.store.models;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * Base class to encapsulates the state and operations of a Hedera account.
 *
 * <p><b>NOTE:</b> This implementation is incomplete, and includes only the API needed to support
 * the Hedera Token Service. The memo field, for example, is not yet present.
 */
public class EvmAccount {
  private final Id id;
  private ByteString alias = ByteString.EMPTY;

  public EvmAccount(Id id) {
    this.id = id;
  }

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
