- (all examples using a signed state file from 2023-03-16)

# `contract` command

The `contract` command lets the user investigate contracts in the
Hedera store.  The contracts themselves, and their current state
(memory slots) are in a Hedera node's "signed state file" - a snapshot
state dump of the Hedera hashgraph's accounts and file store and
smart contract key/value stores (current at the point in time the
snapshot was taken).

The current line of development is directed towards pulling out
contracts from the signed state and doing certain kinds of
analysis on them, including disassembly, and analyzing which
contracts are calling other contracts.<sup>†</sup>

## The signed state "file"

The "signed state file" is not actually a file, it's a directory structure
containing a merkle tree (in `SignedState.swh`) and peer files/directories
which are maps of the file store ("storage") and per-smart-contract key-value
store (these latter two things are "virtual maps").

On disk the signed state file is ~1.8GB (as of 2023-03).  When deserialized
the entire merkle tree is materialized in memory, as
well as the indexes to the two virtual maps.  The values of the virtual maps
are read from disk when accessed.

## Contracts

In the signed state, a contract's bytecode can be found from an account
id by looking in the file store using a (virtual blob) key which is a
pair of the account id and `Type.CONTRACT_BYTECODE`.  The account id
must be of a smart contract account.

## Subcommands of `contract`

The subcommands of the `contract` command are:

* `yahcli contract dumprawcontracts` - dumps the bytecodes for all contracts in the signed state
    - optionally eliminate duplicates
* `yahcli contract decompile` - disassembles a contract, given the runtime<sup>‡</sup> bytecode
    - optionally identifies common code sequences, such as the method selector branch tree ("vtable")
* `yahcli contract selector`
    - does selector → method signature lookups on the web
* (that's it for now)


## `dumprawcontracts` subcommand

### get contract bytecodes out of signed state file
- (signed state file is actually in a directory tree of all signed state information)

```bash
./yahcli contract dumprawcontracts --with-ids --prefix=">" \
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

- 2023-03-16: found 3396 contracts registered with accounts, 3340 with bytecode (3 with 0-length bytecode! - precompiles?)

Add `--unique` to get only _unique_ contracts (by bytecode), and now, if `--with-ids` argument
present, each contract line has an additional tab-separated field:
- fourth field is comma-separated list of all contract ids with this same bytecode

```bash
./yahcli contract dumprawcontracts --unique --with-ids --prefix=">" \
   -f ~/Downloads/contract_state_hip_583_analysis/SignedState.swh | \
   grep -E '^>' | cut -f 2,4 > raw-unique-contract-bytecodes.txt
```

- 2023-03-16: found 768 unique contracts

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
````
- 2023-03-16: 745 "solidity only" contracts left (with maybe some false positives)

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
  and writes disassembles in separate files named `<contractid>.dis`:
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

### Example of `decompile` with actual contract bytecode

```bash
./yahcli contract decompilecontract --with-opcode --with-code-offset \
   --with-contract-bytecode --recognize-sequences --with-selector-lookups \
   --flag=m --flag=s --id=1234567 \
   --bytecode=\
60806040523480156100115760006000fd5b50600436106100305760003560e01c806368cdafe614\
61003657610030565b60006000fd5b610050600480360381019061004b91906102b4565b61005256\
5b005b61008482600060009054906101000a900473ffffffffffffffffffffffffffffffffffffff\
ff1661013c63ffffffff16565b50600060009054906101000a900473ffffffffffffffffffffffff\
ffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1663a9059cbb83836040\
518363ffffffff1660e01b81526004016100e29291906103dd565b60206040518083038160008780\
3b1580156100fd5760006000fd5b505af1158015610112573d600060003e3d6000fd5b5050505060\
40513d601f19601f8201168201806040525081019061013691906102f3565b505b5050565b600060\
00606061016773ffffffffffffffffffffffffffffffffffffffff166349146bde60e01b86866040\
516024016101769291906103b3565b604051602081830303815290604052907bffffffffffffffff\
ffffffffffffffffffffffffffffffffffffffff19166020820180517bffffffffffffffffffffff\
ffffffffffffffffffffffffffffffffff83818316178352505050506040516101e0919061039b56\
5b6000604051808303816000865af19150503d806000811461021d576040519150601f19603f3d01\
1682016040523d82523d6000602084013e610222565b606091505b50915091508161023357601561\
0248565b80806020019051810190610247919061031e565b5b60030b9250825050505b9291505056\
61051a565b60008135905061026b816104ae565b5b92915050565b600081519050610281816104c9\
565b5b92915050565b600081519050610297816104e4565b5b92915050565b6000813590506102ad\
816104ff565b5b92915050565b60006000604083850312156102c95760006000fd5b60006102d785\
82860161025c565b92505060206102e88582860161029e565b9150505b9250929050565b60006020\
82840312156103065760006000fd5b600061031484828501610272565b9150505b92915050565b60\
00602082840312156103315760006000fd5b600061033f84828501610288565b9150505b92915050\
565b6103528161041f565b82525b5050565b600061036482610407565b61036e8185610413565b93\
5061037e818560208601610479565b8084019150505b92915050565b6103948161046e565b82525b\
5050565b60006103a78284610359565b91508190505b92915050565b60006040820190506103c860\
00830185610349565b6103d56020830184610349565b5b9392505050565b60006040820190506103\
f26000830185610349565b6103ff602083018461038b565b5b9392505050565b6000815190505b91\
9050565b60008190505b92915050565b600061042a8261044d565b90505b919050565b6000811515\
90505b919050565b60008160030b90505b919050565b600073ffffffffffffffffffffffffffffff\
ffffffffff821690505b919050565b60008190505b919050565b60005b8381101561049857808201\
51818401525b60208101905061047c565b838111156104a7576000848401525b505b505050565b61\
04b78161041f565b811415156104c55760006000fd5b5b50565b6104d281610432565b8114151561\
04e05760006000fd5b5b50565b6104ed8161043f565b811415156104fb5760006000fd5b5b50565b\
6105088161046e565b811415156105165760006000fd5b5b50565bfea26469706673582212203b08\
4709eeec6a26d18d9bc98eb3c1ba45fb66dd1a799d9e289c1482b1ef147f64736f6c634300060c00\
33
```


----

<sup>†</sup> But other kinds of information extraction from a signed state file
can be added as needed.

<sup>‡</sup> Not the "init bytecode" aka _initcode
