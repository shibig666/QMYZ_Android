package org.shibig666.qmyz.utils

import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

private const val TAG = "Decrypt"
const val AES_BLOCK_SIZE = 16 // AES 块大小固定为 16 字节
const val KEY_BASE64 = "ZDBmMTNiZGI3MDRhMWVhMWE3MTcwNjJiNTk0NzY0ODg"

/**
 * AES解密器(ps.一个破题库用啥加密，有必要吗).
 *
 * @property keyBase64 用于解密的 base64 编码密钥.
 * @constructor 使用提供的 base64 编码密钥创建一个 Decrypt 实例.
 * @throws IllegalArgumentException 如果提供的 keyBase64 为空.
 */
class Decrypt(private val keyBase64: String) {
    init {
        if (keyBase64.isEmpty()) {
            Log.e(TAG, "初始化失败：keyBase64 为空")
            throw IllegalArgumentException("keyBase64 is empty")
        }
        Log.d(TAG, "初始化解密器，使用密钥：${keyBase64.take(4)}...")
    }

    /**
     * 修复 base64 编码字符串的填充.
     *
     * @param b64String 需要修复的 base64 编码字符串.
     * @return 修复填充后的 base64 编码字符串.
     */
    private fun fixBase64Padding(b64String: String): String {
        try {
            // 去除所有非 Base64 字符（如空格、换行符）
            val cleaned = b64String.replace(Regex("[^A-Za-z0-9+/=]"), "")
            // 修复填充
            val missingPadding = (4 - (cleaned.length % 4)) % 4
            return cleaned + "=".repeat(missingPadding)
        } catch (e: Exception) {
            Log.e(TAG, "修复 Base64 填充失败: ${e.message}")
            return b64String
        }
    }

    /**
     * 去除解密数据的填充.
     *
     * @param data 带有填充的解密数据.
     * @param blockSize 解密算法使用的块大小.
     * @return 去除填充后的数据.
     * @throws IllegalArgumentException 如果数据为空或填充无效.
     */
    private fun unpad(data: ByteArray, blockSize: Int): ByteArray {
        if (data.isEmpty()) {
            Log.e(TAG, "去除填充失败：数据为空")
            throw IllegalArgumentException("Data is empty")
        }

        val padLength = data[data.lastIndex].toInt() and 0xFF
        if (padLength < 1 || padLength > blockSize) {
            Log.e(TAG, "去除填充失败：无效的填充长度 $padLength")
            throw IllegalArgumentException("Invalid padding")
        }

        return data.copyOfRange(0, data.size - padLength)
    }

    /**
     * 使用 AES 算法解密 base64 编码的密文.
     *
     * @param ciphertextBase64 需要解密的 base64 编码密文.
     * @return 解密后的明文字符串.
     * @throws IllegalArgumentException 如果填充无效或解密失败.
     */
    fun decryptOne(ciphertextBase64: String): String {
        try {
            // 修复 Base64 填充
            val fixedCiphertext = fixBase64Padding(ciphertextBase64)
            val fixedKey = fixBase64Padding(keyBase64)

            Log.d(TAG, "开始解密，密文长度: ${fixedCiphertext.length}")

            // Base64 解码 - 使用 Android 的 Base64 类
            val ciphertext = Base64.decode(fixedCiphertext, Base64.DEFAULT)
            val keyBytes = Base64.decode(fixedKey, Base64.DEFAULT)

            // 创建 AES 密钥
            val keySpec = SecretKeySpec(keyBytes, "AES")

            // 初始化 AES/ECB/NoPadding 解密器
            val cipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, keySpec)
            }

            // 解密并去除填充
            val decryptedData = cipher.doFinal(ciphertext)
            val unpaddedData = unpad(decryptedData, AES_BLOCK_SIZE)

            val result = String(unpaddedData, Charsets.UTF_8)
            Log.d(TAG, "解密成功，结果长度: ${result.length}")

            return result
        } catch (e: Exception) {
            Log.e(TAG, "解密失败: ${e.message}", e)
            throw IllegalArgumentException("Failed to decrypt: ${e.message}", e)
        }
    }
}