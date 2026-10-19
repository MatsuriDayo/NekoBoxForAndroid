package libcore

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"libcore/procfs"
	"log"
	"net/netip"
	"strings"
	"syscall"

	"github.com/matsuridayo/libneko/neko_log"
	"github.com/sagernet/sing-box/adapter"
	sblog "github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	tun "github.com/sagernet/sing-tun"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
)

var boxPlatformInterfaceInstance adapter.PlatformInterface = &boxPlatformInterfaceWrapper{}

type boxPlatformInterfaceWrapper struct {
	interfaceAddress []netip.Addr
}

func (w *boxPlatformInterfaceWrapper) ReadWIFIState(ctx context.Context) adapter.WIFIState {
	state := strings.SplitN(intfBox.WIFIState(), ",", 2)
	if len(state) != 2 {
		return adapter.WIFIState{}
	}
	return adapter.WIFIState{
		SSID:  state[0],
		BSSID: state[1],
	}
}

func (w *boxPlatformInterfaceWrapper) Initialize(n adapter.NetworkManager) error {
	return nil
}

func (w *boxPlatformInterfaceWrapper) UsePlatformAutoDetectInterfaceControl() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) AutoDetectInterfaceControl(fd int) error {
	// call protect_path
	if !isBgProcess {
		_ = sendFdToProtect(fd, "protect_path")
		return nil
	}
	// bg process call VPNService
	return intfBox.AutoDetectInterfaceControl(int32(fd))
}

func (w *boxPlatformInterfaceWrapper) UsePlatformInterface() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) OpenInterface(options *tun.Options, platformOptions option.TunPlatformOptions) (tun.Tun, error) {
	if len(options.IncludeUID) > 0 || len(options.ExcludeUID) > 0 {
		return nil, E.New("android: unsupported uid options")
	}
	if len(options.IncludeAndroidUser) > 0 {
		return nil, E.New("android: unsupported android_user option")
	}
	a, _ := json.Marshal(options)
	b, _ := json.Marshal(platformOptions)
	tunFd, err := intfBox.OpenTun(string(a), string(b))
	if err != nil {
		return nil, fmt.Errorf("intfBox.OpenTun: %v", err)
	}
	// Do you want to close it?
	tunFd, err = syscall.Dup(tunFd)
	if err != nil {
		return nil, fmt.Errorf("syscall.Dup: %v", err)
	}
	//
	options.FileDescriptor = int(tunFd)
	w.interfaceAddress = w.interfaceAddress[:0]
	for _, prefix := range options.Inet4Address {
		w.interfaceAddress = append(w.interfaceAddress, prefix.Addr())
	}
	for _, prefix := range options.Inet6Address {
		w.interfaceAddress = append(w.interfaceAddress, prefix.Addr())
	}
	return tun.New(*options)
}

func (w *boxPlatformInterfaceWrapper) MyInterfaceAddress() []netip.Addr {
	return append([]netip.Addr(nil), w.interfaceAddress...)
}

func (w *boxPlatformInterfaceWrapper) CloseTun() error {
	return nil
}

func (w *boxPlatformInterfaceWrapper) UsePlatformDefaultInterfaceMonitor() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) CreateDefaultInterfaceMonitor(l logger.Logger) tun.DefaultInterfaceMonitor {
	return &platformInterfaceMonitor{}
}

func (w *boxPlatformInterfaceWrapper) UsePlatformNetworkInterfaces() bool {
	return false
}

func (w *boxPlatformInterfaceWrapper) NetworkInterfaces() ([]adapter.NetworkInterface, error) {
	return nil, errors.New("platform network interfaces are disabled")
}

func (w *boxPlatformInterfaceWrapper) NetworkExtensionIncludeAllNetworks() bool {
	return false
}

func (w *boxPlatformInterfaceWrapper) RequestPermissionForWIFIState() error {
	return nil
}

func (w *boxPlatformInterfaceWrapper) UsePlatformWIFIMonitor() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) UsePlatformNotification() bool {
	return false
}

func (w *boxPlatformInterfaceWrapper) SendNotification(notification *adapter.Notification) error {
	return nil
}

func (s *boxPlatformInterfaceWrapper) SystemCertificates() []string {
	return nil
}

// Android not using

func (w *boxPlatformInterfaceWrapper) UnderNetworkExtension() bool {
	return false
}

func (w *boxPlatformInterfaceWrapper) ClearDNSCache() {
}

