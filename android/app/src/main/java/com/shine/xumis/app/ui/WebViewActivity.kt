package com.shine.xumis.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.shine.xumis.app.databinding.ActivityWebviewBinding

class WebViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        val url = intent.getStringExtra("url") ?: "about:blank"
        binding.web.settings.javaScriptEnabled = true
        binding.web.settings.domStorageEnabled = true
        binding.web.webViewClient = android.webkit.WebViewClient()
        binding.web.loadUrl(url)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.web.canGoBack()) binding.web.goBack() else finish()
            }
        })
    }
}
