package misc

import (
	"encoding/json"
	"io/ioutil"
)

type details struct {
	LpIds       []string `json:"lpIds"`
	Tickers     []string `json:"tickers"`
	TokenIds    []string `json:"tokenIds"`
	TraderIds   []string `json:"traderIds"`
	FactoryId   string   `json:"factoryId"`
	Weth9Id     string   `json:"weth9Id"`
}

func LoadDetails() details {
	rawDetails, err := ioutil.ReadFile("./assets/details.json")
	if err != nil {
		panic(err)
	}
	simDetails := details{}
	err = json.Unmarshal(rawDetails, &simDetails)
	if err != nil {
		panic(err)
	}
	return simDetails
}
