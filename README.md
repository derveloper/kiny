# kiny

your super simple private serverless service running your kotlin (1.1-M04) functions like aws lambda

kiny compiles your kotlin function on the fly and attaches it to a route

HTTP server and routing via [vert.x](https://github.com/vert-x3)

## example

### use gradle to build
```./gradlew assemble```

### run kiny
```
java -jar build/libs/kiny-1.0-SNAPSHOT.jar
```

### create your endpoint
by simply posting a JSON to ```/add```
```
curl -d '{
  "name": "bar",
  "code": "fun handle(context: io.vertx.ext.web.RoutingContext) { context.response().end(\"bar\") }"
}' http://localhost:9090/add
```

### use
use the created function, you can use all HTTP methods e.g. ```GET```
```
curl http://localhost:9090/bar
```

## warning

kiny is highly experimental