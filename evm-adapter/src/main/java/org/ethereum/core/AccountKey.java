package org.ethereum.core;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

public class AccountKey extends AccountState {

	 private byte[] rlpAcctKey;
	 private String etherAddress;
	 private long expireTime;
	 private long createTimeMs;

	 public AccountKey(byte[] rlpData) {
		  super(rlpData);
		  this.rlpAcctKey = rlpData;
		  RLPList items = (RLPList) RLP.decode2(rlpData).get(0);
		  this.etherAddress = items.get(4).getRLPData() == null ? "" : new String(items.get(4).getRLPData());
		  this.expireTime = items.get(5).getRLPData() == null ? 0 : Long.parseLong(new String(items.get(5).getRLPData()));
		  this.createTimeMs = items.get(6).getRLPData() == null ? 0 : Long.parseLong(new String(items.get(6).getRLPData()));
	 }

	 public AccountKey(String etherAddress, long createTimeSec, long expireTime) {
		  super(BigInteger.ZERO, BigInteger.ZERO);
		  this.etherAddress = etherAddress;
		  this.expireTime = expireTime;
		  this.createTimeMs = createTimeSec;
	 }

	 public byte[] getEncoded() {
		  if (rlpAcctKey == null) {
				byte[] nonce = RLP.encodeBigInteger(getNonce());
				byte[] balance = RLP.encodeBigInteger(getBalance());
				byte[] stateRoot = RLP.encodeElement(getStateRoot());
				byte[] codeHash = RLP.encodeElement(getCodeHash());
				byte[] etherAddr = RLP.encodeString(this.etherAddress);
				byte[] expiryTime = RLP.encodeString("" + this.expireTime);
				byte[] createTimeMs = RLP.encodeString("" + this.createTimeMs);
				this.rlpAcctKey = RLP.encodeList(nonce, balance, stateRoot, codeHash, etherAddr, expiryTime, createTimeMs);
		  }
		  return rlpAcctKey;
	 }

	 public String getEthereumKey() {
		  return etherAddress;
	 }

	 public long getExpireTime() {
		  return expireTime;
	 }

	 public long getCreateTimeMs() {
		  return createTimeMs;
	 }

}
