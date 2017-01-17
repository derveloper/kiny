package cc.vileda.kiny

import com.intellij.openapi.util.Disposer
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