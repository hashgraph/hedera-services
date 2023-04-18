package com.hedera.node.app.service.mono.stream;

/**
 * POJO to read a JSON object from the unit test "recovery stream".
 */
public class RecoveryRSO {
    private String consensusTime;
    private String b64Record;
    private String b64Transaction;

    public String getConsensusTime() {
        return consensusTime;
    }

    public void setConsensusTime(String consensusTime) {
        this.consensusTime = consensusTime;
    }

    public String getB64Record() {
        return b64Record;
    }

    public void setB64Record(String b64Record) {
        this.b64Record = b64Record;
    }

    public String getB64Transaction() {
        return b64Transaction;
    }

    public void setB64Transaction(String b64Transaction) {
        this.b64Transaction = b64Transaction;
    }
}
