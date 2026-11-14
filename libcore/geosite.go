package libcore

import (
	"fmt"
	"os"
	"path/filepath"

	geosites "github.com/sagernet/sing-box/common/geosite"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
)

type geosite struct {
	geositeReader *geosites.Reader
	file          *os.File
}

func (g *geosite) Open(path string) error {
	file, err := os.Open(path)
	if err != nil {
		return err
	}
	reader, _, err := geosites.NewReader(file)
	if err != nil {
		file.Close()
		return err
	}
	g.file, g.geositeReader = file, reader
	return nil
}

func (g *geosite) Rules(code string) ([]option.HeadlessRule, error) {
	sourceSet, err := g.geositeReader.Read(code)
	if err != nil {
		return nil, fmt.Errorf("failed to read geosite code %s :%w", code, err)
	}

	var headlessRule option.DefaultHeadlessRule

	defaultRule := geosites.Compile(sourceSet)

	headlessRule.Domain = defaultRule.Domain
	headlessRule.DomainSuffix = defaultRule.DomainSuffix
	headlessRule.DomainKeyword = defaultRule.DomainKeyword
	headlessRule.DomainRegex = defaultRule.DomainRegex

	return []option.HeadlessRule{
		{
			Type:           C.RuleTypeDefault,
			DefaultOptions: headlessRule,
		},
	}, nil
}

func geoSiteHeadlessRules(name string) ([]option.HeadlessRule, error) {
	g := new(geosite)
	if err := g.Open(filepath.Join(externalAssetsPath, "geosite.db")); err != nil {
		return nil, err
	}
	defer g.file.Close()
	return g.Rules(name)
}
