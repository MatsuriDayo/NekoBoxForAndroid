package libcore

import (
	"fmt"
	"path/filepath"
	"strings"

	"github.com/oschwald/maxminddb-golang"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
)

type geoip struct {
	geoipReader *maxminddb.Reader
}

func (g *geoip) Open(path string) error {
	geoipReader, err := maxminddb.Open(path)
	g.geoipReader = geoipReader
	return err
}

func (g *geoip) Rules(countryCode string) ([]option.HeadlessRule, error) {
	networks := g.geoipReader.Networks(maxminddb.SkipAliasedNetworks)
	countryCode = strings.ToLower(countryCode)
	var cidrs []string
	for networks.Next() {
		var nextCountryCode string
		ipNet, err := networks.Network(&nextCountryCode)
		if err != nil {
			return nil, fmt.Errorf("failed to get network: %w", err)
		}
		if nextCountryCode == countryCode {
			cidrs = append(cidrs, ipNet.String())
		}
	}
	if err := networks.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate networks: %w", err)
	}
	if len(cidrs) == 0 {
		return nil, fmt.Errorf("no networks found for country code: %s", countryCode)
	}

	var headlessRule option.DefaultHeadlessRule
	headlessRule.IPCIDR = cidrs

	return []option.HeadlessRule{
		{
			Type:           C.RuleTypeDefault,
			DefaultOptions: headlessRule,
		},
	}, nil
}

func geoIPHeadlessRules(name string) ([]option.HeadlessRule, error) {
	g := new(geoip)
	if err := g.Open(filepath.Join(externalAssetsPath, "geoip.db")); err != nil {
		return nil, err
	}
	defer g.geoipReader.Close()
	return g.Rules(name)
}
