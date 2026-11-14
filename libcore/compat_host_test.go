//go:build !android

package libcore

// Run with the explicit production file list in the Phase 1 audit runner.
// Only Android platform I/O is replaced; BoxInstance and compatibility logic
// below are the same files compiled into libcore.aar.
import (
	"bytes"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/certificate"
	geosites "github.com/sagernet/sing-box/common/geosite"
	"github.com/sagernet/sing-box/common/sniff"
	sblog "github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/route/rule"
	M "github.com/sagernet/sing/common/metadata"
)

var boxPlatformInterfaceInstance adapter.PlatformInterface
var boxPlatformLogWriter sblog.PlatformWriter
var externalAssetsPath string

func TestProductionURLTestRTT(t *testing.T) {
	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { requests.Add(1); w.WriteHeader(http.StatusNoContent) }))
	defer server.Close()
	b := testBox(t, `{"outbounds":[{"type":"direct","tag":"direct"}]}`)
	if _, err := UrlTest(b, server.URL, 3000); err != nil {
		t.Fatal(err)
	}
	if requests.Load() != 2 {
		t.Fatalf("RTT request count=%d want=2", requests.Load())
	}
}

func TestOfficialGeoAssets(t *testing.T) {
	root := os.Getenv("NEKO_GEO_ASSETS")
	if root == "" {
		t.Skip("official asset directory required")
	}
	previous := externalAssetsPath
	externalAssetsPath = root
	defer func() { externalAssetsPath = previous }()
	b := testBox(t, `{"route":{"rule_set":[{"type":"local","tag":"ip","format":"binary","path":"geoip:us"},{"type":"local","tag":"site","format":"binary","path":"geosite:github"}],"rules":[{"rule_set":"ip","outbound":"direct"},{"rule_set":"site","outbound":"direct"}]},"outbounds":[{"type":"direct","tag":"direct"}]}`)
	for _, test := range []struct {
		index       int
		destination string
		want        bool
	}{{0, "8.8.8.8:443", true}, {0, "10.1.2.3:443", false}, {1, "github.com:443", true}, {1, "unrelated.invalid:443", false}} {
		destination := M.ParseSocksaddr(test.destination)
		metadata := adapter.InboundContext{Destination: destination, Domain: destination.Fqdn}
		if got := b.Router().Rules()[test.index].Match(&metadata); got != test.want {
			t.Fatalf("%s match=%v want=%v", test.destination, got, test.want)
		}
	}
}

func TestExternalCertificateReplacement(t *testing.T) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	template := &x509.Certificate{SerialNumber: big.NewInt(1), IsCA: true, BasicConstraintsValid: true, KeyUsage: x509.KeyUsageCertSign, NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour)}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	root, err := x509.ParseCertificate(der)
	if err != nil {
		t.Fatal(err)
	}
	encoded := string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}))
	previous := externalRootPEM.Swap(&encoded)
	defer externalRootPEM.Store(previous)
	for _, kind := range []string{"", "system", "none", "mozilla"} {
		original := &option.CertificateOptions{Store: kind}
		options := option.Options{Certificate: original}
		applyExternalCertificateOptions(&options)
		if kind == "none" || kind == "mozilla" {
			if options.Certificate != original {
				t.Fatal("explicit store changed")
			}
			continue
		}
		if original.Store != kind || len(original.Certificate) != 0 {
			t.Fatal("input options mutated")
		}
		store, err := certificate.NewStore(context.Background(), sblog.NewNOPFactory().Logger(), *options.Certificate)
		if err != nil {
			t.Fatal(err)
		}
		defer store.Close()
		if len(store.Pool().Subjects()) != 1 || !store.ExclusiveAnchors() {
			t.Fatal("external roots must replace system roots")
		}
		if _, err = root.Verify(x509.VerifyOptions{Roots: store.Pool()}); err != nil {
			t.Fatal(err)
		}
	}
}

func TestGeoSiteCompatibilityMatches(t *testing.T) {
	previous := externalAssetsPath
	externalAssetsPath = t.TempDir()
	defer func() { externalAssetsPath = previous }()
	var data bytes.Buffer
	if err := geosites.Write(&data, map[string][]geosites.Item{"fixture": {{Type: geosites.RuleTypeDomain, Value: "example.org"}}}); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(externalAssetsPath, "geosite.db"), data.Bytes(), 0600); err != nil {
		t.Fatal(err)
	}
	b := testBox(t, `{"route":{"rule_set":[{"type":"local","tag":"geo","format":"binary","path":"geosite:fixture"}],"rules":[{"rule_set":"geo","outbound":"direct"}]},"outbounds":[{"type":"direct","tag":"direct"}]}`)
	for domain, want := range map[string]bool{"example.org": true, "other.invalid": false} {
		metadata := adapter.InboundContext{Domain: domain, Destination: M.ParseSocksaddr(domain + ":443")}
		if got := b.Router().Rules()[0].Match(&metadata); got != want {
			t.Fatalf("%s match=%v want=%v", domain, got, want)
		}
	}
}

