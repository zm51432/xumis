package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shine.xumis.app.R
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.api.HotApi
import com.shine.xumis.app.databinding.FragmentHomeBinding
import com.shine.xumis.app.model.MobileProduct
import com.shine.xumis.app.model.ProductPage
import com.shine.xumis.app.ui.adapter.ProductAdapter
import com.shine.xumis.app.util.CatalogCache
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val adapter = ProductAdapter(
        onClick = { openDetail(it) }
    )

    private companion object {
        const val PAGE_SIZE = 20
        /** 提前 4 个位置开始预加载，滚到底时下一页已经就位 */
        const val PREFETCH_DISTANCE = 4
        /** 首页滚动公告内容（店铺口号，重复以形成跑马灯滚动） */
        const val TICKER_TEXT =
            "只看销量，不看评论。　　只看销量，不看评论。　　只看销量，不看评论。　　只看销量，不看评论。"
    }

    private var page = 0
    private var totalPages = 1
    private var loading = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rv.adapter = adapter
        binding.swipe.setOnRefreshListener { load(reset = true) }
        binding.btnRetry.setOnClickListener { load(reset = true) }

        // 滚动公告：选中后才会跑马灯滚动
        binding.tickerText.text = TICKER_TEXT
        binding.tickerText.isSelected = true

        // 滚到底部附近自动加载下一页；没有更多时不再请求
        binding.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = rv.layoutManager as? GridLayoutManager ?: return
                if (lm.findLastVisibleItemPosition() >= adapter.size() - PREFETCH_DISTANCE) {
                    maybeLoadMore()
                }
            }
        })

        if (adapter.size() == 0) load(reset = true)
    }

    private fun maybeLoadMore() {
        if (loading) return
        if (page >= totalPages) return
        // 首屏只有一页且内容很少时不必翻页
        if (adapter.size() == 0) return
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
                // 热销榜（销量映射）：拉不到也不影响主流程，降级为默认顺序
                val salesMap = if (reset) runCatching { HotApi.loadSalesMap() }.getOrDefault(emptyMap()) else null

                val res = ApiClient.api().products(page, PAGE_SIZE)
                var list = res.content ?: emptyList()
                totalPages = (res.totalPages ?: 1).coerceAtLeast(1)

                // 首页优先按销量排序：把销量补进商品并降序；翻页页保持服务端顺序
                if (reset && salesMap != null && salesMap.isNotEmpty()) {
                    list = list.map { it.copy(sales = salesMap[it.id] ?: it.sales) }
                        .sortedByDescending { it.sales ?: 0 }
                }

                if (reset) {
                    adapter.submit(list)
                    if (list.isNotEmpty()) CatalogCache.save(requireContext(), list)
                } else {
                    adapter.append(list)
                }
                page += 1

                binding.tvOffline.visibility = View.GONE
                binding.tvCount.text = countText(res)
                hideEmpty()

                if (adapter.size() == 0) {
                    showEmpty(
                        title = "这里还没有商品",
                        body = "店铺刚上新时会出现在这里，下拉可以刷新",
                        retry = false
                    )
                }
            } catch (e: Exception) {
                if (reset) {
                    // 网络不可用：回退到上次成功拉取的快照，而不是一片空白
                    val snapshot = CatalogCache.load(requireContext())
                    if (snapshot != null && snapshot.items.isNotEmpty()) {
                        adapter.submit(snapshot.items)
                        binding.tvOffline.visibility = View.VISIBLE
                        binding.tvOffline.text =
                            "离线数据 · 展示 ${snapshot.items.size} 件缓存的商品（" +
                                CatalogCache.savedAtText(requireContext()) + " 更新），联网后下拉刷新"
                        binding.tvCount.text = "共 ${snapshot.items.size} 件（离线）"
                        hideEmpty()
                    } else {
                        binding.tvOffline.visibility = View.GONE
                        showEmpty(
                            title = "没能连上店铺",
                            body = "检查一下网络，或稍后再试",
                            retry = true
                        )
                    }
                } else {
                    // 翻页失败不影响已经看到的内容，提示一次即可
                    Toast.makeText(requireContext(), "加载更多失败，稍后再试", Toast.LENGTH_SHORT).show()
                }
            } finally {
                loading = false
                binding.swipe.isRefreshing = false
                binding.pbMore.visibility = View.GONE
            }
        }
    }

    private fun countText(res: ProductPage): String {
        val total = res.totalElements
        return when {
            total != null && total > 0 -> "共 $total 件"
            else -> "共 ${adapter.size()} 件"
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
        val intent = Intent(requireContext(), ProductDetailActivity::class.java)
        intent.putExtra("product_id", p.id ?: 0L)
        startActivity(intent)
    }

    override fun onDestroyView() {
        binding.rv.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
