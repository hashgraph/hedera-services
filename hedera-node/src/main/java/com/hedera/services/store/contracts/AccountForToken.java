package com.hedera.services.store.contracts;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.account.Account;

public interface AccountForToken extends Account {

	void setCode(Bytes code);
}