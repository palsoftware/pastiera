package it.palsoftware.pastiera.data.layout

import android.content.Context
import android.content.res.AssetManager
import android.util.Log

/**
 * Loads layout mappings from either bundled JSON assets or user-provided files.
 */
object JsonLayoutLoader {
    private const val TAG = "JsonLayoutLoader"

    fun loadLayout(
        assets: AssetManager,
        layoutName: String,
        context: Context? = null
    ): Map<Int, LayoutMapping>? {
        if (context != null) {
            val customLayout = LayoutFileStore.loadLayoutFromFile(
                LayoutFileStore.getLayoutFile(context, layoutName)
            )
            if (customLayout != null) {
                Log.d(TAG, "Loaded custom layout: $layoutName with ${customLayout.size} mappings")
                return customLayout
            }
        }

        return loadLayoutFromAssets(assets, layoutName)
    }

    private fun loadLayoutFromAssets(
        assets: AssetManager,
        layoutName: String
    ): Map<Int, LayoutMapping>? {
        return try {
            val filePath = "common/layouts/$layoutName.json"
            assets.open(filePath).use { inputStream ->
                LayoutFileStore.loadLayoutFromStream(inputStream)
            }?.also {
                Log.d(TAG, "Loaded layout from assets: $layoutName with ${it.size} mappings")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading layout from assets: $layoutName", e)
            null
        }
    }

    fun getAvailableLayouts(
        assets: AssetManager,
        context: Context? = null
    ): List<String> {
        val layouts = mutableSetOf<String>()
        context?.let { layouts.addAll(LayoutFileStore.getCustomLayoutNames(it)) }

        return try {
            val layoutFiles = assets.list("common/layouts")
            layoutFiles?.forEach { fileName ->
                if (fileName.endsWith(".json")) {
                    layouts.add(fileName.removeSuffix(".json"))
                }
            }
            layouts.sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available layouts from assets", e)
            layouts.sorted()
        }
    }
}

