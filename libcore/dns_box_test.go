package libcore

import (
	"context"
	"errors"
	"testing"
	"time"

	mDNS "github.com/miekg/dns"
	"github.com/sagernet/sing-box/option"
)

type rawTestDNSTransport struct{}

func (rawTestDNSTransport) Raw() bool            { return true }
func (rawTestDNSTransport) NetworkHandle() int64 { return 42 }
func (rawTestDNSTransport) Lookup(*ExchangeContext, string, string) error {
	return errors.New("unexpected lookup")
}
func (rawTestDNSTransport) Exchange(*ExchangeContext, []byte) error {
	return errors.New("unexpected exchange")
}

func TestPlatformDNSExchangeAsync(t *testing.T) {
	original := rawQueryFunc
	t.Cleanup(func() { rawQueryFunc = original })
	transport := newPlatformTransport(rawTestDNSTransport{}, "test", option.LocalDNSServerOptions{})
	query := new(mDNS.Msg).SetQuestion("example.org.", mDNS.TypeA)
	query.Id = 1234
	queryErr := errors.New("query failed")
	for _, wantErr := range []error{nil, queryErr} {
		rawQueryFunc = func(handle int64, request []byte) ([]byte, error) {
			if handle != 42 {
				return nil, errors.New("wrong network handle")
			}
			if wantErr != nil {
				return nil, wantErr
			}
			message := new(mDNS.Msg)
			if err := message.Unpack(request); err != nil {
				return nil, err
			}
			return new(mDNS.Msg).SetReply(message).Pack()
		}
		finished := make(chan struct{})
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		transport.ExchangeAsync(ctx, query, func(response *mDNS.Msg, err error) {
			defer close(finished)
			if !errors.Is(err, wantErr) {
				t.Errorf("error = %v, want %v", err, wantErr)
			}
			if wantErr == nil && (response == nil || response.Id != query.Id || !response.Response) {
				t.Errorf("invalid DNS response: %v", response)
			}
		})
		select {
		case <-finished:
		case <-ctx.Done():
			t.Fatal("DNS callback did not complete")
		}
		cancel()
	}
}
