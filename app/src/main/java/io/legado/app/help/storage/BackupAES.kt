package io.legado.app.help.storage

import cn.hutool.crypto.symmetric.AES
import io.legado.app.help.config.LocalConfig
import io.legado.app.utils.MD5Utils

/**
 * 备份AES加密工具类
 * 
 * 用于备份和恢复过程中对敏感数据进行加密/解密，包括：
 * - WebDav密码
 * - 服务器配置
 * 
 * 加密机制：
 * - 使用AES对称加密算法
 * - 密钥由用户设置的备份密码经过MD5处理后生成
 * - 取MD5结果的前16字节作为AES密钥
 * 
 * 使用场景：
 * - 备份时：对敏感数据加密后存储
 * - 恢复时：对加密数据解密后导入
 * 
 * 注意事项：
 * - 如果用户未设置备份密码，则使用空字符串作为密钥
 * - 加密失败时会降级为明文存储（备份时）
 * - 解密失败时会尝试使用明文或保留本地配置（恢复时）
 */
class BackupAES : AES(
    MD5Utils.md5Encode(LocalConfig.password ?: "").encodeToByteArray(0, 16)
)
