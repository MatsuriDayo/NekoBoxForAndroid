package libcore

import (
	"sync"
	"testing"

	"github.com/sagernet/sing/common/control"
)

type monitorTestPlatform struct {
	BoxPlatformInterface
	started []int64
	closed  []int64
}

func (p *monitorTestPlatform) StartDefaultInterfaceMonitor(id int64, listener InterfaceUpdateListener) error {
	p.started = append(p.started, id)
	listener.UpdateDefaultInterface("wlan0", 7, 1500)
	return nil
}

func (p *monitorTestPlatform) CloseDefaultInterfaceMonitor(id int64) error {
	p.closed = append(p.closed, id)
	return nil
}

func TestInterfaceMonitorSubscriptionLifecycle(t *testing.T) {
	previous := intfBox
	platform := &monitorTestPlatform{}
	intfBox = platform
	t.Cleanup(func() { intfBox = previous })
	first, second := &platformInterfaceMonitor{}, &platformInterfaceMonitor{}
	for _, monitor := range []*platformInterfaceMonitor{first, second} {
		if err := monitor.Start(); err != nil {
			t.Fatal(err)
		}
	}
	if first.id == second.id || first.id == 0 || second.id == 0 {
		t.Fatal("concurrent subscriptions must have distinct nonzero IDs")
	}
	for _, monitor := range []*platformInterfaceMonitor{second, first} {
		if err := monitor.Close(); err != nil {
			t.Fatal(err)
		}
		monitor.UpdateDefaultInterface("", -1, 0)
		if monitor.DefaultInterface() == nil {
			t.Fatal("closed subscription accepted a late callback")
		}
	}
	if platform.closed[0] != platform.started[1] || platform.closed[1] != platform.started[0] {
		t.Fatal("close did not remove the corresponding subscription")
	}
}

func TestInterfaceMonitorNetworkLifecycle(t *testing.T) {
	m := &platformInterfaceMonitor{}
	var observed []string
	element := m.RegisterCallback(func(current *control.Interface, _ int) {
		// Callbacks may read the monitor; they must run outside its mutex.
		if m.DefaultInterface() != current {
			t.Fatal("callback observed stale interface")
		}
		name := "offline"
		if current != nil {
			name = current.Name
		}
		observed = append(observed, name)
	})
	m.UpdateDefaultInterface("wlan0", 7, 1500)
	m.UpdateDefaultInterface("wlan0", 7, 1500) // identical update must not reset connections
	m.UpdateDefaultInterface("", -1, 0)
	m.UpdateDefaultInterface("rmnet0", 9, 1400)
	if len(observed) != 3 || observed[0] != "wlan0" || observed[1] != "offline" || observed[2] != "rmnet0" {
		t.Fatalf("network events: %v", observed)
	}
	m.UnregisterCallback(element)
	m.UpdateDefaultInterface("wlan0", 7, 1500)
	if len(observed) != 3 {
		t.Fatal("unregistered callback invoked")
	}
}

func TestInterfaceMonitorConcurrentReadsAndUpdates(t *testing.T) {
	m := &platformInterfaceMonitor{}
	var group sync.WaitGroup
	for worker := range 4 {
		group.Go(func() {
			for range 100 {
				m.UpdateDefaultInterface("wlan0", int32(worker+1), 1500)
				_ = m.DefaultInterface()
				e := m.RegisterCallback(func(*control.Interface, int) {})
				m.UnregisterCallback(e)
			}
		})
	}
	group.Wait()
}
