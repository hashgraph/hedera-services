# How to quickly create saved state with pre-defined entity layout

This document tells how to create a Hedera services state file with required entity types and layout.

## Create saved state file local

### Modify the `entity-layout.properties` file
Modify the configuration file `entity-layout.properties` ([here](https://github.com/hashgraph/hedera-services/hedera-node/data/config/entity-layout.properties)) under `hedera-node/data/config/` as the values you needed. You may want to keep the sequence
as it is as some of the entity types depends on other entity types' existence. That is, don't change the `position.N` of the entity, unless you are sure 
what you need. 

For example :
```
position.0=accounts
accounts.total=100000

position.1=topics
topics.total=1000

position.2=tokens
tokens.total=1000

position.3=uniqueTokens
uniqueTokens.total=2000

position.4=files
files.total=1000

position.5=smartContracts
smartContracts.total=0
smartContracts.total.file=0

position.6=schedules
schedules.total=2000

position.7=nfts
nfts.total=250000

position.8=tokenAssociations
tokenAssociations.total=5000

cloud.bucketname=services-regression-jrs-files
cloud.dirForStateFile=auto-upload-test-dir
millseconds.waiting.server.down=60000

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
2021-08-27 13:07:30.881 INFO  245  BuiltinClient - Current seqNo: 2021178
2021-08-27 13:07:43.051 INFO  243  BuiltinClient - Successfully submitted TokenAssociateToAccount txn #50000, handled so far: 49402 
2021-08-27 13:07:43.052 INFO  245  BuiltinClient - Current seqNo: 2021178
2021-08-27 13:07:43.054 INFO  411  BuiltinClient - Successfully submitted 50000 for tokenAssociation , handled so far: 49403
2021-08-27 13:07:43.055 INFO  414  BuiltinClient - Done creating 50000 Token Associations
2021-08-27 13:07:43.055 INFO  109  BuiltinClient - tokenAssociations value range [2011001 - 2061000]
2021-08-27 13:07:43.055 INFO  112  BuiltinClient - Current seqNo: 2021178
2021-08-27 13:07:43.056 INFO  92   BuiltinClient - All entities created. Shutdown the client thread
2021-08-27 13:07:48.986 INFO  39   PostCreateTask - Wait for builtin client to finish...
2021-08-27 13:08:15.439 INFO  144  SignedStateBalancesExporter - Took 195250ms to summarize signed state balances
2021-08-27 13:08:18.991 INFO  39   PostCreateTask - Wait for builtin client to finish...
2021-08-27 13:08:21.173 INFO  161  SignedStateBalancesExporter -  -> Took 5725ms to export and sign proto balances file at 2021-08-27T18:05:00.025242Z
2021-08-27 13:08:43.086 INFO  99   BuiltinClient - Current seqNo: 2021178
2021-08-27 13:08:49.003 INFO  46   PostCreateTask - Done create the state file and shut down the server.
2021-08-27 13:08:50.328 INFO  58   PostCreateTask - Successfully submitted Freeze txn 
2021-08-27 13:08:50.328 INFO  64   PostCreateTask - Sent the freeze command to server and wait its final state file export to finish...
2021-08-27 13:08:53.056 INFO  104  FreezeHandler - Dual state freeze time set to 2021-08-27T18:08:59Z (now is 2021-08-27T18:08:50.269055Z)
2021-08-27 13:08:59.348 INFO  125  ServicesMain - Now current platform status = MAINTENANCE in HederaNode#0.
2021-08-27 13:08:59.359 INFO  226  RecordStreamManager - RecordStream inFreeze is set to be true 
2021-08-27 13:08:59.509 INFO  153  FreezeHandler - NETWORK_UPDATE Node 0 Update file id is not defined, no update will be conducted
2021-08-27 13:10:51.474 INFO  35   SavedStateHandler - Zip fle name base: 20214
2021-08-27 13:10:51.497 INFO  137  FileUtil - Root = /Users/leojiang/projects/R6/hedera-services/hedera-node/./data/saved/com.hedera.services.ServicesMain/0/123/20214, current = /Users/leojiang/projects/R6/hedera-services/hedera-node/./data/saved/com.hedera.services.ServicesMain/0/123/20214
2021-08-27 13:10:51.498 INFO  153  FileUtil - Current directory /Users/leojiang/projects/R6/hedera-services/hedera-node/./data/saved/com.hedera.services.ServicesMain/0/123/20214
2021-08-27 13:10:51.503 INFO  167  FileUtil - Adding file:/PostgresBackup.tar.gz
2021-08-27 13:10:51.679 INFO  167  FileUtil - Adding file:/SignedState.swh.tmp
2021-08-27 13:11:01.899 INFO  95   PostCreateTask - Done uploading state file /Users/leojiang/projects/R6/hedera-services/hedera-node/20214.gz
```

### Verify the created state file.
Now, without changing any of the environment, except the following:
#### Remove the property `create.state.file=true` in `node.properties`
#### (Optional) remove the files under hedera-node/outout. This is to make it easier to verify the created state file

After above steps, you can re-start `ServicesMain` from Intellij, and meanwhile, you can check the file `hedera-services/hedera-node/output/swirlds.log`,
and you should be able to find following lines:

```
2021-08-27 12:49:07.221 9        DEBUG STARTUP          <main> Browser: Starting platforms
2021-08-27 12:49:07.224 10       DEBUG STARTUP          <main> Browser: Scanning the classpath for RuntimeConstructable classes
2021-08-27 12:49:08.360 11       DEBUG STARTUP          <main> Browser: Done with registerConstructables, time taken 1135ms
2021-08-27 12:49:17.662 12       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 999780, treeSize = 999780 ]
2021-08-27 12:49:17.736 13       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 55998, treeSize = 55998 ]
2021-08-27 12:49:17.746 14       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 1000, treeSize = 1000 ]
2021-08-27 12:49:17.763 15       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 3207, treeSize = 3207 ]
2021-08-27 12:49:19.984 16       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 1001192, treeSize = 1001192 ]
2021-08-27 12:49:20.000 17       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 6000, treeSize = 6000 ]
2021-08-27 12:49:20.009 18       DEBUG RECONNECT        <main> FCMap: FCMap Initialized [ internalMapSize = 1000, treeSize = 1000 ]
2021-08-27 12:49:27.455 19       INFO  STARTUP          <main> SwirldsPlatform: Signed state loaded from disk has a valid hash.
2021-08-27 12:49:31.928 20       DEBUG SNAPSHOT_MANAGER <main> SnapshotManager: SnapshotManager: Successfully queued snapshot request [taskType='RESTORE', applicationName='com.hedera.services.ServicesMain', worldId='123', nodeId=0, roundNumber=9633, snapshotId=restore, timeStarted=2021-08-27T17:49:31.920242Z, timeCompleted=null, complete=false, error=false ]
```

The `FCMAP` lines shall tell you the number of entity types that created in ths saved state file.

### Further validation by running some EET suites to verify they are correct

### Limitation of local run

## Create saved state file from CircleCi

