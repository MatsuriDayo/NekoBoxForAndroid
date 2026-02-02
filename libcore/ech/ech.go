package ech

import (
	"context"
	"crypto/tls"
	"encoding/base64"
	"log"
	"net"
	"os"

	mDNS "github.com/miekg/dns"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/dns"
	"github.com/sagernet/sing/common/exceptions"
)

type ECHClientConfig struct {
	*tls.Config
	domain            string
	localDnsTransport adapter.DNSTransport
}

func NewECHClientConfig(domain string, tlsConfig *tls.Config, localDnsTransport adapter.DNSTransport) *ECHClientConfig {
	config := tlsConfig.Clone()
	config.ServerName = domain
	return &ECHClientConfig{
		Config:            config,
		domain:            domain,
		localDnsTransport: localDnsTransport,
	}
}

func (s *ECHClientConfig) Client(ctx context.Context, conn net.Conn) (*tls.Conn, error) {
	err := s.fetchEchKeys(ctx, conn)
	if err != nil {
		// allow empty ech keys
		log.Println("fetchEchKeys:", err)
	}
	return tls.Client(conn, s.Config), nil
}

func (s *ECHClientConfig) fetchEchKeys(ctx context.Context, conn net.Conn) error {
	message := &mDNS.Msg{
		MsgHdr: mDNS.MsgHdr{
			RecursionDesired: true,
		},
		Question: []mDNS.Question{
			{
				Name:   mDNS.Fqdn(s.domain),
				Qtype:  mDNS.TypeHTTPS,
				Qclass: mDNS.ClassINET,
			},
		},
	}
	if s.localDnsTransport == nil {
		return os.ErrInvalid
	}
	response, err := s.localDnsTransport.Exchange(ctx, message)
	if err != nil {
		return exceptions.Cause(err, "fetch ECH config list")
	}
	if response.Rcode != mDNS.RcodeSuccess {
		return exceptions.Cause(dns.RcodeError(response.Rcode), "fetch ECH config list")
	}
	for _, rr := range response.Answer {
		switch resource := rr.(type) {
		case *mDNS.HTTPS:
			for _, value := range resource.Value {
				if value.Key().String() == "ech" {
					echConfigList, err := base64.StdEncoding.DecodeString(value.String())
					if err == nil {
						s.Config.EncryptedClientHelloConfigList = echConfigList
					}
				}
			}
		}
	}
	return nil
}
