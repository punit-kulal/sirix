package org.sirix.rest

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.ext.auth.oauth2.OAuth2FlowType
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.impl.HttpStatusException
import io.vertx.kotlin.core.http.httpServerOptionsOf
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.ext.auth.authenticateAwait
import io.vertx.kotlin.ext.auth.oauth2.oAuth2ClientOptionsOf
import io.vertx.kotlin.ext.auth.oauth2.providers.KeycloakAuth
import kotlinx.coroutines.launch
import org.apache.http.HttpStatus
import org.sirix.rest.crud.CreateMultipleResources
import org.sirix.rest.crud.Delete
import org.sirix.rest.crud.json.*
import org.sirix.rest.crud.xml.*
import java.nio.file.Paths
import java.util.*


class SirixVerticle : CoroutineVerticle() {
    /** User home directory. */
    private val userHome = System.getProperty("user.home")

    /** Storage for databases: Sirix data in home directory. */
    private val location = Paths.get(userHome, "sirix-data")

    override suspend fun start() {
        val router = createRouter()

        // Start an HTTP/2 server
        val server = vertx.createHttpServer(
            httpServerOptionsOf()
                .setSsl(true)
                .setUseAlpn(true)
                .setPemKeyCertOptions(
                    PemKeyCertOptions().setKeyPath(location.resolve("key.pem").toString())
                        .setCertPath(
                            location.resolve("cert.pem").toString()
                        )
                )
        )

        server.requestHandler { router.handle(it) }
            .listenAwait(config.getInteger("https.port", 9443))
    }

