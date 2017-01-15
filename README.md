# kiny

your super simple private serverless service running your kotlin functions like aws lambda

kiny is based on [vert.x](https://github.com/vert-x3) for great networking capabilities

# example

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

# warning

kiny is highly experimental