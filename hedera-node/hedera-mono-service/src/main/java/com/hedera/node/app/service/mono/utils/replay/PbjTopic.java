package com.hedera.node.app.service.mono.utils.replay;

public class PbjTopic {
    private long number;

    private String b64Topic;

    public long getNumber() {
        return number;
    }

    public void setNumber(long number) {
        this.number = number;
    }

    public String getB64Topic() {
        return b64Topic;
    }

    public void setB64Topic(String b64Topic) {
        this.b64Topic = b64Topic;
    }
}
