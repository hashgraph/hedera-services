package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.state.merkle.virtual.persistence.SlotStore;
import com.hedera.services.store.models.Id;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SlotStoreTest {
    private static final Path STORE_PATH = Path.of("store");
    private static final Random RANDOM = new Random(1234);
    private static final SlotStore.SlotStoreFactory SLOT_STORE_FACTORY = InMemorySlotStore::new;

    /**
     * test basic data storage and retrieval for a single version
     */
    @Test
    public void createSomeDataAndReadBack() throws IOException {
        final int COUNT = 1_000_000;
//        final int COUNT = 1;
        // create and open file
        SlotStore store = SLOT_STORE_FACTORY.open(Integer.BYTES+ContractKey.SERIALIZED_SIZE, 5, STORE_PATH,"test","dat");
        // create some data for a number of accounts
        for (long i = 0; i < COUNT; i++) {
            final ContractKey data = new ContractKey(new Id(1,2,i), new ContractUint256(BigInteger.valueOf(i)));
            store.writeSlot(i, outputStream -> outputStream.writeSelfSerializable(data, ContractUint256.SERIALIZED_SIZE));
        }
        // read back and check that data
        for (long i = 0; i < COUNT; i++) {
            ContractKey readUint = store.readSlot(i, inputStream -> inputStream.readSelfSerializable(ContractKey::new));
            final ContractKey expected = new ContractKey(new Id(1,2,i), new ContractUint256(BigInteger.valueOf(i)));
            assertEquals(expected, readUint);
        }

        // read back random and check that data
        for (int j = 0; j < COUNT; j++) {
            int i = RANDOM.nextInt(COUNT);
            ContractKey readUint = store.readSlot(i, inputStream -> inputStream.readSelfSerializable(ContractKey::new));
            final ContractKey expected = new ContractKey(new Id(1,2,i), new ContractUint256(BigInteger.valueOf(i)));
            assertEquals(expected, readUint);
        }

        // change all data to random values
        for (int j = 0; j < COUNT; j++) {
            long i = RANDOM.nextInt(COUNT);
            final ContractKey data = new ContractKey(new Id(1,2,j), new ContractUint256(BigInteger.valueOf(i+COUNT)));
            store.writeSlot(j, outputStream -> outputStream.writeSelfSerializable(data, ContractUint256.SERIALIZED_SIZE));
        }

        // read back and check that data
        for (long i = 0; i < COUNT; i++) {
            ContractKey readUint = store.readSlot(i, inputStream -> inputStream.readSelfSerializable(ContractKey::new));
            final ContractKey expected = new ContractKey(new Id(1,2,i), new ContractUint256(BigInteger.valueOf(i)));
            assertEquals(expected.getContractId(), readUint.getContractId());
        }

        // randomly do updates all over data
        for (int k = 0; k < 10_000_000; k++) {
            int i = RANDOM.nextInt();
            int j = RANDOM.nextInt(COUNT);
            final ContractKey data = new ContractKey(new Id(1,2,j), new ContractUint256(BigInteger.valueOf(i+COUNT)));
            store.writeSlot(j, outputStream -> outputStream.writeSelfSerializable(data, ContractUint256.SERIALIZED_SIZE));
        }

        // read back and check that data
        for (long i = 0; i < COUNT; i++) {
            ContractKey readUint = store.readSlot(i, inputStream -> inputStream.readSelfSerializable(ContractKey::new));
            final ContractKey expected = new ContractKey(new Id(1,2,i), new ContractUint256(BigInteger.valueOf(i)));
            assertEquals(expected.getContractId(), readUint.getContractId());
        }

        // close
        store.close();
    }


    @Test
    public void threadedReadTest() throws Exception {
        final int COUNT = 10_000;
        SlotStore store = SLOT_STORE_FACTORY.open(Integer.BYTES+ContractKey.SERIALIZED_SIZE, 5, STORE_PATH,"test","dat");
        // create some data for a number of accounts
        for (long i = 0; i < COUNT; i++) {
            final ContractKey data = new ContractKey(new Id(1,2,i), new ContractUint256(BigInteger.valueOf(i)));
            store.writeSlot(i, outputStream -> outputStream.writeSelfSerializable(data, ContractUint256.SERIALIZED_SIZE));
        }
        System.out.println("Created "+COUNT+" data items");
        // create random read a writer
        IntStream.range(0,COUNT).parallel().forEach(j -> {
            // READ
            int i = RANDOM.nextInt(COUNT);
            try {
                ContractKey readUint = store.readSlot(i, inputStream -> inputStream.readSelfSerializable(ContractKey::new));
                final ContractKey expected = new ContractKey(new Id(1,2,i), new ContractUint256(BigInteger.valueOf(i)));
                assertEquals(expected, readUint);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        System.out.println("Done reading");
        // random read or write
        IntStream.range(0,100_000_000).parallel().forEach(j -> {
            int i = RANDOM.nextInt(COUNT);
            if (RANDOM.nextBoolean()) {
                try {
                    ContractKey readKey = store.readSlot(i, inputStream -> inputStream.readSelfSerializable(ContractKey::new));
                    final ContractKey expected = new ContractKey(new Id(1,2,i), new ContractUint256(BigInteger.valueOf(i)));
                    final ContractKey expected2 = new ContractKey(new Id(1,2,i), new ContractUint256(BigInteger.valueOf(i+COUNT)));
                    assertTrue(expected.equals(readKey) || expected2.equals(readKey), "Failed i="+i+" readKey="+readKey);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    final ContractKey data = new ContractKey(new Id(1,2,i), new ContractUint256(BigInteger.valueOf(i+COUNT)));
                    store.writeSlot(i, outputStream -> outputStream.writeSelfSerializable(data, ContractUint256.SERIALIZED_SIZE));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println("Done random read/writing");
        // close
        store.close();
    }
}
