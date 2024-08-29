package com.hedera.node.app.blocks.impl;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.node.app.blocks.impl.BlockNodeGrpcStub;
import com.hedera.node.app.blocks.impl.GrpcBlockItemWriter;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

public class GrpcBlockItemWriterTest {
    private BlockNodeGrpcStub stub;

    private Bytes blockItemBytes;

    public GrpcBlockItemWriterTest() {
        // Create a CryptoTransferTransactionBody
        CryptoTransferTransactionBody cryptoTransfer = CryptoTransferTransactionBody.DEFAULT;

        // Serialize the CryptoTransferTransactionBody to a byte array
        byte[] cryptoTransferBytes = CryptoTransferTransactionBody.PROTOBUF.toBytes(cryptoTransfer).toByteArray();

        // Wrap the byte array in a Bytes object
        Bytes serializedItem = Bytes.wrap(cryptoTransferBytes);

        // Create a BlockItem with the serialized CryptoTransferTransactionBody
        BlockItem blockItem = BlockItem.newBuilder()
                .eventTransaction(EventTransaction.newBuilder()
                        .applicationTransaction(serializedItem)
                        .build())
                .build();

        // Write the BlockItem
        blockItemBytes = BlockItem.PROTOBUF.toBytes(blockItem);
    }

    @BeforeEach
    public void setup() throws IOException {
        stub = new BlockNodeGrpcStub();
        stub.startServer(9090); // Start the server
    }

    @AfterEach
    public void tearDown() {
        stub.stopServer(); // Stop the server
    }

    @Test
    public void testDifferentScenarios() {
        testWriteItemLatency(40000, 5000); // 50k/s, 5 seconds delay threshold
    }

    public void testWriteItemLatency(int requestsPerSecond, long delayThreshold) {
        GrpcBlockItemWriter writer = new GrpcBlockItemWriter();
        writer.openBlock(1);

        double delayBetweenRequests = 1000.0 / requestsPerSecond; // in milliseconds
        double accumulatedDelay = 0.0;
        AtomicInteger requestCounter = new AtomicInteger(0);

        // Record the start time
        long startTime = System.currentTimeMillis();

        // Create a new thread for printing the number of writeItem calls every second
        Thread printThread = new Thread(() -> {
            int previousCount = 0;
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    int currentCount = requestCounter.get();
                    System.out.println("writeItem calls/sec: " + (currentCount - previousCount) + " latency: " + stub.getLatency() + "ms");
                    previousCount = currentCount;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        // Start the print thread
        printThread.start();

        while (true) {
            // Write the BlockItem
            writer.writeItem(blockItemBytes);
            requestCounter.incrementAndGet();

            accumulatedDelay += delayBetweenRequests;
            if (accumulatedDelay >= 1.0) {
                try {
                    TimeUnit.MILLISECONDS.sleep((long) accumulatedDelay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                accumulatedDelay -= (long) accumulatedDelay; // subtract the whole part
            }

            // Check the latency in BlockNodeGrpcStub
            long latency = stub.getLatency();
            if (latency > delayThreshold) {
                // Calculate the elapsed time
                long elapsedTime = System.currentTimeMillis() - startTime;
                System.out.println("Time taken to exceed " + delayThreshold + " milliseconds latency: " + elapsedTime + " milliseconds");
                break;
            }
        }

        writer.closeBlock();
    }
}