package io.nekohasekai.sagernet.ui.profile

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import com.blacksquircle.ui.editorkit.insert
import com.blacksquircle.ui.language.json.JsonLanguage
import com.github.shadowsocks.plugin.Empty
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutEditConfigBinding
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.toStringPretty
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.widget.ListListener
import moe.matsuri.nb4a.ui.ExtendedKeyboard
import org.json.JSONObject

class ConfigEditActivity : ThemedActivity() {

    var dirty = false
    var key = Key.SERVER_CONFIG
    var useConfigStore = false

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.unsaved_changes_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                (requireActivity() as ConfigEditActivity).saveAndExit()
            }
            setNegativeButton(R.string.no) { _, _ ->
                requireActivity().finish()
            }
            setNeutralButton(android.R.string.cancel, null)
        }
    }

    lateinit var binding: LayoutEditConfigBinding

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.extras?.apply {
            getString("key")?.let { key = it }
            getString("useConfigStore")?.let { useConfigStore = true }
        }

        binding = LayoutEditConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.config_settings)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        binding.editor.apply {
            language = JsonLanguage()
            setHorizontallyScrolling(true)
            if (useConfigStore) {
                setTextContent(DataStore.configurationStore.getString(key) ?: "")
            } else {
                setTextContent(DataStore.profileCacheStore.getString(key) ?: "")
            }
            addTextChangedListener {
                if (!dirty) {
                    dirty = true
                    DataStore.dirty = true
                }
            }
        }

        binding.actionTab.setOnClickListener {
            try {
                binding.editor.insert(binding.editor.tab())
            } catch (e: Exception) {
            }
        }
        binding.actionUndo.setOnClickListener {
            try {
                binding.editor.undo()
            } catch (_: Exception) {
            }
        }
        binding.actionRedo.setOnClickListener {
            try {
                binding.editor.redo()
            } catch (_: Exception) {
            }
        }
        binding.actionFormat.setOnClickListener {
            formatText()?.let {
                binding.editor.setTextContent(it)
            }
        }

        val extendedKeyboard = findViewById<ExtendedKeyboard>(R.id.extended_keyboard)
        extendedKeyboard.setKeyListener { char ->
            try {
                binding.editor.insert(char)
            } catch (e: Exception) {
            }
        }
        extendedKeyboard.setHasFixedSize(true)
        extendedKeyboard.submitList("{},:_\"".map { it.toString() })
        extendedKeyboard.setBackgroundColor(getColorAttr(R.attr.primaryOrTextPrimary))

        val keyboardContainer = findViewById<LinearLayout>(R.id.keyboard_container)
        ViewCompat.setOnApplyWindowInsetsListener(keyboardContainer) { v, windowInsets ->
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())
            v.updateLayoutParams<MarginLayoutParams> {
                // systemBar insets are applied to the bottom of the keyboard
                if (imeVisible) {
                    bottomMargin = imeInsets.bottom - systemBarInsets.bottom
                } else {
                    bottomMargin = 0
                }
            }

            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root, ListListener)
    }

    fun formatText(): String? {
        try {
            val txt = binding.editor.text.toString()
            if (txt.isBlank()) {
                return ""
            }
            return JSONObject(txt).toStringPretty()
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.error_title)
                .setMessage(e.readableMessage).show()
            return null
        }
    }

    fun saveAndExit() {
        formatText()?.let {
            if (useConfigStore) {
                DataStore.configurationStore.putString(key, it)
            } else {
                DataStore.profileCacheStore.putString(key, it)
            }
            finish()
        }
    }

    override fun onBackPressed() {
        if (dirty) UnsavedChangesDialogFragment().apply { key() }
            .show(supportFragmentManager, null) else super.onBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.profile_apply_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_apply -> {
                saveAndExit()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}