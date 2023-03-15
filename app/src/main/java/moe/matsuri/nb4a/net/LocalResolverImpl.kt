package moe.matsuri.nb4a.net

import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.ktx.tryResume
import io.nekohasekai.sagernet.ktx.tryResumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import kotlin.coroutines.suspendCoroutine

object LocalResolverImpl : libcore.LocalResolver {

    override fun lookupIP(network: String, domain: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return runBlocking {
                suspendCoroutine { continuation ->
                    val signal = CancellationSignal()
                    val callback = object : DnsResolver.Callback<Collection<InetAddress>> {
                        @Suppress("ThrowableNotThrown")
                        override fun onAnswer(answer: Collection<InetAddress>, rcode: Int) {
                            // libcore/v2ray.go
                            when {
                                answer.isNotEmpty() -> {
                                    continuation.tryResume((answer as Collection<InetAddress?>).mapNotNull { it?.hostAddress }
                                        .joinToString(","))
                                }
                                rcode == 0 -> {
                                    // fuck AAAA no record
                                    // features/dns/client.go
                                    continuation.tryResume("")
                                }
                                else -> {
                                    // Need return rcode
                                    // proxy/dns/dns.go
                                    continuation.tryResumeWithException(Exception("$rcode"))
                                }
                            }
                        }

                        override fun onError(error: DnsResolver.DnsException) {
                            continuation.tryResumeWithException(error)
                        }
                    }
                    val type = when {
                        network.endsWith("4") -> DnsResolver.TYPE_A
                        network.endsWith("6") -> DnsResolver.TYPE_AAAA
                        else -> null
                    }
                    if (type != null) {
                        DnsResolver.getInstance().query(
                            SagerNet.underlyingNetwork,
                            domain,
                            type,
                            DnsResolver.FLAG_EMPTY,
                            Dispatchers.IO.asExecutor(),
                            signal,
                            callback
                        )
                    } else {
                        DnsResolver.getInstance().query(
                            SagerNet.underlyingNetwork,
                            domain,
                            DnsResolver.FLAG_EMPTY,
                            Dispatchers.IO.asExecutor(),
                            signal,
                            callback
                        )
                    }
                }
            }
        } else {
            throw Exception("114514")
        }
    }

}