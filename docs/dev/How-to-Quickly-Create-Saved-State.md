# How to quickly create saved state with pre-defined entity layout

This document tells how to create a Hedera services state file with required entity types and layout.

## Create saved state file local

### Modify the `entity-layout.properties` file
Modify the configuration file `entity-layout.properties` ([here](https://github.com/hashgraph/hedera-services/hedera-node/data/config/entity-layout.properties)) under `hedera-node/data/config/` as the values you needed. You may want to keep the sequence
as it is as some of the entity types depends on other entity types' existence. That is, don't change the `position.N` of the entity, unless you are sure 
what you need. 

For example :
```
# this file shall not be deployed

position.0=accounts
accounts.total=30000

position.1=topics
topics.total=100

position.2=tokens
tokens.total=100

position.3=uniqueTokens
uniqueTokens.total=1000

position.4=files
files.total=100

position.5=smartContracts
smartContracts.total=100
smartContracts.total.file=10

position.6=schedules
schedules.total=200

position.7=nfts
nfts.total=25000

position.8=tokenAssociations
tokenAssociations.total=5000

cloud.bucketname=services-regression-jrs-files
cloud.dirForStateFile=auto-upload-test-dir
millseconds.waiting.server.down=120000

```

### Modify `node.properties`
Under `hedera-node/data/config`, create or modify the `node.properties` file to have the following property:

```
create.state.file=true
```

NOTE: for normal run, remember to remove this line, or change it to `create.state.file=false`, or remove this line completely.

### remove remaining saved state if it exists
Also if you have run the serices before, you need to clean up artifacts like this:

```
cd .../hedera-services/hedera-node/data
rm -rf saved recordstreams accountBalances
```

After that, you can start your `ServiceMain` and watch the saved state to be created

### Run ServicesMain to create the saved state file
You can observe the log from the `Run` pane of Intellij or from `hgcaa.log`.

``` 
...
2021-09-08 18:39:00.385 INFO  124  BuiltinClient - Current seqNo: 32611
2021-09-08 18:39:00.385 INFO  102  BuiltinClient - All entities created. Shutdown the client thread
2021-09-08 18:39:10.210 INFO  42   PostCreateTask - Wait for builtin client to finish...
2021-09-08 18:39:40.211 INFO  42   PostCreateTask - Wait for builtin client to finish...
Storage Path: /Users/leojiang/projects/R6/hedera-services/hedera-node/data/saved/com.hedera.services.ServicesMain/0/123/2075
2021-09-08 18:40:00.288 INFO  152  SignedStateBalancesExporter - Took 133ms to summarize signed state balances
2021-09-08 18:40:00.326 INFO  174  SignedStateBalancesExporter -  -> Took 38ms to export and sign proto balances file at 2021-09-08T23:40:00.037823Z
2021-09-08 18:40:00.391 INFO  111  BuiltinClient - Current seqNo: 32611
2021-09-08 18:40:10.216 INFO  49   PostCreateTask - Done create the state file and shut down the server.
2021-09-08 18:40:10.281 INFO  60   PostCreateTask - Successfully submitted Freeze txn.
2021-09-08 18:40:10.281 INFO  66   PostCreateTask - Sent the freeze command to server and wait its final state file export to finish...
Storage Path: /Users/leojiang/projects/R6/hedera-services/hedera-node/data/saved/com.hedera.services.ServicesMain/0/123/2457
2021-09-08 18:40:20.209 INFO  74   ServicesMain - Now current platform status = MAINTENANCE in HederaNode#0.
2021-09-08 18:40:20.210 INFO  227  RecordStreamManager - RecordStream inFreeze is set to be true 
2021-09-08 18:42:10.289 INFO  25   ServiceGCPUploadHelper - User.home: /Users/leojiang
2021-09-08 18:42:10.290 INFO  28   ServiceGCPUploadHelper - Path to credential file: /Users/leojiang/.ssh/gcp-credit.json
2021-09-08 18:42:10.369 INFO  38   SavedStateHandler - Zip fle name base: 2457
2021-09-08 18:42:10.373 INFO  147  FileUtil - Adding file:/SignedState.swh
2021-09-08 18:42:10.964 INFO  147  FileUtil - Adding file:/PostgresBackup.tar.gz
2021-09-08 18:42:10.967 INFO  147  FileUtil - Adding file:/settingsUsed.txt
2021-09-08 18:42:14.675 INFO  96   ServiceGCPUploadHelper - Done uploading state file /Users/leojiang/projects/R6/hedera-services/hedera-node/2457.gz
```

### Verify the created state file.
Now, without changing any of the environment, except the following:
#### Remove the property `create.state.file=true` in `node.properties`
#### (Optional) remove the files under hedera-node/outout. This is to make it easier to verify the created state file

After above steps, you can re-start `ServicesMain` from Intellij, and meanwhile, you can check the file `hedera-services/hedera-node/output/swirlds.log`,
and you should be able to find following lines:

```
2021-09-08 18:43:48.805 10       DEBUG STARTUP          <main> Browser: Scanning the classpath for RuntimeConstructable classes
2021-09-08 18:43:50.125 11       DEBUG STARTUP          <main> Browser: Done with registerConstructables, time taken 1319ms
2021-09-08 18:43:50.953 12       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 30000, treeSize = 30000 ]
2021-09-08 18:43:50.965 13       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 6095, treeSize = 6095 ]
2021-09-08 18:43:50.966 14       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 100, treeSize = 100 ]
2021-09-08 18:43:50.969 15       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 335, treeSize = 335 ]
2021-09-08 18:43:51.097 16       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 30301, treeSize = 30301 ]
2021-09-08 18:43:51.100 17       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 1100, treeSize = 1100 ]
2021-09-08 18:43:51.101 18       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 200, treeSize = 200 ]
2021-09-08 18:43:51.838 19       INFO  STARTUP          <main> SwirldsPlatform: Signed state loaded from disk has a valid hash.
2021-09-08 18:43:52.613 20       DEBUG SNAPSHOT_MANAGER <main> SnapshotManager: SnapshotManager: Successfully queued snapshot request [taskType='RESTORE', applicationName='com.hedera.services.ServicesMain', worldId='123', nodeId=0, roundNumber=2457, snapshotId=restore, timeStarted=2021-09-08T23:43:52.603475Z, timeCompleted=null, complete=false, error=false ]

```

The `FCMAP` lines shall tell you the number of entity types that created in ths saved state file.

### Further validation by running some EET suites to verify they are correct

### Limitation of local run

## Create saved state file from CircleCi

