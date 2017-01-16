package cc.vileda.kiny

import io.vertx.core.AsyncResult
import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.StaticHandler
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
        try {
            val appDef = AppDef(name, json.getString("code"))
            apps.put(name, appDef)

            vertx.deployVerticle(EndpointVerticle(apps[name]!!)) {
                appDef.deploymentId = it.result()
                appDef.route = createRoute(appDef, eventBus, name, router)
                logger.info("deployed ${appDef.deploymentId}")
                logger.info("code(${appDef.name}) ${appDef.code}")
                fut.complete()
            }
        } catch (e: Exception) {
            fut.fail("Compilation error")
        }
    }, {
        if (it.succeeded()) ctx.response().end("Added")
        else ctx.response().setStatusCode(500).end(it.cause().message)
    })
}

private fun createRoute(appDef: AppDef, eventBus: EventBus, name: String, router: Router): Route? {
    return router.route("/fn/$name").handler {
        val innerCtx = it
        val request = JsonObject()
                .put("body", it.bodyAsString)
                .put("method", it.request().method().name)
                .put("headers", it.request().headers().map { JsonObject().put(it.key, it.value) })
                .put("params", it.request().params().map { "${it.key}=${it.value}" })
        sendRequest(appDef, eventBus, innerCtx, request)
    }
}

private fun sendRequest(appDef: AppDef, eventBus: EventBus, innerCtx: RoutingContext, request: JsonObject) {
    eventBus.send("trigger.request.${appDef.deploymentId}", request.encode(), { msg: AsyncResult<Message<JsonObject>> ->
        if (msg.succeeded()) {
            val response = msg.result().body()
            innerCtx.response().setStatusCode(response.getInteger("status")).end(response.getString("body"))
        } else {
            innerCtx.response().setStatusCode(500).end(msg.cause().message)
        }
    })
}