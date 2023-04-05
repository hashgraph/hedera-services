package com.hedera.node.app.service.mono.utils.replay;

import java.time.Instant;

public class ConsensusTxn {
    private long memberId;
    private String b64Transaction;
    private String consensusTimestamp;

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

    public String getConsensusTimestamp() {
        return consensusTimestamp;
    }

    public void setConsensusTimestamp(String consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
    }
}
