# SavePointStack
A new `SavePointStack` is created once for user transaction and each time a child transaction is dispatched. 

The `SavePointStack` manages a stack of `AbstractSavePoint` objects.  It also has
  - the root `HederaState` used to create the stack
  - `RecordSink` that is only created for the root stack created with user transaction. It will be updated with all records created in that savepoint when the savepoint is committed
  - `BaseRecordBuilder` that is created for the transaction that created the savepoint

The `AbstractSavePoint` has 
- the `HederaState` used to create the savepoint which captures all the state changes
- the parent `RecordSink` that will be updated with all records created in that savepoint when the savepoint is committed

The initial save point in a stack is created when constructing the stack. The following save points are created
everytime a new message frame is created in EVM stack while handling user transaction.

There are three types of SavePoint implementations which have different implementation for committing records. 

`AbstractSavePoint` is the base class for all the savePoints. 
`AbstractFollowingSavePoint` is the base class for savePoints that are created after the initial savepoint.

1. `FirstSavePoint`- This is the initial save point in the first stack that is created for the user transaction. 
                     This is extended from `AbstractSavePoint`.
2. `BaseSavePoint`- It serves as the initial save point for any child stack, which is created by dispatching 
                    a preceding or child transaction. It is extended from `AbstractFollowingSavePoint`.
3. `FollowingSavePoint`- All the subsequent savePoints created after the initial savepoint in any stack. This is 
                         extended from `AbstractFollowingSavePoint`.
Each time `commitFullStack` is called, all the savePoints in the stack are committed.


Each Stack starts by creating the initial save point and the base record builder for the transaction that created the stack.
For the user transaction, the user stack starts by creating a `FirstSavePoint`and a base record builder for the user transaction.
For a child transaction, the stack starts by creating a `BaseSavePoint` and a base record builder for the child transaction.
There might be `FollowingSavePoint` created after the initial savepoint in the stack, if the transaction being handled 
is for a contract operation and if there are any new message frames created in the EVM stack. Each message frame creates
a new `FollowingSavePoint` in the stack.

### Adding records to SavePoint
When a record needs to be added to a savePoint, based on teh type of the record and type of SavePoint it is added to,
some checks are done.
- **AbstractFollowingSavePoint** 
   - **Preceding Record** - Checks if the total number of preceding records already added in all savePoiunts is less than
     allowed max preceding records. 
   - **Child Record** - Checks if the total number of child records added after userRecordBuilder added in all savePoints
     is less than allowed max child records. 

- **FirstSavePoint** 
   - **Preceding Record** Checks if the sum of the total number of preceding records already committed to the `RecordSink` in the `SavePointStack`
     (if commitFullStack was already called) and current preceding records in save point is less than allowed max preceding records
   - **Child Record** Checks if the sum of the total number of following records already committed to the `RecordSink` in the `SavePointStack`
     (if commitFullStack was already called) and current following records in save point is less than allowed max child records

If the conditions satisfy, the record is added to the records list of the savepoint. Otherwise, an exception is thrown.

### Commit
When commit is called by `HandleWorkflow` on the `SavePointStack`, all the state changes of that particular stack 
are committed to the parent stack. The record builders are also pushed to a `RecordSink` based on the type of savepoint. 
- If the savepoint is of type `FirstSavePoint` all the final record builders are stored in the `RecordSink` of user `SavePointStack`.
Since there is no parent stack for the user stack. All these record builders will then be streamed out to the record file in the 
same order.
- If the savepoint is of type `BaseSavePoint`, based on the type of transaction that is dispatched to create this save point
we add record builders differently to parent stack. If the transaction dispatched is a preceding transaction, all the record 
builders are added to the parent stack's preceding records list. Otherwise, all are added to the parent stack's following records.
- If the savepoint is of type `FollowingSavePoint`, all the record builders are added to the parent save point's following records.

### CommitFullStack
When `commitFullStack` is called by `HandleWorkflow` on the `SavePointStack`, commit is called on all the stacks.
All the state changes of all the stacks are committed to root state. 
All record builders are also pushed to a `RecordSink` of the root SavePointStack and a new FirstSavePoint is 
created for the user stack with a state that has all the previous changes committed and creates the User transaction
base record builder.

[savepoint-stack.md](savepoint-stack.md)

### Rollback

When rollback is called by `HandleWorkflow` on the `SavePointStack`, all the state changes of that particular stack
are rolled back. RecordBuilders will not be deleted from any save points. But based on the reversingBehavior of 
the recordBuilder, the records will be reversed in the recordSink.
- If the recordBuilder is `REVERSIBLE`, all the side effects are removed on the record. if it is a `SUCCESS` record, 
a `REVERTED_SUCCESS` status is set on the record
- If the recordBuilder is `REMOVABLE`, the record is removed from the list of recordBuilders.
After these changes the records are pushed to the parent stack's recordSink based on the type of savepoint similar 
to commit.

### RollbackFullStack

When `rollbackFullStack` is called by `HandleWorkflow` on the `SavePointStack`, rollback is called on
all the stacks.

**NEXT: [Signatures](signatures.md)**