func TestGeoSiteLoadsCloseTheirFiles(t *testing.T) {
	if _, err := os.Stat("/proc/self/fd"); err != nil {
		t.Skip("requires Linux file descriptor inspection")
	}
	previous := externalAssetsPath
	externalAssetsPath = t.TempDir()
	defer func() { externalAssetsPath = previous }()
	var data bytes.Buffer
	if err := geosites.Write(&data, map[string][]geosites.Item{"fixture": {{Type: geosites.RuleTypeDomain, Value: "example.org"}}}); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(externalAssetsPath, "geosite.db")
	if err := os.WriteFile(path, data.Bytes(), 0600); err != nil {
		t.Fatal(err)
	}
	for range 64 {
		if _, err := geoSiteHeadlessRules("fixture"); err != nil {
			t.Fatal(err)
		}
		if _, err := geoSiteHeadlessRules("missing"); err == nil {
			t.Fatal("missing code should fail")
		}
	}
	entries, err := os.ReadDir("/proc/self/fd")
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		target, _ := os.Readlink(filepath.Join("/proc/self/fd", entry.Name()))
		if target == path {
			t.Fatalf("GeoSite file descriptor was leaked: %s", entry.Name())
		}
	}
}

func testBox(t *testing.T, config string) *BoxInstance {
	t.Helper()
	b, err := NewSingBoxInstance(config, nil)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { b.Close() })
	if err = b.Start(); err != nil {
		t.Fatal(err)
	}
	return b
}

type testCallback struct{ callback func(string, string) }

func (c testCallback) UseOfficialAssets() bool                    { return true }
func (c testCallback) Selector_OnProxySelected(group, tag string) { c.callback(group, tag) }

func TestProductionSelectorResetAndOutboundStats(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			go func() {
				defer conn.Close()
				data := make([]byte, 4)
				if _, err := io.ReadFull(conn, data); err == nil {
					conn.Write([]byte("answer"))
					io.Copy(io.Discard, conn)
				}
			}()
		}
	}()
	config := `{"outbounds":[{"type":"selector","tag":"proxy","outbounds":["a","b"]},{"type":"direct","tag":"a"},{"type":"direct","tag":"b"}]}`
	first := testBox(t, config)
	second := testBox(t, config)
	first.SetV2rayStats("proxy\na\nb")
	open := func(b *BoxInstance) net.Conn {
		conn, err := urlTestClient(b).Transport.(*http.Transport).DialContext(context.Background(), "tcp", listener.Addr().String())
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() { conn.Close() })
		conn.SetDeadline(time.Now().Add(3 * time.Second))
		if _, err = conn.Write([]byte("ping")); err != nil {
			t.Fatal(err)
		}
		data := make([]byte, 6)
		if _, err = io.ReadFull(conn, data); err != nil {
			t.Fatal(err)
		}
		if string(data) != "answer" {
			t.Fatal(string(data))
		}
		return conn
	}
	a, b := open(first), open(second)
	if n := first.QueryStats("proxy", "uplink"); n != 4 {
		t.Fatalf("uplink=%d", n)
	}
	if n := first.QueryStats("proxy", "downlink"); n != 6 {
		t.Fatalf("downlink=%d", n)
	}
	if n := first.QueryStats("proxy", "downlink"); n != 0 {
		t.Fatalf("reset-on-read=%d", n)
	}
	count := 0
	intfNB4A = testCallback{func(group, tag string) {
		count++
		if first.selector.Now() != tag || group != "proxy" {
			t.Error("callback state")
		}
		ResetAllConnections(true)
		for _, conn := range []net.Conn{a, b} {
			if _, err := conn.Write([]byte("closed")); err == nil {
				t.Error("global reset left connection open")
			}
		}
		open(second) // Must survive the already-completed selector Interrupt.
	}}
	t.Cleanup(func() { intfNB4A = nil })
	if !first.SelectOutbound("b") || count != 1 {
		t.Fatal("switch callback missing")
	}
	if !first.SelectOutbound("b") || count != 1 {
		t.Fatal("same-tag callback changed")
	}
	if first.SelectOutbound("missing") || count != 1 {
		t.Fatal("invalid-tag callback changed")
	}
}

