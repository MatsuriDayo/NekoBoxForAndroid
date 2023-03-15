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
	D.RegisterTransport([]string{"underlying"}, CreateUnderlyingTransport)
}

// CreateUnderlyingTransport for Android
func CreateUnderlyingTransport(ctx context.Context, logger logger.ContextLogger, dialer N.Dialer, link string) (D.Transport, error) {
	return underlyingResolver, nil
}

var systemResolver = &net.Resolver{PreferGo: false}                                  // Using System API, lookup from current network.
var underlyingResolver = &androidUnderlyingTransport{systemResolver: systemResolver} // Using System API, lookup from non-VPN network.

type LocalResolver interface {
	LookupIP(network string, domain string) (string, error)
}

type androidUnderlyingTransport struct {
	systemResolver *net.Resolver
	localResolver  LocalResolver // Android: passed from java (only when VPNService)
}

func (t *androidUnderlyingTransport) Start() error { return nil }
func (t *androidUnderlyingTransport) Close() error { return nil }
func (t *androidUnderlyingTransport) Raw() bool    { return false }
func (t *androidUnderlyingTransport) Exchange(ctx context.Context, message *dns.Msg) (*dns.Msg, error) {
	return nil, D.ErrNoRawSupport
}

func (t *androidUnderlyingTransport) Lookup(ctx context.Context, domain string, strategy D.DomainStrategy) (ips []netip.Addr, err error) {
	isSekai := t.localResolver != nil

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
			str, err = t.localResolver.LookupIP(network, domain)
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
			ips2, err2 := t.systemResolver.LookupIP(context.Background(), network, domain)
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
