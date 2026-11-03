package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shine.xumis.app.api.CartApi
import com.shine.xumis.app.api.CartLine
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.FragmentCartBinding
import com.shine.xumis.app.ui.adapter.CartAdapter
import kotlinx.coroutines.launch

/**
 * 原生购物车：直接读 /api/m/cart，支持改数量 / 删除 / 去结算，全程不跳 Web。
 */
class CartFragment : Fragment() {
    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private val adapter = CartAdapter(
        onQtyChange = { line, qty -> changeQty(line, qty) },
        onRemove = { line -> removeLine(line) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rv.adapter = adapter
        binding.swipe.setOnRefreshListener { load() }
        binding.btnCheckout.setOnClickListener {
            startActivity(Intent(requireContext(), CheckoutActivity::class.java))
        }
        load()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) load()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && adapter.itemCount == 0) load()
    }

    private fun load() {
        binding.pb.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val data = CommerceApi.cart()
                adapter.submit(data.lines)
                binding.tvTotal.text = "$" + String.format(java.util.Locale.US, "%.2f", data.total)
                binding.bottomBar.visibility = if (data.isEmpty) View.GONE else View.VISIBLE
                binding.emptyState.visibility = if (data.isEmpty) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "加载购物车失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.pb.visibility = View.GONE
                binding.swipe.isRefreshing = false
            }
        }
    }

    private fun changeQty(line: CartLine, qty: Int) {
        if (qty <= 0) { removeLine(line); return }
        adapter.setBusy(line.id, true)
        lifecycleScope.launch {
            try {
                val count = CommerceApi.update(line.id, qty)
                CartApi.publish(count)
                load()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "修改失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                adapter.setBusy(line.id, false)
            }
        }
    }

    private fun removeLine(line: CartLine) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("移除商品")
            .setMessage("确定把「${line.name}」移出购物车吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("移除") { _, _ -> doRemoveLine(line) }
            .show()
    }

    private fun doRemoveLine(line: CartLine) {
        adapter.setBusy(line.id, true)
        lifecycleScope.launch {
            try {
                val count = CommerceApi.remove(line.id)
                CartApi.publish(count)
                load()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "删除失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                adapter.setBusy(line.id, false)
            }
        }
    }

    override fun onDestroyView() {
        binding.rv.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
