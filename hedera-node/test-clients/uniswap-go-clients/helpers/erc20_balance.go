package misc

import (
	"github.com/hashgraph/hedera-sdk-go/v2"
)

func BalanceVia(client *hedera.Client, tokenId hedera.ContractID, accountId string) uint64 {
	var callParams, err = hedera.NewContractFunctionParameters().AddAddress(accountId)
	if err != nil {
		panic(err)
	}

	response, err := hedera.NewContractCallQuery().
		SetContractID(tokenId).
		SetGas(75000).
		SetQueryPayment(hedera.NewHbar(1)).
		SetFunction("balanceOf", callParams).
		Execute(client)

	if err != nil {
		panic(err)
	}

	return response.GetUint64(0)
}
