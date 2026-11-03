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
import com.shine.xumis.app.R
import com.shine.xumis.app.api.CartApi
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.databinding.FragmentAccountBinding
import com.shine.xumis.app.databinding.ItemMenuRowBinding
import kotlinx.coroutines.launch

/**
 * 原生「我的」：未登录显示登录/注册表单；已登录显示账号头 + 堆叠菜单
 * （收货资料 / 修改密码 / 我的订单 / 支付方式）+ 退出登录，全程不跳 Web。
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
        binding.tvForgot.setOnClickListener {
            startActivity(Intent(requireContext(), ForgotPasswordActivity::class.java))
        }
        binding.tvReset.setOnClickListener {
            startActivity(Intent(requireContext(), ResetPasswordActivity::class.java))
        }
        buildGuestMenu()
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
                // 登录态变了，购物车是另一份会话，顺手把角标同步过来
                runCatching { CommerceApi.cart().count }.getOrNull()?.let { CartApi.publish(it) }
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
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("退出登录")
            .setMessage("确定要退出当前账号吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("退出") { _, _ -> performLogout() }
            .show()
    }

    private fun performLogout() {
        lifecycleScope.launch {
            runCatching { CommerceApi.logout() }
            // 退出后购物车是另一份会话，角标要归零，避免残留数字误导
            CartApi.publish(0)
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
        buildMenu()
    }

    /** 堆叠菜单：收货资料 / 修改密码 / 我的订单 / 我的记录 / 来图定制 / 在线咨询 / 支付方式 */
    private fun buildMenu() {
        binding.menuContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val items = listOf(
            Triple(R.drawable.ic_location, "收货资料") { startActivity(Intent(requireContext(), ProfileActivity::class.java)) },
            Triple(R.drawable.ic_shield, "修改密码") { startActivity(Intent(requireContext(), PasswordActivity::class.java)) },
            Triple(R.drawable.ic_doc, "我的订单") { startActivity(Intent(requireContext(), OrdersActivity::class.java)) },
            Triple(R.drawable.ic_info, "我的记录") { startActivity(Intent(requireContext(), MyRecordsActivity::class.java)) },
            Triple(R.drawable.ic_image, "来图定制") { startActivity(Intent(requireContext(), CustomIdeaActivity::class.java)) },
            Triple(R.drawable.ic_help, "在线咨询") { startActivity(Intent(requireContext(), InquiryActivity::class.java)) },
            Triple(R.drawable.ic_card, "支付方式") { startActivity(Intent(requireContext(), PaymentActivity::class.java)) }
        )
        for ((icon, label, action) in items) {
            val row = ItemMenuRowBinding.inflate(inflater, binding.menuContainer, false)
            row.icon.setImageResource(icon)
            row.label.text = label
            row.root.setOnClickListener { action() }
            binding.menuContainer.addView(row.root)
        }
    }

    /**
     * 游客菜单：未登录也能查订单（按下单邮箱回查）、看记录、提定制。
     * 之前这些入口只在登录后显示，游客下完单根本找不到订单，属于断头路。
     */
    private fun buildGuestMenu() {
        binding.guestMenuContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val items = listOf(
            Triple(R.drawable.ic_doc, "我的订单（按邮箱查询）") { startActivity(Intent(requireContext(), OrdersActivity::class.java)) },
            Triple(R.drawable.ic_info, "我的记录") { startActivity(Intent(requireContext(), MyRecordsActivity::class.java)) },
            Triple(R.drawable.ic_image, "来图定制") { startActivity(Intent(requireContext(), CustomIdeaActivity::class.java)) }
        )
        for ((icon, label, action) in items) {
            val row = ItemMenuRowBinding.inflate(inflater, binding.guestMenuContainer, false)
            row.icon.setImageResource(icon)
            row.label.text = label
            row.root.setOnClickListener { action() }
            binding.guestMenuContainer.addView(row.root)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
