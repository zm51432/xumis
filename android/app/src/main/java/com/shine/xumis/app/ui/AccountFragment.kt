package com.shine.xumis.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.shine.xumis.app.R
import com.shine.xumis.app.api.ApiClient
import com.shine.xumis.app.databinding.FragmentAccountBinding
import com.shine.xumis.app.model.MeResponse
import com.shine.xumis.app.model.OrderBrief
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 原生的「我的」页：未登录显示登录表单（邮箱 + 密码），
 * 已登录显示真实账号信息（邮箱、昵称）与该账号的订单列表。
 * 不再把整张 /account 网页塞进 WebView。
 */
class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnLogin.setOnClickListener { doLogin() }
        binding.btnLogout.setOnClickListener { doLogout() }
        // 注册 / 找回密码都走原生页面，不再打开网页
        binding.tvGoRegister.setOnClickListener {
            startActivity(
                Intent(requireContext(), RegisterActivity::class.java)
                    .putExtra("email", binding.etEmail.text.toString().trim())
            )
        }
        binding.tvGoForgot.setOnClickListener {
            startActivity(
                Intent(requireContext(), ResetPasswordActivity::class.java)
                    .putExtra("email", binding.etEmail.text.toString().trim())
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // 从注册/找回密码页返回时，登录态可能已变化
        refresh()
    }

    private fun refresh() {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                render(ApiClient.api().me())
            } catch (e: Exception) {
                // 网络异常时保留上次界面，只提示
                toast("获取账号信息失败：${e.message}")
            }
        }
    }

    private fun render(me: MeResponse) {
        if (!isAdded) return
        if (!me.loggedIn) {
            showLogin()
            return
        }
        binding.boxLogin.visibility = View.GONE
        binding.boxProfile.visibility = View.VISIBLE

        val email = me.email ?: ""
        binding.tvEmail.text = email
        val name = me.name?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
        binding.tvName.text = name
        binding.tvAvatar.text = name.take(1).uppercase().ifBlank { "S" }

        binding.boxOrders.removeAllViews()
        val orders = me.orders ?: emptyList()
        binding.tvNoOrders.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
        orders.forEach { binding.boxOrders.addView(orderRow(it)) }
    }

    private fun showLogin() {
        binding.boxProfile.visibility = View.GONE
        binding.boxLogin.visibility = View.VISIBLE
        binding.tvProgress.visibility = View.GONE
        binding.etPassword.setText("")
    }

    /** 订单行 → 点击进入原生「账单详情」，不再弹网页 */
    private fun orderRow(o: OrderBrief): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(28, 26, 28, 26)
            setBackgroundResource(R.drawable.bg_panel)
        }

        val left = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(TextView(ctx).apply {
            text = o.orderNo ?: "—"
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ctx.getColor(R.color.shine_text))
        })
        left.addView(TextView(ctx).apply {
            text = "${statusText(o.status)} · ${o.itemCount ?: 0} 件 · ${shortTime(o.createdAt)}"
            textSize = 11.5f
            setTextColor(ctx.getColor(R.color.shine_sub))
            setPadding(0, 6, 0, 0)
        })

        val right = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.END
        }
        right.addView(TextView(ctx).apply {
            text = String.format(Locale.US, "US$%.2f", o.totalAmount ?: 0.0)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ctx.getColor(R.color.shine_pink))
            gravity = android.view.Gravity.END
        })
        right.addView(TextView(ctx).apply {
            text = "查看账单 ›"
            textSize = 11.5f
            setTextColor(ctx.getColor(R.color.shine_mint_dark))
            gravity = android.view.Gravity.END
            setPadding(0, 6, 0, 0)
        })

        row.addView(left)
        row.addView(right)

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = 8
        row.layoutParams = lp

        row.setOnClickListener {
            val no = o.orderNo ?: return@setOnClickListener
            startActivity(
                Intent(requireContext(), OrderDetailActivity::class.java).putExtra("order_no", no)
            )
        }
        return row
    }

    /** 2026-11-02T18:46:28 → 11-02 18:46 */
    private fun shortTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        val t = raw.replace("T", " ")
        return if (t.length >= 16) t.substring(5, 16) else t
    }

    private fun statusText(status: String?): String = when (status?.uppercase()) {
        "PENDING" -> "待支付"
        "PAID" -> "已支付"
        "SHIPPED" -> "已发货"
        "DONE" -> "已完成"
        "CANCELLED" -> "已取消"
        else -> status ?: "—"
    }

    private fun doLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        if (email.isBlank() || !email.contains("@")) {
            toast("请输入正确的邮箱")
            return
        }
        if (password.isBlank()) {
            toast("请输入密码")
            return
        }
        binding.tvProgress.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = ApiClient.api().login(email, password)
                binding.tvProgress.visibility = View.GONE
                binding.btnLogin.isEnabled = true
                if (resp.isSuccessful && resp.body()?.ok == true) {
                    toast("登录成功")
                    binding.etPassword.setText("")
                    refresh()
                } else {
                    when (resp.body()?.error) {
                        "locked" -> toast("尝试次数过多，请稍后再试")
                        else -> toast("邮箱或密码不正确")
                    }
                }
            } catch (e: Exception) {
                binding.tvProgress.visibility = View.GONE
                binding.btnLogin.isEnabled = true
                toast("登录失败：${e.message}")
            }
        }
    }

    private fun doLogout() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { ApiClient.api().logout() }
            ApiClient.clearSession()
            showLogin()
            toast("已退出登录")
        }
    }

    private fun toast(msg: String) {
        if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
