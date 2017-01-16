package cc.vileda.kiny

import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CommonConfigurationKeys.MODULE_NAME
import org.jetbrains.kotlin.config.addKotlinSourceRoot
import java.io.File

fun compile(taskPath: String): Class<*> {
    val classpathEntries = System.getProperty("java.class.path").split(java.io.File.pathSeparator)
    val classpathEntries2 = System.getProperty("sun.boot.class.path").split(java.io.File.pathSeparator)

    val configuration = org.jetbrains.kotlin.config.CompilerConfiguration().apply {
        put<org.jetbrains.kotlin.cli.common.messages.MessageCollector>(org.jetbrains.kotlin.cli.common.CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector(System.err, org.jetbrains.kotlin.cli.common.messages.MessageRenderer.PLAIN_RELATIVE_PATHS, false))
        addKotlinSourceRoot(taskPath)
        addJvmClasspathRoots(classpathEntries.map(::File).plus(classpathEntries2.map(::File)))
        addJvmClasspathRoot(org.jetbrains.kotlin.utils.PathUtil.getPathUtilJar())
        put(MODULE_NAME, "cc.vileda.kiny")
    }

    val disposable = com.intellij.openapi.util.Disposer.newDisposable()
    val environment = org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment.Companion.createForProduction(disposable, configuration, org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles.JVM_CONFIG_FILES)
    org.jetbrains.kotlin.script.KotlinScriptDefinitionProvider.Companion.getInstance(environment.project).addScriptDefinition(org.jetbrains.kotlin.script.StandardScriptDefinition)
    org.jetbrains.kotlin.util.ExtensionProvider.Companion.create(org.jetbrains.kotlin.resolve.diagnostics.DiagnosticSuppressor.Companion.EP_NAME)

    val state = org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler.analyzeAndGenerate(environment)!!
    val nameForScript = org.jetbrains.kotlin.script.ScriptNameUtil.generateNameByFileName(environment.getSourceFiles()[0].script!!.name!!, "kts")
    val classLoader = org.jetbrains.kotlin.codegen.GeneratedClassLoader(state.factory, ClassLoader.getSystemClassLoader())
    return classLoader.loadClass(nameForScript)
}