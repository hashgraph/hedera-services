package com.hedera.services.store.contracts.repository;

import java.math.BigInteger;

import static com.hedera.services.store.contracts.repository.ByteUtil.toHexString;
import static com.hedera.services.store.contracts.repository.FastByteComparisons.equal;
import static com.hedera.services.store.contracts.repository.HashUtil.EMPTY_DATA_HASH;
import static com.hedera.services.store.contracts.repository.HashUtil.EMPTY_TRIE_HASH;

public class AccountState {
    private byte[] rlpEncoded;

    /* A value equal to the number of transactions sent
     * from this address, or, in the case of contract accounts,
     * the number of contract-creations made by this account */
    private BigInteger nonce;

    /* A scalar value equal to the number of Wei owned by this address */
    private BigInteger balance;

    /* A 256-bit hash of the root node of a trie structure
     * that encodes the storage contents of the contract,
     * itself a simple mapping between byte arrays of size 32.
     * The hash is formally denoted σ[a] s .
     *
     * Since I typically wish to refer not to the trie’s root hash
     * but to the underlying set of key/value pairs stored within,
     * I define a convenient equivalence TRIE (σ[a] s ) ≡ σ[a] s .
     * It shall be understood that σ[a] s is not a ‘physical’ member
     * of the account and does not contribute to its later serialisation */
    private byte[] stateRoot;

    /* The hash of the EVM code of this contract—this is the code
     * that gets executed should this address receive a message call;
     * it is immutable and thus, unlike all other fields, cannot be changed
     * after construction. All such code fragments are contained in
     * the state database under their corresponding hashes for later
     * retrieval */
    private byte[] codeHash;

    private long shardId;
    private long realmId;
    private long accountNum;
    private long proxyAccountNum;
    private long proxyAccountShard;
    private long proxyAccountRealm;
    private long autoRenewPeriod;
    private long createTimeMs = 0;
    private long expirationTime = 0;
    private long senderThreshold;
    private long receiverThreshold;
    private boolean deleted = false;
    private boolean smartContract = false;
    private boolean receiverSigRequired = false;

    private AccountState withServicesFields(AccountState acctState) {
        acctState.setAutoRenewPeriod(autoRenewPeriod);
        acctState.setCreateTimeMs(createTimeMs);
        acctState.setExpirationTime(expirationTime);
        acctState.setAccountNum(accountNum);
        acctState.setRealmId(realmId);
        acctState.setShardId(shardId);
        acctState.setProxyAccountNum(proxyAccountNum);
        acctState.setProxyAccountRealm(proxyAccountRealm);
        acctState.setProxyAccountShard(proxyAccountShard);
        acctState.setReceiverSigRequired(receiverSigRequired);
        acctState.setReceiverThreshold(receiverThreshold);
        acctState.setSenderThreshold(senderThreshold);
        acctState.setDeleted(deleted);
        return acctState;
    }

    public AccountState() {
        this(BigInteger.ZERO, BigInteger.ZERO);
    }

    public AccountState(BigInteger nonce, BigInteger balance) {
        this(nonce, balance, EMPTY_TRIE_HASH, EMPTY_DATA_HASH);
    }

    public AccountState(BigInteger nonce, BigInteger balance, byte[] stateRoot, byte[] codeHash) {
        this.balance = balance;
        this.stateRoot = stateRoot == EMPTY_TRIE_HASH || equal(stateRoot, EMPTY_TRIE_HASH) ? EMPTY_TRIE_HASH : stateRoot;
        this.codeHash = codeHash == EMPTY_DATA_HASH || equal(codeHash, EMPTY_DATA_HASH) ? EMPTY_DATA_HASH : codeHash;
    }

    public AccountState(byte[] rlpData) {
        this.rlpEncoded = rlpData;

        RLPList items = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.nonce = ByteUtil.bytesToBigInteger(items.get(0).getRLPData());
        this.balance = ByteUtil.bytesToBigInteger(items.get(1).getRLPData());
        this.stateRoot = items.get(2).getRLPData();
        this.codeHash = items.get(3).getRLPData();
    }

    public BigInteger getNonce() {
        return nonce;
    }

    public AccountState withNonce(BigInteger nonce) {
        return withServicesFields(new AccountState(nonce, balance, stateRoot, codeHash));
    }

