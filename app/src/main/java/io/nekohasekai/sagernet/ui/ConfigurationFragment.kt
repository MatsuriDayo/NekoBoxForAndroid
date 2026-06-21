@@
             R.id.action_new_vless -> {
                 startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java).apply {
                     putExtra("vless", true)
                 })
             }
+            R.id.action_quick_import -> {
+                startActivity(Intent(requireActivity(), io.nekohasekai.sagernet.ui.importer.QuickImportActivity::class.java))
+            }
@@
         }
         return true
     }
