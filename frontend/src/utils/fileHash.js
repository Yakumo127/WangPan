// 文件哈希计算工具（SHA-256）
// 采用增量/分块方式计算，支持大文件

class Sha256 {
  constructor() {
    this._init()
  }

  _init() {
    this._hash = new Uint32Array(8)
    this._hash[0] = 0x6a09e667
    this._hash[1] = 0xbb67ae85
    this._hash[2] = 0x3c6ef372
    this._hash[3] = 0xa54ff53a
    this._hash[4] = 0x510e527f
    this._hash[5] = 0x9b05688c
    this._hash[6] = 0x1f83d9ab
    this._hash[7] = 0x5be0cd19
    this._buffer = new Uint8Array(64)
    this._bufLen = 0
    this._bytesHashed = 0
    this._finished = false
  }

  _rotr(n, x) {
    return (x >>> n) | (x << (32 - n))
  }

  _processChunk(chunk, offset) {
    const K = Sha256.K
    const w = Sha256.w
    for (let i = 0; i < 16; i++) {
      const j = offset + i * 4
      w[i] = ((chunk[j] << 24) | (chunk[j + 1] << 16) | (chunk[j + 2] << 8) | (chunk[j + 3])) >>> 0
    }
    for (let i = 16; i < 64; i++) {
      const s0 = (this._rotr(7, w[i - 15]) ^ this._rotr(18, w[i - 15]) ^ (w[i - 15] >>> 3)) >>> 0
      const s1 = (this._rotr(17, w[i - 2]) ^ this._rotr(19, w[i - 2]) ^ (w[i - 2] >>> 10)) >>> 0
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0
    }
    let a = this._hash[0]
    let b = this._hash[1]
    let c = this._hash[2]
    let d = this._hash[3]
    let e = this._hash[4]
    let f = this._hash[5]
    let g = this._hash[6]
    let h = this._hash[7]
    for (let i = 0; i < 64; i++) {
      const S1 = (this._rotr(6, e) ^ this._rotr(11, e) ^ this._rotr(25, e)) >>> 0
      const ch = ((e & f) ^ (~e & g)) >>> 0
      const temp1 = (h + S1 + ch + K[i] + w[i]) >>> 0
      const S0 = (this._rotr(2, a) ^ this._rotr(13, a) ^ this._rotr(22, a)) >>> 0
      const maj = ((a & b) ^ (a & c) ^ (b & c)) >>> 0
      const temp2 = (S0 + maj) >>> 0
      h = g
      g = f
      f = e
      e = (d + temp1) >>> 0
      d = c
      c = b
      b = a
      a = (temp1 + temp2) >>> 0
    }
    this._hash[0] = (this._hash[0] + a) >>> 0
    this._hash[1] = (this._hash[1] + b) >>> 0
    this._hash[2] = (this._hash[2] + c) >>> 0
    this._hash[3] = (this._hash[3] + d) >>> 0
    this._hash[4] = (this._hash[4] + e) >>> 0
    this._hash[5] = (this._hash[5] + f) >>> 0
    this._hash[6] = (this._hash[6] + g) >>> 0
    this._hash[7] = (this._hash[7] + h) >>> 0
  }

  update(data) {
    if (this._finished) throw new Error('hash already finished')
    if (!(data instanceof Uint8Array)) {
      throw new Error('data must be Uint8Array')
    }
    let pos = 0
    const len = data.length
    this._bytesHashed += len
    // 先填满 buffer
    if (this._bufLen > 0) {
      while (this._bufLen < 64 && pos < len) {
        this._buffer[this._bufLen++] = data[pos++]
      }
      if (this._bufLen === 64) {
        this._processChunk(this._buffer, 0)
        this._bufLen = 0
      }
    }
    // 处理完整块
    while (pos + 64 <= len) {
      this._processChunk(data, pos)
      pos += 64
    }
    // 剩余放入 buffer
    while (pos < len) {
      this._buffer[this._bufLen++] = data[pos++]
    }
  }

  digest() {
    if (this._finished) {
      return Sha256._hashArrayToHex(this._hash)
    }
    this._finished = true
    // 填充
    const bytesHashed = this._bytesHashed
    const bitLenHi = Math.floor(bytesHashed / 0x20000000)
    const bitLenLo = (bytesHashed << 3) >>> 0

    this._buffer[this._bufLen++] = 0x80
    if (this._bufLen > 56) {
      while (this._bufLen < 64) this._buffer[this._bufLen++] = 0
      this._processChunk(this._buffer, 0)
      this._bufLen = 0
    }
    while (this._bufLen < 56) this._buffer[this._bufLen++] = 0

    // 写入长度（高32位 + 低32位）
    this._buffer[this._bufLen++] = (bitLenHi >>> 24) & 0xff
    this._buffer[this._bufLen++] = (bitLenHi >>> 16) & 0xff
    this._buffer[this._bufLen++] = (bitLenHi >>> 8) & 0xff
    this._buffer[this._bufLen++] = bitLenHi & 0xff
    this._buffer[this._bufLen++] = (bitLenLo >>> 24) & 0xff
    this._buffer[this._bufLen++] = (bitLenLo >>> 16) & 0xff
    this._buffer[this._bufLen++] = (bitLenLo >>> 8) & 0xff
    this._buffer[this._bufLen++] = bitLenLo & 0xff

    this._processChunk(this._buffer, 0)
    return Sha256._hashArrayToHex(this._hash)
  }

  static _hashArrayToHex(hash) {
    const out = []
    for (let i = 0; i < hash.length; i++) {
      const v = hash[i]
      out.push((v >>> 28).toString(16))
      out.push(((v >>> 24) & 0xf).toString(16))
      out.push(((v >>> 20) & 0xf).toString(16))
      out.push(((v >>> 16) & 0xf).toString(16))
      out.push(((v >>> 12) & 0xf).toString(16))
      out.push(((v >>> 8) & 0xf).toString(16))
      out.push(((v >>> 4) & 0xf).toString(16))
      out.push((v & 0xf).toString(16))
    }
    return out.join('')
  }
}

Sha256.w = new Uint32Array(64)
Sha256.K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
  0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
  0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
  0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
  0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
  0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
  0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
  0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
  0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
])

/**
 * 流式计算文件 SHA-256
 * @param {File} file
 * @param {{chunkSize?:number,onProgress?:(loaded,total)=>void,signal?:AbortSignal}} options
 * @returns {Promise<string>}
 */
export async function computeFileSha256(file, options = {}) {
  if (!file) throw new Error('文件为空')
  const {
    chunkSize = 4 * 1024 * 1024,
    onProgress,
    signal
  } = options

  const hasher = new Sha256()
  const total = file.size
  let offset = 0

  while (offset < total) {
    if (signal?.aborted) {
      const err = new Error('hash aborted')
      err.code = 'HASH_ABORTED'
      throw err
    }
    const end = Math.min(offset + chunkSize, total)
    const blob = file.slice(offset, end)
    const buf = await blob.arrayBuffer()
    hasher.update(new Uint8Array(buf))
    offset = end
    if (onProgress) {
      onProgress(offset, total)
    }
  }
  return hasher.digest()
}

