package libcore

import (
	"net"
	"sync"
	"sync/atomic"

	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	"github.com/sagernet/sing/common/x/list"
)

// InterfaceUpdateListener bridges Android's existing DefaultNetworkListener to
// sing-box's native interface monitor and WireGuard network lifecycle.
type InterfaceUpdateListener interface {
	UpdateDefaultInterface(name string, index, mtu int32)
}

type platformInterfaceMonitor struct {
	id           int64
	access       sync.Mutex
	current      *control.Interface
	callbacks    list.List[tun.DefaultInterfaceUpdateCallback]
	myInterfaces []string
	closed       bool
}

var nextInterfaceMonitorID atomic.Int64

func (m *platformInterfaceMonitor) Start() error {
	// gomobile creates a new Java proxy for each Go-to-Java call. Use a stable
	// subscription ID rather than Java proxy identity when removing listeners.
	m.id = nextInterfaceMonitorID.Add(1)
	return intfBox.StartDefaultInterfaceMonitor(m.id, m)
}

func (m *platformInterfaceMonitor) Close() error {
	m.access.Lock()
	m.closed = true
	m.access.Unlock()
	return intfBox.CloseDefaultInterfaceMonitor(m.id)
}

func (m *platformInterfaceMonitor) DefaultInterface() *control.Interface {
	m.access.Lock()
	defer m.access.Unlock()
	return m.current
}

func (m *platformInterfaceMonitor) UpdateDefaultInterface(name string, index, mtu int32) {
	m.access.Lock()
	if m.closed {
		m.access.Unlock()
		return
	}
	var current *control.Interface
	if name != "" && index > 0 {
		current = &control.Interface{Name: name, Index: int(index), MTU: int(mtu), Flags: net.FlagUp | net.FlagRunning}
	}
	old := m.current
	if (old == nil && current == nil) || (old != nil && current != nil && old.Name == current.Name && old.Index == current.Index && old.MTU == current.MTU) {
		m.access.Unlock()
		return
	}
	m.current = current
	callbacks := m.callbacks.Array()
	m.access.Unlock()
	for _, callback := range callbacks {
		callback(current, 0)
	}
}

func (m *platformInterfaceMonitor) OverrideAndroidVPN() bool { return false }
func (m *platformInterfaceMonitor) AndroidVPNEnabled() bool  { return false }

func (m *platformInterfaceMonitor) RegisterCallback(callback tun.DefaultInterfaceUpdateCallback) *list.Element[tun.DefaultInterfaceUpdateCallback] {
	m.access.Lock()
	defer m.access.Unlock()
	return m.callbacks.PushBack(callback)
}

func (m *platformInterfaceMonitor) UnregisterCallback(element *list.Element[tun.DefaultInterfaceUpdateCallback]) {
	m.access.Lock()
	defer m.access.Unlock()
	m.callbacks.Remove(element)
}

func (m *platformInterfaceMonitor) RegisterMyInterface(name string) {
	m.access.Lock()
	defer m.access.Unlock()
	m.myInterfaces = append(m.myInterfaces, name)
}

func (m *platformInterfaceMonitor) MyInterfaces() []string {
	m.access.Lock()
	defer m.access.Unlock()
	return append([]string(nil), m.myInterfaces...)
}
