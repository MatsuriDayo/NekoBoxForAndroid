package libcore

import (
	"time"
)

var outdated string

func GetBuildTime() int64 {
	buildDate := "20230315"
	buildTime, _ := time.Parse("20060102", buildDate)
	return buildTime.Unix()
}

func GetExpireTime() int64 {
	buildTime := time.Unix(GetBuildTime(), 0)
	expireTime := buildTime.AddDate(0, 0, 7) // current force update: 6 months
	return expireTime.Unix()
}
