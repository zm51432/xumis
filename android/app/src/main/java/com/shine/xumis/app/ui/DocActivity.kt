package com.shine.xumis.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shine.xumis.app.databinding.ActivityDocBinding
import com.shine.xumis.app.util.Docs

/**
 * 本地文档页（隐私政策 / 服务条款）。
 * 内容随安装包内置，断网也能看，不依赖 WebView。
 */
class DocActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_KEY = "doc_key"

        fun intent(ctx: Context, key: String): Intent =
            Intent(ctx, DocActivity::class.java).putExtra(EXTRA_KEY, key)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityDocBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val key = intent.getStringExtra(EXTRA_KEY) ?: Docs.PRIVACY
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.title = Docs.title(key)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        binding.tvBody.text = Docs.body(key)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
