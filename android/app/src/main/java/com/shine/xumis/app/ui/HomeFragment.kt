package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.shine.xumis.app.R
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.api.CartApi
import com.shine.xumis.app.api.HotApi
import com.shine.xumis.app.databinding.FragmentHomeBinding
import com.shine.xumis.app.model.MobileProduct
import com.shine.xumis.app.util.CatalogCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var product: MobileProduct? = null

    private companion object {
        /** 顶部滚动公告：店铺服务承诺，干净利落不喧宾夺主 */
        const val TICKER_TEXT =
            "shine 星心 · 全场包邮 · 七天无理由退换 · 正品保证 · 专注每一件心动好物"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipe.setOnRefreshListener { load() }
        binding.btnRetry.setOnClickListener { load() }

        binding.tickerText.text = TICKER_TEXT
        binding.tickerText.isSelected = true

        binding.btnCart.setOnClickListener { addToCart() }
        binding.btnBuy.setOnClickListener { buyNow() }

        load()
    }

    private fun load() {
        if (!binding.swipe.isRefreshing) binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // 销量榜（已售 N 件）：拉不到也不影响主流程，降级为空映射
                val salesMap = withContext(Dispatchers.IO) { runCatching { HotApi.loadSalesMap() }.getOrDefault(emptyMap()) }

                // 单品模式：首页只展示后台配置的那一件（默认取列表第一件，后台可随时改）
                val res = ApiClient.api().products(0, 1)
                val p = (res.content ?: emptyList()).firstOrNull()
                if (p == null) {
                    showEmpty(title = "这里还没有商品", body = "店铺刚上新时会出现在这里，下拉可以刷新", retry = false)
                    return@launch
                }
                val sp = if (salesMap.isNotEmpty()) p.copy(sales = salesMap[p.id] ?: p.sales) else p
                product = sp
                render(sp)
                CatalogCache.save(requireContext(), listOf(sp))
                hideEmpty()
                binding.tvOffline.visibility = View.GONE
            } catch (e: Exception) {
                // 网络不可用：回退到上次成功拉取的快照
                val snapshot = CatalogCache.load(requireContext())
                val p = snapshot?.items?.firstOrNull()
                if (p != null) {
                    product = p
                    render(p)
                    binding.tvOffline.visibility = View.VISIBLE
                    binding.tvOffline.text =
                        "离线数据 · 展示缓存的商品（" + CatalogCache.savedAtText(requireContext()) + " 更新），联网后下拉刷新"
                    hideEmpty()
                } else {
                    showEmpty(title = "没能连上店铺", body = "检查一下网络，或稍后再试", retry = true)
                }
            } finally {
                binding.swipe.isRefreshing = false
                binding.progress.visibility = View.GONE
            }
        }
    }

    private fun render(p: MobileProduct) {
        val url = p.fullImageUrl()
        if (url != null) {
            Glide.with(this).load(url).placeholder(R.drawable.ic_placeholder).into(binding.imgProduct)
            Glide.with(this).load(url).placeholder(R.drawable.ic_placeholder).into(binding.imgDetail)
        } else {
            binding.imgDetail.visibility = View.GONE
        }

        binding.tvPrice.text = p.priceText()
        val sold = p.soldText()
        binding.tvSold.text = sold ?: ""
        binding.tvSold.visibility = if (sold == null) View.GONE else View.VISIBLE

        binding.tvTitle.text = p.title()
        binding.tvSubtitle.text = p.nameEn ?: ""

        binding.tvMaterial.text = if (p.material.isNullOrBlank()) "—" else p.material
        binding.tvCat.text = p.category ?: "—"
        binding.tvDesc.text = p.description ?: "（暂无描述）"
    }

    private fun addToCart() {
        val p = product ?: return
        binding.btnCart.isEnabled = false
        lifecycleScope.launch {
            try {
                val count = CartApi.add(p.id ?: 0L)
                CartApi.publish(count)
                // 加购后给一个「去购物车」动作，形成 加购 → 结算 的闭环
                Snackbar.make(binding.root, "已加入购物车（$count 件）", Snackbar.LENGTH_LONG)
                    .setAction("去购物车") {
                        startActivity(MainActivity.intentTo(requireContext(), MainActivity.TAB_CART))
                    }
                    .setActionTextColor(requireContext().getColor(R.color.shine_mint))
                    .show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "加购失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnCart.isEnabled = true
            }
        }
    }

    private fun buyNow() {
        val p = product ?: return
        binding.btnBuy.isEnabled = false
        lifecycleScope.launch {
            try {
                val count = CartApi.add(p.id ?: 0L)
                CartApi.publish(count)
                startActivity(Intent(requireContext(), CheckoutActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "加购失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnBuy.isEnabled = true
            }
        }
    }

    private fun showEmpty(title: String, body: String, retry: Boolean) {
        binding.emptyState.visibility = View.VISIBLE
        binding.tvEmptyTitle.text = title
        binding.tvEmptyBody.text = body
        binding.btnRetry.visibility = if (retry) View.VISIBLE else View.GONE
    }

    private fun hideEmpty() {
        binding.emptyState.visibility = View.GONE
        binding.btnRetry.visibility = View.GONE
    }

    /** 双击首页 Tab 时回滚到顶部（由 MainActivity 的 reselected 监听调用） */
    fun scrollToTop() {
        _binding?.scroll?.smoothScrollTo(0, 0)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
