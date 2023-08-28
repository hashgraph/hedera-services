(Better documentation will come later, but here are some sample command lines:)

## Summarize contents of a signed state file

```
./platform-sdk/swirlds-cli/pcli.sh \
   --memory 40 \
    --ignore-jars \
    --load hedera-node/data/lib \
    --load hedera-node/cli-clients/build/libs \
    --cli com.hedera.services.cli.signedstate \
    signed-state -f /Users/davidbakin/StatesStreams/mainnet-2023-07-19/state/2023-07-19.00.00/144535932/SignedState.swh \
      --verbose --log-level WARN \
      summarize
```

## Dump the bytecodes and complete contract stores of a signed state file

```
./platform-sdk/swirlds-cli/pcli.sh \
    --memory 40 \
    --ignore-jars \
    --load hedera-node/data/lib \
    --load hedera-node/cli-clients/build/libs \
    --cli com.hedera.services.cli.signedstate \
    signed-state  -f /Users/davidbakin/StatesStreams/mainnet-2023-07-19/state/2023-07-19.00.00/144535932/SignedState.swh \
        --verbose \
    dump \
        contract-bytecodes --contract-bytecode ./bytecode.lst --summary --unique --with-ids \
        contract-stores    --contract-store    ./store.lst    --summary --slots
```
