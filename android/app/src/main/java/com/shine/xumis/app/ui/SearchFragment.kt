package com.shine.xumis.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.api.CartApi
import com.shine.xumis.app.databinding.FragmentSearchBinding
import com.shine.xumis.app.model.CategoryItem
import com.shine.xumis.app.model.MobileProduct
import com.shine.xumis.app.ui.adapter.ProductAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val adapter = ProductAdapter(
        onClick = { openDetail(it) },
        onAddCart = { addToCart(it) }
    )
    private val categories = mutableListOf<CategoryItem>()

    private companion object {
        const val PAGE_SIZE = 20
        /** 输入停顿这么久再请求，避免每敲一个字打一次接口 */
        const val DEBOUNCE_MS = 400L
    }

    private var page = 0
    private var totalPages = 1
    private var loading = false
    private var searchJob: Job? = null
    private var categoryLoaded = false
    private var spinnerBound = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvResults.adapter = adapter
        binding.swipe.setOnRefreshListener { doSearch(reset = true) }
        binding.btnSearch.setOnClickListener { doSearch(reset = true) }
        binding.btnClear.setOnClickListener { clearConditions() }

        binding.etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                doSearch(reset = true)
                true
            } else false
        }

        // 输入即搜（带防抖）：不用每敲一次都点按钮
        binding.etQuery.doAfterTextChanged {
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(DEBOUNCE_MS)
                doSearch(reset = true)
            }
        }

        binding.rvResults.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = rv.layoutManager as? GridLayoutManager ?: return
                if (lm.findLastVisibleItemPosition() >= adapter.size() - 4) {
                    if (!loading && page < totalPages && adapter.size() > 0) doSearch(reset = false)
                }
            }
        })

        setupCategorySpinner()

        // 进页面先把「全部商品」铺出来，用户不用对着空白页发呆；
        // 想看某个词再敲，列表会被搜索结果替换。
        if (adapter.size() == 0) doSearch(reset = true)

        // 进页面就把键盘叫起来（1.5.3 修的那个「首点才弹输入法」）
        binding.etQuery.requestFocus()
        binding.etQuery.post { showKeyboard() }
    }

    private fun setupCategorySpinner() {
        if (categoryLoaded) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val cats = ApiClient.api().categories()
                categories.clear()
                categories.add(CategoryItem(null, "全部分类"))
                categories.addAll(cats)
                categoryLoaded = true
                val labels = categories.map { it.label ?: it.code ?: "?" }
                val spAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
                spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerCat.adapter = spAdapter
                binding.spinnerCat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                        // 首次绑定会立刻回调一次，那次不算用户选择
                        if (!spinnerBound) {
                            spinnerBound = true
                            return
                        }
                        doSearch(reset = true)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            } catch (e: Exception) {
                // 分类拉不到不影响搜索，静默降级为「全部分类」
            }
        }
    }

    private fun doSearch(reset: Boolean) {
        if (loading) return
        val ctx = context ?: return
        loading = true

        if (reset) {
            page = 0
            binding.swipe.isRefreshing = adapter.size() == 0
        } else {
            binding.pbMore.visibility = View.VISIBLE
        }

        val q = binding.etQuery.text.toString().trim()
        val sort = when (binding.spinnerSort.selectedItemPosition) {
            1 -> "price_asc"
            2 -> "price_desc"
            else -> "default"
        }
        val cat = categories.getOrNull(binding.spinnerCat.selectedItemPosition)?.code

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = ApiClient.api().search(
                    q = if (q.isEmpty()) null else q,
                    cat = cat,
                    sort = sort,
                    page = page,
                    size = PAGE_SIZE
                )
                val list = res.content ?: emptyList()
                totalPages = (res.totalPages ?: 1).coerceAtLeast(1)
                if (reset) adapter.submit(list) else adapter.append(list)
                page += 1

                binding.tvCount.text = "共 ${res.totalElements ?: adapter.size()} 件"

                if (adapter.size() == 0) {
                    showEmpty(
                        "没有找到相关商品",
                        "换个关键词，或把分类改成「全部分类」再试",
                        showClear = q.isNotEmpty() || cat != null
                    )
                } else {
                    hideEmpty()
                }
            } catch (e: Exception) {
                if (reset) {
                    adapter.submit(emptyList())
                    showEmpty("搜索没能完成", "网络似乎不太稳定，稍后再试一次", showClear = false)
                }
                Toast.makeText(ctx, "搜索失败，请稍后重试", Toast.LENGTH_SHORT).show()
            } finally {
                loading = false
                if (_binding != null) {
                    binding.swipe.isRefreshing = false
                    binding.pbMore.visibility = View.GONE
                }
            }
        }
    }

    private fun clearConditions() {
        binding.etQuery.setText("")
        binding.spinnerCat.setSelection(0)
        binding.spinnerSort.setSelection(0)
        hideKeyboard()
        doSearch(reset = true)
    }

    private fun showEmpty(title: String, body: String, showClear: Boolean) {
        binding.emptyState.visibility = View.VISIBLE
        binding.tvEmptyTitle.text = title
        binding.tvEmptyBody.text = body
        binding.btnClear.visibility = if (showClear) View.VISIBLE else View.GONE
    }

    private fun hideEmpty() {
        binding.emptyState.visibility = View.GONE
        binding.btnClear.visibility = View.GONE
    }

    private fun showKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.etQuery, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etQuery.windowToken, 0)
        binding.etQuery.clearFocus()
    }

    private fun openDetail(p: MobileProduct) {
        hideKeyboard()
        val intent = Intent(requireContext(), ProductDetailActivity::class.java)
        intent.putExtra("product_id", p.id ?: 0L)
        startActivity(intent)
    }

    /** 列表里点加购按钮：原生加购，带 loading 态，加完刷新角标 */
    private fun addToCart(p: MobileProduct) {
        val id = p.id ?: return
        adapter.setAdding(id, true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val count = CartApi.add(id)
                CartApi.publish(count)
                Toast.makeText(requireContext(), "已加入购物车（$count 件）", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "加购失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                adapter.setAdding(id, false)
            }
        }
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        binding.rvResults.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
