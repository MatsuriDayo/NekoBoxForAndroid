package libcore

import (
	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	"github.com/sagernet/sing/common/x/list"
)

// wtf

type interfaceMonitorStub struct{}

func (s *interfaceMonitorStub) Start() error {
	return nil
}

func (s *interfaceMonitorStub) Close() error {
	return nil
}

func (s *interfaceMonitorStub) DefaultInterface() *control.Interface {
	return nil
}

func (s *interfaceMonitorStub) OverrideAndroidVPN() bool {
	return false
}

func (s *interfaceMonitorStub) AndroidVPNEnabled() bool {
	return false
}

func (s *interfaceMonitorStub) RegisterCallback(callback tun.DefaultInterfaceUpdateCallback) *list.Element[tun.DefaultInterfaceUpdateCallback] {
	return nil
}

func (s *interfaceMonitorStub) UnregisterCallback(element *list.Element[tun.DefaultInterfaceUpdateCallback]) {
}

func (s *interfaceMonitorStub) RegisterMyInterface(interfaceName string) {
}

func (s *interfaceMonitorStub) MyInterface() string {
	return ""
}
