package com.alsoug.keswa.server

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object BackOfficeHtml {

    /**
     * The Keswa icon as a data URI, so the back office has a favicon without a static-file route.
     * A flat copy of `design/brand/keswa-icon.svg` without the stitches, which 16 px cannot show.
     */
    private const val FAVICON = "data:image/svg+xml," +
        "%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20viewBox='0%200%201024%201024'%3E" +
        "%3Crect%20width='1024'%20height='1024'%20rx='230'%20fill='%231A2344'/%3E" +
        "%3Cg%20transform='translate%28512%20512%29%20scale%281.12%29'%3E" +
        "%3Cpath%20d='M-204-250H-112V250H-204Z'%20fill='%23E8CD8F'/%3E" +
        "%3Cpath%20d='M-112-66L72-250H204L-112%2066Z'%20fill='%23CAA55C'/%3E" +
        "%3Cpath%20d='M-112%2066L72%20250H204L-112-66Z'%20fill='%23A5843F'/%3E" +
        "%3Cpath%20d='M-112-66L-46%200L-112%2066Z'%20fill='%237A5F2A'/%3E" +
        "%3C/g%3E%3C/svg%3E"

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)
        .withZone(ZoneId.systemDefault())

    fun renderLogin(error: String? = null, lang: String = "ar"): String {
        val isAr = lang == "ar"
        val dir = if (isAr) "rtl" else "ltr"
        val title = if (isAr) "كسوة — لوحة إدارة المحل" else "Keswa — Store Back Office"
        val prompt = if (isAr) "رمز مدير النظام (Admin Token)" else "Admin Token"
        val submit = if (isAr) "تسجيل الدخول" else "Sign In"
        val errorMsg = if (error != null) {
            """<div class="error">$error</div>"""
        } else ""

        return """
<!DOCTYPE html>
<html lang="$lang" dir="$dir">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title</title>
    <link rel="icon" type="image/svg+xml" href="$FAVICON">
    <style>
        :root {
            --bg: #0F172A;
            --surface: #1E293B;
            --border: #334155;
            --primary: #38BDF8;
            --primary-hover: #0EA5E9;
            --text: #F8FAFC;
            --text-dim: #94A3B8;
            --error: #EF4444;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }
        body { background-color: var(--bg); color: var(--text); display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
        .card { background-color: var(--surface); border: 1px solid var(--border); border-radius: 12px; width: 100%; max-width: 420px; padding: 32px; box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.4); }
        .header { text-align: center; margin-bottom: 24px; }
        .header h1 { font-size: 24px; font-weight: 700; color: var(--text); margin-bottom: 8px; }
        .header p { font-size: 14px; color: var(--text-dim); }
        .form-group { margin-bottom: 20px; }
        label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 8px; color: var(--text); }
        input[type="password"], input[type="text"] { width: 100%; padding: 12px 14px; background: #0B1120; border: 1px solid var(--border); border-radius: 8px; color: var(--text); font-size: 14px; outline: none; transition: border 0.2s; }
        input:focus { border-color: var(--primary); }
        button { width: 100%; padding: 12px; background: var(--primary); border: none; border-radius: 8px; color: #0F172A; font-size: 15px; font-weight: 600; cursor: pointer; transition: background 0.2s; }
        button:hover { background: var(--primary-hover); }
        .error { background: rgba(239, 68, 68, 0.15); border: 1px solid var(--error); color: var(--error); padding: 10px 14px; border-radius: 6px; font-size: 13px; margin-bottom: 16px; }
        .lang-switch { text-align: center; margin-top: 16px; }
        .lang-switch a { color: var(--primary); font-size: 13px; text-decoration: none; }
    </style>
</head>
<body>
    <div class="card">
        <div class="header">
            <img src="$FAVICON" alt="" width="56" height="56" style="display: block; margin: 0 auto 14px;">
            <h1>KESWA · كسوة</h1>
            <p>$title</p>
        </div>
        $errorMsg
        <form action="/login" method="POST">
            <input type="hidden" name="lang" value="$lang">
            <div class="form-group">
                <label for="token">$prompt</label>
                <input type="password" id="token" name="token" required autofocus autocomplete="current-password">
            </div>
            <button type="submit">$submit</button>
        </form>
        <div class="lang-switch">
            <a href="?lang=${if (isAr) "en" else "ar"}">${if (isAr) "English" else "العربية"}</a>
        </div>
    </div>
</body>
</html>
        """.trimIndent()
    }

    fun renderDashboard(
        token: String,
        summary: DashboardSummaryDto,
        valuation: InventoryValuationDto,
        devices: List<DeviceSummaryDto>,
        lang: String = "ar",
    ): String {
        val isAr = lang == "ar"
        val dir = if (isAr) "rtl" else "ltr"
        val title = if (isAr) "لوحة إدارة كسوة" else "Keswa Store Back Office"

        val kpis = summary.headline
        val grossMarginText = if (kpis.grossMarginPercent != null) String.format(Locale.ENGLISH, "%.1f%%", kpis.grossMarginPercent) else "—"
        val returnRate = if (kpis.unitsSold > 0) String.format(Locale.ENGLISH, "%.1f%%", (kpis.unitsReturned.toDouble() / kpis.unitsSold) * 100.0) else "0.0%"

        fun formatMoney(amount: Double): String = String.format(Locale.ENGLISH, "%,.2f EGP", amount)

        val days = summary.periodDays
        fun periodClass(p: Int) = if (days == p) "btn-pill active" else "btn-pill"

        val maxDaily = summary.dailyRevenue.maxOfOrNull { it.amountPounds }?.coerceAtLeast(1.0) ?: 1.0

        val dailyRowsHtml = summary.dailyRevenue.joinToString("\n") { row ->
            val barPct = ((row.amountPounds / maxDaily) * 100).coerceIn(4.0, 100.0)
            """
            <tr>
                <td style="font-weight: 600;">${row.day}</td>
                <td style="text-align: right; font-weight: 700;">${formatMoney(row.amountPounds)}</td>
                <td style="text-align: right;">${row.count}</td>
                <td>
                    <div class="bar-container">
                        <div class="bar-fill" style="width: ${String.format(Locale.ENGLISH, "%.1f", barPct)}%;"></div>
                    </div>
                </td>
            </tr>
            """.trimIndent()
        }

        val moverRowsHtml = summary.topMovers.joinToString("\n") { mover ->
            val pName = if (isAr && !mover.productNameAr.isNullOrBlank()) mover.productNameAr else mover.productName
            val cName = if (isAr && !mover.colourNameAr.isNullOrBlank()) mover.colourNameAr else mover.colourName
            val marginStr = if (mover.marginPercent != null) String.format(Locale.ENGLISH, "%.1f%%", mover.marginPercent) else "—"
            """
            <tr>
                <td><code>${mover.sku}</code></td>
                <td><strong>$pName</strong> <span class="badge">$cName</span></td>
                <td style="text-align: right;"><strong>${mover.sold}</strong></td>
                <td style="text-align: right; font-weight: 700;">${formatMoney(mover.revenuePounds)}</td>
                <td style="text-align: right;">$marginStr</td>
                <td style="text-align: right;">${mover.onHand}</td>
            </tr>
            """.trimIndent()
        }

        val coloursHtml = summary.colourPerformance.joinToString("\n") { col ->
            val cName = if (isAr && col.nameAr.isNotBlank()) col.nameAr else col.name
            val total = (col.sold + col.onHand).coerceAtLeast(1)
            val soldPct = (col.sold.toDouble() / total * 100).coerceIn(0.0, 100.0)
            """
            <div class="colour-item">
                <div class="colour-header">
                    <span class="colour-swatch" style="background-color: ${col.hex};"></span>
                    <span style="font-weight: 600;">$cName</span>
                    <span style="margin-inline-start: auto; font-size: 13px; color: var(--text-dim);">
                        ${if (isAr) "بيع: ${col.sold} · متبقي: ${col.onHand}" else "Sold: ${col.sold} · On Hand: ${col.onHand}"}
                    </span>
                </div>
                <div class="colour-bar">
                    <div class="colour-sold" style="width: ${String.format(Locale.ENGLISH, "%.1f", soldPct)}%;"></div>
                </div>
            </div>
            """.trimIndent()
        }

        val devicesHtml = devices.joinToString("\n") { dev ->
            val statusBadge = if (dev.isRevoked) {
                """<span class="badge badge-revoked">${if (isAr) "ملغي" else "Revoked"}</span>"""
            } else {
                """<span class="badge badge-active">${if (isAr) "نشط" else "Active"}</span>"""
            }
            val enrolledDate = DATE_FORMAT.format(Instant.ofEpochMilli(dev.enrolledAt))
            """
            <tr>
                <td><strong>${dev.name}</strong></td>
                <td><code>${dev.receiptRange}</code></td>
                <td>$enrolledDate</td>
                <td>$statusBadge</td>
                <td style="text-align: center;">
                    ${if (!dev.isRevoked) """<button class="btn-sm btn-revoke" onclick="revokeDevice('${dev.id}')">${if (isAr) "إلغاء التفعيل" else "Revoke"}</button>""" else "—"}
                </td>
            </tr>
            """.trimIndent()
        }

        return """
<!DOCTYPE html>
<html lang="$lang" dir="$dir">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title</title>
    <link rel="icon" type="image/svg+xml" href="$FAVICON">
    <style>
        :root {
            --bg: #0B1120;
            --surface: #1E293B;
            --surface-hover: #334155;
            --border: #334155;
            --primary: #38BDF8;
            --primary-hover: #0EA5E9;
            --accent: #10B981;
            --rose: #F43F5E;
            --amber: #F59E0B;
            --text: #F8FAFC;
            --text-dim: #94A3B8;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }
        body { background-color: var(--bg); color: var(--text); padding-bottom: 40px; }
        .navbar { background-color: var(--surface); border-bottom: 1px solid var(--border); padding: 14px 24px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; }
        .logo { font-size: 20px; font-weight: 800; letter-spacing: -0.5px; color: var(--primary); display: flex; align-items: center; gap: 8px; }
        .status-dot { width: 8px; height: 8px; border-radius: 50%; background-color: var(--accent); box-shadow: 0 0 8px var(--accent); }
        .nav-controls { display: flex; align-items: center; gap: 12px; }
        .btn-pill { padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 600; text-decoration: none; color: var(--text-dim); border: 1px solid var(--border); transition: all 0.2s; }
        .btn-pill:hover, .btn-pill.active { background-color: var(--primary); color: #0F172A; border-color: var(--primary); }
        .btn-outline { padding: 6px 12px; border-radius: 6px; font-size: 13px; font-weight: 500; text-decoration: none; color: var(--text); border: 1px solid var(--border); }
        .btn-outline:hover { background-color: var(--surface-hover); }
        .container { max-width: 1280px; margin: 0 auto; padding: 24px 20px; }
        
        /* KPI Cards */
        .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px; }
        .kpi-card { background-color: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 18px 20px; }
        .kpi-title { font-size: 13px; font-weight: 600; color: var(--text-dim); margin-bottom: 6px; }
        .kpi-value { font-size: 24px; font-weight: 800; color: var(--text); }
        .kpi-sub { font-size: 12px; color: var(--text-dim); margin-top: 4px; }
        .text-accent { color: var(--accent); }
        .text-rose { color: var(--rose); }

        /* Valuation Banner */
        .valuation-banner { background: linear-gradient(135deg, #1E293B 0%, #0F172A 100%); border: 1px solid var(--border); border-radius: 10px; padding: 16px 20px; margin-bottom: 24px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; }
        .valuation-metric { display: flex; flex-direction: column; }
        .valuation-metric .label { font-size: 12px; color: var(--text-dim); font-weight: 600; }
        .valuation-metric .val { font-size: 18px; font-weight: 700; color: var(--primary); }

        /* Two-column layout */
        .layout-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 24px; }
        @media (max-width: 960px) { .layout-grid { grid-template-columns: 1fr; } }
        
        .panel { background-color: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 20px; margin-bottom: 24px; }
        .panel-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
        .panel-header h2 { font-size: 16px; font-weight: 700; color: var(--text); }

        table { width: 100%; border-collapse: collapse; font-size: 13px; }
        th { text-align: start; padding: 10px 12px; color: var(--text-dim); font-weight: 600; border-bottom: 1px solid var(--border); }
        td { padding: 12px; border-bottom: 1px solid rgba(51, 65, 85, 0.5); }
        code { background-color: #0B1120; padding: 2px 6px; border-radius: 4px; font-family: monospace; font-size: 12px; color: var(--primary); }
        .badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: 600; background: var(--border); color: var(--text); }
        .badge-active { background: rgba(16, 185, 129, 0.2); color: var(--accent); }
        .badge-revoked { background: rgba(244, 63, 94, 0.2); color: var(--rose); }

        .bar-container { width: 100%; height: 8px; background: #0B1120; border-radius: 4px; overflow: hidden; }
        .bar-fill { height: 100%; background: var(--primary); border-radius: 4px; }

        .colour-item { margin-bottom: 14px; }
        .colour-header { display: flex; align-items: center; gap: 8px; font-size: 13px; margin-bottom: 4px; }
        .colour-swatch { width: 14px; height: 14px; border-radius: 3px; border: 1px solid rgba(255,255,255,0.2); }
        .colour-bar { width: 100%; height: 6px; background: #0B1120; border-radius: 3px; overflow: hidden; }
        .colour-sold { height: 100%; background: var(--accent); }

        .btn-action { background: var(--primary); color: #0F172A; border: none; padding: 8px 16px; border-radius: 6px; font-size: 13px; font-weight: 600; cursor: pointer; }
        .btn-action:hover { background: var(--primary-hover); }
        .btn-sm { padding: 4px 10px; font-size: 11px; border-radius: 4px; cursor: pointer; border: 1px solid var(--border); background: transparent; color: var(--text); }
        .btn-revoke { color: var(--rose); border-color: rgba(244, 63, 94, 0.4); }
        .btn-revoke:hover { background: rgba(244, 63, 94, 0.15); }

        .modal-backdrop { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.7); z-index: 100; align-items: center; justify-content: center; }
        .modal { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 24px; max-width: 440px; width: 90%; }
        .code-display { font-family: monospace; font-size: 28px; font-weight: 800; letter-spacing: 4px; color: var(--primary); background: #0B1120; padding: 14px; border-radius: 8px; text-align: center; margin: 16px 0; }
    </style>
</head>
<body>
    <nav class="navbar">
        <div class="logo">
            <img src="$FAVICON" alt="" width="26" height="26">
            <span class="status-dot"></span>
            <span>KESWA · كسوة</span>
        </div>
        <div class="nav-controls">
            <a href="?days=1&lang=$lang" class="${periodClass(1)}">${if (isAr) "اليوم" else "Today"}</a>
            <a href="?days=7&lang=$lang" class="${periodClass(7)}">${if (isAr) "٧ أيام" else "7 Days"}</a>
            <a href="?days=30&lang=$lang" class="${periodClass(30)}">${if (isAr) "٣٠ يوم" else "30 Days"}</a>
            <a href="?days=90&lang=$lang" class="${periodClass(90)}">${if (isAr) "٩٠ يوم" else "90 Days"}</a>
            <a href="?days=$days&lang=${if (isAr) "en" else "ar"}" class="btn-outline">${if (isAr) "English" else "العربية"}</a>
            <a href="/logout" class="btn-outline">${if (isAr) "خروج" else "Sign Out"}</a>
        </div>
    </nav>

    <div class="container">
        <!-- Valuation Banner -->
        <div class="valuation-banner">
            <div class="valuation-metric">
                <span class="label">${if (isAr) "تقييم المخزون بالتكلفة" else "Stock Valuation at Cost"}</span>
                <span class="val">${formatMoney(valuation.totalCostValuePounds)}</span>
            </div>
            <div class="valuation-metric">
                <span class="label">${if (isAr) "إجمالي القطع المتاحة" else "Total Pieces on Hand"}</span>
                <span class="val">${valuation.totalPiecesOnHand} pcs</span>
            </div>
            <div class="valuation-metric">
                <span class="label">${if (isAr) "الأصناف المتوفرة" else "Active SKUs"}</span>
                <span class="val">${valuation.trackedSkusCount} SKUs</span>
            </div>
            <div>
                <button class="btn-action" onclick="openEnrolModal()">+ ${if (isAr) "ربط نقطة بيع جديدة" else "Enrol New Device"}</button>
            </div>
        </div>

        <!-- KPI Grid -->
        <div class="kpi-grid">
            <div class="kpi-card">
                <div class="kpi-title">${if (isAr) "صافي المبيعات" else "Net Sales"}</div>
                <div class="kpi-value text-accent">${formatMoney(kpis.netRevenuePounds)}</div>
                <div class="kpi-sub">${if (isAr) "الإجمالي: " else "Gross: "}${formatMoney(kpis.revenuePounds)}</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-title">${if (isAr) "القطع المباعة" else "Units Sold"}</div>
                <div class="kpi-value">${kpis.unitsSold}</div>
                <div class="kpi-sub text-rose">${if (isAr) "المرتجع: " else "Returns: "}${kpis.unitsReturned} (${returnRate})</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-title">${if (isAr) "المعاملات ومتوسط السلة" else "Transactions & Basket"}</div>
                <div class="kpi-value">${kpis.transactions}</div>
                <div class="kpi-sub">${if (isAr) "متوسط السلة: " else "Avg Basket: "}${formatMoney(kpis.averageBasketPounds)}</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-title">${if (isAr) "هامش الربح الإجمالي" else "Gross Profit Margin"}</div>
                <div class="kpi-value">${grossMarginText}</div>
                <div class="kpi-sub">${if (isAr) "تكلفة البضاعة: " else "COGS: "}${if (kpis.cogsPounds != null) formatMoney(kpis.cogsPounds) else "—"}</div>
            </div>
        </div>

        <!-- Main Layout -->
        <div class="layout-grid">
            <!-- Left Column: Trend & Movers -->
            <div>
                <!-- Daily Trend -->
                <div class="panel">
                    <div class="panel-header">
                        <h2>${if (isAr) "حركة المبيعات اليومية" else "Daily Sales Trend"}</h2>
                    </div>
                    <table>
                        <thead>
                            <tr>
                                <th>${if (isAr) "اليوم" else "Day"}</th>
                                <th style="text-align: right;">${if (isAr) "المبيعات" else "Revenue"}</th>
                                <th style="text-align: right;">${if (isAr) "الفواتير" else "Orders"}</th>
                                <th style="width: 35%;">${if (isAr) "النسبة" else "Relative"}</th>
                            </tr>
                        </thead>
                        <tbody>
                            $dailyRowsHtml
                        </tbody>
                    </table>
                </div>

                <!-- Top Movers -->
                <div class="panel">
                    <div class="panel-header">
                        <h2>${if (isAr) "الأصناف الأكثر مبيعًا" else "Top Movers"}</h2>
                    </div>
                    <table>
                        <thead>
                            <tr>
                                <th>SKU</th>
                                <th>${if (isAr) "المنتج واللون" else "Product & Colour"}</th>
                                <th style="text-align: right;">${if (isAr) "المباع" else "Sold"}</th>
                                <th style="text-align: right;">${if (isAr) "الإيراد" else "Revenue"}</th>
                                <th style="text-align: right;">${if (isAr) "الهامش" else "Margin"}</th>
                                <th style="text-align: right;">${if (isAr) "المتبقي" else "Stock"}</th>
                            </tr>
                        </thead>
                        <tbody>
                            $moverRowsHtml
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- Right Column: Colours & Devices -->
            <div>
                <!-- Colours -->
                <div class="panel">
                    <div class="panel-header">
                        <h2>${if (isAr) "أداء الألوان (مباع / متبقي)" else "Colour Performance"}</h2>
                    </div>
                    $coloursHtml
                </div>

                <!-- Devices -->
                <div class="panel">
                    <div class="panel-header">
                        <h2>${if (isAr) "نقاط البيع والأجهزة المتصلة" else "Connected Devices"}</h2>
                    </div>
                    <table>
                        <thead>
                            <tr>
                                <th>${if (isAr) "الجهاز" else "Device"}</th>
                                <th>${if (isAr) "أرقام الفواتير" else "Receipt Range"}</th>
                                <th>${if (isAr) "تاريخ الربط" else "Enrolled"}</th>
                                <th>${if (isAr) "الحالة" else "Status"}</th>
                                <th></th>
                            </tr>
                        </thead>
                        <tbody>
                            $devicesHtml
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>

    <!-- Enrol Modal -->
    <div id="enrolModal" class="modal-backdrop">
        <div class="modal">
            <h3 style="font-size: 18px; font-weight: 700; margin-bottom: 8px;">${if (isAr) "رمز ربط جهاز جديد" else "Device Enrolment Code"}</h3>
            <p style="font-size: 13px; color: var(--text-dim); margin-bottom: 16px;">
                ${if (isAr) "أدخل هذا الكود في الجهاز الجديد أو نقطة البيع لربطه بالنظام." else "Enter this code on the new handheld or till to enrol it."}
            </p>
            <div id="generatedCode" class="code-display">••••••••</div>
            <div style="display: flex; gap: 10px; justify-content: flex-end;">
                <button class="btn-outline" onclick="closeEnrolModal()">${if (isAr) "إغلاق" else "Close"}</button>
                <button class="btn-action" onclick="mintCode()">${if (isAr) "إنشاء كود جديد" else "Generate Code"}</button>
            </div>
        </div>
    </div>

    <script>
        function openEnrolModal() {
            document.getElementById('enrolModal').style.display = 'flex';
            mintCode();
        }
        function closeEnrolModal() {
            document.getElementById('enrolModal').style.display = 'none';
        }
        async function mintCode() {
            try {
                const res = await fetch('/v1/admin/enrolment-code', {
                    method: 'POST',
                    headers: { 'Authorization': 'Bearer $token' }
                });
                if (res.ok) {
                    const data = await res.json();
                    document.getElementById('generatedCode').textContent = data.code;
                } else {
                    document.getElementById('generatedCode').textContent = 'ERROR';
                }
            } catch(e) {
                document.getElementById('generatedCode').textContent = 'ERROR';
            }
        }
        async function revokeDevice(deviceId) {
            if (!confirm('${if (isAr) "هل أنت متأكد من إلغاء تفعيل هذا الجهاز؟" else "Are you sure you want to revoke this device?"}')) return;
            try {
                const res = await fetch('/v1/admin/revoke?deviceId=' + encodeURIComponent(deviceId), {
                    method: 'POST',
                    headers: { 'Authorization': 'Bearer $token' }
                });
                if (res.ok) {
                    location.reload();
                } else {
                    alert('${if (isAr) "فشل إلغاء التفعيل" else "Failed to revoke"}');
                }
            } catch(e) {
                alert('${if (isAr) "حدث خطأ في الاتصال" else "Network error"}');
            }
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
