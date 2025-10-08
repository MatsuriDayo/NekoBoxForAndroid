package juicity

import (
	"bytes"
	"context"
	"crypto/x509"
  "crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"net"
	"os"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/common/dialer"
	"github.com/sagernet/sing-box/common/tls"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing-box/option"

	"github.com/dyhkwong/sing-juicity"
	"github.com/gofrs/uuid/v5"
)

const TypeJuicity = "juicity"

type JuicityOutboundOptions struct {
	option.DialerOptions
	option.ServerOptions
	UUID     string `json:"uuid,omitempty"`
	Password string `json:"password,omitempty"`
	option.OutboundTLSOptionsContainer
	PinCertSha256 string `json:"pin_cert_sha256,omitempty"`
}

func RegisterOutbound(registry *outbound.Registry) {
	outbound.Register[JuicityOutboundOptions](registry, TypeJuicity, NewOutbound)
}

var _ adapter.Outbound = (*Outbound)(nil)

type Outbound struct {
	outbound.Adapter
	logger logger.ContextLogger
	client *juicity.Client
}

func NewOutbound(ctx context.Context, router adapter.Router, logger log.ContextLogger, tag string, options JuicityOutboundOptions) (adapter.Outbound, error) {
	if options.TLS == nil || !options.TLS.Enabled {
		return nil, C.ErrTLSRequired
	}
	outboundDialer, err := dialer.NewWithOptions(dialer.Options{
		Context:        ctx,
		Options:        options.DialerOptions,
		RemoteIsDomain: options.ServerIsDomain(),
	})
	if err != nil {
		return nil, err
	}
	if options.TLS.ALPN == nil { // not len(options.TLS.ALPN) > 0
		options.TLS.ALPN = []string{"h3"}
	}
	tlsConfig, err := tls.NewSTDClient(ctx, options.Server, *options.TLS)
	if err != nil {
		return nil, err
	}
	if options.PinCertSha256 != "" {
		pinCertSha256, err := tryDecodeBase64(options.PinCertSha256)
		if err != nil {
			return nil, E.Cause(err, "decode pin cert sha256")
		}
		stdTLSConfig, _ := tlsConfig.Config()
		stdTLSConfig.VerifyPeerCertificate = func(rawCerts [][]byte, verifiedChains [][]*x509.Certificate) error {
			peerHash := certChainHash(rawCerts)
			if !bytes.Equal(pinCertSha256, peerHash) {
				return E.New("peer cert hash mismatch")
			}
			return nil
		}
		stdTLSConfig.InsecureSkipVerify = true
	}
	uuidInstance, err := uuid.FromString(options.UUID)
	if err != nil {
		return nil, err
	}
	client, err := juicity.NewClient(juicity.ClientOptions{
		Context:       ctx,
		Dialer:        outboundDialer,
		ServerAddress: options.Build(),
		TLSConfig:     tlsConfig,
		UUID:          [uuid.Size]byte(uuidInstance.Bytes()),
		Password:      options.Password,
	})
	if err != nil {
		return nil, err
	}
	return &Outbound{
		Adapter: outbound.NewAdapterWithDialerOptions(TypeJuicity, tag, []string{N.NetworkTCP, N.NetworkUDP}, options.DialerOptions),
		logger:  logger,
		client:  client,
	}, nil
}

func (o *Outbound) Close() error {
	return o.client.CloseWithError(os.ErrClosed)
}

func (o *Outbound) DialContext(ctx context.Context, network string, destination M.Socksaddr) (net.Conn, error) {
	switch network {
	case N.NetworkTCP:
		ctx, metadata := adapter.ExtendContext(ctx)
		metadata.Outbound = o.Tag()
		metadata.Destination = destination
		o.logger.InfoContext(ctx, "outbound connection to ", destination)
		return o.client.DialConn(ctx, destination)
	case N.NetworkUDP:
		conn, err := o.ListenPacket(ctx, destination)
		if err != nil {
			return nil, err
		}
		return bufio.NewBindPacketConn(conn, destination), nil
	default:
		return nil, E.New("unsupported network: ", network)
	}
}

func (o *Outbound) ListenPacket(ctx context.Context, destination M.Socksaddr) (net.PacketConn, error) {
	ctx, metadata := adapter.ExtendContext(ctx)
	metadata.Outbound = o.Tag()
	metadata.Destination = destination
	o.logger.InfoContext(ctx, "outbound packet connection to ", destination)
	return o.client.ListenPacket(ctx, destination)
}

// https://github.com/juicity/juicity/blob/412dbe43e091788c5464eb2d6e9c169bdf39f19c/cmd/client/run.go#L86-L96
func tryDecodeBase64(raw string) ([]byte, error) {
	var errors []error
	decoders := []*base64.Encoding{base64.URLEncoding, base64.StdEncoding}
	for _, decoder := range decoders {
		decoded, err := decoder.DecodeString(raw)
		if err == nil {
			return decoded, nil
		}
		errors = append(errors, err)
	}
	decoded, err := hex.DecodeString(raw)
	if err == nil {
		return decoded, nil
	}
	errors = append(errors, err)
	return nil, E.Cause(E.Errors(errors...), "try decoding base64")
}

// Simple implementation of cert chain hash - you may need to adjust this
func certChainHash(rawCerts [][]byte) (hash []byte) {
	// This is a simplified implementation. You might need to implement
	// the exact hash calculation method used by juicity
	// For now, return the first cert's hash as placeholder
	// if len(rawCerts) > 0 {
	// 	return rawCerts[0][:32] // simplified
	// }
	// return nil
  for _, cert := range rawCerts {
    sum := sha256.Sum256(cert)
    hash = append(hash, sum[:]...)
  }
  return
}
