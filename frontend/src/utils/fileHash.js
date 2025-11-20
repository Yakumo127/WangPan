// 文件哈希计算工具（SHA-256）
// 说明：当前实现为基础版，可根据需要优化为分块计算

export async function computeFileSha256(file) {
  if (!file) throw new Error('文件为空')
  if (!window.crypto || !window.crypto.subtle) {
    throw new Error('当前环境不支持加密 API，无法计算哈希')
  }
  const buffer = await file.arrayBuffer()
  const hashBuffer = await window.crypto.subtle.digest('SHA-256', buffer)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  const hashHex = hashArray.map((b) => b.toString(16).padStart(2, '0')).join('')
  return hashHex
}