    public byte[] getStateRoot() {
        return stateRoot;
    }

    public AccountState withStateRoot(byte[] stateRoot) {
        return withServicesFields(new AccountState(nonce, balance, stateRoot, codeHash));
    }

    public AccountState withIncrementedNonce() {
        return withServicesFields(new AccountState(nonce.add(BigInteger.ONE), balance, stateRoot, codeHash));
    }

    public byte[] getCodeHash() {
        return codeHash;
    }

    public AccountState withCodeHash(byte[] codeHash) {
        return withServicesFields(new AccountState(nonce, balance, stateRoot, codeHash));
    }

    public BigInteger getBalance() {
        return balance;
    }

    public void setBalance(BigInteger balance) {
        this.balance = balance;
    }

    public AccountState withBalanceIncrement(BigInteger value) {
        return withServicesFields(new AccountState(nonce, balance.add(value), stateRoot, codeHash));
    }

    public byte[] getEncoded() {
        if (rlpEncoded == null) {
            byte[] nonce = RLP.encodeBigInteger(this.nonce);
            byte[] balance = RLP.encodeBigInteger(this.balance);
            byte[] stateRoot = RLP.encodeElement(this.stateRoot);
            byte[] codeHash = RLP.encodeElement(this.codeHash);
            this.rlpEncoded = RLP.encodeList(nonce, balance, stateRoot, codeHash);
        }
        return rlpEncoded;
    }

//    public boolean isContractExist(BlockchainConfig blockchainConfig) {
//        return !equal(codeHash, EMPTY_DATA_HASH) ||
//                !blockchainConfig.getConstants().getInitialNonce().equals(nonce);
//    }

    public boolean isEmpty() {
        return equal(codeHash, EMPTY_DATA_HASH) &&
                BigInteger.ZERO.equals(balance) &&
                BigInteger.ZERO.equals(nonce);
    }

    public String toString() {
        String ret = "  Nonce: " + this.getNonce().toString() + "\n" +
                "  Balance: " + getBalance() + "\n" +
                "  State Root: " + toHexString(this.getStateRoot()) + "\n" +
                "  Code Hash: " + toHexString(this.getCodeHash());
        return ret;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }

    public long getReceiverThreshold() {
        return receiverThreshold;
    }

    public void setReceiverThreshold(long receiverThreshold) {
        this.receiverThreshold = receiverThreshold;
    }

    public long getSenderThreshold() {
        return senderThreshold;
    }

    public void setSenderThreshold(long senderThreshold) {
        this.senderThreshold = senderThreshold;
    }

    public boolean isReceiverSigRequired() {
        return receiverSigRequired;
    }

    public void setReceiverSigRequired(boolean receiverSigRequired) {
        this.receiverSigRequired = receiverSigRequired;
    }

    public long getAccountNum() {
        return accountNum;
    }

    public void setAccountNum(long bit64) {
        this.accountNum = bit64;
    }

    public long getRealmId() {
        return realmId;
    }

    public void setRealmId(long realmId) {
        this.realmId = realmId;
    }

    public long getShardId() {
        return shardId;
    }

    public void setShardId(long shardId) {
        this.shardId = shardId;
    }

    public long getProxyAccountNum() {
        return proxyAccountNum;
    }

    public void setProxyAccountNum(long proxyAccountNum) {
        this.proxyAccountNum = proxyAccountNum;
    }

    public long getProxyAccountShard() {
        return proxyAccountShard;
    }

    public void setProxyAccountShard(long proxyAccountShard) {
        this.proxyAccountShard = proxyAccountShard;
    }

    public long getProxyAccountRealm() {
        return proxyAccountRealm;
    }

    public void setProxyAccountRealm(long proxyAccountRealm) {
        this.proxyAccountRealm = proxyAccountRealm;
    }

    public long getAutoRenewPeriod() {
        return autoRenewPeriod;
    }

    public void setAutoRenewPeriod(long autoRenewPeriod) {
        this.autoRenewPeriod = autoRenewPeriod;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isSmartContract() {
        return smartContract;
    }

    public void setSmartContract(boolean smartContract) {
        this.smartContract = smartContract;
    }

    public long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }
}

