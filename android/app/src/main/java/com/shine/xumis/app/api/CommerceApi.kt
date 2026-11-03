package com.shine.xumis.app.api

import com.shine.xumis.app.util.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// ==================== 数据模型 ====================

data class CartLine(
    val id: Long,
    val name: String,
    val price: Double,
    val imageUrl: String?,
    val stock: Int,
    val qty: Int,
    val lineTotal: Double
)

data class CartData(
    val lines: List<CartLine>,
    val count: Int,
    val total: Double,
    val isEmpty: Boolean
)

data class CheckoutForm(
    val customerName: String,
    val email: String,
    val phone: String?,
    val country: String?,
    val address: String?,
    val note: String?,
    val currency: String = "USD",
    val paymentMethod: String?
)

data class PlaceResult(val orderNo: String, val total: Double, val currency: String)

data class OrderItemSummary(
    val name: String?,
    val quantity: Int,
    val price: Double,
    val imageUrl: String?
)

data class OrderSummary(
    val orderNo: String,
    val status: String,
    val total: Double,
    val currency: String,
    val createdAt: String,
    val items: List<OrderItemSummary>
)

data class OrderDetail(
    val orderNo: String,
    val status: String,
    val total: Double,
    val currency: String,
    val createdAt: String,
    val paymentMethod: String?,
    val customerName: String?,
    val email: String?,
    val phone: String?,
    val country: String?,
    val address: String?,
    val note: String?,
    val items: List<OrderItemSummary>
)

data class AuthUser(
    val email: String,
    val name: String?,
    val phone: String?,
    val country: String?,
    val address: String?
)

data class MeResult(val loggedIn: Boolean, val user: AuthUser?)

/**
 * 原生交易 API（后端 /api/m 前缀，与站点共用 HttpSession）。
 * 写操作一律 form 提交；失败时后端返回 4xx + { ok:false, error }，这里统一转成带文案的异常。
 */
object CommerceApi {

