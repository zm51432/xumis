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
import com.shine.xumis.app.databinding.FragmentCartBinding
import com.shine.xumis.app.model.CartItem
import com.shine.xumis.app.model.CartResponse
import com.shine.xumis.app.ui.adapter.CartAdapter
import com.shine.xumis.app.util.ServerConfig
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 原生购物车：直接调用 /api/m/cart 系列接口渲染，
 * 商品、数量、合计、去结算全部原生渲染，不加载任何网页。
 */
class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private val adapter = CartAdapter(
        onQtyChange = { item, qty -> changeQty(item, qty) },
        onRemove = { item -> removeItem(item) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvCart.adapter = adapter
        binding.btnCheckout.setOnClickListener { goCheckout() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                render(ApiClient.api().cart())
            } catch (e: Exception) {
                toast("购物车加载失败：${e.message}")
            }
        }
    }

    private fun render(cart: CartResponse) {
        val items = cart.items ?: emptyList()
        adapter.submit(items)
        val empty = items.isEmpty()
        binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvCart.visibility = if (empty) View.GONE else View.VISIBLE
        binding.btnCheckout.isEnabled = !empty
        binding.btnCheckout.alpha = if (empty) 0.4f else 1f
        binding.tvTotal.text = String.format(Locale.US, "合计 US$%.2f", cart.total ?: 0.0)
    }

    private fun changeQty(item: CartItem, qty: Int) {
        val id = item.productId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = if (qty <= 0) ApiClient.api().cartRemove(id) else ApiClient.api().cartSet(id, qty)
                render(res)
            } catch (e: Exception) {
                toast("更新失败：${e.message}")
            }
        }
    }

    private fun removeItem(item: CartItem) {
        val id = item.productId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                render(ApiClient.api().cartRemove(id))
                toast("已从购物车移除")
            } catch (e: Exception) {
                toast("删除失败：${e.message}")
            }
        }
    }

    private fun goCheckout() {
        // App 内原生结算，不再跳网页
        startActivity(Intent(requireContext(), CheckoutActivity::class.java))
    }

    private fun toast(msg: String) {
        if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
