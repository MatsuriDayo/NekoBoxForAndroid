package device

import (
	"fmt"
	"runtime/debug"
)

var DebugFunc func(interface{})

func GoDebug(any interface{}) {
	if DebugFunc != nil {
		go DebugFunc(any)
	}
}

func AllDefer(name string, log func(string)) {
	if r := recover(); r != nil {
		s := fmt.Sprintln(name+" panic", r, string(debug.Stack()))
		log(s)
	}
}