func TestProductionSniffOverride(t *testing.T) {
	first, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer first.Close()
	port := first.Addr().(*net.TCPAddr).Port
	second, err := net.Listen("tcp", fmt.Sprintf("127.0.0.2:%d", port))
	if err != nil {
		t.Fatal(err)
	}
	defer second.Close()
	for name, listener := range map[string]net.Listener{"sniffed": first, "original": second} {
		go func() {
			for {
				conn, err := listener.Accept()
				if err != nil {
					return
				}
				go func() { defer conn.Close(); buffer := make([]byte, 4096); conn.Read(buffer); conn.Write([]byte(name)) }()
			}
		}()
	}
	for _, enabled := range []bool{false, true} {
		t.Run(fmt.Sprint(enabled), func(t *testing.T) {
			config := fmt.Sprintf(`{"dns":{"servers":[{"type":"hosts","tag":"hosts","predefined":{"sniff.test":"127.0.0.1"}}]},"route":{"default_domain_resolver":"hosts","rules":[{"action":"sniff","_neko_sniff_override":%v}]},"outbounds":[{"type":"direct","tag":"direct"}]}`, enabled)
			b := testBox(t, config)
			// Test HTTP extraction locally. Windows HTTP filtering intercepts
			// loopback Host headers, so use a plain payload for the routing leg.
			metadata := adapter.InboundContext{}
			if err := sniff.HTTPHost(context.Background(), &metadata, strings.NewReader("GET / HTTP/1.1\r\nHost: sniff.test\r\n\r\n")); err != nil || metadata.Domain != "sniff.test" {
				t.Fatalf("HTTP sniff: %v", err)
			}
			action := b.Router().Rules()[0].Action().(*rule.RuleActionSniff)
			action.StreamSniffers = []sniff.StreamSniffer{func(_ context.Context, m *adapter.InboundContext, reader io.Reader) error {
				m.Domain = metadata.Domain
				m.Protocol = "http"
				return nil
			}}
			client, server := net.Pipe()
			defer client.Close()
			go b.Router().RouteConnectionEx(context.Background(), server, adapter.InboundContext{Network: "tcp", Destination: M.ParseSocksaddr(fmt.Sprintf("127.0.0.2:%d", port))}, nil)
			client.SetDeadline(time.Now().Add(4 * time.Second))
			if _, err := client.Write([]byte("ping")); err != nil {
				t.Fatal(err)
			}
			data, err := io.ReadAll(client)
			if err != nil {
				t.Fatal(err)
			}
			want := "original"
			if enabled {
				want = "sniffed"
			}
			if string(data) != want {
				t.Fatalf("got %.100q want %q", data, want)
			}
		})
	}
}

func TestCustomLegacyConfigStillRejected(t *testing.T) {
	config := `{"inbounds":[{"type":"mixed","sniff":true}]}`
	if _, err := NewSingBoxInstance(config, nil); err == nil || !strings.Contains(err.Error(), "legacy inbound") {
		t.Fatalf("custom config was silently rewritten: %v", err)
	}
}

func TestDNSBaselineSuccessCallbackCompletes(t *testing.T) {
	called := false
	c := ExchangeContext{done: func() { called = true }}
	c.Success("127.0.0.1")
	if !called || len(c.addresses) != 1 {
		t.Fatal("successful callback must publish addresses and complete")
	}
}

func TestActualAppGeneratedConfigs(t *testing.T) {
	root := os.Getenv("NEKO_CONFIG_FIXTURES")
	if root == "" {
		t.Skip("set NEKO_CONFIG_FIXTURES to the actual JVM ConfigBuilder output")
	}
	files, err := filepath.Glob(filepath.Join(root, "*.json"))
	if err != nil || len(files) == 0 {
		t.Fatal("missing generated configs", err)
	}
	for _, file := range files {
		t.Run(filepath.Base(file), func(t *testing.T) {
			data, err := os.ReadFile(file)
			if err != nil {
				t.Fatal(err)
			}
			b, err := NewSingBoxInstance(string(data), nil)
			if err != nil {
				t.Fatal(err)
			}
			if strings.Contains(filepath.Base(file), "urltest") {
				if err := b.Start(); err != nil {
					b.Close()
					t.Fatal(err)
				}
			}
			b.Close()
		})
	}
}
