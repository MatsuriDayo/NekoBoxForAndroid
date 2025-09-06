//go:build android && cgo

package libcore

/*
#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
#include <dlfcn.h>

typedef int (*android_res_nsend_t)(uint64_t network, const uint8_t* msg, size_t msglen, int flags);
typedef int (*android_res_nresult_t)(int fd, int* rcode, uint8_t* resp, size_t resp_len);

static int call_android_res_nsend(void* sym, uint64_t network, const uint8_t* msg, size_t msglen, int flags) {
    android_res_nsend_t f = (android_res_nsend_t)sym;
    if (!f) return -1;
    return f(network, msg, msglen, flags);
}

static int call_android_res_nresult(void* sym, int fd, int* rcode, uint8_t* resp, size_t resp_len) {
    android_res_nresult_t f = (android_res_nresult_t)sym;
    if (!f) return -1;
    return f(fd, rcode, resp, resp_len);
}
*/
import "C"

import (
	"context"
	"errors"
	"os"
	"unsafe"

	"golang.org/x/sys/unix"
)

func init() {
	libname := C.CString("libandroid.so")
	defer C.free(unsafe.Pointer(libname))

	libHandle := C.dlopen(libname, C.int(C.RTLD_NOW))
	if libHandle == nil {
		return
	}

	symNameSend := C.CString("android_res_nsend")
	defer C.free(unsafe.Pointer(symNameSend))
	androidResNSendSym := C.dlsym(libHandle, symNameSend)
	if androidResNSendSym == nil {
		return
	}

	symNameResult := C.CString("android_res_nresult")
	defer C.free(unsafe.Pointer(symNameResult))
	androidResNResultSym := C.dlsym(libHandle, symNameResult)
	if androidResNResultSym == nil {
		return
	}

	callAndroidResNSend := func(network uint64, msg []byte) (int, error) {
		if len(msg) == 0 {
			return 0, errors.New("empty payload")
		}
		msgPtr := (*C.uint8_t)(unsafe.Pointer(&msg[0]))
		msgLen := C.size_t(len(msg))
		ret := C.call_android_res_nsend(androidResNSendSym, C.uint64_t(network), msgPtr, msgLen, C.int(0))
		return int(ret), nil
	}

	callAndroidResNResult := func(fd int, resp []byte) (int, int) {
		if len(resp) == 0 {
			return 0, 0
		}
		respPtr := (*C.uint8_t)(unsafe.Pointer(&resp[0]))
		respLen := C.size_t(len(resp))
		var rcode C.int
		n := C.call_android_res_nresult(androidResNResultSym, C.int(fd), &rcode, respPtr, respLen)
		return int(rcode), int(n)
	}

	// set rawQueryFunc
	rawQueryFunc = func(networkHandle int64, request []byte) ([]byte, error) {
		fd, err := callAndroidResNSend(uint64(networkHandle), request)
		if err != nil {
			return nil, err
		}
		if fd < 0 {
			return nil, unix.Errno(-fd)
		}

		// wait for response (timeout 5000 ms)
		pfds := []unix.PollFd{{Fd: int32(fd), Events: unix.POLLIN | unix.POLLERR}}
		nReady, err := unix.Poll(pfds, 5000)
		if err != nil {
			unix.Close(fd)
			return nil, err
		}
		if nReady == 0 {
			unix.Close(fd)
			return nil, context.DeadlineExceeded
		}

		// read response into buffer
		response := make([]byte, 8192)
		_, n := callAndroidResNResult(fd, response)
		if n < 0 {
			return nil, unix.Errno(-n)
		}
		if n == 0 {
			return nil, os.ErrInvalid
		}
		return response[:n], nil
	}
}
