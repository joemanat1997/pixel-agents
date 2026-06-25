package com.example.applocker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.applocker.databinding.ActivitySettingsBinding

/**
 * Settings screen. Currently lets the user pick the app language; the choice is
 * applied immediately via AppCompat per-app locales and persisted automatically.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkCurrentLanguage()

        binding.languageGroup.setOnCheckedChangeListener { _, checkedId ->
            val tag = TAG_BY_ID[checkedId] ?: return@setOnCheckedChangeListener
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (current.startsWith(tag)) return@setOnCheckedChangeListener
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    private fun checkCurrentLanguage() {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val id = ID_BY_TAG.entries.firstOrNull { current.startsWith(it.key) }?.value
            ?: R.id.lang_en
        binding.languageGroup.check(id)
    }

    companion object {
        private val ID_BY_TAG = linkedMapOf(
            "th" to R.id.lang_th,
            "zh" to R.id.lang_zh,
            "ja" to R.id.lang_ja,
            "es" to R.id.lang_es,
            "nb" to R.id.lang_nb,
            "en" to R.id.lang_en
        )
        private val TAG_BY_ID = ID_BY_TAG.entries.associate { (tag, id) -> id to tag }
    }
}
