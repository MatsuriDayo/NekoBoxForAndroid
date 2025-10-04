package ech

import (
	"context"
	"crypto/tls"
	"encoding/base64"
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

// ClientHandshake 封装 TLS 握手
func (s *ECHClientConfig) ClientHandshake(ctx context.Context, conn net.Conn) (*tls.Conn, error) {
	tlsConn, err := s.fetchAndHandshake(ctx, conn)
	if err != nil {
		return nil, err
	}
	err = tlsConn.HandshakeContext(ctx)
	if err != nil {
		return nil, err
	}
	return tlsConn, nil
}

// fetchAndHandshake 查询 ECHConfigList 并完成 TLS 连接
func (s *ECHClientConfig) fetchAndHandshake(ctx context.Context, conn net.Conn) (*tls.Conn, error) {
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
		return nil, os.ErrInvalid
	}
	response, err := s.localDnsTransport.Exchange(ctx, message)
	if err != nil {
		return nil, exceptions.Cause(err, "fetch ECH config list")
	}
	if response.Rcode != mDNS.RcodeSuccess {
		return nil, exceptions.Cause(dns.RcodeError(response.Rcode), "fetch ECH config list")
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
	return tls.Client(conn, s.Config), nil
}