    private fun base() = ServerConfig.apiBase()

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(base() + path).get().build()
        NativeSession.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw apiError(body)
            body
        }
    }

    private suspend fun post(path: String, form: FormBody): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(base() + path).post(form).build()
        NativeSession.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw apiError(body)
            body
        }
    }

    private fun apiError(body: String): Exception {
        val msg = try {
            JSONObject(body).optString("error").takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
        return IllegalStateException(msg ?: "请求失败，请稍后重试")
    }

    private fun fullImg(u: String?): String? {
        if (u.isNullOrBlank()) return null
        return if (u.startsWith("http")) u else ServerConfig.siteBase() + u.removePrefix("/")
    }

    // ==================== 购物车 ====================

    suspend fun cart(): CartData {
        val j = JSONObject(get("cart"))
        val arr = j.optJSONArray("lines") ?: JSONArray()
        val lines = ArrayList<CartLine>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            lines.add(
                CartLine(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    price = o.optDouble("price"),
                    imageUrl = fullImg(o.optString("imageUrl")),
                    stock = o.optInt("stock"),
                    qty = o.optInt("qty"),
                    lineTotal = o.optDouble("lineTotal")
                )
            )
        }
        return CartData(lines, j.optInt("count"), j.optDouble("total"), j.optBoolean("isEmpty"))
    }

    suspend fun add(productId: Long, qty: Int = 1): Int {
        val j = JSONObject(post("cart/add", FormBody.Builder()
            .add("productId", productId.toString())
            .add("quantity", qty.toString()).build()))
        if (!j.optBoolean("ok")) throw IllegalStateException(j.optString("error", "加购失败"))
        return j.optInt("count")
    }

    suspend fun update(productId: Long, qty: Int): Int {
        val j = JSONObject(post("cart/update", FormBody.Builder()
            .add("productId", productId.toString())
            .add("quantity", qty.toString()).build()))
        return j.optInt("count")
    }

    suspend fun remove(productId: Long): Int {
        val j = JSONObject(post("cart/remove", FormBody.Builder()
            .add("productId", productId.toString()).build()))
        return j.optInt("count")
    }

    // ==================== 下单 ====================

    suspend fun placeOrder(f: CheckoutForm): PlaceResult {
        val fb = FormBody.Builder()
            .add("customerName", f.customerName)
            .add("email", f.email)
            .add("currency", f.currency)
        f.phone?.takeIf { it.isNotBlank() }?.let { fb.add("phone", it) }
        f.country?.takeIf { it.isNotBlank() }?.let { fb.add("country", it) }
        f.address?.takeIf { it.isNotBlank() }?.let { fb.add("address", it) }
        f.note?.takeIf { it.isNotBlank() }?.let { fb.add("note", it) }
        f.paymentMethod?.takeIf { it.isNotBlank() }?.let { fb.add("paymentMethod", it) }
        val j = JSONObject(post("orders", fb.build()))
        if (!j.optBoolean("ok")) throw IllegalStateException(j.optString("error", "下单失败"))
        return PlaceResult(j.optString("orderNo"), j.optDouble("total"), j.optString("currency"))
    }

    suspend fun myOrders(email: String? = null): List<OrderSummary> {
        // 游客未登录时，用下单时留下的邮箱回查自己的订单；登录态由后端按账号归并，无需带邮箱。
        val path = if (email.isNullOrBlank()) "orders"
        else "orders?email=" + java.net.URLEncoder.encode(email, "UTF-8")
        val j = JSONObject(get(path))
        val arr = j.optJSONArray("orders") ?: JSONArray()
        val out = ArrayList<OrderSummary>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val itemsArr = o.optJSONArray("items") ?: JSONArray()
            val items = ArrayList<OrderItemSummary>(itemsArr.length())
            for (k in 0 until itemsArr.length()) {
                val it = itemsArr.getJSONObject(k)
                items.add(
                    OrderItemSummary(
                        name = it.optString("name"),
                        quantity = it.optInt("quantity"),
                        price = it.optDouble("price"),
                        imageUrl = fullImg(it.optString("imageUrl"))
                    )
                )
            }
            out.add(
                OrderSummary(
                    orderNo = o.optString("orderNo"),
                    status = o.optString("status"),
                    total = o.optDouble("total"),
                    currency = o.optString("currency"),
                    createdAt = o.optString("createdAt"),
                    items = items
                )
            )
        }
        return out
    }

    // ==================== 我的账户 ====================

    suspend fun me(): MeResult {
        val j = JSONObject(get("me"))
        val loggedIn = j.optBoolean("loggedIn")
        val u = j.optJSONObject("user")
        return MeResult(loggedIn, if (u == null) null else parseUser(u))
    }

    suspend fun login(email: String, password: String): AuthUser {
        val j = JSONObject(post("auth/login", FormBody.Builder()
            .add("email", email).add("password", password).build()))
        val u = j.optJSONObject("user")
            ?: throw IllegalStateException(j.optString("error", "登录失败"))
        return parseUser(u)
    }

    suspend fun register(email: String, password: String, name: String?): AuthUser {
        val j = JSONObject(post("auth/register", FormBody.Builder()
            .add("email", email).add("password", password)
            .add("confirmPassword", password).add("name", name ?: "").build()))
        val u = j.optJSONObject("user")
            ?: throw IllegalStateException(j.optString("error", "注册失败"))
        return parseUser(u)
    }

    suspend fun logout() {
        post("auth/logout", FormBody.Builder().build())
    }

    suspend fun updateProfile(name: String?, phone: String?, country: String?, address: String?): AuthUser {
        val j = JSONObject(post("auth/profile", FormBody.Builder()
            .add("name", name ?: "").add("phone", phone ?: "")
            .add("country", country ?: "").add("address", address ?: "").build()))
        val u = j.optJSONObject("user")
            ?: throw IllegalStateException(j.optString("error", "保存失败"))
        return parseUser(u)
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val j = JSONObject(post("auth/password", FormBody.Builder()
            .add("oldPassword", oldPassword).add("newPassword", newPassword).build()))
        if (!j.optBoolean("ok")) throw IllegalStateException(j.optString("error", "修改失败"))
    }

    suspend fun orderDetail(orderNo: String): OrderDetail {
        val j = JSONObject(get("orders/" + orderNo))
        val arr = j.optJSONArray("items") ?: JSONArray()
        val items = ArrayList<OrderItemSummary>(arr.length())
        for (k in 0 until arr.length()) {
            val it = arr.getJSONObject(k)
            items.add(
                OrderItemSummary(
                    name = it.optString("name"), quantity = it.optInt("quantity"),
                    price = it.optDouble("price"), imageUrl = fullImg(it.optString("imageUrl"))
                )
            )
        }
        return OrderDetail(
            orderNo = j.optString("orderNo"), status = j.optString("status"),
            total = j.optDouble("total"), currency = j.optString("currency"),
            createdAt = j.optString("createdAt"),
            paymentMethod = j.optString("paymentMethod").takeIf { it.isNotBlank() },
            customerName = j.optString("customerName").takeIf { it.isNotBlank() },
            email = j.optString("email").takeIf { it.isNotBlank() },
            phone = j.optString("phone").takeIf { it.isNotBlank() },
            country = j.optString("country").takeIf { it.isNotBlank() },
            address = j.optString("address").takeIf { it.isNotBlank() },
            note = j.optString("note").takeIf { it.isNotBlank() },
            items = items
        )
    }

    suspend fun forgotPassword(email: String) {
        post("auth/forgot-password", FormBody.Builder().add("email", email).build())
    }

    suspend fun resetPassword(token: String, password: String) {
        val j = JSONObject(post("auth/reset-password", FormBody.Builder()
            .add("token", token).add("password", password)
            .add("confirmPassword", password).build()))
        if (!j.optBoolean("ok")) throw IllegalStateException(j.optString("error", "重置失败"))
    }

    suspend fun customIdea(customerName: String, email: String, country: String?, description: String, expectCategory: String?) {
        val fb = FormBody.Builder()
            .add("customerName", customerName).add("email", email)
            .add("ideaDescription", description)
        country?.takeIf { it.isNotBlank() }?.let { fb.add("country", it) }
        expectCategory?.takeIf { it.isNotBlank() }?.let { fb.add("expectCategory", it) }
        val j = JSONObject(post("custom-ideas", fb.build()))
        if (!j.optBoolean("ok")) throw IllegalStateException(j.optString("error", "提交失败"))
    }

    suspend fun inquiry(name: String, email: String, subject: String?, message: String) {
        val body = JSONObject().apply {
            put("name", name); put("email", email)
            put("subject", subject ?: ""); put("message", message)
        }
        val j = JSONObject(postJson("inquiries", body))
        if (!j.optBoolean("ok")) throw IllegalStateException(j.optString("error", "提交失败"))
    }

    private suspend fun postJson(path: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(base() + path)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        NativeSession.client.newCall(req).execute().use { resp ->
            val b = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw apiError(b)
            b
        }
    }

    private fun parseUser(u: JSONObject) = AuthUser(
        email = u.optString("email"),
        name = u.optString("name").takeIf { it.isNotBlank() },
        phone = u.optString("phone").takeIf { it.isNotBlank() },
        country = u.optString("country").takeIf { it.isNotBlank() },
        address = u.optString("address").takeIf { it.isNotBlank() }
    )
}