    private suspend fun createRouter() = Router.router(vertx).apply {

        val oauth2Config = oAuth2ClientOptionsOf()
            .setFlow(OAuth2FlowType.valueOf(config.getString("oAuthFlowType", "PASSWORD")))
            .setSite(config.getString("keycloak.url"))
            .setClientID("sirix")
            .setClientSecret(config.getString("client.secret"))
            .setTokenPath(config.getString("token.path", "/token"))
            .setAuthorizationPath(config.getString("auth.path", "/user/authorize"))

        val keycloak = KeycloakAuth.discoverAwait(
            vertx, oauth2Config
        )

        if (oauth2Config.flow == OAuth2FlowType.AUTH_CODE) {
            val allowedHeaders = HashSet<String>()
            allowedHeaders.add("x-requested-with")
            allowedHeaders.add("Access-Control-Allow-Origin")
            allowedHeaders.add("origin")
            allowedHeaders.add("Content-Type")
            allowedHeaders.add("accept")
            allowedHeaders.add("X-PINGARUNER")
            allowedHeaders.add("Authorization")
            allowedHeaders.add("authorization")

            val allowedMethods = HashSet<HttpMethod>()
            allowedMethods.add(HttpMethod.GET)
            allowedMethods.add(HttpMethod.POST)
            allowedMethods.add(HttpMethod.OPTIONS)

            allowedMethods.add(HttpMethod.DELETE)
            allowedMethods.add(HttpMethod.PATCH)
            allowedMethods.add(HttpMethod.PUT)

            this.route().handler(
                CorsHandler.create(
                    config.getString(
                        "cors.allowedOriginPattern",
                        "*"
                    )
                ).allowedHeaders(allowedHeaders).allowedMethods(allowedMethods).allowCredentials(
                    config.getBoolean("cors.allowCredentials", false)
                )
            )
        }

        get("/user/authorize").coroutineHandler { rc ->
            if (oauth2Config.flow != OAuth2FlowType.AUTH_CODE) {
                rc.response().statusCode = HttpStatus.SC_BAD_REQUEST
            } else {
                val redirectUri =
                    rc.queryParam("redirect_uri").getOrElse(0) { config.getString("redirect.uri") }
                val state = rc.queryParam("state").getOrElse(0) { java.util.UUID.randomUUID().toString() }

                val authorizationUri = keycloak.authorizeURL(
                    JsonObject()
                        .put("redirect_uri", redirectUri)
                        .put("state", state)
                )
                rc.response().putHeader("Location", authorizationUri)
                    .setStatusCode(HttpStatus.SC_MOVED_TEMPORARILY)
                    .end()
            }
        }

        post("/token").handler(BodyHandler.create()).coroutineHandler { rc ->
            try {
                val dataToAuthenticate: JsonObject =
                    when (rc.request().getHeader(HttpHeaders.CONTENT_TYPE)) {
                        "application/json" -> rc.bodyAsJson
                        "application/x-www-form-urlencoded" -> formToJson(rc)
                        else -> rc.bodyAsJson
                    }

                val user = keycloak.authenticateAwait(dataToAuthenticate)
                rc.response().end(user.principal().toString())
            } catch (e: DecodeException) {
                rc.fail(
                    HttpStatusException(
                        HttpResponseStatus.INTERNAL_SERVER_ERROR.code(),
                        "\"application/json\" and \"application/x-www-form-urlencoded\" are supported Content-Types." +
                                "If none is specified it's tried to parse as JSON"
                    )
                )
            }
        }

        // Create.
        put("/:database").consumes("application/xml").coroutineHandler {
            Auth(keycloak, AuthRole.CREATE).handle(it)
            it.next()
        }.handler(BodyHandler.create()).coroutineHandler {
            XmlCreate(location, false).handle(it)
        }
        put("/:database").consumes("application/json").coroutineHandler {
            Auth(keycloak, AuthRole.CREATE).handle(it)
            it.next()
        }.handler(BodyHandler.create()).coroutineHandler {
            JsonCreate(location, false).handle(it)
        }

        put("/:database/:resource").consumes("application/xml").coroutineHandler {
            Auth(keycloak, AuthRole.CREATE).handle(it)
            it.next()
        }.handler(BodyHandler.create()).coroutineHandler {
            XmlCreate(location, false).handle(it)
        }
        put("/:database/:resource").consumes("application/json").coroutineHandler {
            Auth(keycloak, AuthRole.CREATE).handle(it)
            it.next()
        }.handler(BodyHandler.create()).coroutineHandler {
            JsonCreate(location, false).handle(it)
        }

        post("/:database").consumes("multipart/form-data").coroutineHandler {
            Auth(keycloak, AuthRole.CREATE).handle(it)
            it.next()
        }.handler(BodyHandler.create()).coroutineHandler {
            CreateMultipleResources(location).handle(it)
        }

        // Update.
        post("/:database/:resource")
            .consumes("application/xml")
            .produces("application/xml")
            .coroutineHandler {
                Auth(keycloak, AuthRole.MODIFY).handle(it)
                it.next()
            }.handler(BodyHandler.create()).coroutineHandler {
                XmlUpdate(location).handle(it)
            }
        post("/:database/:resource")
            .consumes("application/json")
            .produces("application/json")
            .coroutineHandler {
                Auth(keycloak, AuthRole.MODIFY).handle(it)
                it.next()
            }.handler(BodyHandler.create()).coroutineHandler {
                JsonUpdate(location).handle(it)
            }

        // Get.
        get("/").produces("application/xml").coroutineHandler {
            Auth(keycloak, AuthRole.VIEW).handle(it)
            it.next()
        }.coroutineHandler {
            XmlGet(location).handle(it)
        }
        get("/").produces("application/json").coroutineHandler {
            Auth(keycloak, AuthRole.VIEW).handle(it)
            it.next()
        }.coroutineHandler {
            JsonGet(location).handle(it)
        }

        head("/:database/:resource").produces("application/xml").coroutineHandler {
            Auth(keycloak, AuthRole.VIEW).handle(it)
            it.next()
        }.coroutineHandler {
            XmlHead(location).handle(it)
        }
        get("/:database/:resource").produces("application/xml").coroutineHandler {
            Auth(keycloak, AuthRole.VIEW).handle(it)
            it.next()
        }.coroutineHandler {
            XmlGet(location).handle(it)
        }
        head("/:database/:resource").produces("application/json").coroutineHandler {
            Auth(keycloak, AuthRole.VIEW).handle(it)
            it.next()
        }.coroutineHandler {
            JsonHead(location).handle(it)
        }
        get("/:database/:resource").produces("application/json").coroutineHandler {
            Auth(keycloak, AuthRole.VIEW).handle(it)
            it.next()
        }.coroutineHandler {
            JsonGet(location).handle(it)
        }

        get("/:database").produces("application/xml").coroutineHandler {
            Auth(keycloak, AuthRole.VIEW).handle(it)
            it.next()
        }.coroutineHandler {
            XmlGet(location).handle(it)
        }
        get("/:database").produces("application/json").coroutineHandler {
            Auth(keycloak, AuthRole.VIEW).handle(it)
            it.next()
        }.coroutineHandler {
            JsonGet(location).handle(it)
        }

        post("/")
            .consumes("application/xml")
            .produces("application/xml")
            .coroutineHandler {
                Auth(keycloak, AuthRole.VIEW).handle(it)
                it.next()
            }.handler(BodyHandler.create()).coroutineHandler {
                XmlGet(location).handle(it)
            }
        post("/")
            .consumes("application/json")
            .produces("application/json")
            .coroutineHandler {
                Auth(keycloak, AuthRole.VIEW).handle(it)
                it.next()
            }.handler(BodyHandler.create()).coroutineHandler {
                JsonGet(location).handle(it)
            }
        post("/:database/:resource")
            .consumes("application/xml")
            .produces("application/xml")
            .coroutineHandler {
                Auth(keycloak, AuthRole.VIEW).handle(it)
                it.next()
            }.handler(BodyHandler.create()).coroutineHandler {
                XmlGet(location).handle(it)
            }
        post("/:database/:resource")
            .consumes("application/json")
            .produces("application/json")
            .coroutineHandler {
                Auth(keycloak, AuthRole.VIEW).handle(it)
                it.next()
            }.handler(BodyHandler.create()).coroutineHandler {
                JsonGet(location).handle(it)
            }

        // Delete.
        delete("/:database/:resource").consumes("application/xml").coroutineHandler {
            Auth(keycloak, AuthRole.DELETE).handle(it)
            it.next()
        }.coroutineHandler {
            XmlDelete(location).handle(it)
        }
        delete("/:database/:resource").consumes("application/json").coroutineHandler {
            Auth(keycloak, AuthRole.DELETE).handle(it)
            it.next()
        }.coroutineHandler {
            JsonDelete(location).handle(it)
        }

        delete("/:database").consumes("application/xml").coroutineHandler {
            Auth(keycloak, AuthRole.DELETE).handle(it)
            it.next()
        }.coroutineHandler {
            XmlDelete(location).handle(it)
        }
        delete("/:database").consumes("application/json").coroutineHandler {
            Auth(keycloak, AuthRole.DELETE).handle(it)
            it.next()
        }.coroutineHandler {
            JsonDelete(location).handle(it)
        }
        delete("/").coroutineHandler {
            Auth(keycloak, AuthRole.DELETE).handle(it)
            it.next()
        }.coroutineHandler {
            Delete(location).handle(it)
        }

        // Exception with status code
        route().handler { ctx ->
            ctx.fail(HttpStatusException(HttpResponseStatus.NOT_FOUND.code()))
        }

        route().failureHandler { failureRoutingContext ->
            val statusCode = failureRoutingContext.statusCode()
            val failure = failureRoutingContext.failure()

            if (statusCode == -1) {
                if (failure is HttpStatusException)
                    response(failureRoutingContext.response(), failure.statusCode, failure.message)
                else
                    response(
                        failureRoutingContext.response(), HttpResponseStatus.INTERNAL_SERVER_ERROR.code(),
                        failure.message
                    )
            } else {
                response(failureRoutingContext.response(), statusCode, failure?.message)
            }
        }
    }

    private fun formToJson(rc: RoutingContext): JsonObject {
        val formAttributes = rc.request().formAttributes()
        val code =
            formAttributes.get("code")
        val redirectUri =
            formAttributes.get("redirect_uri")
        val responseType =
            formAttributes.get("response_type")
        val grantType =
            formAttributes.get("grant_type")

        return JsonObject()
            .put("code", code)
            .put("redirect_uri", redirectUri)
            .put("response_type", responseType)
            .put("grant_type", grantType)
    }

    private fun response(response: HttpServerResponse, statusCode: Int, failureMessage: String?) {
        response.setStatusCode(statusCode).end("Failure calling the RESTful API: $failureMessage")
    }

    /**
     * An extension method for simplifying coroutines usage with Vert.x Web routers.
     */
    private fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Unit): Route {
        return handler { ctx ->
            launch(ctx.vertx().dispatcher()) {
                try {
                    fn(ctx)
                } catch (e: Exception) {
                    ctx.fail(e)
                }
            }
        }
    }
}
