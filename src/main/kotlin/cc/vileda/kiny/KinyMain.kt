package cc.vileda.kiny

import com.intellij.openapi.util.Disposer
import io.vertx.core.Vertx
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
    router.route("/console").handler { staticHandler.handle(it) }
    router.route("/console/*").handler { staticHandler.handle(it) }

    router.post("/add").handler {
        val json = it.bodyAsJson
        val name = json.getString("name")
        apps.put(name, AppDef(name, json.getString("code")))
        router.routes.removeIf { it.path == "/fn/$name" }
        router.route("/fn/$name").handler {
            val appDef = apps[name]
            appDef?.invoke(it)
        }
        it.response().end("Added")
    }

    createHttpServer.requestHandler { router.accept(it) }.listen(Integer.valueOf(System.getProperty("server.port", "9090")))
}

data class AppDef(val name: String, val code: String) {
    private val path = Files.createTempFile("kinyscript-", ".kts").toString()
    private val clazz = compile(Files.write(Paths.get(path), code.toByteArray()).toString())
    val instance: Any = clazz.getConstructor(Array<String>::class.java).newInstance(arrayOf<String>())
    val method: Method = clazz.getMethod("handle", RoutingContext::class.java)
    fun invoke(param: RoutingContext) {
        method.invoke(instance, param)
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