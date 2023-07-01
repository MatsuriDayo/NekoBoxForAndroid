package moe.matsuri.nb4a.net

import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import android.system.ErrnoException
import androidx.annotation.RequiresApi
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.ktx.tryResume
import io.nekohasekai.sagernet.ktx.tryResumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import libcore.ExchangeContext
import libcore.LocalDNSTransport
import libcore.LocalResolver
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object LocalResolverImpl : LocalResolver, LocalDNSTransport {

    // old

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

    // new local

    private const val RCODE_NXDOMAIN = 3

    override fun raw(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun exchange(ctx: ExchangeContext, message: ByteArray) {
        return runBlocking {
            suspendCoroutine { continuation ->
                val signal = CancellationSignal()
                ctx.onCancel(signal::cancel)
                val callback = object : DnsResolver.Callback<ByteArray> {
                    override fun onAnswer(answer: ByteArray, rcode: Int) {
                        if (rcode == 0) {
                            ctx.rawSuccess(answer)
                        } else {
                            ctx.errorCode(rcode)
                        }
                        continuation.resume(Unit)
                    }

                    override fun onError(error: DnsResolver.DnsException) {
                        when (val cause = error.cause) {
                            is ErrnoException -> {
                                ctx.errnoCode(cause.errno)
                                continuation.resume(Unit)
                                return
                            }
                        }
                        continuation.tryResumeWithException(error)
                    }
                }
                DnsResolver.getInstance().rawQuery(
                    SagerNet.underlyingNetwork,
                    message,
                    DnsResolver.FLAG_NO_RETRY,
                    Dispatchers.IO.asExecutor(),
                    signal,
                    callback
                )
            }
        }
    }

    override fun lookup(ctx: ExchangeContext, network: String, domain: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return runBlocking {
                suspendCoroutine { continuation ->
                    val signal = CancellationSignal()
                    ctx.onCancel(signal::cancel)
                    val callback = object : DnsResolver.Callback<Collection<InetAddress>> {
                        override fun onAnswer(answer: Collection<InetAddress>, rcode: Int) {
                            if (rcode == 0) {
                                ctx.success((answer as Collection<InetAddress?>).mapNotNull { it?.hostAddress }
                                    .joinToString("\n"))
                            } else {
                                ctx.errorCode(rcode)
                            }
                            continuation.resume(Unit)
                        }

                        override fun onError(error: DnsResolver.DnsException) {
                            when (val cause = error.cause) {
                                is ErrnoException -> {
                                    ctx.errnoCode(cause.errno)
                                    continuation.resume(Unit)
                                    return
                                }
                            }
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
                            DnsResolver.FLAG_NO_RETRY,
                            Dispatchers.IO.asExecutor(),
                            signal,
                            callback
                        )
                    } else {
                        DnsResolver.getInstance().query(
                            SagerNet.underlyingNetwork,
                            domain,
                            DnsResolver.FLAG_NO_RETRY,
                            Dispatchers.IO.asExecutor(),
                            signal,
                            callback
                        )
                    }
                }
            }
        } else {
            val underlyingNetwork =
                SagerNet.underlyingNetwork ?: error("upstream network not found")
            val answer = try {
                underlyingNetwork.getAllByName(domain)
            } catch (e: UnknownHostException) {
                ctx.errorCode(RCODE_NXDOMAIN)
                return
            }
            ctx.success(answer.mapNotNull { it.hostAddress }.joinToString("\n"))
        }
    }


}