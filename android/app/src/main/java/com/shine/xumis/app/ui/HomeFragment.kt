package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.api.HotApi
import com.shine.xumis.app.databinding.FragmentHomeBinding
import com.shine.xumis.app.model.MobileProduct
import com.shine.xumis.app.ui.adapter.HomeHeaderAdapter
import com.shine.xumis.app.ui.adapter.ProductAdapter
import com.shine.xumis.app.util.CatalogCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页（平台式多商品）：品牌横幅 + 分类筛选 + 双列商品流。
 * 横幅/筛选作为列表头部（ConcatAdapter），随内容滚动；下拉刷新、上滑翻页、离线兜底完整保留。
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val productAdapter = ProductAdapter(onClick = { openDetail(it) })
    private val headerAdapter = HomeHeaderAdapter(
        onFeatured = { openFeatured() },
        onCategory = { code -> selectCategory(code) }
    )

    private companion object {
        const val PAGE_SIZE = 20
        /** 提前 4 个位置开始预加载 */
        const val PREFETCH_DISTANCE = 4
    }

    private var page = 0
    private var totalPages = 1
    private var loading = false
    private var selectedCat: String? = null
    private var featured: MobileProduct? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 双列网格：头部（横幅+筛选）占满一行，商品卡片一列两个
        val glm = GridLayoutManager(requireContext(), 2)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = if (position == 0) 2 else 1
        }
        binding.rv.layoutManager = glm
        binding.rv.adapter = ConcatAdapter(headerAdapter, productAdapter)

        binding.swipe.setOnRefreshListener { load(reset = true) }
        binding.btnRetry.setOnClickListener { load(reset = true) }

        binding.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = rv.layoutManager as? GridLayoutManager ?: return
                val total = rv.adapter?.itemCount ?: return
                if (total > 0 && lm.findLastVisibleItemPosition() >= total - PREFETCH_DISTANCE) {
                    maybeLoadMore()
                }
            }
        })

        loadCategories()
        load(reset = true)
    }

    private fun loadCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            val cats = withContext(Dispatchers.IO) {
                runCatching { ApiClient.api().categories() }.getOrDefault(emptyList())
            }
            headerAdapter.setCategories(cats)
        }
    }

    private fun selectCategory(code: String?) {
        if (selectedCat == code) return
        selectedCat = code
        headerAdapter.setSelected(code)
        binding.rv.scrollToPosition(0)
        load(reset = true)
    }

    private fun maybeLoadMore() {
        if (loading) return
        if (page >= totalPages) return
        if (productAdapter.size() == 0) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        if (loading) return
        loading = true

        if (reset) {
            page = 0
            binding.swipe.isRefreshing = true
            binding.btnRetry.visibility = View.GONE
        } else {
            binding.pbMore.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                // 热销榜（销量映射）：拉不到也不影响主流程
                val salesMap = if (reset) {
                    withContext(Dispatchers.IO) { runCatching { HotApi.loadSalesMap() }.getOrDefault(emptyMap()) }
                } else emptyMap()

                val res = ApiClient.api().search(
                    q = null,
                    cat = selectedCat,
                    sort = "default",
                    page = page,
                    size = PAGE_SIZE
                )
                var list = res.content ?: emptyList()
                totalPages = (res.totalPages ?: 1).coerceAtLeast(1)

                // 首页按销量优先，翻页保持服务端顺序
                if (reset && salesMap.isNotEmpty()) {
                    list = list.map { it.copy(sales = salesMap[it.id] ?: it.sales) }
                        .sortedByDescending { it.sales ?: 0 }
                }

                if (reset) {
                    productAdapter.submit(list)
                    if (list.isNotEmpty()) {
                        CatalogCache.save(requireContext(), list)
                        if (selectedCat == null) featured = list.firstOrNull() ?: featured
                    }
                } else {
                    productAdapter.append(list)
                }
                page += 1

                binding.tvOffline.visibility = View.GONE
                headerAdapter.setCount("共 " + (res.totalElements ?: productAdapter.size()) + " 件")
                hideEmpty()

                if (productAdapter.size() == 0) {
                    showEmpty("这个分类还没有商品", "换个分类看看，或下拉刷新", retry = false)
                }
            } catch (e: Exception) {
                if (reset) {
                    // 仅在「全部」下回退缓存快照，分类筛选时保持真实结果
                    val snapshot = if (selectedCat == null) CatalogCache.load(requireContext()) else null
                    if (snapshot != null && snapshot.items.isNotEmpty()) {
                        productAdapter.submit(snapshot.items)
                        featured = snapshot.items.firstOrNull() ?: featured
                        binding.tvOffline.visibility = View.VISIBLE
                        binding.tvOffline.text =
                            "离线数据 · 展示 ${snapshot.items.size} 件缓存商品（" +
                                CatalogCache.savedAtText(requireContext()) + " 更新），联网后下拉刷新"
                        headerAdapter.setCount("共 ${snapshot.items.size} 件（离线）")
                        hideEmpty()
                    } else {
                        binding.tvOffline.visibility = View.GONE
                        showEmpty("没能连上店铺", "检查一下网络，或稍后再试", retry = true)
                    }
                } else {
                    Toast.makeText(requireContext(), "加载更多失败，稍后再试", Toast.LENGTH_SHORT).show()
                }
            } finally {
                loading = false
                binding.swipe.isRefreshing = false
                binding.pbMore.visibility = View.GONE
            }
        }
    }

    private fun openFeatured() {
        val p = featured
        if (p != null) {
            openDetail(p)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val p2 = withContext(Dispatchers.IO) {
                runCatching { ApiClient.api().products(0, 1).content?.firstOrNull() }.getOrNull()
            }
            if (p2 != null) openDetail(p2)
            else Toast.makeText(requireContext(), "商品加载中，请稍后再试", Toast.LENGTH_SHORT).show()
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
        _binding?.rv?.smoothScrollToPosition(0)
    }

    private fun openDetail(p: MobileProduct) {
        startActivity(
            Intent(requireContext(), ProductDetailActivity::class.java)
                .putExtra("product_id", p.id ?: 0L)
        )
    }

    override fun onDestroyView() {
        binding.rv.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
