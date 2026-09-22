package com.alsoug.keswa.server

import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.sync.EnrolRequest
import com.alsoug.keswa.core.sync.EnrolResponse
import com.alsoug.keswa.core.sync.PullResponse
import com.alsoug.keswa.core.sync.PushRequest
import com.alsoug.keswa.core.sync.PushResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Enrolment, authentication and the two calls a till makes.
 *
 * What the server refuses matters more here than what it accepts: an unusable code, an unknown
 * token, a revoked device. Revocation is the control KD-007 leans on, so it is tested rather than
 * asserted in prose.
 */
class SyncRoutesTest {

    private val fixture = temporaryLog()
    private val log = fixture.first
    private val directory: File = fixture.second
    private val shop: KeswaDatabase = inMemoryShop()
    private var secrets = 0

    @AfterTest
    fun tearDown() {
        shop.close()
        log.close()
        directory.deleteRecursively()
    }

    private fun ApplicationTestBuilder.serve() {
        application {
            syncModule(
                log = log,
                materialiser = materialiserFor(log, shop),
                adminToken = ADMIN,
                now = { CLOCK },
                newSecret = { "secret-${secrets++}" },
            )
        }
    }

    private fun ApplicationTestBuilder.jsonClient() =
        createClient { install(ContentNegotiation) { json() } }

    private suspend fun ApplicationTestBuilder.mintCode(): String {
        val response = jsonClient().post("/v1/admin/enrolment-code") { header(AUTH, "Bearer $ADMIN") }
        return response.body<Map<String, String>>().getValue("code")
    }

    private suspend fun ApplicationTestBuilder.enrol(deviceId: String): EnrolResponse =
        jsonClient().post("/v1/enrol") {
            contentType(ContentType.Application.Json)
            setBody(EnrolRequest(code = mintCode(), deviceId = deviceId, deviceName = "Till"))
        }.body()

    @Test
    fun `health answers without a token`() = testApplication {
        serve()

        val response: HttpResponse = jsonClient().get("/v1/health")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `the first device to enrol keeps ordinal zero`() = testApplication {
        serve()

        val first = enrol("device-a")
        val second = enrol("device-b")

        // So a shop already trading on one till sees its receipt numbering carry on (9i).
        assertEquals(0, first.ordinal)
        assertEquals(1, second.ordinal)
        assertTrue(first.token != second.token)
    }

    @Test
    fun `an enrolment code is good once`() = testApplication {
        serve()
        val code = mintCode()
        val request = EnrolRequest(code = code, deviceId = "device-a", deviceName = "Till")

        val first = jsonClient().post("/v1/enrol") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val second = jsonClient().post("/v1/enrol") {
            contentType(ContentType.Application.Json)
            setBody(request.copy(deviceId = "device-b"))
        }

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.Forbidden, second.status)
    }

    @Test
    fun `minting a code needs the admin token`() = testApplication {
        serve()

        val response = jsonClient().post("/v1/admin/enrolment-code") { header(AUTH, "Bearer wrong") }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `a device with no token gets nowhere`() = testApplication {
        serve()

        val response = jsonClient().get("/v1/sync/pull?since=0")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `what one device pushes, another pulls`() = testApplication {
        serve()
        val till = enrol("device-till")
        val handheld = enrol("device-handheld")

        val push: PushResponse = jsonClient().post("/v1/sync/push") {
            header(AUTH, "Bearer ${till.token}")
            contentType(ContentType.Application.Json)
            setBody(PushRequest(catalogueRows() + movement("mov-1", 24)))
        }.body()

        val pull: PullResponse = jsonClient().get("/v1/sync/pull?since=0") {
            header(AUTH, "Bearer ${handheld.token}")
        }.body()

        assertEquals(6, push.accepted)
        assertEquals(6, pull.rows.size)
        assertEquals(6, pull.nextSeq)
        // And the server built the shop out of it on the way past.
        assertEquals(24, shop.stockLedgerDao().sumQuantity("var-1", "loc-1"))
    }

    @Test
    fun `a device is not handed back its own work`() = testApplication {
        serve()
        val till = enrol("device-till")

        jsonClient().post("/v1/sync/push") {
            header(AUTH, "Bearer ${till.token}")
            contentType(ContentType.Application.Json)
            setBody(PushRequest(catalogueRows()))
        }
        val pull: PullResponse = jsonClient().get("/v1/sync/pull?since=0") {
            header(AUTH, "Bearer ${till.token}")
        }.body()

        assertEquals(emptyList(), pull.rows)
        // The cursor still moves, so a batch that is entirely this device's own work does not stall.
        assertEquals(5, pull.nextSeq)
    }

    @Test
    fun `a revoked device is refused on its next attempt`() = testApplication {
        serve()
        val till = enrol("device-till")

        jsonClient().post("/v1/admin/revoke?deviceId=device-till") { header(AUTH, "Bearer $ADMIN") }
        val response = jsonClient().get("/v1/sync/pull?since=0") { header(AUTH, "Bearer ${till.token}") }

        // A stolen laptop stops syncing in seconds, which is the control that does the real work
        // behind KD-007's decision not to encrypt one string next to a plaintext database.
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private companion object {
        const val ADMIN = "admin-token"
        const val AUTH = "Authorization"
    }
}
