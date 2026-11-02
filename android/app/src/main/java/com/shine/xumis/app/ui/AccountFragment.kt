package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.FragmentAccountBinding
import com.shine.xumis.app.databinding.ItemOrderBinding
import kotlinx.coroutines.launch

/**
 * 原生「我的」：未登录显示登录/注册表单，已登录显示资料 + 我的订单 + 退出。
 * 全程走 /api/m/auth、/api/m/me、/api/m/orders，不跳 Web。
 */
class AccountFragment : Fragment() {
    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    /** true = 注册模式，false = 登录模式 */
    private var registerMode = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSubmit.setOnClickListener { submit() }
        binding.btnLogout.setOnClickListener { doLogout() }
        binding.tvToggle.setOnClickListener { toggleMode() }
        load()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) load()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && binding.profileLayout.visibility != View.VISIBLE) load()
    }

    private fun toggleMode() {
        registerMode = !registerMode
        binding.etName.visibility = if (registerMode) View.VISIBLE else View.GONE
        binding.btnSubmit.text = if (registerMode) "注册" else "登录"
        binding.tvToggle.text = if (registerMode) "已有账号？去登录" else "没有账号？去注册"
        binding.tvError.visibility = View.GONE
    }

    private fun submit() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val name = binding.etName.text.toString().trim()
        if (!email.contains("@")) { showError("请输入正确的邮箱"); return }
        if (password.length < 8) { showError("密码至少 8 位"); return }
        if (registerMode && name.isBlank()) { showError("请填写昵称 / 收货人姓名"); return }

        binding.pb.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false
        binding.tvError.visibility = View.GONE
        lifecycleScope.launch {
            try {
                if (registerMode) {
                    CommerceApi.register(email, password, name)
                    Toast.makeText(requireContext(), "注册成功，已自动登录", Toast.LENGTH_SHORT).show()
                } else {
                    CommerceApi.login(email, password)
                    Toast.makeText(requireContext(), "登录成功", Toast.LENGTH_SHORT).show()
                }
                resetForm()
                load()
            } catch (e: Exception) {
                showError(e.message ?: "操作失败")
            } finally {
                binding.pb.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
            }
        }
    }

    private fun doLogout() {
        lifecycleScope.launch {
            runCatching { CommerceApi.logout() }
            load()
        }
    }

    private fun resetForm() {
        binding.etPassword.setText("")
        registerMode = false
        binding.etName.visibility = View.GONE
        binding.btnSubmit.text = "登录"
        binding.tvToggle.text = "没有账号？去注册"
        binding.tvError.visibility = View.GONE
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun load() {
        binding.pb.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val me = CommerceApi.me()
                if (me.loggedIn && me.user != null) {
                    showProfile(me)
                } else {
                    binding.loginLayout.visibility = View.VISIBLE
                    binding.profileLayout.visibility = View.GONE
                }
            } catch (e: Exception) {
                // 网络不可用：回落到登录表单，避免白屏
                binding.loginLayout.visibility = View.VISIBLE
                binding.profileLayout.visibility = View.GONE
            } finally {
                binding.pb.visibility = View.GONE
            }
        }
    }

    private fun showProfile(me: com.shine.xumis.app.api.MeResult) {
        val u = me.user ?: return
        binding.loginLayout.visibility = View.GONE
        binding.profileLayout.visibility = View.VISIBLE
        binding.tvName.text = u.name ?: "shine 用户"
        binding.tvEmail.text = u.email

        binding.ordersContainer.removeAllViews()
        lifecycleScope.launch {
            val orders = runCatching { CommerceApi.myOrders() }.getOrDefault(emptyList())
            if (orders.isEmpty()) {
                binding.tvNoOrders.visibility = View.VISIBLE
            } else {
                binding.tvNoOrders.visibility = View.GONE
                val inflater = LayoutInflater.from(requireContext())
                for (o in orders) {
                    val card = ItemOrderBinding.inflate(inflater, binding.ordersContainer, false)
                    card.orderNo.text = "订单 ${o.orderNo}"
                    card.status.text = statusText(o.status)
                    card.date.text = o.createdAt
                    card.items.text = o.items.joinToString("\n") {
                        "${it.name ?: "商品"} × ${it.quantity}"
                    }
                    card.total.text = "合计 " + o.currency + " " +
                        String.format(java.util.Locale.US, "%.2f", o.total)
                    binding.ordersContainer.addView(card.root)
                }
            }
        }
    }

    private fun statusText(s: String): String = when (s.uppercase()) {
        "PENDING" -> "待处理"
        "PAID" -> "已付款"
        "SHIPPED" -> "已发货"
        "DONE" -> "已完成"
        "CANCELLED" -> "已取消"
        else -> s
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
