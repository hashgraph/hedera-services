# this script presumes solc (8.13 or higher) is in the path.

mkdir tmpOutput

compileContract() {
  contractName=$1
  shift
  solc -o tmpOutput --abi --bin --overwrite --pretty-json ${contractName}/${contractName}.sol --base-path ${contractName} --include-path ../solidity/hip-206 $@
  cp tmpOutput/${contractName}*.bin ${contractName}/${contractName}.bin
  cp tmpOutput/${contractName}*.abi ${contractName}/${contractName}.json
}

# should be kept up to date with HederaTokenService

compileContract AssociateDissociate

compileContract AssociateTryCatch  --include-path ./CalledContract

compileContract AutoCreationModes

compileContract BadRelayClient --include-path ../solidity --include-path FeeHelper

compileContract BurnToken

compileContract CalledContract

compileContract ConsTimeRepro

compileContract Create2Factory

compileContract Create2PrecompileUser

compileContract Create2User

compileContract CryptoTransfer

compileContract DelegateContract  --include-path ServiceContract

compileContract DeleteTokenContract

compileContract DirectPrecompileCallee

compileContract ERC20DelegateCallee --include-path ../solidity

compileContract ERC721Contract --include-path ../solidity '@openzeppelin/contracts/token/ERC721/extensions/=./' '@openzeppelin/contracts/token/ERC721/=./'

compileContract ERC721ContractWithHTSCalls --include-path ../solidity

compileContract ErcAndHtsAlternatives --include-path ../solidity

compileContract FeeDistributor

compileContract FeeHelper  --import-path KeyHelper

compileContract FreezeUnfreezeContract

compileContract GracefullyFailing

compileContract GrantRevokeKyc

compileContract HTSCalls

compileContract HbarFeeCollector  --include-path ./NestedHTSTransferrer

compileContract HelloWorldMint

compileContract HtsApproveAllowance

compileContract HtsTransferFrom

compileContract ImmediateChildAssociation

compileContract InstantStorageHog

compileContract LazyPrecompileTransfers

compileContract ManyChildren --include-path ../solidity

compileContract MinimalTokenCreations

compileContract MintContract

compileContract MintNFTContract

compileContract MintToken

compileContract MixedFramesScenarios

compileContract MixedMintToken

compileContract NestedAssociateDissociate

compileContract NestedBurn  --include-path MintToken

compileContract NestedHTSTransferrer

compileContract NestedLazyCreateContract

compileContract NestedMint  --include-path MintNFTContract

compileContract NewTokenCreateContract

compileContract NonDelegateCallee --include-path ../solidity

compileContract NonDelegateCryptoTransfer

compileContract PauseUnpauseTokenAccount

compileContract PrecompileAliasXfer --include-path ../solidity

compileContract PrecompileCaller

compileContract PretendCallee --include-path ../solidity

compileContract PretendPair --include-path ../solidity  --include-path PretendCallee

compileContract SafeOperations

compileContract SelfAssociating

compileContract ServiceContract

compileContract SomeERC20Scenarios --include-path ../solidity

compileContract SomeERC721Scenarios --include-path ../solidity

compileContract StaticContract  --include-path ServiceContract

compileContract TestApprover

compileContract TokenAndTypeCheck

compileContract TokenCreateContract --include-path ../solidity  --optimize

compileContract TokenDefaultKycAndFreezeStatus

compileContract TokenExpiryContract

compileContract TokenInfoContract  --include-path TokenCreateContract

compileContract TokenMiscOperations  --include-path TokenCreateContract

compileContract TransferAmountAndToken

compileContract TransferAndBurn

compileContract UpdateTokenInfoContract  --include-path FeeHelper

compileContract VersatileTransfers  --include-path FeeDistributor

compileContract WipeTokenAccount

compileContract WorkingHours

compileContract ZenosBank

rm -rf tmpOutput