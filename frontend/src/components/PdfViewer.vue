<template>
  <div class="pdf-viewer" ref="container">
    <div v-if="error" class="pdf-status pdf-error">
      {{ error }}
    </div>
    <div v-else-if="loading" class="pdf-status pdf-loading">
      正在加载 PDF，请稍候…
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'

// 配置 pdf.js worker，当前指向官方 CDN，后续可按需改为本地静态资源
pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://unpkg.com/pdfjs-dist@3.11.174/build/pdf.worker.min.js'

const props = defineProps({
  // PDF 原始二进制数据（Uint8Array）
  data: {
    type: Uint8Array,
    required: true
  },
  // 渲染缩放比例
  scale: {
    type: Number,
    default: 1.2
  }
})

const container = ref(null)
const loading = ref(false)
const error = ref('')

let pdfDoc = null
let renderTask = null

const cleanup = () => {
  if (renderTask && renderTask.cancel) {
    try {
      renderTask.cancel()
    } catch (e) {}
  }
  renderTask = null
  if (pdfDoc) {
    try {
      pdfDoc.destroy()
    } catch (e) {}
    pdfDoc = null
  }
  const el = container.value
  if (el) {
    el.innerHTML = ''
  }
}

const renderPdf = async () => {
  if (!props.data || !(props.data instanceof Uint8Array)) {
    error.value = 'PDF 数据无效，无法预览'
    return
  }

  cleanup()
  loading.value = true
  error.value = ''

  try {
    const loadingTask = pdfjsLib.getDocument({ data: props.data })
    pdfDoc = await loadingTask.promise

    const numPages = pdfDoc.numPages || 0
    const el = container.value
    if (!el) return

    for (let pageNum = 1; pageNum <= numPages; pageNum++) {
      const page = await pdfDoc.getPage(pageNum)
      const viewport = page.getViewport({ scale: props.scale })
      const canvas = document.createElement('canvas')
      const context = canvas.getContext('2d')
      canvas.height = viewport.height
      canvas.width = viewport.width
      canvas.style.display = 'block'
      canvas.style.margin = '0 auto 8px'
      el.appendChild(canvas)

      renderTask = page.render({ canvasContext: context, viewport })
      await renderTask.promise
    }
  } catch (e) {
    // 这里的错误多为解析异常或 worker 加载失败
    // 保证给出清晰提示，避免用户看到“永远转圈”
    console.error('PDF 渲染失败:', e)
    error.value = 'PDF 预览失败，请稍后重试或下载后在本地查看'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  renderPdf()
})

watch(
  () => props.data,
  (val) => {
    if (val) {
      renderPdf()
    }
  }
)

onBeforeUnmount(() => {
  cleanup()
})
</script>

<style scoped>
.pdf-viewer {
  width: 100%;
  height: 100%;
  overflow: auto;
  background: #f5f5f5;
  box-sizing: border-box;
  padding: 8px;
}

.pdf-status {
  text-align: center;
  margin-top: 40px;
  color: #666;
}

.pdf-error {
  color: #f56c6c;
}
</style>

