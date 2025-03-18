package org.shibig666.qmyz.data

import org.json.JSONArray
import android.content.Context
import androidx.core.content.edit
import java.util.regex.Pattern
import android.util.Log

class UserItem(
    val name: String,
    val url: String
) {

}

fun saveUserItems(context: Context, items: List<UserItem>) {
    val sharedPrefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
    val jsonItems = items.map {
        "{\"name\":\"${it.name}\",\"url\":\"${it.url}\"}"
    }
    val jsonArray = jsonItems.toString()
    sharedPrefs.edit() { putString("user_items", jsonArray) }
}

fun loadUserItems(context: Context): List<UserItem>? {
    val sharedPrefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
    val jsonArray = sharedPrefs.getString("user_items", null) ?: return null

    try {
        val jsonItems = JSONArray(jsonArray)
        return (0 until jsonItems.length()).map { i ->
            val jsonItem = jsonItems.getJSONObject(i)
            UserItem(
                name = jsonItem.getString("name"),
                url = jsonItem.getString("url")
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

// 验证URL格式是否正确
fun isValidUrl(url: String): Boolean {
    Log.d("UserItem", "Url: $url")
    val regex = "^https?://.*\$"
    val pattern = Pattern.compile(regex)
    val matcher = pattern.matcher(url)
    val isValid = matcher.matches()
    Log.d("UserItem", "isValidUrl: $isValid")
    return isValid
}