package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.FragmentHomeBinding
import com.shine.xumis.app.model.MobileProduct
import com.shine.xumis.app.ui.adapter.ProductAdapter
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val adapter = ProductAdapter { openDetail(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rv.adapter = adapter
        binding.swipe.setOnRefreshListener { load() }
        binding.btnRetry.setOnClickListener { load() }
        load()
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        binding.btnRetry.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val page = ApiClient.api().products(0, 30)
                val list = page.content ?: emptyList()
                adapter.submit(list)
                binding.btnRetry.visibility = View.GONE
                if (list.isEmpty()) Toast.makeText(requireContext(), "暂无商品，请确认后端已初始化", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.btnRetry.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "加载失败：${e.message}", Toast.LENGTH_LONG).show()
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
