package cc.vileda.kiny

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Route
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Paths

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