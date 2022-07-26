package com.hedera.services.state.validation;

public interface ContractStorageLimits {
  void refreshStorageSlots();
  void assertUsableTotalSlots(long n);
  void assertUsableContractSlots(long n);
}
