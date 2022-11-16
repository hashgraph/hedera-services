- (all examples using a signed state file from ~2022-11-10)

## get contract bytecodes out of signed state file
- (signed state file is actually in a directory tree of all signed state information)

```bash
./yahcli signedstate dumprawcontracts --with-ids --prefix=">" \
   -f ~/Downloads/contract_state_hip_583_analysis/SignedState.swh | \
   grep -E '^>' | cut -f 2,3 > raw-contract-bytecodes.txt
```
### output is one contract/line plus a bunch of cruft to ignore
 - contract lines are preceded by prefix (here `>`)
   - use `grep -E '^>'` to get _only_ contract lines
 - each contract line has tab-separated fields
   - first field is prefix (here '>')
     - above command uses `cut` to remove prefix from line
   - second field is hex-encoded contract
   - third field is contract id (decimal)

- found 1879 raw contracts
  - but remember that many contracts appear multiple times in state

## find only solidity contracts

- consider that the solidity prelude is
  ```
  (0x60) PUSH1 0x??
  (0x60) PUSH1 0x??
  (0x52) MSTORE
  ```

So:

```bash
grep -E '^60..60..52' < raw-contract-bytecodes.txt > raw-solidity-only-contract-bytecodes.txt
```

- 1853 contracts left

## disassemble all contracts

- to disassemble _one_ contract, find its bytecode (hex-encoded) and contract id, then:
  
  ```bash
  ./yahcli signedstate decompilecontract [--prefix '> '] [--with-code-offset] [--with-opcode] \
    [--id=<contractid>] [--bytecode=<hex-encoded-bytecode]
- ```
  
  - `--with-code-offset` - include, on each code line, the offset (it's "address") as first field
  - `--with-opcode` - include, on each code line, the opcode (hex) before its mnemonic
  
- so, to disassemble _all_ contracts from the file of all contracts:

    ```bash
    < raw-solidity-only-contract-bytecodes.txt awk -f get-uniq-contracts.awk | \
      ./disassemble-all-contracts.sh 2>&1 | tee dissassemble-all-contracts.log
    ```

    This will put one contract per file, named for its contract id.  When you've got them all,
    move `*.dis` to some convenient directory.

## analyze!

- use `grep`, `awk`, `python`, `excel`, whatever to do further analysis
