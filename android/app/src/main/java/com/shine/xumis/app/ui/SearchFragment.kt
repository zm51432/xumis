package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.FragmentSearchBinding
import com.shine.xumis.app.model.CategoryItem
import com.shine.xumis.app.model.MobileProduct
import com.shine.xumis.app.ui.adapter.ProductAdapter
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val adapter = ProductAdapter { openDetail(it) }
    private val categories = mutableListOf<CategoryItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvResults.adapter = adapter
        setupCategorySpinner()
        binding.btnSearch.setOnClickListener { doSearch() }
        binding.etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(); true } else false
        }
    }

    private fun setupCategorySpinner() {
        lifecycleScope.launch {
            try {
                val cats = ApiClient.api().categories()
                categories.clear()
                categories.add(CategoryItem(null, "全部分类"))
                categories.addAll(cats)
                val labels = categories.map { it.label ?: it.code ?: "?" }
                val spAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
                spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerCat.adapter = spAdapter
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "分类加载失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun doSearch() {
        val q = binding.etQuery.text.toString().trim()
        val sort = when (binding.spinnerSort.selectedItemPosition) {
            1 -> "price_asc"
            2 -> "price_desc"
            else -> "default"
        }
        val cat = categories.getOrNull(binding.spinnerCat.selectedItemPosition)?.code
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            try {
                val page = ApiClient.api().search(
                    q = if (q.isEmpty()) null else q,
                    cat = cat,
                    sort = sort,
                    page = 0,
                    size = 30
                )
                adapter.submit(page.content ?: emptyList())
                if ((page.content ?: emptyList()).isEmpty()) {
                    Toast.makeText(requireContext(), "没有找到相关商品", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "搜索失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.swipe.isRefreshing = false
            }
        }
    }

    private fun openDetail(p: MobileProduct) {
        val intent = Intent(requireContext(), ProductDetailActivity::class.java)
        intent.putExtra("product_id", p.id ?: 0L)
        startActivity(intent)
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
