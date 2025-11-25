import {
  Document,
  Picture,
  VideoPlay,
  Headset,
  Folder,
  Box,
  DataAnalysis,
  Reading,
  Link,
  Coin,
  Cpu,
  Monitor,
  Setting,
  Warning
} from '@element-plus/icons-vue'

/**
 * 根据文件名获取图标配置
 * @param {string} filename 文件名
 * @param {boolean} isFolder 是否是文件夹
 * @returns {{ name: string, color: string }} 图标组件名和颜色
 */
export const getFileIconConfig = (filename, isFolder = false) => {
  if (isFolder) {
    return { name: 'Folder', color: '#fbbf24' } // Amber-400
  }

  if (!filename) {
    return { name: 'Document', color: '#94a3b8' } // Slate-400
  }

  const ext = filename.split('.').pop().toLowerCase()

  const iconMap = {
    // Images
    jpg: { name: 'Picture', color: '#f472b6' }, // Pink-400
    jpeg: { name: 'Picture', color: '#f472b6' },
    png: { name: 'Picture', color: '#ec4899' }, // Pink-500
    gif: { name: 'Picture', color: '#d946ef' }, // Fuchsia-500
    svg: { name: 'Picture', color: '#a855f7' }, // Purple-500
    webp: { name: 'Picture', color: '#c026d3' }, // Fuchsia-600
    bmp: { name: 'Picture', color: '#db2777' }, // Pink-600
    ico: { name: 'Picture', color: '#9d174d' }, // Pink-800
    
    // Video
    mp4: { name: 'VideoPlay', color: '#f87171' }, // Red-400
    avi: { name: 'VideoPlay', color: '#ef4444' }, // Red-500
    mkv: { name: 'VideoPlay', color: '#dc2626' }, // Red-600
    mov: { name: 'VideoPlay', color: '#b91c1c' }, // Red-700
    wmv: { name: 'VideoPlay', color: '#991b1b' }, // Red-800
    webm: { name: 'VideoPlay', color: '#7f1d1d' }, // Red-900
    
    // Audio
    mp3: { name: 'Headset', color: '#34d399' }, // Emerald-400
    wav: { name: 'Headset', color: '#10b981' }, // Emerald-500
    flac: { name: 'Headset', color: '#059669' }, // Emerald-600
    aac: { name: 'Headset', color: '#047857' }, // Emerald-700
    ogg: { name: 'Headset', color: '#065f46' }, // Emerald-800
    
    // Documents
    pdf: { name: 'Reading', color: '#ef4444' }, // Red-500
    doc: { name: 'Document', color: '#3b82f6' }, // Blue-500
    docx: { name: 'Document', color: '#2563eb' }, // Blue-600
    xls: { name: 'DataAnalysis', color: '#10b981' }, // Emerald-500
    xlsx: { name: 'DataAnalysis', color: '#059669' }, // Emerald-600
    ppt: { name: 'DataAnalysis', color: '#f97316' }, // Orange-500
    pptx: { name: 'DataAnalysis', color: '#ea580c' }, // Orange-600
    txt: { name: 'Document', color: '#64748b' }, // Slate-500
    md: { name: 'Document', color: '#475569' }, // Slate-600
    csv: { name: 'DataAnalysis', color: '#0d9488' }, // Teal-600
    
    // Archives
    zip: { name: 'Box', color: '#a855f7' }, // Purple-500
    rar: { name: 'Box', color: '#9333ea' }, // Purple-600
    '7z': { name: 'Box', color: '#7e22ce' }, // Purple-700
    tar: { name: 'Box', color: '#6b21a8' }, // Purple-800
    gz: { name: 'Box', color: '#581c87' }, // Purple-900
    
    // Code & Dev
    html: { name: 'Link', color: '#f97316' }, // Orange-500
    css: { name: 'Link', color: '#3b82f6' }, // Blue-500
    js: { name: 'Link', color: '#eab308' }, // Yellow-500
    ts: { name: 'Link', color: '#0284c7' }, // Sky-600
    vue: { name: 'Link', color: '#41b883' }, // Vue Green
    jsx: { name: 'Link', color: '#61dafb' }, // React Blue
    tsx: { name: 'Link', color: '#61dafb' },
    java: { name: 'Cpu', color: '#ea580c' }, // Orange-600
    py: { name: 'Cpu', color: '#3b82f6' }, // Blue-500
    go: { name: 'Cpu', color: '#00add8' }, // Go Blue
    c: { name: 'Cpu', color: '#0284c7' }, // Sky-600
    cpp: { name: 'Cpu', color: '#0369a1' }, // Sky-700
    php: { name: 'Link', color: '#8b5cf6' }, // Violet-500
    sql: { name: 'DataAnalysis', color: '#f59e0b' }, // Amber-500
    json: { name: 'Link', color: '#fbbf24' }, // Amber-400
    xml: { name: 'Link', color: '#f59e0b' },
    yaml: { name: 'Setting', color: '#64748b' },
    yml: { name: 'Setting', color: '#64748b' },
    properties: { name: 'Setting', color: '#64748b' },
    conf: { name: 'Setting', color: '#64748b' },
    env: { name: 'Setting', color: '#ef4444' },
    
    // Executables & System
    exe: { name: 'Monitor', color: '#3b82f6' },
    dll: { name: 'Setting', color: '#64748b' },
    sh: { name: 'Cpu', color: '#22c55e' }, // Green-500
    bat: { name: 'Cpu', color: '#22c55e' },
    iso: { name: 'Box', color: '#6366f1' }, // Indigo-500
    dmg: { name: 'Box', color: '#6366f1' },
    
    // Default
    default: { name: 'Document', color: '#94a3b8' }
  }

  return iconMap[ext] || iconMap.default
}
