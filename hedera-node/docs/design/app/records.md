# Records

The `RecordStreamManager` is not implemented in this POC, but has a simple method that it exposes:
`submit(TransactionRecord)`. Internally it keeps track of last-consensus-time and can see in the transaction record what
the consensus time is, and therefore knows deterministically when to produce a record file. The entire process of
batching them together and sending records is handled by the `RecordStreamManager` (implementation TBD).

The `RecordBuilderImpl` implements all known `RecordBuilder` subtypes. The implementation you see there is a stub,
but you can easily imagine the implementation.

**NEXT: [States](states.md)**
