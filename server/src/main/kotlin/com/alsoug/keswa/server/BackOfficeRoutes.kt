package com.alsoug.keswa.server

import com.alsoug.keswa.core.database.KeswaDatabase
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.backOfficeRoutes(
    database: KeswaDatabase,
    log: LogStore,
    adminToken: String,
    now: () -> Long = System::currentTimeMillis,
) {
    val reportService = BackOfficeReportService(database, log, now)

    get("/") {
        call.handleDashboardRequest(reportService, adminToken)
    }

    get("/dashboard") {
        call.handleDashboardRequest(reportService, adminToken)
    }

    post("/login") {
        val params = call.receiveParameters()
        val token = params["token"]?.trim().orEmpty()
        val lang = params["lang"]?.trim() ?: "ar"

        if (token == adminToken) {
            call.response.cookies.append(
                Cookie(
                    name = COOKIE_NAME,
                    value = token,
                    path = "/",
                    httpOnly = true,
                ),
            )
            call.respondRedirect("/dashboard?lang=$lang")
        } else {
            val error = if (lang == "ar") "رمز المدير غير صحيح" else "Invalid admin token"
            call.respondText(BackOfficeHtml.renderLogin(error, lang), ContentType.Text.Html, HttpStatusCode.Unauthorized)
        }
    }

    get("/logout") {
        call.response.cookies.append(
            Cookie(
                name = COOKIE_NAME,
                value = "",
                path = "/",
                maxAge = 0,
            ),
        )
        call.respondRedirect("/")
    }

    route("/api") {
        get("/reports/summary") {
            if (!call.isBackOfficeAdmin(adminToken)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@get
            }
            val days = call.parameters["days"]?.toIntOrNull() ?: 7
            val summary = reportService.getSummary(days)
            call.respond(summary)
        }

        get("/reports/valuation") {
            if (!call.isBackOfficeAdmin(adminToken)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@get
            }
            val valuation = reportService.getValuation()
            call.respond(valuation)
        }

        get("/devices") {
            if (!call.isBackOfficeAdmin(adminToken)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@get
            }
            val devices = reportService.getDevices()
            call.respond(devices)
        }
    }
}

private suspend fun ApplicationCall.handleDashboardRequest(
    reportService: BackOfficeReportService,
    adminToken: String,
) {
    val lang = parameters["lang"]?.ifBlank { null } ?: "ar"
    if (!isBackOfficeAdmin(adminToken)) {
        respondText(BackOfficeHtml.renderLogin(lang = lang), ContentType.Text.Html)
        return
    }

    val days = parameters["days"]?.toIntOrNull() ?: 7
    val summary = reportService.getSummary(days)
    val valuation = reportService.getValuation()
    val devices = reportService.getDevices()

    val html = BackOfficeHtml.renderDashboard(
        token = adminToken,
        summary = summary,
        valuation = valuation,
        devices = devices,
        lang = lang,
    )
    respondText(html, ContentType.Text.Html)
}

private fun ApplicationCall.isBackOfficeAdmin(adminToken: String): Boolean {
    // 1. Authorization header: Bearer <token>
    val bearer = request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
    if (bearer == adminToken) return true

    // 2. Query parameter: ?token=<token>
    val queryToken = parameters["token"]?.trim()
    if (queryToken == adminToken) return true

    // 3. Cookie: keswa_admin_token=<token>
    val cookieToken = request.cookies[COOKIE_NAME]?.trim()
    if (cookieToken == adminToken) return true

    return false
}

private const val COOKIE_NAME = "keswa_admin_token"
