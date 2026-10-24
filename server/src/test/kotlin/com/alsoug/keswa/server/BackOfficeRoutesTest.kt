package com.alsoug.keswa.server

import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.sync.EnrolRequest
import com.alsoug.keswa.core.sync.EnrolResponse
import com.alsoug.keswa.core.sync.PushRequest
import com.alsoug.keswa.core.sync.PushResponse
import com.alsoug.keswa.core.sync.SyncRow
import com.alsoug.keswa.core.sync.SyncValue
import com.alsoug.keswa.core.sync.SyncValueType
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackOfficeRoutesTest {

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
                adminToken = ADMIN_TOKEN,
                now = { CLOCK },
                newSecret = { "sec-${secrets++}" },
                database = shop,
            )
        }
    }

    @Test
    fun `unauthenticated dashboard request renders login page`() = testApplication {
        serve()
        val response = client.get("/dashboard")
        assertEquals(HttpStatusCode.OK, response.status)
        val html = response.bodyAsText()
        assertTrue(html.contains("<form action=\"/login\""))
        assertTrue(html.contains("Admin Token") || html.contains("رمز مدير النظام"))
    }

    @Test
    fun `dashboard request with valid bearer token returns 200 and renders HTML dashboard`() = testApplication {
        serve()
        val response = client.get("/dashboard") {
            header("Authorization", "Bearer $ADMIN_TOKEN")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val html = response.bodyAsText()
        assertTrue(html.contains("KESWA · كسوة"))
        assertTrue(html.contains("Net Sales") || html.contains("صافي المبيعات"))
        assertTrue(html.contains("Stock Valuation at Cost") || html.contains("تقييم المخزون بالتكلفة"))
    }

    @Test
    fun `dashboard request with valid query token returns 200 and renders HTML dashboard`() = testApplication {
        serve()
        val response = client.get("/dashboard?token=$ADMIN_TOKEN&lang=en")
        assertEquals(HttpStatusCode.OK, response.status)
        val html = response.bodyAsText()
        assertTrue(html.contains("Net Sales"))
        assertTrue(html.contains("Stock Valuation at Cost"))
    }

    @Test
    fun `login with valid admin token sets cookie and redirects`() = testApplication {
        serve()
        val response = client.post("/login") {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("token", ADMIN_TOKEN)
                        append("lang", "en")
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/dashboard?lang=en", response.headers["Location"])
        val setCookie = response.headers["Set-Cookie"]
        assertNotNull(setCookie)
        assertTrue(setCookie.contains("keswa_admin_token=$ADMIN_TOKEN"))
    }

    @Test
    fun `login with invalid token returns 401 and error message`() = testApplication {
        serve()
        val response = client.post("/login") {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("token", "wrong-token")
                        append("lang", "en")
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val html = response.bodyAsText()
        assertTrue(html.contains("Invalid admin token"))
    }

    @Test
    fun `logout clears cookie`() = testApplication {
        serve()
        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/logout")
        assertEquals(HttpStatusCode.Found, response.status)
        val setCookie = response.headers["Set-Cookie"]
        assertNotNull(setCookie)
        assertTrue(setCookie.contains("keswa_admin_token=;"))
    }

    @Test
    fun `api summary endpoint returns 401 without admin token`() = testApplication {
        serve()
        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/reports/summary")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `api summary endpoint returns 200 and correct KPI calculations`() = testApplication {
        serve()
        val client = createClient { install(ContentNegotiation) { json() } }

        // Enrol a till and push catalogue and sales
        log.mintEnrolmentCode("ENROL123", CLOCK)
        val enrol = client.post("/v1/enrol") {
            contentType(ContentType.Application.Json)
            setBody(EnrolRequest("ENROL123", "device-pos-1", "Counter POS"))
        }.body<EnrolResponse>()

        // Push catalogue & stock
        val catalogue = catalogueRows()
        val stockMov = movement("mov-1", 50, "var-1")
        client.post("/v1/sync/push") {
            header("Authorization", "Bearer ${enrol.token}")
            contentType(ContentType.Application.Json)
            setBody(PushRequest(catalogue + stockMov))
        }

        // Push a sale: 2 pieces @ 180 EGP each (360 EGP total), cost 120 EGP each (240 EGP total)
        val saleRow = SyncRow(
            "sale", "sale-1",
            mapOf(
                "id" to text("sale-1"),
                "receiptNumber" to integer(1),
                "locationId" to text("loc-1"),
                "priceListId" to text("pl-1"),
                "userId" to text("usr-1"),
                "shiftId" to absent,
                "customerId" to absent,
                "status" to text("COMPLETED"),
                "subtotalPiastres" to integer(36_000),
                "discountPiastres" to integer(0),
                "taxPiastres" to integer(0),
                "totalPiastres" to integer(36_000),
                "tenderedPiastres" to integer(36_000),
                "changePiastres" to integer(0),
                "occurredAt" to integer(CLOCK - 10_000L),
                "voidedAt" to absent,
                "voidedByUserId" to absent,
                "voidReason" to absent,
            ),
        )
        val saleLineRow = SyncRow(
            "sale_line", "line-1",
            mapOf(
                "id" to text("line-1"),
                "saleId" to text("sale-1"),
                "lineNumber" to integer(1),
                "variantId" to text("var-1"),
                "description" to text("Round-neck t-shirt - Navy"),
                "descriptionAr" to text("تيشيرت - كحلي"),
                "quantity" to integer(2),
                "unitPricePiastres" to integer(18_000),
                "lineDiscountPiastres" to integer(0),
                "orderDiscountPiastres" to integer(0),
                "lineTotalPiastres" to integer(36_000),
                "taxPiastres" to integer(0),
                "unitCostPiastres" to integer(12_000),
                "authorisedByUserId" to absent,
                "packId" to absent,
            ),
        )
        client.post("/v1/sync/push") {
            header("Authorization", "Bearer ${enrol.token}")
            contentType(ContentType.Application.Json)
            setBody(PushRequest(listOf(saleRow, saleLineRow)))
        }

        // Materialise into shop
        materialiserFor(log, shop).runToCompletion()

        // Fetch back-office summary
        val summary = client.get("/api/reports/summary?days=7") {
            header("Authorization", "Bearer $ADMIN_TOKEN")
        }.body<DashboardSummaryDto>()

        assertEquals(7, summary.periodDays)
        assertEquals(360.0, summary.headline.revenuePounds, 0.01)
        assertEquals(0.0, summary.headline.refundedPounds, 0.01)
        assertEquals(360.0, summary.headline.netRevenuePounds, 0.01)
        assertEquals(1, summary.headline.transactions)
        assertEquals(2, summary.headline.unitsSold)
        assertEquals(240.0, summary.headline.cogsPounds)
        // Gross margin: (360 - 240) / 360 = 33.33%
        val margin = summary.headline.grossMarginPercent
        assertNotNull(margin)
        assertEquals(33.33, margin, 0.1)

        // Top movers contains our variant
        assertTrue(summary.topMovers.isNotEmpty())
        val top = summary.topMovers.first()
        assertEquals("ROU-NAV", top.sku)
        assertEquals(2, top.sold)
        assertEquals(360.0, top.revenuePounds, 0.01)

        // Valuation endpoint
        val valuation = client.get("/api/reports/valuation") {
            header("Authorization", "Bearer $ADMIN_TOKEN")
        }.body<InventoryValuationDto>()

        assertEquals(50, valuation.totalPiecesOnHand)
        // 50 pieces * 120 EGP cost = 6000 EGP
        assertEquals(6000.0, valuation.totalCostValuePounds, 0.01)
        assertEquals(1, valuation.trackedSkusCount)

        // Devices endpoint
        val devices = client.get("/api/devices") {
            header("Authorization", "Bearer $ADMIN_TOKEN")
        }.body<List<DeviceSummaryDto>>()

        assertEquals(1, devices.size)
        val dev = devices.first()
        assertEquals("Counter POS", dev.name)
        assertEquals(0, dev.ordinal)
        assertEquals("#0 – #999999", dev.receiptRange)
        assertFalse(dev.isRevoked)
    }

    private companion object {
        const val ADMIN_TOKEN = "super-secret-admin-token-12345"

        fun text(value: String) = SyncValue(SyncValueType.TEXT, value)
        fun integer(value: Long) = SyncValue(SyncValueType.INTEGER, value.toString())
        val absent = SyncValue(SyncValueType.NULL)
    }
}
