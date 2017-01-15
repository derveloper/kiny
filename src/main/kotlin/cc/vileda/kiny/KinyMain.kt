package cc.vileda.kiny

import com.intellij.openapi.util.Disposer
import io.vertx.core.*
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.StaticHandler
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.codegen.GeneratedClassLoader
import org.jetbrains.kotlin.config.CommonConfigurationKeys.MODULE_NAME
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.addKotlinSourceRoot
import org.jetbrains.kotlin.resolve.diagnostics.DiagnosticSuppressor
import org.jetbrains.kotlin.script.KotlinScriptDefinitionProvider
import org.jetbrains.kotlin.script.ScriptNameUtil
import org.jetbrains.kotlin.script.StandardScriptDefinition
import org.jetbrains.kotlin.util.ExtensionProvider
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

val logger: Logger = LoggerFactory.getLogger("cc.vileda.kiny")

fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    val createHttpServer = vertx.createHttpServer()
    val router = Router.router(vertx)

    val apps = ConcurrentHashMap<String, AppDef>()

    val bodyHandler = BodyHandler.create()
    val cookieHandler = CookieHandler.create()
    val staticHandler = StaticHandler.create()

    router.route().handler { bodyHandler.handle(it) }
    router.route().handler { cookieHandler.handle(it) }
    router.route("/console/*").handler { staticHandler.handle(it) }

    val eventBus = vertx.eventBus()

    router.post("/add").handler {
        val json = it.bodyAsJson
        val name = json.getString("name")
        val ctx = it

        if (apps.containsKey(name)) {
            apps[name]?.route?.remove()?.disable()
            vertx.undeploy(apps[name]?.deploymentId) {
                logger.info("undeployed ${apps[name]?.deploymentId}")
                apps.remove(name)
                createNewEndpoint(apps, ctx, eventBus, json, name, router, vertx)
            }
        } else {
            createNewEndpoint(apps, ctx, eventBus, json, name, router, vertx)
        }
    }

    createHttpServer.requestHandler { router.accept(it) }.listen(Integer.valueOf(System.getProperty("server.port", "9090")))
}

private fun createNewEndpoint(apps: ConcurrentHashMap<String, AppDef>, ctx: RoutingContext, eventBus: EventBus, json: JsonObject, name: String, router: Router, vertx: Vertx) {
    vertx.executeBlocking<Void>({ fut ->
        val appDef = AppDef(name, json.getString("code"))
        apps.put(name, appDef)

        vertx.deployVerticle(EndpointVerticle(apps[name]!!)) {
            appDef.deploymentId = it.result()
            appDef.route = router.route("/fn/$name").handler {
                val innerCtx = it
                val request = JsonObject()
                        .put("body", it.bodyAsString)
                        .put("method", it.request().method().name)
                        .put("headers", it.request().headers().map { JsonObject().put(it.key, it.value) })
                        .put("params", it.request().params().map { "${it.key}=${it.value}" })
                eventBus.send("trigger.request.${appDef.deploymentId}", request.encode(), Handler { msg: AsyncResult<Message<JsonObject>> ->
                    if (msg.succeeded()) {
                        val response = msg.result().body()
                        innerCtx.response().setStatusCode(response.getInteger("status")).end(response.getString("body"))
                    } else {
                        innerCtx.response().setStatusCode(500).end(msg.cause().message)
                    }
                })
            }
            fut.complete()
            logger.info("deployed ${appDef.deploymentId}")
            logger.info("code ${appDef.code}")
        }
    }, { _ ->
        ctx.response().end("Added")
    })
}

class EndpointVerticle(private val appDef: AppDef) : AbstractVerticle() {
    private var consumer: MessageConsumer<String>? = null

    override fun start() {
        val eventBus = vertx.eventBus()
        consumer = eventBus.consumer<String>("trigger.request.${deploymentID()}") {
            logger.info("(${deploymentID()}) received ${it.body()}")
            it.reply(appDef.invoke(JsonObject(it.body())))
        }
    }

    override fun stop(stopFuture: Future<Void>?) {
        logger.info("stopping ${deploymentID()}")
        consumer?.unregister { stopFuture?.complete() }
    }
}

data class AppDef(val name: String, val code: String) {
    private val path = Files.createTempFile("kinyscript-", ".kts").toString()
    private val clazz = compile(Files.write(Paths.get(path), code.toByteArray()).toString())
    val instance: Any = clazz.getConstructor(Array<String>::class.java).newInstance(arrayOf<String>())
    val method: Method = clazz.getMethod("handle", JsonObject::class.java)
    var deploymentId: String? = null
    var route: Route? = null

    fun invoke(param: JsonObject): JsonObject {
        return method.invoke(instance, param) as JsonObject
    }
}

fun compile(taskPath: String): Class<*> {
    val classpathEntries = System.getProperty("java.class.path").split(File.pathSeparator)
    val classpathEntries2 = System.getProperty("sun.boot.class.path").split(File.pathSeparator)

    val configuration = CompilerConfiguration().apply {
        put<MessageCollector>(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, false))
        addKotlinSourceRoot(taskPath)
        addJvmClasspathRoots(classpathEntries.map(::File).plus(classpathEntries2.map(::File)))
        addJvmClasspathRoot(PathUtil.getPathUtilJar())
        put(MODULE_NAME, "cc.vileda.kiny")
    }

    val disposable = Disposer.newDisposable()
    val environment = KotlinCoreEnvironment.createForProduction(disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
    KotlinScriptDefinitionProvider.getInstance(environment.project).addScriptDefinition(StandardScriptDefinition)
    ExtensionProvider.create(DiagnosticSuppressor.EP_NAME)

    val state = KotlinToJVMBytecodeCompiler.analyzeAndGenerate(environment)!!
    val nameForScript = ScriptNameUtil.generateNameByFileName(environment.getSourceFiles()[0].script!!.name!!, "kts")
    val classLoader = GeneratedClassLoader(state.factory, ClassLoader.getSystemClassLoader())
    return classLoader.loadClass(nameForScript)
}