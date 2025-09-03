// libbox/dns.go

package libcore

import (
	"context"
	"net/netip"
	"strings"
	"syscall"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/dns"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	"github.com/sagernet/sing/common/task"

	mDNS "github.com/miekg/dns"
)

type LocalDNSTransport interface {
	Raw() bool
	Lookup(ctx *ExchangeContext, network string, domain string) error
	Exchange(ctx *ExchangeContext, message []byte) error
}

var gLocalDNSTransport *platformLocalDNSTransport = nil

type platformLocalDNSTransport struct {
	iif LocalDNSTransport
	tag string
}

func (p *platformLocalDNSTransport) Start(adapter.StartStage) error {
	return nil
}

func (p *platformLocalDNSTransport) Reset() {
}

func (p *platformLocalDNSTransport) Close() error {
	return nil
}

func (p *platformLocalDNSTransport) Raw() bool {
	return p.iif.Raw()
}

func (p *platformLocalDNSTransport) Exchange(ctx context.Context, message *mDNS.Msg) (*mDNS.Msg, error) {
	messageBytes, err := message.Pack()
	if err != nil {
		return nil, err
	}
	response := &ExchangeContext{
		context: ctx,
	}
	var responseMessage *mDNS.Msg
	return responseMessage, task.Run(ctx, func() error {
		err = p.iif.Exchange(response, messageBytes)
		if err != nil {
			return err
		}
		if response.error != nil {
			return response.error
		}
		responseMessage = &response.message
		return nil
	})
}

func (p *platformLocalDNSTransport) Lookup(ctx context.Context, domain string, strategy constant.DomainStrategy) ([]netip.Addr, error) {
	var network string
	switch strategy {
	case constant.DomainStrategyIPv4Only:
		network = "ip4"
	case constant.DomainStrategyPreferIPv6:
		network = "ip6"
	default:
		network = "ip"
	}
	response := &ExchangeContext{
		context: ctx,
	}
	var responseAddr []netip.Addr
	return responseAddr, task.Run(ctx, func() error {
		err := p.iif.Lookup(response, network, domain)
		if err != nil {
			return err
		}
		if response.error != nil {
			return response.error
		}
		switch strategy {
		case constant.DomainStrategyIPv4Only:
			responseAddr = common.Filter(response.addresses, func(it netip.Addr) bool {
				return it.Is4()
			})
		case constant.DomainStrategyPreferIPv6:
			responseAddr = common.Filter(response.addresses, func(it netip.Addr) bool {
				return it.Is6()
			})
		default:
			responseAddr = response.addresses
		}
		/*if len(responseAddr) == 0 {
			response.error = dns.RCodeSuccess
		}*/
		return nil
	})
}

func (p *platformLocalDNSTransport) Tag() string {
	return p.tag
}

func (p *platformLocalDNSTransport) Type() string {
	return "local"
}

func (p *platformLocalDNSTransport) Dependencies() []string {
	return nil
}

type Func interface {
	Invoke() error
}

type ExchangeContext struct {
	context   context.Context
	message   mDNS.Msg
	addresses []netip.Addr
	error     error
}

func (c *ExchangeContext) OnCancel(callback Func) {
	go func() {
		<-c.context.Done()
		callback.Invoke()
	}()
}

func (c *ExchangeContext) Success(result string) {
	c.addresses = common.Map(common.Filter(strings.Split(result, "\n"), func(it string) bool {
		return !common.IsEmpty(it)
	}), func(it string) netip.Addr {
		return M.ParseSocksaddrHostPort(it, 0).Unwrap().Addr
	})
}

func (c *ExchangeContext) RawSuccess(result []byte) {
	err := c.message.Unpack(result)
	if err != nil {
		c.error = E.Cause(err, "parse response")
	}
}

func (c *ExchangeContext) ErrorCode(code int32) {
	c.error = dns.RcodeError(code)
}

func (c *ExchangeContext) ErrnoCode(code int32) {
	c.error = syscall.Errno(code)
}