func (w *boxPlatformInterfaceWrapper) UsePlatformConnectionOwnerFinder() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) FindConnectionOwner(request *adapter.FindConnectionOwnerRequest) (*adapter.ConnectionOwner, error) {
	var uid int32
	if useProcfs {
		var network string
		switch request.IpProtocol {
		case int32(syscall.IPPROTO_TCP):
			network = "tcp"
		case int32(syscall.IPPROTO_UDP):
			network = "udp"
		default:
			return nil, E.New("unknown protocol: ", request.IpProtocol)
		}
		sourceAddress, err := netip.ParseAddr(request.SourceAddress)
		if err != nil {
			return nil, err
		}
		destinationAddress, err := netip.ParseAddr(request.DestinationAddress)
		if err != nil {
			return nil, err
		}
		uid = procfs.ResolveSocketByProcSearch(
			network,
			netip.AddrPortFrom(sourceAddress, uint16(request.SourcePort)),
			netip.AddrPortFrom(destinationAddress, uint16(request.DestinationPort)),
		)
		if uid == -1 {
			return nil, E.New("procfs: not found")
		}
	} else {
		var err error
		uid, err = intfBox.FindConnectionOwner(
			request.IpProtocol,
			request.SourceAddress,
			request.SourcePort,
			request.DestinationAddress,
			request.DestinationPort,
		)
		if err != nil {
			return nil, err
		}
	}
	packageName, _ := intfBox.PackageNameByUid(uid)
	var packageNames []string
	if packageName != "" {
		packageNames = []string{packageName}
	}
	return &adapter.ConnectionOwner{UserId: uid, AndroidPackageNames: packageNames}, nil
}

// sing-box v1.14.x PlatformInterface additions. NekoBox Android does not
// implement the new platform capabilities (TUN option passthrough, neighbor
// resolver, shell/SSH/SFTP, bridge); the stubs keep the interface satisfied
// while explicitly disabling every feature so existing Android behavior is
// unchanged.

func (w *boxPlatformInterfaceWrapper) ProcessPlatformOptions(options option.TunPlatformOptions) error {
	// Android TUN is driven entirely through intfBox.OpenTun; the extra
	// platform options are not used. Accept them and ignore.
	return nil
}

func (w *boxPlatformInterfaceWrapper) CancelNotification(identifier string, typeID int32) error {
	// Notifications are not sent on Android (UsePlatformNotification = false);
	// cancellation is a no-op.
	return nil
}

func (w *boxPlatformInterfaceWrapper) UsePlatformNeighborResolver() bool {
	return false
}

func (w *boxPlatformInterfaceWrapper) StartNeighborMonitor(listener adapter.NeighborUpdateListener) error {
	return E.New("android: neighbor resolver is not supported")
}

func (w *boxPlatformInterfaceWrapper) CloseNeighborMonitor(listener adapter.NeighborUpdateListener) error {
	return E.New("android: neighbor resolver is not supported")
}

func (w *boxPlatformInterfaceWrapper) UsePlatformShell() bool {
	return false
}

func (w *boxPlatformInterfaceWrapper) CheckPlatformShell() error {
	return E.New("android: shell is not supported")
}

func (w *boxPlatformInterfaceWrapper) OpenShellSession(user *adapter.PlatformUser, command string, env []string, term string, rows int32, cols int32) (adapter.ShellSession, error) {
	return nil, E.New("android: shell is not supported")
}

func (w *boxPlatformInterfaceWrapper) LookupUser(username string) (*adapter.PlatformUser, error) {
	return nil, E.New("android: shell is not supported")
}

func (w *boxPlatformInterfaceWrapper) LookupSFTPServer() (string, error) {
	return "", E.New("android: sftp is not supported")
}

func (w *boxPlatformInterfaceWrapper) ReadSystemSSHHostKey() ([]byte, error) {
	return nil, E.New("android: shell is not supported")
}

func (w *boxPlatformInterfaceWrapper) TailscaleHostname() string {
	return ""
}

func (w *boxPlatformInterfaceWrapper) UsePlatformBridge() bool {
	return false
}

func (w *boxPlatformInterfaceWrapper) CreateBridge(options adapter.BridgeOptions) (adapter.BridgeSession, error) {
	return nil, E.New("android: bridge is not supported")
}

// io.Writer

var disableSingBoxLog = false

func (w *boxPlatformInterfaceWrapper) Write(p []byte) (n int, err error) {
	// use neko_log
	if !disableSingBoxLog {
		log.Print(string(p))
	}
	return len(p), nil
}

// 日志

type boxPlatformLogWriterWrapper struct {
}

var boxPlatformLogWriter sblog.PlatformWriter = &boxPlatformLogWriterWrapper{}

func (w *boxPlatformLogWriterWrapper) DisableColors() bool { return true }

func (w *boxPlatformLogWriterWrapper) WriteMessage(level uint8, message string) {
	if !strings.HasSuffix(message, "\n") {
		message += "\n"
	}
	neko_log.LogWriter.Write([]byte(message))
}
