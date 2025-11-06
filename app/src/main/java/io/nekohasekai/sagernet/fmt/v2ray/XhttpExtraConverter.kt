package io.nekohasekai.sagernet.fmt.v2ray

import org.json.JSONObject

object XhttpExtraConverter {

    fun xrayToSingBox(xrayExtra: String): String {
        if (xrayExtra.isBlank()) return ""
        return try {
            val xray = JSONObject(xrayExtra)
            if (isSingBoxFormat(xray)) return xrayExtra
            val singBox = JSONObject()

            convertField(xray, singBox, "xPaddingBytes", "x_padding_bytes")
            convertField(xray, singBox, "scMaxEachPostBytes", "sc_max_each_post_bytes")
            convertField(xray, singBox, "scMinPostsIntervalMs", "sc_min_posts_interval_ms")
            convertField(xray, singBox, "noGRPCHeader", "no_grpc_header")

            if (xray.has("xmux")) {
                val xrayXmux = xray.getJSONObject("xmux")
                val singBoxXmux = JSONObject()
                convertField(xrayXmux, singBoxXmux, "maxConcurrency", "max_concurrency")
                convertField(xrayXmux, singBoxXmux, "maxConnections", "max_connections")
                convertField(xrayXmux, singBoxXmux, "cMaxReuseTimes", "c_max_reuse_times")
                convertField(xrayXmux, singBoxXmux, "hMaxRequestTimes", "h_max_request_times")
                convertField(xrayXmux, singBoxXmux, "hMaxReusableSecs", "h_max_reusable_secs")
                convertField(xrayXmux, singBoxXmux, "hKeepAlivePeriod", "h_keep_alive_period")
                if (singBoxXmux.length() > 0) singBox.put("xmux", singBoxXmux)
            }

            if (xray.has("downloadSettings")) {
                val xrayDown = xray.getJSONObject("downloadSettings")
                val singBoxDown = JSONObject()

                xrayDown.optJSONObject("xhttpSettings")?.let { xhttpSettings ->
                    convertField(xhttpSettings, singBoxDown, "mode", "mode")
                    convertField(xhttpSettings, singBoxDown, "host", "host")
                    convertField(xhttpSettings, singBoxDown, "path", "path")
                }
                convertField(xrayDown, singBoxDown, "address", "server")
                convertField(xrayDown, singBoxDown, "port", "server_port")

                if (xrayDown.has("security")) {
                    val tls = JSONObject().apply { put("enabled", true) }

                    when (xrayDown.getString("security")) {
                        "tls" -> {
                            xrayDown.optJSONObject("tlsSettings")?.let { tlsSettings ->
                                convertField(tlsSettings, tls, "serverName", "server_name")
                                convertField(tlsSettings, tls, "alpn", "alpn")
                                convertField(tlsSettings, tls, "allowInsecure", "insecure")
                                tlsSettings.optString("fingerprint")?.let { fp ->
                                    if (fp.isNotBlank()) {
                                        val utls = JSONObject().apply {
                                            put("enabled", true)
                                            put("fingerprint", fp)
                                        }
                                        tls.put("utls", utls)
                                    }
                                }
                            }
                        }
                        "reality" -> {
                            xrayDown.optJSONObject("realitySettings")?.let { realitySettings ->
                                convertField(realitySettings, tls, "serverName", "server_name")
                                val reality = JSONObject().apply {
                                    put("enabled", true)
                                    convertField(realitySettings, this, "publicKey", "public_key")
                                    convertField(realitySettings, this, "shortId", "short_id")
                                }
                                tls.put("reality", reality)
                                realitySettings.optString("fingerprint")?.let { fp ->
                                    if (fp.isNotBlank()) {
                                        val utls = JSONObject().apply {
                                            put("enabled", true)
                                            put("fingerprint", fp)
                                        }
                                        tls.put("utls", utls)
                                    }
                                }
                            }
                        }
                    }
                    singBoxDown.put("tls", tls)
                }

                xrayDown.optJSONObject("xhttpSettings")?.optJSONObject("extra")?.let { extra ->
                    if (extra.has("xmux")) {
                        val xrayXmux = extra.getJSONObject("xmux")
                        val downXmux = JSONObject()
                        convertField(xrayXmux, downXmux, "maxConcurrency", "max_concurrency")
                        convertField(xrayXmux, downXmux, "maxConnections", "max_connections")
                        convertField(xrayXmux, downXmux, "cMaxReuseTimes", "c_max_reuse_times")
                        convertField(xrayXmux, downXmux, "hMaxRequestTimes", "h_max_request_times")
                        convertField(xrayXmux, downXmux, "hMaxReusableSecs", "h_max_reusable_secs")
                        convertField(xrayXmux, downXmux, "hKeepAlivePeriod", "h_keep_alive_period")
                        if (downXmux.length() > 0) singBoxDown.put("xmux", downXmux)
                    }

                    convertField(extra, singBoxDown, "xPaddingBytes", "x_padding_bytes")
                    convertField(extra, singBoxDown, "scMaxEachPostBytes", "sc_max_each_post_bytes")
                    convertField(extra, singBoxDown, "scMinPostsIntervalMs", "sc_min_posts_interval_ms")
                    convertField(extra, singBoxDown, "noGRPCHeader", "no_grpc_header")
                }

                if (singBoxDown.length() > 0) singBox.put("download", singBoxDown)
            }

            singBox.toString(2).replace("\\/", "/")
        } catch (e: Exception) {
            e.printStackTrace()
            xrayExtra
        }
    }

