package libcore

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"reflect"
	"runtime"
	"runtime/debug"
	"strings"
	"time"
	"unsafe"

	"github.com/matsuridayo/sing-box-extra/boxbox"
	_ "github.com/matsuridayo/sing-box-extra/distro/all"

	"github.com/matsuridayo/libneko/neko_common"
	"github.com/matsuridayo/libneko/protect_server"
	"github.com/matsuridayo/libneko/speedtest"
	"github.com/matsuridayo/sing-box-extra/boxapi"

	"github.com/sagernet/sing-box/common/dialer/conntrack"
	sblog "github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/outbound"
)

var mainInstance *BoxInstance

func init() {
	neko_common.GetProxyHttpClient = func() *http.Client {
		if mainInstance == nil {
			return nil
		}
		return boxapi.GetProxyHttpClient(mainInstance.Box)
	}
}

func VersionBox() string {
	version := []string{
		"sing-box-extra: " + boxbox.Version(),
		runtime.Version() + "@" + runtime.GOOS + "/" + runtime.GOARCH,
	}

	var tags string
	debugInfo, loaded := debug.ReadBuildInfo()
	if loaded {
		for _, setting := range debugInfo.Settings {
			switch setting.Key {
			case "-tags":
				tags = setting.Value
			}
		}
	}

	if tags != "" {
		version = append(version, tags)
	}

	return strings.Join(version, "\n")
}

func ResetAllConnections(system bool) {
	if system {
		conntrack.Close()
	}
}

type BoxInstance struct {
	*boxbox.Box
	cancel context.CancelFunc
	state  int

	v2api    *boxapi.SbV2rayServer
	selector *outbound.Selector

	ForTest bool
}

func NewSingBoxInstance(config string) (b *BoxInstance, err error) {
	defer func() {
		if v := recover(); v != nil {
			err = fmt.Errorf("panic: %v", v)
		}
	}()

	// parse options
	var options option.Options
	err = options.UnmarshalJSON([]byte(config))
	if err != nil {
		return nil, fmt.Errorf("decode config: %v", err)
	}

	// create box
	ctx, cancel := context.WithCancel(context.Background())
	instance, err := boxbox.New(ctx, options, boxPlatformInterfaceInstance)
	if err != nil {
		cancel()
		return nil, fmt.Errorf("create service: %v", err)
	}

	b = &BoxInstance{
		Box:    instance,
		cancel: cancel,
	}

	// fuck your sing-box platformFormatter
	logFactory_ := reflect.Indirect(reflect.ValueOf(instance)).FieldByName("logFactory")
	logFactory_ = reflect.NewAt(logFactory_.Type(), unsafe.Pointer(logFactory_.UnsafeAddr())).Elem() // get unexported logFactory
	logFactory_ = logFactory_.Elem().Elem()                                                          // get struct
	platformFormatter_ := logFactory_.FieldByName("platformFormatter")
	platformFormatter_ = reflect.NewAt(platformFormatter_.Type(), unsafe.Pointer(platformFormatter_.UnsafeAddr())) // get unexported Formatter
	platformFormatter := platformFormatter_.Interface().(*sblog.Formatter)
	platformFormatter.DisableColors = true

	// selector
	if proxy, ok := b.Router().Outbound("proxy"); ok {
		if selector, ok := proxy.(*outbound.Selector); ok {
			b.selector = selector
		}
	}

	return b, nil
}

func (b *BoxInstance) Start() error {
	if b.state == 0 {
		b.state = 1
		return b.Box.Start()
	}
	return errors.New("already started")
}

func (b *BoxInstance) Close() error {
	// no double close
	if b.state == 2 {
		return nil
	}
	b.state = 2

	// clear main instance
	if mainInstance == b {
		mainInstance = nil
		goServeProtect(false)
	}

	// close box
	t := time.NewTimer(time.Second * 2)
	c := make(chan struct{}, 1)
	disableSingBoxLog = true

	go func(cancel context.CancelFunc, closer io.Closer) {
		cancel()
		closer.Close()
		c <- struct{}{}
		close(c)
	}(b.cancel, b.Box)

	select {
	case <-t.C:
		log.Println("[Warning] sing-box close takes longer than expected.")
	case <-c:
	}

	disableSingBoxLog = false
	t.Stop()
	return nil
}

func (b *BoxInstance) SetAsMain() {
	mainInstance = b
	goServeProtect(true)
}

func (b *BoxInstance) SetConnectionPoolEnabled(enable bool) {
	// TODO api
}

func (b *BoxInstance) SetV2rayStats(outbounds string) {
	b.v2api = boxapi.NewSbV2rayServer(option.V2RayStatsServiceOptions{
		Enabled:   true,
		Outbounds: strings.Split(outbounds, "\n"),
	})
	b.Box.Router().SetV2RayServer(b.v2api)
}

func (b *BoxInstance) QueryStats(tag, direct string) int64 {
	if b.v2api == nil {
		return 0
	}
	return b.v2api.QueryStats(fmt.Sprintf("outbound>>>%s>>>traffic>>>%s", tag, direct))
}

func (b *BoxInstance) SelectOutbound(tag string) bool {
	if b.selector != nil {
		var result = b.selector.SelectOutbound(tag)
		if result {
			ResetAllConnections(true)
		}
		return result
	}
	return false
}

func UrlTest(i *BoxInstance, link string, timeout int32) (int32, error) {
	if i == nil {
		// test current
		return speedtest.UrlTest(neko_common.GetProxyHttpClient(), link, timeout)
	}
	return speedtest.UrlTest(boxapi.GetProxyHttpClient(i.Box), link, timeout)
}

var protectCloser io.Closer

func goServeProtect(start bool) {
	if protectCloser != nil {
		protectCloser.Close()
		protectCloser = nil
	}
	if start {
		protectCloser = protect_server.ServeProtect("protect_path", false, 0, func(fd int) {
			intfBox.AutoDetectInterfaceControl(int32(fd))
		})
	}
}
