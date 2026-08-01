import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { resolve, dirname } from 'path'
import { copyFileSync, mkdirSync, existsSync, readdirSync, statSync, readFileSync, writeFileSync } from 'fs'

// pdfjs-dist v6 toHex polyfill — 注入到 pdf.worker 首部
// v6.x 的 calculateMD5/stringToBytes 返回 Uint8Array，但内部代码期望 .toHex()
const TOHEX_POLYFILL = `
if(!Uint8Array.prototype.toHex){Uint8Array.prototype.toHex=function(){return Array.from(this).map(b=>b.toString(16).padStart(2,"0")).join("")}};
`

// 递归复制目录
function copyDir(src: string, dest: string) {
  if (!existsSync(dest)) mkdirSync(dest, { recursive: true })
  for (const entry of readdirSync(src)) {
    const srcPath = resolve(src, entry)
    const destPath = resolve(dest, entry)
    if (statSync(srcPath).isDirectory()) {
      copyDir(srcPath, destPath)
    } else {
      copyFileSync(srcPath, destPath)
    }
  }
}

export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    {
      name: 'copy-pdfjs-cmaps',
      writeBundle() {
        const cmapsSrc = resolve(__dirname, 'node_modules/pdfjs-dist/cmaps')
        const cmapsDest = resolve(__dirname, '../backend/src/main/resources/static/assets/cmaps')
        if (existsSync(cmapsSrc)) {
          copyDir(cmapsSrc, cmapsDest)
          console.log(`✓ copied pdfjs-dist/cmaps → assets/cmaps`)
        }
      },
    },
    {
      name: 'patch-pdfjs-worker',
      writeBundle() {
        const assetsDir = resolve(__dirname, '../backend/src/main/resources/static/assets')
        if (!existsSync(assetsDir)) return
        for (const entry of readdirSync(assetsDir)) {
          if (entry.startsWith('pdf.worker-') && entry.endsWith('.mjs') && !entry.includes('.min')) {
            const filePath = resolve(assetsDir, entry)
            let content = readFileSync(filePath, 'utf-8')
            // 在版本注释块（第二个 */）之后注入 polyfill
            const marker = '*/'
            let pos = content.indexOf(marker)
            if (pos !== -1) pos = content.indexOf(marker, pos + marker.length)
            if (pos !== -1 && !content.includes('toHex=function')) {
              content = content.slice(0, pos + marker.length)
                + TOHEX_POLYFILL
                + content.slice(pos + marker.length)
              writeFileSync(filePath, content, 'utf-8')
              console.log(`✓ patched ${entry} with toHex polyfill`)
            }
            break // 只处理第一个匹配的 worker 文件
          }
        }
      },
    },
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  build: {
    outDir: resolve(__dirname, '../backend/src/main/resources/static'),
    emptyOutDir: true
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8899',
        changeOrigin: true,
      },
    },
  },
})
