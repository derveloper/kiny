package cc.vileda.kiny

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.JsonObject

class EndpointVerticle(private val appDef: AppDef) : AbstractVerticle() {
    private var consumer: MessageConsumer<String>? = null

    override fun start() {
        val eventBus = vertx.eventBus()
        consumer = eventBus.consumer<String>("trigger.request.${deploymentID()}") {
            logger.info("(${deploymentID()}) received ${it.body()}")
            val message = it
            vertx.executeBlocking<JsonObject>({ fut ->
                try {
                    val invoke = appDef.invoke(JsonObject(message.body()))
                    fut.complete(invoke)
                } catch (e: Exception) {
                    fut.fail(JsonObject().put("error", "timeout").encode())
                }
            }, {
                if (it.succeeded()) message.reply(it.result())
                else message.reply(JsonObject(it.cause().message))
            })
        }
    }

    override fun stop(stopFuture: Future<Void>?) {
        logger.info("stopping ${deploymentID()}")
        consumer?.unregister { stopFuture?.complete() }
    }
}