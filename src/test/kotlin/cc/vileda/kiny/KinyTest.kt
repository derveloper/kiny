package cc.vileda.kiny

import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.JsonObject
import org.hamcrest.CoreMatchers.`is`
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


class KinyTest {
    @Test
    fun shouldRun() {
        val fut = CompletableFuture<Void>()
        httpClient.get("/console") {
            Assert.assertThat(it.statusCode(), `is`(200))
            fut.complete(null)
        }.exceptionHandler {
            Assert.assertTrue(false)
            fut.complete(null)
        }.end()
        fut.get(1000, TimeUnit.MILLISECONDS)
    }

    @Test
    fun shouldCompileAndRun() {
        val fut = CompletableFuture<Void>()
        httpClient.post("/add") {
            Assert.assertThat(it.statusCode(), `is`(200))
            fut.complete(null)
        }.exceptionHandler {
            Assert.assertTrue(false)
            fut.complete(null)
        }.end(JsonObject().put("name", "test")
                .put("code", "import io.vertx.core.json.JsonObject;fun handle(request: JsonObject): JsonObject { " +
                        "return JsonObject().put(\"status\", 200).put(\"body\", \"hello world!!\")}")
                .encode())
        fut.get(20000, TimeUnit.MILLISECONDS)

        val fut2 = CompletableFuture<Void>()
        httpClient.get("/fn/test") {
            Assert.assertThat(it.statusCode(), `is`(200))
            it.bodyHandler {
                Assert.assertThat(it.toString(Charset.defaultCharset()), `is`("hello world!!"))
                fut2.complete(null)
            }
        }.exceptionHandler {
            Assert.assertTrue(false)
            fut2.complete(null)
        }.end()
        fut2.get(1000, TimeUnit.MILLISECONDS)
    }

    companion object {
        val vertx: Vertx = Vertx.vertx()
        val port = findRandomOpenPortOnAllLocalInterfaces()
        private val options: HttpClientOptions = HttpClientOptions()
                .setDefaultHost("localhost")
                .setDefaultPort(port)
        val httpClient: HttpClient = vertx.createHttpClient(options)

        @BeforeClass
        @JvmStatic
        fun before() {
            runKiny(vertx, port)
        }

        @AfterClass
        @JvmStatic
        fun after() {
            vertx.close()
        }

        @Throws(IOException::class)
        @JvmStatic
        private fun findRandomOpenPortOnAllLocalInterfaces(): Int {
            ServerSocket(0).use { socket ->
                return socket.localPort
            }
        }
    }
}