module libcore

go 1.24

toolchain go1.24.9

require (
	github.com/dyhkwong/sing-juicity v0.0.3
	github.com/gofrs/uuid/v5 v5.3.2
	github.com/matsuridayo/libneko v1.0.0 // replaced
	github.com/miekg/dns v1.1.67
	github.com/oschwald/maxminddb-golang v1.13.1
	github.com/sagernet/quic-go v0.52.0-sing-box-mod.3
	github.com/sagernet/sing v0.7.13
	github.com/sagernet/sing-box v1.0.0 // replaced
	github.com/sagernet/sing-tun v0.7.3
	github.com/ulikunitz/xz v0.5.15
	golang.org/x/mobile v0.0.0-20231108233038-35478a0c49da
	golang.org/x/sys v0.35.0
)

require (
	github.com/ajg/form v1.5.1 // indirect
	github.com/andybalholm/brotli v1.1.0 // indirect
	github.com/anytls/sing-anytls v0.0.11 // indirect
	github.com/caddyserver/certmagic v0.23.0 // indirect
	github.com/caddyserver/zerossl v0.1.3 // indirect
	github.com/cretz/bine v0.2.0 // indirect
	github.com/fsnotify/fsnotify v1.9.0 // indirect
	github.com/go-chi/chi/v5 v5.2.3 // indirect
	github.com/go-chi/render v1.0.3 // indirect
	github.com/go-ole/go-ole v1.3.0 // indirect
	github.com/gobwas/httphead v0.1.0 // indirect
	github.com/gobwas/pool v0.2.1 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/google/go-cmp v0.7.0 // indirect
	github.com/hashicorp/yamux v0.1.2 // indirect
	github.com/klauspost/compress v1.17.11 // indirect
	github.com/klauspost/cpuid/v2 v2.2.10 // indirect
	github.com/libdns/alidns v1.0.5-libdns.v1.beta1 // indirect
	github.com/libdns/cloudflare v0.2.2-0.20250708034226-c574dccb31a6 // indirect
	github.com/libdns/libdns v1.1.0 // indirect
	github.com/logrusorgru/aurora v2.0.3+incompatible // indirect
	github.com/mdlayher/netlink v1.7.3-0.20250113171957-fbb4dce95f42 // indirect
	github.com/mdlayher/socket v0.5.1 // indirect
	github.com/metacubex/chacha v0.1.5 // indirect
	github.com/metacubex/mihomo v1.19.13 // indirect
	github.com/metacubex/randv2 v0.2.0 // indirect
	github.com/metacubex/sing v0.5.5 // indirect
	github.com/metacubex/tfo-go v0.0.0-20250921095601-b102db4216c0 // indirect
	github.com/metacubex/utls v1.8.3 // indirect
	github.com/mholt/acmez/v3 v3.1.2 // indirect
	github.com/quic-go/qpack v0.5.1 // indirect
	github.com/quic-go/quic-go v0.55.0 // indirect
	github.com/sagernet/bbolt v0.0.0-20231014093535-ea5cb2fe9f0a // indirect
	github.com/sagernet/cors v1.2.1 // indirect
	github.com/sagernet/fswatch v0.1.1 // indirect
	github.com/sagernet/gvisor v0.0.0-20250325023245-7a9c0f5725fb // indirect
	github.com/sagernet/netlink v0.0.0-20240612041022-b9a21c07ac6a // indirect
	github.com/sagernet/nftables v0.3.0-beta.4 // indirect
	github.com/sagernet/sing-mux v0.3.3 // indirect
	github.com/sagernet/sing-quic v0.5.2-0.20250909083218-00a55617c0fb // indirect
	github.com/sagernet/sing-shadowsocks v0.2.8 // indirect
	github.com/sagernet/sing-shadowsocks2 v0.2.1 // indirect
	github.com/sagernet/sing-shadowtls v0.2.1-0.20250503051639-fcd445d33c11 // indirect
	github.com/sagernet/sing-vmess v0.2.7 // indirect
	github.com/sagernet/smux v1.5.34-mod.2 // indirect
	github.com/sagernet/wireguard-go v0.0.1-beta.7 // indirect
	github.com/sagernet/ws v0.0.0-20231204124109-acfe8907c854 // indirect
	github.com/sirupsen/logrus v1.9.3 // indirect
	github.com/vishvananda/netns v0.0.5 // indirect
	github.com/zeebo/blake3 v0.2.4 // indirect
	gitlab.com/go-extension/aes-ccm v0.0.0-20230221065045-e58665ef23c7 // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.uber.org/zap v1.27.0 // indirect
	go.uber.org/zap/exp v0.3.0 // indirect
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba // indirect
	golang.org/x/crypto v0.41.0 // indirect
	golang.org/x/exp v0.0.0-20250506013437-ce4c2cf36ca6 // indirect
	golang.org/x/mod v0.27.0 // indirect
	golang.org/x/net v0.43.0 // indirect
	golang.org/x/sync v0.16.0 // indirect
	golang.org/x/text v0.28.0 // indirect
	golang.org/x/time v0.9.0 // indirect
	golang.org/x/tools v0.36.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20250324211829-b45e905df463 // indirect
	google.golang.org/grpc v1.73.0 // indirect
	google.golang.org/protobuf v1.36.6 // indirect
	lukechampine.com/blake3 v1.3.0 // indirect
)

replace github.com/matsuridayo/libneko => ../../libneko

replace github.com/sagernet/sing-box => ../../sing-box

replace github.com/sagernet/sing-vmess => github.com/starifly/sing-vmess v0.2.7-mod.4

// replace github.com/sagernet/sing-quic => github.com/matsuridayo/sing-quic v0.0.0-20241009042333-b49ce60d9b36
// replace github.com/sagernet/sing-quic => ../../sing-quic

// replace github.com/sagernet/sing => ../../sing

// replace github.com/sagernet/sing-dns => ../../sing-dns

// replace berty.tech/go-libtor => github.com/berty/go-libtor v0.0.0-20220627102132-9189eb6e3982
