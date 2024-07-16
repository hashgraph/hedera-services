package com.hedera.node.app.workflows.handle;

import com.hedera.pbj.runtime.io.buffer.Bytes;

// block stream equivalent of BlockRecordManager, which handles the ongoing BlockInfo object
public class BlockstreamBlockFacilitator {
    //todo load the following from state
    private long previousBlockNumber = 0;
    private Bytes previousBlockProofHash = Bytes.wrap(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes());

    public long previousBlockNumber() {
        return previousBlockNumber;
    }

    public long incrementBlockNumber() {
        previousBlockNumber++;
        return previousBlockNumber;
    }

    public Bytes previousBlockProofHash() {
        return previousBlockProofHash;
    }

    public void newBlockProofHash(Bytes newBlockProofHash) {
        this.previousBlockProofHash = newBlockProofHash;
    }
}
