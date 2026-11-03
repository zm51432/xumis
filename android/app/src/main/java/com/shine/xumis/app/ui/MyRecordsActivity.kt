package com.shine.xumis.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.shine.xumis.app.databinding.ActivityMyRecordsBinding
import com.shine.xumis.app.databinding.ItemRecordBinding
import com.shine.xumis.app.util.LocalRecords
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 我的记录：本机提交过的来图定制 / 在线咨询。
 * 补上「提交后无处可查」的断点，形成 提交 → 留档 → 回看 的闭环。
 */
class MyRecordsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyRecordsBinding
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }
        load()
    }

    private fun load() {
        val records = LocalRecords.all(this)
        binding.recordsContainer.removeAllViews()
        binding.emptyState.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)
        for (r in records) {
            val card = ItemRecordBinding.inflate(inflater, binding.recordsContainer, false)
            card.tvType.text = if (r.type == LocalRecords.TYPE_CUSTOM) "来图定制" else "在线咨询"
            card.tvTime.text = fmt.format(Date(r.createdAt))
            card.tvTitle.text = r.title.ifBlank { "未命名记录" }
            card.tvDetail.text = r.detail
            card.tvEmail.text = r.email
            card.tvStatus.text = r.status
            binding.recordsContainer.addView(card.root)
        }
    }
}
