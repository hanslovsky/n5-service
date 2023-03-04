package com.hanslovsky.n5.service

import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.scijava.Context
import org.scijava.Priority
import org.scijava.annotations.Indexable
import org.scijava.plugin.*
import org.scijava.service.AbstractService
import org.scijava.service.SciJavaService
import org.scijava.service.Service


data class N5ReaaderWithUrl(val url: String, private val reader: N5Reader): N5Reader by reader
data class N5WriterWithUrl(val url: String, private val writer: N5Writer): N5Writer by writer

@Plugin(type = Service::class)
class N5Service @JvmOverloads constructor(pluginService: PluginService? = null): AbstractService(), SciJavaService {


    @Parameter
    private lateinit var pluginService: PluginService

    private lateinit var readerPlugins: Map<String, List<Pair<PluginInfo<N5ReaderPlugin>, N5ReaderPlugin>>>
    private lateinit var writerPlugins: Map<String, List<Pair<PluginInfo<N5WriterPlugin>, N5WriterPlugin>>>

    init {
        pluginService?.let { this.pluginService = it }
    }

    fun getReader(url: String): N5ReaaderWithUrl {
        if (!::readerPlugins.isInitialized) {
            readerPlugins = pluginService.findPlugins()
        }
        return readerPlugins
                .getForUrl(url)
                .let { (urlWithoutProtocol, plugin) -> plugin.getReader(urlWithoutProtocol, url) }
    }

    fun getWriter(url: String): N5WriterWithUrl {
        if (!::writerPlugins.isInitialized) {
            writerPlugins = pluginService.findPlugins()
        }
        return writerPlugins
                .getForUrl(url)
                .let { (urlWithoutProtocol, plugin) -> plugin.getWriter(urlWithoutProtocol, url) }
    }

    companion object {
        private inline fun <reified P: SciJavaPlugin> PluginService.findPlugins(): Map<String, List<Pair<PluginInfo<P>, P>>> {
            val infos = getPluginsOfType(P::class.java)
            val sortedGroupedInfos = infos
                    .flatMap { it.annotation.attrs.map { a -> a.value to it } }
                    .map { it.first to it.second }
                    .groupBy({ it.first }) { it.second }
                    .mapValues { it.value.sortedBy { p -> -p.priority }.map { p -> p to p.createInstance() } }
            return sortedGroupedInfos
        }
        private fun <T: SciJavaPlugin> Map<String, List<Pair<PluginInfo<T>, T>>>.getForUrl(url: String): Pair<String, T> {
            val split = url.split("://", limit = 2)
            val (protocol, urlWithoutProtocol) = when(split.size) {
                2 -> split
                else -> listOf("", split[0])
            }
            val t =this[protocol]
                    ?.firstOrNull()
                    ?.second
//                    ?.get(urlWithoutProtocol, url)
                    ?: error("Unsupported protocol $protocol in $url")
            return urlWithoutProtocol to t
        }
    }

}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Indexable
annotation class N5ServicePlugin(val plugin: Plugin, val protocols: Array<String>)

interface N5ReaderPlugin : SciJavaPlugin {
    fun getReader(url: String): N5Reader
    fun getReader(urlWithoutProtocol: String, url: String) = N5ReaaderWithUrl(url, getReader(urlWithoutProtocol))
}

interface N5WriterPlugin : SciJavaPlugin {
    fun getWriter(url: String): N5Writer
    fun getWriter(urlWithoutProtocol: String, url: String) = N5WriterWithUrl(url, getWriter(urlWithoutProtocol))
}

interface N5Plugin : N5ReaderPlugin, N5WriterPlugin


// @N5ServicePlugin(plugin = Plugin(type = N5Plugin::class), protocols = ["fs", "", "file"])
// TODO abusing Attr fields for supported protocols
@Plugin(type = N5Plugin::class, priority = Priority.NORMAL, attrs = [Attr("n5", name=""), Attr("", name=""), Attr("file", name="")])
class DefaultN5Plugin : N5Plugin {
    override fun getReader(url: String) = N5FSReader(url)
    override fun getWriter(url: String) = N5FSWriter(url)
}

fun main(args: Array<String>) {
    Context(PluginService::class.java).use { context ->
        val n5Service = N5Service(context.getService(PluginService::class.java))
        println("reader: ${n5Service.getReader("n5:///bla")}")
        println("reader: ${n5Service.getReader("file:///bla")}")
        println("reader: ${n5Service.getReader("/bla")}")
        println("reader: ${n5Service.getWriter("n5://bla")}")
        println("reader: ${n5Service.getWriter("file://bla")}")
        println("reader: ${n5Service.getWriter("bla")}")
        println("reader: ${n5Service.getWriter("grpc://localhost:9090")}")
    }
}