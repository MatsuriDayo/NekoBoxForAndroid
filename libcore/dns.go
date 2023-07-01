//go:build android

package libcore

import (
	"context"
	"log"
	"net"
	"net/netip"
	"strconv"
	"strings"
	"time"

	"github.com/miekg/dns"
	D "github.com/sagernet/sing-dns"
	"github.com/sagernet/sing/common/logger"
	N "github.com/sagernet/sing/common/network"
)

func init() {
	D.RegisterTransport([]string{"underlying"}, createUnderlyingTransport)
}

func createUnderlyingTransport(name string, ctx context.Context, logger logger.ContextLogger, dialer N.Dialer, link string) (D.Transport, error) {
	return &androidUnderlyingTransportSing{name, underlyingResolver}, nil
}

type androidUnderlyingTransportSing struct {
	name string
	*androidUnderlyingTransport
}

func (t *androidUnderlyingTransportSing) Name() string { return t.name }

//

var systemResolver = &net.Resolver{PreferGo: false}    // Using System API, lookup from current network.
var underlyingResolver = &androidUnderlyingTransport{} // Using System API, lookup from non-VPN network.

type androidUnderlyingTransport struct{}

func (t *androidUnderlyingTransport) Start() error { return nil }
func (t *androidUnderlyingTransport) Close() error { return nil }
func (t *androidUnderlyingTransport) Raw() bool    { return false }
func (t *androidUnderlyingTransport) Exchange(ctx context.Context, message *dns.Msg) (*dns.Msg, error) {
	return nil, D.ErrNoRawSupport
}

func (t *androidUnderlyingTransport) Lookup(ctx context.Context, domain string, strategy D.DomainStrategy) (ips []netip.Addr, err error) {
	isSekai := localResolver != nil

	var cancel context.CancelFunc
	ctx, cancel = context.WithTimeout(ctx, time.Second*5)
	ok := make(chan interface{})
	defer cancel()

	go func() {
		defer func() {
			select {
			case <-ctx.Done():
			default:
				ok <- nil
			}
			close(ok)
		}()

		var network, str string
		if strategy == D.DomainStrategyUseIPv4 {
			network = "ip4"
		} else if strategy == D.DomainStrategyUseIPv6 {
			network = "ip6"
		} else {
			network = "ip"
		}

		if isSekai {
			str, err = localResolver.LookupIP(network, domain)
			// java -> go
			if err != nil {
				rcode, err2 := strconv.Atoi(err.Error())
				if err2 == nil {
					err = D.RCodeError(rcode)
				}
				return
			} else if str == "" {
				err = D.RCodeNameError
				return
			}
			ips = make([]netip.Addr, 0)
			for _, ip := range strings.Split(str, ",") {
				ips = append(ips, netip.MustParseAddr(ip))
			}
		} else {
			ips2, err2 := systemResolver.LookupIP(context.Background(), network, domain)
			if err2 != nil {
				err = err2
				return
			}
			for _, ip2 := range ips2 {
				if ip, ok := netip.AddrFromSlice(ip2); ok {
					ips = append(ips, ip)
				}
			}
		}
	}()

	select {
	case <-ctx.Done():
		log.Printf("underlyingResolver: context cancelled! (sekai=%t)\n", isSekai)
		return nil, D.RCodeServerFailure
	case <-ok:
		return
	}
}
