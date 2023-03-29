package libcore

import (
	"bufio"
	"bytes"
	"crypto/sha256"
	"fmt"
	"libcore/device"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"
	_ "unsafe"

	"log"

	"github.com/avast/apkverifier"
	"github.com/matsuridayo/libneko/neko_common"
	"github.com/matsuridayo/libneko/neko_log"
)

//go:linkname resourcePaths github.com/sagernet/sing-box/constant.resourcePaths
var resourcePaths []string

func NekoLogPrintln(s string) {
	log.Println(s)
}

func NekoLogClear() {
	neko_log.LogWriter.Truncate()
}

func ForceGc() {
	go runtime.GC()
}

func SetLocalResolver(lr LocalResolver) {
	underlyingResolver.localResolver = lr
}

func initCoreDefer() {
	device.AllDefer("InitCore", func(s string) { log.Println(s) })
}

func InitCore(process, cachePath, internalAssets, externalAssets string,
	maxLogSizeKb int32, logEnable bool,
	iif NB4AInterface,
) {
	defer initCoreDefer()
	isBgProcess := strings.HasSuffix(process, ":bg")

	neko_common.RunMode = neko_common.RunMode_NekoBoxForAndroid
	intfNB4A = iif

	// Working dir
	tmp := filepath.Join(cachePath, "../no_backup")
	os.MkdirAll(tmp, 0755)
	os.Chdir(tmp)

	// sing-box fs
	resourcePaths = append(resourcePaths, externalAssets)

	// Set up log
	if maxLogSizeKb < 50 {
		maxLogSizeKb = 50
	}
	neko_log.LogWriterDisable = !logEnable
	// neko_log.NB4AGuiLogWriter = iif.(io.Writer)
	neko_log.SetupLog(int(maxLogSizeKb)*1024, filepath.Join(cachePath, "neko.log"))

	// Set up some component
	go func() {
		defer initCoreDefer()
		device.GoDebug(process)

		externalAssetsPath = externalAssets
		internalAssetsPath = internalAssets

		if time.Now().Unix() >= GetExpireTime() {
			outdated = "Your version is too old! Please update!! 版本太旧，请升级！"
		}

		// bg
		if isBgProcess {
			go verifyAPK()
			extractAssets()
		}
	}()
}

var apkSignerSHA256 = [][]byte{
	{0x35, 0x76, 0x27, 0x58, 0xce, 0x86, 0xa6, 0xec, 0x29, 0x7d, 0x9c, 0xca, 0xc6, 0x89, 0x46, 0x9b, 0xc4, 0x3b, 0x9f, 0xed, 0x8a, 0xe1, 0xb2, 0x7f, 0x10, 0x0a, 0x86, 0xbb, 0xac, 0x00, 0xa0, 0x55},
}

func verifyAPK() {
	var apkPath string
	f, err := os.Open("/proc/self/maps")
	if err != nil {
		outdated = fmt.Sprintf("verifyAPK: open maps: %v", err)
		return
	}
	defer f.Close()
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := sc.Text()
		if strings.HasSuffix(line, "/base.apk") {
			apkPath = line[strings.Index(line, "/data/"):]
			break
		}
	}
	//
	certs, err := apkverifier.ExtractCerts(apkPath, nil)
	if certs == nil || err != nil {
		outdated = fmt.Sprintf("verifyAPK: no certificate: %v", err)
		return
	}

	var ok = false
	for _, cert := range certs {
		for _, c := range cert {
			var s = sha256.Sum256(c.Raw)
			if isGoodSigner(s[:]) {
				ok = true
				break
			}
		}
	}

	if !ok {
		outdated = fmt.Sprintf("verifyAPK: unknown signer")
	}
}

func isGoodSigner(sha256 []byte) bool {
	for _, hash := range apkSignerSHA256 {
		if bytes.Equal(sha256, hash) {
			return true
		}
	}
	return false
}
