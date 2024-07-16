package com.hedera.node.app.workflows.handle;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.pbj.runtime.io.buffer.Bytes;

import java.util.ArrayList;
import java.util.List;

public class TransactionsBuilder {
    private final List<BlockStreamTransaction> transactions = new ArrayList<>();
    private SingleBlockStreamTransactionBuilder
            singleBlockStreamTransactionBuilder = new HiddenSingleBlockStreamTransactionBuilder();

    public SingleBlockStreamTransactionBuilder newTransaction() {
        transactions.add(singleBlockStreamTransactionBuilder.build());
        singleBlockStreamTransactionBuilder = new HiddenSingleBlockStreamTransactionBuilder();
        return singleBlockStreamTransactionBuilder;
    }

    private static class HiddenSingleBlockStreamTransactionBuilder implements
            SingleBlockStreamTransactionBuilder {
        private Bytes submittedBytes;
        private TransactionResult.Builder result = new TransactionResult.Builder();
        private TransactionOutput.Builder output = new TransactionOutput.Builder();
        private StateChanges.Builder stateChanges = new StateChanges.Builder();

        public HiddenSingleBlockStreamTransactionBuilder submittedBytes(Bytes submittedBytes) {
            this.submittedBytes = submittedBytes;
            return this;
        }

        public TransactionResult.Builder result() {
            return result;
        }

        public TransactionOutput.Builder output() {
            return output;
        }

        public StateChanges.Builder stateChanges() {
            return stateChanges;
        }

        public BlockStreamTransaction build() {
            return new BlockStreamTransaction(submittedBytes, result.build(), output.build(),
                    stateChanges.build());
        }
    }

    public List<BlockStreamTransaction> build() {
        //todo
        return null;
    }
}
