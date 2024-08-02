# Compiling this contract is complicated, as Solidity and YUL don't currently
# (as of 0.8.18) support PUSH0, and when they do they won't support push via
# assembly, so we have to verbarim include the opcode.  Verbatim is not
# supported directly in solidity, so we have to dance
# solidity->text replacement->YUL->bin.

solc OpcodesContract.sol --ir > OpcodesContract.yul

sed -E 's/IR://g' OpcodesContract.yul > OpcodesContract.tmp
sed 's/add(msize(), 0x5f)/add(verbatim_0i_1o(hex"5f"), 0x5f)/g' OpcodesContract.tmp > OpcodesContract.yul

solc --strict-assembly OpcodesContract.yul --bin > OpcodesContract.binish
solc OpcodesContract.sol --combined-json abi | jq '.contracts."OpcodesContract.sol:NewOpcodes".abi' > OpcodesContract.json

cat OpcodesContract.binish | tr "
" " "  | sed 's/======= OpcodesContract.yul (EVM) =======  Binary representation: //g' | sed 's/ //g' > OpcodesContract.bin

rm OpcodesContract.tmp
rm OpcodesContract.yul
rm OpcodesContract.binish
