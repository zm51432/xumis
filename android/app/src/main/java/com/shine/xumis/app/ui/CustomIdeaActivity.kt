package com.shine.xumis.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shine.xumis.app.R
import com.shine.xumis.app.api.CommerceApi
import com.shine.xumis.app.api.MeResult
import com.shine.xumis.app.databinding.ActivityCustomIdeaBinding
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.regex.Pattern

/** 来图定制（原生）：提交定制需求，支持从相册 / 拍照选择参考图 */
class CustomIdeaActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCustomIdeaBinding
    private var selectedUri: Uri? = null
    private var cameraUri: Uri? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onImageChosen(it) }
    }
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && cameraUri != null) onImageChosen(cameraUri!!)
    }
    private val cameraPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, "未授予相机权限，可使用相册选择", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomIdeaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        setupCategoryDropdown()
        binding.layoutPhoto.setOnClickListener { openImageSourceChooser() }
        binding.btnRemovePhoto.setOnClickListener { clearImage() }
        binding.btnSubmit.setOnClickListener { submit() }

        prefillIfLoggedIn()
    }

    private fun setupCategoryDropdown() {
        val items = listOf("饰品", "手工品", "其他")
        binding.actCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, items))
    }

    /** 把界面显示的中文品类映射成服务端枚举名（JEWELRY / HANDMADE / OTHER） */
    private fun categoryEnum(): String? = when (binding.actCategory.text?.toString()?.trim()) {
        "饰品" -> "JEWELRY"
        "手工品" -> "HANDMADE"
        "其他" -> "OTHER"
        else -> null
    }

    /** 已登录用户自动预填姓名 / 邮箱，减少重复输入 */
    private fun prefillIfLoggedIn() {
        lifecycleScope.launch {
            runCatching { CommerceApi.me() }.onSuccess { me: MeResult ->
                if (me.loggedIn && me.user != null) {
                    val u = me.user
                    if (binding.etName.text.isNullOrBlank() && !u.name.isNullOrBlank()) binding.etName.setText(u.name)
                    if (binding.etEmail.text.isNullOrBlank() && !u.email.isNullOrBlank()) binding.etEmail.setText(u.email)
                }
            }
        }
    }

    private fun openImageSourceChooser() {
        val choices = mutableListOf("从相册选择")
        val hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        if (hasCamera) choices.add("拍照")
        MaterialAlertDialogBuilder(this)
            .setTitle("添加参考图")
            .setItems(choices.toTypedArray()) { _, which ->
                if (which == 0) galleryLauncher.launch("image/*")
                else cameraPermLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "captures").also { it.mkdirs() }
        val file = File(dir, "ci_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraLauncher.launch(cameraUri)
    }

    private fun onImageChosen(uri: Uri) {
        selectedUri = uri
        Glide.with(this).load(uri).into(binding.imgPreview)
        binding.tvPhotoState.text = "已选择 1 张参考图"
        binding.btnRemovePhoto.visibility = View.VISIBLE
    }

    private fun clearImage() {
        selectedUri = null
        cameraUri = null
        binding.imgPreview.setImageResource(R.drawable.ic_image)
        binding.tvPhotoState.text = "从相册选择或拍照，帮助工匠更懂你的想法"
        binding.btnRemovePhoto.visibility = View.GONE
    }

    private fun buildImagePart(): MultipartBody.Part? {
        val uri = selectedUri ?: return null
        val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return null
        val ext = runCatching {
            val t = contentResolver.getType(uri)?.substringAfterLast("/") ?: ""
            if (EXT.matcher(t).matches()) t else "jpg"
        }.getOrDefault("jpg")
        val body = bytes.toRequestBody("image/$ext".toMediaType())
        return MultipartBody.Part.createFormData("referenceImage", "reference.$ext", body)
    }

    private fun submit() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val desc = binding.etDesc.text.toString().trim()
        if (name.isBlank()) { binding.etName.error = "请填写姓名"; return }
        if (!email.contains("@")) { binding.etEmail.error = "请填写正确的邮箱"; return }
        if (desc.isBlank()) { binding.etDesc.error = "请描述定制想法"; return }

        val imagePart = buildImagePart()
        binding.pb.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            try {
                CommerceApi.customIdea(
                    customerName = name,
                    email = email,
                    country = binding.etCountry.text.toString().trim().takeIf { it.isNotBlank() },
                    description = desc,
                    expectCategory = categoryEnum(),
                    imagePart = imagePart
                )
                showSuccess(email)
            } catch (e: Exception) {
                Toast.makeText(this@CustomIdeaActivity, "提交失败：${e.message}", Toast.LENGTH_LONG).show()
                binding.btnSubmit.isEnabled = true
            } finally {
                binding.pb.visibility = View.GONE
            }
        }
    }

    private fun showSuccess(email: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("已收到你的定制需求")
            .setMessage("我们会尽快通过邮箱（$email）与你联系。\n参考图已一并提交给工匠。")
            .setCancelable(false)
            .setPositiveButton("完成") { _, _ -> finish() }
            .show()
    }

    companion object {
        private val EXT = Pattern.compile("[a-zA-Z0-9]+")
    }
}
