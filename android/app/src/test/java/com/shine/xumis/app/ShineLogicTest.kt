package com.shine.xumis.app

import com.google.gson.GsonBuilder
import com.shine.xumis.app.model.CategoryItem
import com.shine.xumis.app.model.MobileProduct
import com.shine.xumis.app.model.PingResponse
import com.shine.xumis.app.model.ProductPage
import com.shine.xumis.app.util.UrlUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * 核心逻辑单元测试（JVM，可直接在 CI/本地运行）。
 * 用于「自检验证 50 遍」：测试套件会在脚本里循环运行 50 次。
 */
class ShineLogicTest {

    private val gson = GsonBuilder().setLenient().create()

    @Test fun productTitle_fallback() {
        assertEquals("项链", MobileProduct(name = "项链").title())
        assertEquals("Necklace", MobileProduct(displayName = "Necklace").title())
        assertEquals("商品", MobileProduct().title())
    }

    @Test fun priceText_format() {
        assertEquals("$19.90", MobileProduct(price = 19.9).priceText())
        assertEquals("$0.00", MobileProduct(price = 0.0).priceText())
        assertEquals("", MobileProduct(price = null).priceText())
    }

    @Test fun stockText_variants() {
        assertEquals("暂时缺货", MobileProduct(inStock = false).stockText())
        assertEquals("现货 5 件", MobileProduct(inStock = true, stock = 5).stockText())
        assertEquals("现货", MobileProduct(inStock = true, stock = null).stockText())
    }

    @Test fun ping_parse() {
        val json = """{"ok":true,"service":"shine-xumis","ts":123}"""
        val p = gson.fromJson(json, PingResponse::class.java)
        assertTrue(p.ok == true)
        assertEquals("shine-xumis", p.service)
    }

    @Test fun product_parse_full() {
        val json = """{"id":1,"name":"星心项链","nameEn":"Star Heart Necklace","displayName":"星心项链","category":"饰品","price":19.9,"imageUrl":"/img/products/p01.png","customizable":true,"material":"合金","stock":100,"featured":true,"description":"测试","inStock":true}"""
        val p = gson.fromJson(json, MobileProduct::class.java)
        assertEquals(1L, p.id)
        assertEquals("饰品", p.category)
        assertEquals(19.9, p.price!!, 0.001)
        assertEquals("/img/products/p01.png", p.imageUrl)
        assertTrue(p.featured == true)
        assertTrue(p.inStock == true)
    }

    @Test fun page_parse() {
        val json = """{"content":[{"id":1,"name":"A","price":1.0}],"page":0,"size":30,"totalElements":1,"totalPages":1}"""
        val page = gson.fromJson(json, ProductPage::class.java)
        assertEquals(1, page.content!!.size)
        assertEquals(1L, page.totalElements)   // 后端 Spring Page 的 totalElements 为 long
        assertEquals(1, page.totalPages)
    }

    @Test fun categories_parse() {
        val json = """[{"code":"JEWELRY","label":"饰品"},{"code":"HANDMADE","label":"手工品"}]"""
        val cats = gson.fromJson(json, Array<CategoryItem>::class.java)
        assertEquals(2, cats.size)
        assertEquals("饰品", cats[0].label)
    }

    @Test fun urlUtils_join() {
        assertEquals("http://x:8080/api/m/ping", UrlUtils.join("http://x:8080/", "/api/m/ping"))
        assertEquals("http://x:8080/api/m/ping", UrlUtils.join("http://x:8080", "api/m/ping"))
    }

    @Test fun selfCheck_logic() {
        assertTrue(selfCheck(true, listOf(MobileProduct(id = 1)), listOf(CategoryItem("A", "a"))))
        assertFalse(selfCheck(false, listOf(MobileProduct(id = 1)), listOf(CategoryItem("A", "a"))))
        assertFalse(selfCheck(true, emptyList(), listOf(CategoryItem("A", "a"))))
    }

    companion object {
        /** 模拟「自检一次」：给定已解析数据，判断后端是否健康 */
        fun selfCheck(pingOk: Boolean, products: List<MobileProduct>?, cats: List<CategoryItem>?): Boolean {
            return pingOk && !products.isNullOrEmpty() && !cats.isNullOrEmpty()
        }
    }
}
