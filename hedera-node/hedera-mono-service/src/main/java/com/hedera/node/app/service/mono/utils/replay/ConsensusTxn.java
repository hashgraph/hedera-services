package com.hedera.node.app.service.mono.utils.replay;

public class ConsensusTxn {
    private long memberId;
    private String b64Transaction;

    public long getMemberId() {
        return memberId;
    }

    public void setMemberId(long memberId) {
        this.memberId = memberId;
    }

    public String getB64Transaction() {
        return b64Transaction;
    }

    public void setB64Transaction(String b64Transaction) {
        this.b64Transaction = b64Transaction;
    }
}
