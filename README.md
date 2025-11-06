# NekoBox for Android

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![Releases](https://img.shields.io/github/v/release/MatsuriDayo/NekoBoxForAndroid)](https://github.com/MatsuriDayo/NekoBoxForAndroid/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

sing-box / universal proxy toolchain for Android.

一款使用 sing-box 的 Android 通用代理软件.

> 免责声明：本项目仅供个人学习和交流，请不要将其用于非法目的，请不要在生产环境中使用。请于下载后24小时内删除所有相关内容，勿用于长期存储或传播。
> 
> Disclaimer: This project is only for personal learning and communication, please do not use it for illegal purposes, please do not use it in a production environment. Please delete all related content within 24 hours after downloading and do not use it for long-term storage or dissemination.

## 下载 / Downloads

[![GitHub All Releases](https://img.shields.io/github/downloads/Matsuridayo/NekoBoxForAndroid/total?label=downloads-total&logo=github&style=flat-square)](https://github.com/starifly/NekoBoxForAndroid/releases)

[GitHub Releases 下载](https://github.com/starifly/NekoBoxForAndroid/releases)

**Google Play 版本自 2024 年 5 月起已被第三方控制，为非开源版本，请不要下载。**

**The Google Play version has been controlled by a third party since May 2024 and is a non-open
source version. Please do not download it.**

## 更新日志 & Telegram 发布频道 / Changelog & Telegram Channel

https://t.me/Matsuridayo

## 项目主页 & 文档 / Homepage & Documents

https://matsuridayo.github.io

## 支持的代理协议 / Supported Proxy Protocols

* SOCKS (4/4a/5)
* HTTP(S)
* SSH
* Shadowsocks
* VMess
* Trojan
* VLESS
* AnyTLS
* ShadowTLS
* TUIC
* Juicity
* Hysteria 1/2
* WireGuard
* Trojan-Go (trojan-go-plugin)
* NaïveProxy (naive-plugin)
* Mieru (mieru-plugin)

<details>
<summary>XHTTP Extra TLS配置示例</summary>

<pre><code class="language-json">
{
	"x_padding_bytes": "0-0",
	"sc_max_each_post_bytes": "0-0",
	"sc_min_posts_interval_ms": "0-0",
	"sc_stream_up_server_secs": "0-0",
	"xmux": {
		"max_concurrency": "0-1",
		"max_connections": "0-1",
		"c_max_reuse_times": "0-1",
		"h_max_request_times": "0-1",
		"h_max_reusable_secs": "0-1",
		"h_keep_alive_period": 60
	},
	"download": {
		"mode": "auto",
		"host": "b.yourdomain.com",
		"path": "/xhttp",
		"x_padding_bytes": "0-0",
		"sc_max_each_post_bytes": "0-0",
		"sc_min_posts_interval_ms": "0-0",
		"sc_stream_up_server_secs": "0-0",
		"xmux": {
			"max_concurrency": "0-1",
			"max_connections": "0-1",
			"c_max_reuse_times": "0-1",
			"h_max_request_times": "0-1",
			"h_max_reusable_secs": "0-1",
			"h_keep_alive_period": 60
		},
		"server": "$(ip_or_domain_of_your_cdn)",
		"server_port": 443,
		"tls": {
			"enabled": true,
			"server_name": "b.yourdomain.com",
			"alpn": "h2",
			"utls": {
				"enabled": true,
				"fingerprint": "chrome"
			}
		}
	}
}
</code></pre>
</details>

<details>
<summary>XHTTP Extra Reality配置示例</summary>

<pre><code class="language-json">
{
	"x_padding_bytes": "0-0",
	"sc_max_each_post_bytes": "0-0",
	"sc_min_posts_interval_ms": "0-0",
	"sc_stream_up_server_secs": "0-0",
	"xmux": {
		"max_concurrency": "0-1",
		"max_connections": "0-1",
		"c_max_reuse_times": "0-1",
		"h_max_request_times": "0-1",
		"h_max_reusable_secs": "0-1",
		"h_keep_alive_period": 60
	},
	"download": {
		"mode": "",
		"host": "example.com",
		"path": "/xhttp",
		"x_padding_bytes": "0-0",
		"sc_max_each_post_bytes": "0-0",
		"sc_min_posts_interval_ms": "0-0",
		"sc_stream_up_server_secs": "0-0",
		"xmux": {
			"max_concurrency": "0-1",
			"max_connections": "0-1",
			"c_max_reuse_times": "0-1",
			"h_max_request_times": "0-1",
			"h_max_reusable_secs": "0-1",
			"h_keep_alive_period": 60
		},
		"server": "$(ip_or_domain_of_your_cdn)",
		"server_port": 443,
		"tls": {
			"enabled": true,
			"server_name": "example.com",
			"reality": {
				"enabled": true,
				"public_key": "$(your_publicKey)",
				"short_id": "$(your_shortId)"
			},
			"utls": {
				"enabled": true,
				"fingerprint": "chrome"
			}
		}
	}
}
</code></pre>
</details>

请到[这里](https://matsuridayo.github.io/nb4a-plugin/)下载插件以获得完整的代理支持.

Please visit [here](https://matsuridayo.github.io/nb4a-plugin/) to download plugins for full proxy
supports.

## 支持的订阅格式 / Supported Subscription Format

* 一些广泛使用的格式 (如 Shadowsocks, ClashMeta 和 v2rayN)
* sing-box 出站

仅支持解析出站，即节点。分流规则等信息会被忽略。

* Some widely used formats (like Shadowsocks, ClashMeta and v2rayN)
* sing-box outbound

Only resolving outbound, i.e. nodes, is supported. Information such as diversion rules are ignored.

## 捐助 / Donate

<details>

如果这个项目对您有帮助, 可以通过捐赠的方式帮助我们维持这个项目.

捐赠满等额 50 USD 可以在「[捐赠榜](https://mtrdnt.pages.dev/donation_list)」显示头像, 如果您未被添加到这里,
欢迎联系我们补充.

Donations of 50 USD or more can display your avatar on
the [Donation List](https://mtrdnt.pages.dev/donation_list). If you are not added here, please
contact us to add it.

USDT TRC20

`TRhnA7SXE5Sap5gSG3ijxRmdYFiD4KRhPs`

XMR

`49bwESYQjoRL3xmvTcjZKHEKaiGywjLYVQJMUv79bXonGiyDCs8AzE3KiGW2ytTybBCpWJUvov8SjZZEGg66a4e59GXa6k5`

</details>

## Credits

Core:

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)

Android GUI:

- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet)

Web Dashboard:

- [Yacd-meta](https://github.com/MetaCubeX/Yacd-meta)
