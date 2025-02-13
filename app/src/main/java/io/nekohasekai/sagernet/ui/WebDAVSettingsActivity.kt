package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import com.google.android.material.snackbar.Snackbar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class WebDAVSettingsActivity : AppCompatActivity() {
    
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.layout_webdav_settings)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.webdav_settings)
            setDisplayHomeAsUpEnabled(true)
        }
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, WebDAVSettingsFragment())
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class WebDAVSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.configurationStore
            addPreferencesFromResource(R.xml.webdav_preferences)
            
            findPreference<EditTextPreference>("webdavServer")?.apply {
                setOnBindEditTextListener { it.setSingleLine() }
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            }
            
            findPreference<EditTextPreference>("webdavUsername")?.apply {
                setOnBindEditTextListener { it.setSingleLine() }
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            }
            
            findPreference<EditTextPreference>("webdavPassword")?.apply {
                setOnBindEditTextListener { it.setSingleLine() }
                summary = "********"
            }
            
            findPreference<EditTextPreference>("webdavPath")?.apply {
                setOnBindEditTextListener { it.setSingleLine() }
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            }
            
            findPreference<Preference>("webdavTest")?.setOnPreferenceClickListener {
                testWebDAV()
                true  // 返回 true 表示事件已被处理
            }
        }

        private fun testWebDAV() {
            runOnDefaultDispatcher {
                try {
                    val server = DataStore.webdavServer ?: ""
                    if (server.isBlank()) {
                        throw Exception(getString(R.string.webdav_server_empty))
                    }

                    val url = URL(server)
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .build()

                    // 首先测试连接和认证
                    val authRequest = Request.Builder()
                        .url(url)
                        .method("PROPFIND", null)
                        .apply {
                            val credentials = Credentials.basic(
                                DataStore.webdavUsername ?: "",
                                DataStore.webdavPassword ?: ""
                            )
                            header("Authorization", credentials)
                            header("Depth", "0")
                        }
                        .build()

                    val response = client.newCall(authRequest).execute()
                    
                    when (response.code) {
                        401 -> throw Exception(getString(R.string.webdav_auth_error))
                        403 -> throw Exception(getString(R.string.webdav_permission_denied))
                        404 -> throw Exception(getString(R.string.webdav_server_not_found))
                        in 500..599 -> throw Exception(getString(R.string.webdav_server_error))
                    }

                    if (!response.isSuccessful) {
                        throw Exception(getString(R.string.webdav_connect_failed, response.code))
                    }

                    // 如果认证成功，再测试目录操作
                    val path = (DataStore.webdavPath ?: "").trim('/')
                    if (path.isNotBlank()) {
                        val dirRequest = Request.Builder()
                            .url("${url}${if (url.toString().endsWith("/")) "" else "/"}$path")
                            .method("MKCOL", null)
                            .apply {
                                val credentials = Credentials.basic(
                                    DataStore.webdavUsername ?: "",
                                    DataStore.webdavPassword ?: ""
                                )
                                header("Authorization", credentials)
                            }
                            .build()

                        val dirResponse = client.newCall(dirRequest).execute()
                        if (!dirResponse.isSuccessful && dirResponse.code != 405) {  // 405 表示目录已存在
                            throw Exception(getString(R.string.webdav_create_dir_failed))
                        }
                    }

                    onMainDispatcher {
                        Snackbar.make(
                            requireView(),
                            getString(R.string.webdav_test_success),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    onMainDispatcher {
                        Snackbar.make(
                            requireView(),
                            getString(R.string.webdav_test_failed, e.message),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}
