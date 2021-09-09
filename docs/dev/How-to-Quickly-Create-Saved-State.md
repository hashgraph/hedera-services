# How to quickly create saved state with pre-defined entity layout

This document tells how to create Hedera services saved state with required entity types and layout.

## Create state files locally

### Prerequisites
If you want to upload generated state files to GCP cloud storage, you need to have access permissions
to the GCP cloud. You can contact Nathan and Ron to get help with the permissions.

Once you get the access permissions, you need to install the `google-cloud-sdk` to be able to use `gsutil` and other tools, APIs. 

Now let's get into the steps to create state files with expected layout.

### 1. Create or modify file `entity-layout.properties` 
Create or modify the configuration file `entity-layout.properties` ([here](https://github.com/hashgraph/hedera-services/hedera-node/data/config/entity-layout.properties)) 
under `hedera-node/data/config/` as the values you needed. 

For example :
```
# this file shall not be deployed

position.0=accounts
accounts.total=30000

position.1=topics
topics.total=1000

position.2=tokens
tokens.total=1000

position.3=uniqueTokens
uniqueTokens.total=1000

position.4=files
files.total=1000

position.5=smartContracts
smartContracts.total=1000
smartContracts.total.file=100

position.6=schedules
schedules.total=2000

position.7=nfts
nfts.total=25000

position.8=tokenAssociations
tokenAssociations.total=5000

cloud.bucketname=services-regression-jrs-files
cloud.dirForStateFile=auto-upload-test-dir
millseconds.waiting.server.down=120000
gsutil.command=gsutil
```

NOTES on how to change the above properties:

#### 1. How to understand and configure the entity total and their positions
Basically, keep in mind that first 1000 entity ids are reserved for system accounts. So for the saved state file using
above sample `entity-layout.properties` file, we can assume that entities with id range from `0.0.1001` and
`0.0.31000` will be Crypto accounts, and id range from `0.0.31001` to `0.0.32000` will be topics, so on and so forth.

Normally you may want to keep the entity ordering as it is, as some of the entity types depend on other 
entity types' existence. That is, don't change the `position.N` of the entity. 
If you really need to change the relative position of these entities, you need to be clear their dependency relationship.

Many a time, you don't need all entity types in your state file, in this case, you can simply remove the entries
for those entity types you don't want or change their `.total=0`. 

#### 2. For GCP cloud storage bucket and directory entities
You can change `cloud.bucketname` and `cloud.dirForStateFile` to anywhere you have access.

#### 3. gsutil.command specifies the location of the `gsutil` command
If you know the location of `gsutil` command, you can use the absolute path to replace the `gsutil`, for example,
```
gsutil.command=/Users/username/gcp/google-cloud-sdk/bin/gsutil
```
This way, it will reduce the security risk that Java ProcessBuilder has to search the path to find the command.
Otherwise, you can leave this property value as it is.

#### 4. Configure `millseconds.waiting.server.down` property
This property value basically tells the application how long it should wait after the `freeze` request was sent to
the server, before it starts to collect (zip and transfer) the created saved state files. 

This value basically tries to wait for the server node to finish its saving of last state file. For large state file, 
sometimes it can 20, 30 minutes or even longer to finish. By `large`, we mean you want to generate 3 million of accounts, 
~30 millions of NFTs and other reasonable amount of entities. A single `.swh` file can reach 5~6G in size.

This value shall normally be 1 or 2 minutes for small state file, however it can be tricky if you are
trying to create large state file. The good news is that you don't have to be bothered too much to put a good value
for this property for now. (Sometimes, it's hard to have last round's `.swh` file saving operation completed as the server 
node becomes too slow.If this happens, the good news is that you can always use previous round's saved state files as a 
replacement.)

### 2. Create or modify `node.properties`
Under `hedera-node/data/config`, create or modify the `node.properties` file to have the following property:

```
create.state.file=true
```

NOTE: for normal run, remember to remove this line, or change it to `create.state.file=false`, or remove this line completely.

### 3. Modify `config.txt`
If you are running local, to generate a saved state as large as possible, you better to run one node services network 
for this process.

You can achieve this by simply modify and allow one node in the `config.txt` as below:

```
...
# ** END REMOVE FROM SDK RELEASES **

 address,  A, Alice,    1, 127.0.0.1, 50204, 127.0.0.1, 50204, 0.0.3
# address,  B, Bob,      1, 127.0.0.1, 50205, 127.0.0.1, 50205, 0.0.4

# address,  C, Carol,    1, 127.0.0.1, 50206, 127.0.0.1, 50206, 0.0.5
# address,  D, Dave,     1, 127.0.0.1, 50207, 127.0.0.1, 50207, 0.0.6
...
```
(By default, the 3 three nodes of Alice, Bob, and Carol will be started)

### 4. remove previous run's saved state if it exists
Also if you have run `ServicesMain` before, you need to clean up artifacts like this:

```
cd .../hedera-services/hedera-node/data
rm -rf saved recordstreams accountBalances
```

By cleanup previous artifacts, we can make sure the id ranges are close enough to the `entity-layout.properties` file described.

After that, you can start your `ServicesMain` and watch the saved state to be created

### 5. Run ServicesMain to create the saved state file
You can observe the log from the `Run` pane of Intellij or from `hgcaa.log`. You need to wait till you see the following 
messages before you kill the running `ServicesMain` safely.

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

### 6. Verify the created state file.
Now, without changing any of the environment, except the following:
#### 1. Remove or commend out the property `create.state.file=true` in `node.properties`
#### 2. (Optional) remove the files under hedera-node/output/*. This is to make it easier to verify the created state file

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

The `FCMAP` lines shall tell you the number of entity types that created in ths saved state. 


### 7. Further validation by running some EET suites to verify they are correct

You can run `test-clietn`s `src/main/java/com/hedera/services/bdd/suites/regression/SavedStateCheck.java` 
([here](https://github.com/hashgraph/hedera-services/test-clients/src/main/java/com/hedera/services/bdd/suites/regression/SavedStateCheck.java)) 
to verify and check the boundary of saved state's entity types. (You need to modify those Id values based on your layout config).

## Create saved state file on GCP instances from JRS workflow
Simply put, the size of state file generated depends on the RAM of you server node size. So if you need to generate very
large saved state, you probably have to do that through JRS workflow (either run local JRS workflow or through CircleCi workflow).

However, right now JRS framework is in the process of a major refactoring. Before this process is completed, we have to take  
some manual actions to create large state for the moment.

### (Only for the transition period)Change `RegressionMain.java` to not kill GCP instances after the workflow
The purpose for doing so is to give you enough time to manually download the created saved state. If the saved state file
is large, sometimes it can take some time to finish the zipping and download.

You always want to zip the saved state files before downloading.

Before JRS provide the parameter to keep the GCP instances after finish the workflow, you need to do it manually. One way to do so 
is to comment out the lines in `RegressionMain#RunCloudExperiment` method:

```
...
	private void RunCloudExperiment() {
		final CloudService cloud;
		try {
			cloud = setUpCloudService();
		} catch (CloudStartupException e) {
			reportErrorToSlack(e, null);
			return;
		}
		//TODO Unit test for system.exit after cloud service set up
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
//				log.error(ERROR, "Shutdown hook invoked. Destroying cloud instances");
//				cloud.destroyInstances();
//				log.info(MARKER, "cloud instances destroyed");
			}
		}));

		try {
			runExperiments(cloud);
		} finally {
			// always destroy instances if anything goes wrong
//			cloud.destroyInstances();
		}
	}
...
```
and following the steps below to run the workflow to create large saved state.

NOTE: right now, the following steps are still based on legacy JRS framework before refactoring. They will be updated once the
new JRS framework is stable.

### Create saved state by running JRS workflow from local
If you are running local, you only need to re-built your `regression` repo and then follow the steps below.

#### 1. Change the `hedera-services` repo's `entity-layout.properties` and `node.properties` as described above
#### 2. Run the workflow with the command:

```
./regression_services.sh configs/services/suites/ci/GCP-Create-SavedState-With-Layout.json <target-hedera-services-repo>
```
The `<target-hedera-services-repo>` should be the one with your configuration changes.

#### 3. Monitor the above workflow 
You can monitor the regression log output to check the process of the workflow.

You may need to navigate to the ([GCP cloud Compute Engine](https://console.cloud.google.com/compute/instanceGroups/list?authuser=3&orgonly=true&project=hedera-regression&supportedpurview=organizationId))
, identify the server instance and `ssh` to it. 

As the workflow is kicked off by `services_nightly_regression`, You may need to get into  `/home/services_nightly_regression` once 
you log on to the instance.

#### 4. Zip and download the saved state
You can now check the status of the latest state file like this:

```
gcp-commit-comp-basic-4n-1c-2021-09-08-185758-0-3fr7:/home/services_nightly_regression/remoteExperiment/data/saved/com.hedera.services.ServicesMain/3/123$ ls -l 
total 12
drwxrwxr-x 2 services_nightly_regression services_nightly_regression 4096 Sep  8 19:10 14357
drwxrwxr-x 2 services_nightly_regression services_nightly_regression 4096 Sep  8 19:15 18271
drwxrwxr-x 2 services_nightly_regression services_nightly_regression 4096 Sep  8 19:20 22042
```

make sure the (newest) round is completely written before zip it and download:

```
gcp-commit-comp-basic-4n-1c-2021-09-08-185758-0-3fr7:/home/services_nightly_regression/remoteExperiment/data/saved/com.hedera.services.ServicesMain/3/123/22042$ ls -lrt
total 2184
-rw-rw-r-- 1 services_nightly_regression services_nightly_regression 2039355 Sep  8 19:20 SignedState.swh
-rw-rw-r-- 1 services_nightly_regression services_nightly_regression    6223 Sep  8 19:20 settingsUsed.txt
-rw-rw-r-- 1 services_nightly_regression services_nightly_regression  187625 Sep  8 19:20 PostgresBackup.tar.gz
```
If you don't see the 3 files as listed above, or the `.swh` is still ending with `.tmp`, that means this round's state creation 
is still in process. You have to wait or pick another (previous) round's state files.
#### 5. Kill the instance group after you are done
Do NOT forget to kill the instance groups (server and client) created by this workflow.


### Create saved state by running JRS workflow from CircleCi

If you decide to run the workflow from CircleCi, you will do the following:

#### 1. Change the `hedera-services` repo's `entity-layout.properties` and `node.properties` as described above
#### 2. Create a branch off of master and modify the config.yml 
In the `config.yml`, you need to have the 

```
  # This workflow is for generating state file with large volume of NFTs
  # It's not intended to run on a daily basis.
  GCP-Create-SavedState-With-Layout:
    triggers:
      - schedule:
          cron: "46 18 * * *"   <=== change the cron schedule
          filters:
            branches:
              only:
                - <your-branch-for-the-workflow>  <=== change the branch name to the one you plan to run
    jobs:
      - build-platform-and-services
      - jrs-regression:
          context: Slack
          regression_path: /swirlds-platform/regression
          result_path: results/1N-1C/SavedStateWithLayout
          config_type: "ci"
          workflow-name: "GCP-Create-SavedState-With-Layout"
          slack_results_channel: "hedera-regression-test"
          slack_summary_channel: "hedera-regression-test"
          requires:
            - build-platform-and-services
          pre-steps:
            - install-tools
            - attach_workspace:
                at: /
```

commit and push the above changes to github, you should checkout ([CircleCi Dashboard](https://app.circleci.com/pipelines/github/hashgraph/hedera-services))
to make sure your workflow is scheduled and/or started.

After this, the following steps are the same as running JRS workflow from local.

#### 3. Monitor the workflow 
#### 4. Zip and download generated state files
#### 5. Kill the GCP instance groups 


## Some generated state file

Some generated sample states are ([here](https://console.cloud.google.com/storage/browser/services-regression-jrs-files/StartFromSavedState/SavedStateWithLayouts?authuser=3&orgonly=true&project=hedera-regression&supportedpurview=organizationId&pageState=(%22StorageObjectListTable%22:(%22f%22:%22%255B%255D%22))&prefix=&forceOnObjectsSortingFiltering=false))  
