# Design of the update feature

## Goals
-	Allow software on mainnet to be updated automatically, based on signed transactions
-	Allow updates of everything: jar files, settings, etc.
-	Efficient: only use bandwidth on those files that need to change. Don't send unchanged files over the internet.
-	Make sure the update can be rolled back if any issue happens

## Design
-	Add an optional field to the freeze transaction that is called "updateFile" of type "FileId".
-	FileID MUST be restricted to 0.0.150
-	ONLY account 0.0.58 can send the Dynamic Update (Freeze transaction)
-	If that field is absent, then it is an ordinary freeze: all nodes simply quiesce at the requested time
-	If the field is present, then after quiescing, each node will do the following:
     * For clarity, we assume here that the sdk directory is at location "./sdk"
     * Make a copy of the sdk directory
     * If "./temp" exists, delete (recursive) everything in it, otherwise create it
     * Copy the given Hedera file to the local hard drive as "./temp/sdk.zip"
     * Unzip "./temp/sdk.zip"
     * If the unzip created a directory "./temp/sdk", then copy each file such as "./temp/sdk/a/b/c.txt" to the corresponding location "./sdk/a/b/c.txt"
     * If the unzip created a file "./temp/delete.txt", then look at each row of it, and if a row is "a/b/c.txt", then delete the file "./sdk/a/b/c.txt".  WARNING: we must first ensure that this file does not contain the string "..", because we want to sandbox our deletions to entirely be inside of "./sdk/"
     * Delete (recursive) the "./temp/sdk" directory and the "./temp/delete.txt" file (if they exist)
     * If the unzip create a file "./temp/exec.sh" then do an operating system call to run that as a script
-	From this point on, continue doing everything that a normal freeze would do.  In a typical update, we would expect "./temp/exec.sh" to actually kill this Java process and start a new one, so we will never actually return from the OS call to run  the script. All of the platform code should be written so that this won't cause any major problems. It likely is already written to support that, since we currently kill the Java process during a freeze.

We would expect a typical exec.sh file to simply kill the Java process and start a new one. But it could optionally do other operations.  

## Deployment Process
### Current Deployment Process
Current DevOp team deploys new service in the following steps
1.	Network is put on a freeze mode
2.	Java process is shutdown
3.	A new hedera service directory is created, and new jar files, configurations are coped to here
4.	Previous saved signed states are copy to new directory
5.	A symbolic link is created to point to the new hedera service directory
6.	Launch hedera service again
7.	To rollback to old release, simply change the symbolic link, point back to the previous release directory

### Deployment Approach with New Update Feature
* Create a zip file includes two parts
    - The zip file contains the update script, and
    - A sdk directory includes all the jar files, configuration files that need to be updated
    - These files can be extracted by doing a diff between the current the release and new release
* Created the zip file on hedera network through a file update transaction to update pre-exisiting file 0.0.150
* Network is put on a freeze mode with the update file ID 0.0.150
* HGCApp reads file ID 0.0.150 and extracts to ./temp directory
* HGCApp delete files according to the content of "./temp/delete.txt"
* HGCApp launches the update script as an independent process
* Java process is killed by the update script
* The update script creates new hedera service directory HapiApp2.0-Date-HHMM
* The update script copies everything from previous hedera service directory to the new directory
* The update script copies newly extracted file from sdk directory to the new hedera service directory
* The update script creates new symbolic link to the new hedera service directory
* The update script relaunches hedera service 


## Versions State & Reconnect

### Merkle Nodes
With the introduction of the Merkle interface a class representing every merkle node (internal, leaf, platform entity, application entity) must contain a current version number.
- Each merkle entity (class) serializes itself with the version defined in the class.
- Each merkle entity (class) also contains a minimum supported version which defines the version of the state it can deserialize. Useful when starting from a saved state or reconnecting.

### Table Definitions
- Network Code is the version of the software running on all the nodes in the network.
- Network State is the version of the state read by the node of the network at startup.
- Node Code is the version of the software running on a node.
- Node State is the version of the state read by the node at startup.
Code version X represents a version and X+1 the next released version of software.
State version N is the version of state operated on, and written by version X of the code and N+1 the state created by X+1, the next released version of software . 



|   | Network Code  |  Network State | Node Code  | Node State  | Behavior  |
|---|---|---|---|---|---|
| 1  | X | N | X | N| **Restart**: All nodes must start and participate in the network <br> **Reconnect**: All nodes must start, reconnect and and participate in the network|
| 2  | X+1 | N <br><br> After restart N+1 | X  | N  | **Restart**: Network will migrate state from N to N+1 <br> **Reconnect**: The node running at version X will start up with node state N and attempt to reconnect. Reconnect MUST / SHOULD fail since the version number check will fail. Hence it is important to have incremented the version in the various classes. <br> **Result**: Network will end up running version X+1 & N+1, but the node will not reconnect. Node will be required to deploy software version X+1 and attempt to reconnect.|
| 3  | X+1 | N <br><br> After restart N+1 | X+1  | N  |  This is the typical update scenario. <br> **Restart**: Nodes will migration state N at startup to version N+1. <br> **Reconnect**: A running network ALWAYS has matching version of code and state since an older version of the state will be migrated at startup. A reconnecting node will migrate from state N to N+1 when it starts and loads the older state and will successfully reconnect with the network. <br> **Result**: Network will end up running with version X+1 & N+1 and the node will reconnect and participate in the network.|
| 4  | X  | N  | X+1  | N  |  **Restart**: Network will continue to run at X with state N <br> **Reconnect**: The node will attempt to reconnect, and will likely succeed. HOWEVER when the node participates in the network its state will result in a different hash and will result in an ISS. <br> **Result**: Network will run at version X & N, the node will reconnect but the node suffer an ISS.|
