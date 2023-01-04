- (all examples using a signed state file from ~2022-11-10)

# `signedstate` command

The `signedstate` command lets the user access certain kinds 
of information that is in the "signed state file" - a snapshot
state dump of the Hedera hashgraph's accounts and file store and
smart contract key/value stores (current at the point in time the
snapshot was taken).

The current line of development is directed towards pulling out
contracts from the signed state and doing certain kinds of
analysis on them.<sup>†</sup>

## The signed state "file"

The "signed state file" is not actually a file, it's a directory structure
containing a merkle tree (in `SignedState.swh`) and peer files/directories
which are maps of the file store ("storage") and per-smart-contract key-value
store (these latter two things are "virtual maps").

On disk the signed state file is ~1.3GB (as of 2022-10).  When deserialized
the entire merkle tree (~725MB serialized) is materialized in memory, as 
well as the indexes to the two virtual maps.  The values of the virtual maps
are read from disk when accessed.

## Contracts

In the signed state, a contract's bytecode can be found from an account
id by looking in the file store using a (virtual blob) key which is a
pair of the account id and `Type.CONTRACT_BYTECODE`.  The account id
must be of a smart contract account.

## Contract-related subcommands of `signedstate`

The contract-related subcommands of the `signedstate` command are:

* `yahcli signedstate dumprawcontracts` - dumps the bytecodes for all contracts in the signed state
  - optionally eliminate duplicates
* (that's it for now)
    

## `dumprawcontracts` subcommand

### get contract bytecodes out of signed state file
- (signed state file is actually in a directory tree of all signed state information)

```bash
./yahcli signedstate dumprawcontracts --with-ids --prefix=">" \
   -f ~/Downloads/contract_state_hip_583_analysis/SignedState.swh | \
   grep -E '^>' | cut -f 2,3 > raw-contract-bytecodes.txt
```
#### output is one contract/line plus a bunch of cruft to ignore
 - contract lines are preceded by prefix (here `>`)
   - use `grep -E '^>'` to get _only_ contract lines
 - each contract line has tab-separated fields
   - first field is prefix (here '>') (if `--prefix` argument given)
     - above command uses `cut` to remove prefix from line
   - second field is hex-encoded contract
   - third field is contract id (decimal) (if `--with-ids`) argument given

- found 1932 contracts registered with accounts, 1876 with bytecode (3 with 0-length bytecode!)

Add `--unique` to get only _unique_ contracts (by bytecode), and now, if `--with-ids` argument
present, each contract line has an additional tab-separated field:
  - fourth field is comma-separated list of all contract ids with this same bytecode

```bash
./yahcli signedstate dumprawcontracts --unique --with-ids --prefix=">" \
   -f ~/Downloads/contract_state_hip_583_analysis/SignedState.swh | \
   grep -E '^>' | cut -f 2,3 > raw-unique-contract-bytecodes.txt
```

### find only solidity contracts

- consider that the solidity prelude is
  ```
  (0x60) PUSH1 0x??
  (0x60) PUSH1 0x??
  (0x52) MSTORE
  ```

So:

```bash
grep -E '^60..60..52' \
     < raw-unique-contract-bytecodes.txt \
     > raw-unique-solidity-only-contract-bytecodes.txt```

- 481 "solidity only" contracts left (with maybe some false positives)

### get a disassembly from a single contract

* Takes a contract's bytecode (in hex) on command line, with contract id; output to stdout

   ```bash
   ./yahcli signedstate decompilecontract \
            --with-code-offset --with-opcode --with-contract-bytecode \
            --id <contract-id> --bytecode 60806040⬅·····⮕0033
   ```
  
    - `--id <nnn>` - contract's id, will be put in the output for identification
    - `--bytecode <hex>` - the bytecode of the contract
      - (don't worry: MacOS can take command lines up to 1Mb long ...)
    - `--with-code-offset` - put the code offset on each line with data bytes (in hex)
    - `--with-opcode` - put the opcode on each line before the mnemonic (in hex)
    - `--with-contract-bytecode` - put the contract bytecode (in hex) in the disassembly
    
#### get the disassembly of all contracts in a state file

* Start with the following shell script which reads contracts (bytecode), one per line, from stdin
  and writes disassemblies in separate files named `<contractid>.dis`:
    ```bash
    while read -r contract idscsv; do
    if [ "$idscsv" != "" ]; then
        ids=($(echo $idscsv | tr "," "\n"))
        firstid=${ids[0]}
        echo working on $firstid "--" $idscsv
        > ${firstid}.dis ./yahcli signedstate decompilecontract \
                                  --with-code-offset --with-opcode --with-contract-bytecode \
                                  --id ${firstid} --bytecode ${contract}
    else
        echo missing id!
    fi
    done
    ```
  
  then:

  ```bash
  ./disassemble-all-contracts.sh \
      < raw-unique-solidity-only-contract-bytecodes.txt \
      2>&1 | tee disassemble-all-contracts.log
  ```
----

<sup>†</sup> But other kinds of information extraction from a signed state file
can be added as needed.