    fun singBoxToXray(singBoxExtra: String): String {
        if (singBoxExtra.isBlank()) return ""
        return try {
            val singBox = JSONObject(singBoxExtra)
            if (isXrayFormat(singBox)) return singBoxExtra
            val xray = JSONObject()

            convertField(singBox, xray, "x_padding_bytes", "xPaddingBytes")
            convertField(singBox, xray, "sc_max_each_post_bytes", "scMaxEachPostBytes")
            convertField(singBox, xray, "sc_min_posts_interval_ms", "scMinPostsIntervalMs")
            convertField(singBox, xray, "no_grpc_header", "noGRPCHeader")

            if (singBox.has("xmux")) {
                val singBoxXmux = singBox.getJSONObject("xmux")
                val xrayXmux = JSONObject()
                convertField(singBoxXmux, xrayXmux, "max_concurrency", "maxConcurrency")
                convertField(singBoxXmux, xrayXmux, "max_connections", "maxConnections")
                convertField(singBoxXmux, xrayXmux, "c_max_reuse_times", "cMaxReuseTimes")
                convertField(singBoxXmux, xrayXmux, "h_max_request_times", "hMaxRequestTimes")
                convertField(singBoxXmux, xrayXmux, "h_max_reusable_secs", "hMaxReusableSecs")
                convertField(singBoxXmux, xrayXmux, "h_keep_alive_period", "hKeepAlivePeriod")
                if (xrayXmux.length() > 0) xray.put("xmux", xrayXmux)
            }

            if (singBox.has("download")) {
                val singBoxDown = singBox.getJSONObject("download")
                val xrayDown = JSONObject()

                convertField(singBoxDown, xrayDown, "server", "address")
                convertField(singBoxDown, xrayDown, "server_port", "port")
                xrayDown.put("network", "xhttp")

                if (singBoxDown.has("tls")) {
                    val tls = singBoxDown.getJSONObject("tls")

                    if (tls.has("reality") && tls.getJSONObject("reality").optBoolean("enabled", false)) {
                        xrayDown.put("security", "reality")
                        val reality = tls.getJSONObject("reality")
                        val realitySettings = JSONObject()
                        convertField(tls, realitySettings, "server_name", "serverName")
                        convertField(reality, realitySettings, "public_key", "publicKey")
                        convertField(reality, realitySettings, "short_id", "shortId")
                        if (tls.has("utls")) {
                            val utls = tls.getJSONObject("utls")
                            convertField(utls, realitySettings, "fingerprint", "fingerprint")
                        }
                        xrayDown.put("realitySettings", realitySettings)
                    } else {
                        xrayDown.put("security", "tls")
                        val tlsSettings = JSONObject()
                        convertField(tls, tlsSettings, "server_name", "serverName")
                        convertField(tls, tlsSettings, "alpn", "alpn")
                        convertField(tls, tlsSettings, "insecure", "allowInsecure")
                        if (tls.has("utls")) {
                            val utls = tls.getJSONObject("utls")
                            convertField(utls, tlsSettings, "fingerprint", "fingerprint")
                        }
                        xrayDown.put("tlsSettings", tlsSettings)
                    }
                }

                val xhttpSettings = JSONObject()
                convertField(singBoxDown, xhttpSettings, "mode", "mode")
                convertField(singBoxDown, xhttpSettings, "host", "host")
                convertField(singBoxDown, xhttpSettings, "path", "path")

                val xhttpExtra = JSONObject()
                convertField(singBoxDown, xhttpExtra, "x_padding_bytes", "xPaddingBytes")
                convertField(singBoxDown, xhttpExtra, "sc_max_each_post_bytes", "scMaxEachPostBytes")
                convertField(singBoxDown, xhttpExtra, "sc_min_posts_interval_ms", "scMinPostsIntervalMs")
                convertField(singBoxDown, xhttpExtra, "no_grpc_header", "noGRPCHeader")

                if (singBoxDown.has("xmux")) {
                    val singBoxDownXmux = singBoxDown.getJSONObject("xmux")
                    val xrayDownXmux = JSONObject()
                    convertField(singBoxDownXmux, xrayDownXmux, "max_concurrency", "maxConcurrency")
                    convertField(singBoxDownXmux, xrayDownXmux, "max_connections", "maxConnections")
                    convertField(singBoxDownXmux, xrayDownXmux, "c_max_reuse_times", "cMaxReuseTimes")
                    convertField(singBoxDownXmux, xrayDownXmux, "h_max_request_times", "hMaxRequestTimes")
                    convertField(singBoxDownXmux, xrayDownXmux, "h_max_reusable_secs", "hMaxReusableSecs")
                    convertField(singBoxDownXmux, xrayDownXmux, "h_keep_alive_period", "hKeepAlivePeriod")
                    if (xrayDownXmux.length() > 0) xhttpExtra.put("xmux", xrayDownXmux)
                }

                if (xhttpExtra.length() > 0) xhttpSettings.put("extra", xhttpExtra)
                xrayDown.put("xhttpSettings", xhttpSettings)

                if (xrayDown.length() > 0) xray.put("downloadSettings", xrayDown)
            }

            xray.toString(2).replace("\\/", "/")
        } catch (e: Exception) {
            e.printStackTrace()
            singBoxExtra
        }
    }

    private fun isSingBoxFormat(json: JSONObject): Boolean {
        return json.has("x_padding_bytes") || json.has("sc_max_each_post_bytes") ||
               json.has("sc_min_posts_interval_ms") || json.has("sc_stream_up_server_secs") ||
               json.has("download")
    }

    private fun isXrayFormat(json: JSONObject): Boolean {
        return json.has("xPaddingBytes") || json.has("scMaxEachPostBytes") ||
               json.has("scMinPostsIntervalMs") || json.has("scStreamUpServerSecs") ||
               json.has("downloadSettings")
    }

    private fun convertField(from: JSONObject, to: JSONObject, fromKey: String, toKey: String) {
        if (from.has(fromKey)) {
            to.put(toKey, from.get(fromKey))
        }
    }
}
