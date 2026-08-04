import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { resolve, dirname } from 'path'
import { copyFileSync, mkdirSync, existsSync, readdirSync, statSync, readFileSync, writeFileSync } from 'fs'

// pdfjs-dist v6 polyfill — 注入到 pdf.worker 首部
// v6.x 用到 ES2025 新增内置方法，旧版 Chrome（<130）缺失，需补齐：
//  1. Uint8Array.prototype.toHex —— calculateMD5/stringToBytes 期望
//  2. Map.prototype.getOrInsertComputed —— 字体处理路径依赖
const TOHEX_POLYFILL = `
if(!Uint8Array.prototype.toHex){Uint8Array.prototype.toHex=function(){return Array.from(this).map(b=>b.toString(16).padStart(2,"0")).join("")}};
if(!Map.prototype.getOrInsertComputed){Map.prototype.getOrInsertComputed=function(k,cb){if(this.has(k))return this.get(k);var v=cb(k,this);this.set(k,v);return v}};
if(!Map.prototype.getOrInsert){Map.prototype.getOrInsert=function(k,v){if(this.has(k))return this.get(k);this.set(k,v);return v}};
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
        // 给所有 pdf.worker*.mjs（min 与非 min）开头注入 polyfill：
        // pdf-arrange 用 min worker（前缀 pdf.worker.min-），水印/涂黑/去水印用非 min（前缀 pdf.worker-）
        for (const entry of readdirSync(assetsDir)) {
          if (!entry.startsWith('pdf.worker') || !entry.endsWith('.mjs')) continue
          const filePath = resolve(assetsDir, entry)
          const content = readFileSync(filePath, 'utf-8')
          if (!content.includes('toHex=function')) {
            writeFileSync(filePath, TOHEX_POLYFILL + content, 'utf-8')
            console.log(`✓ patched ${entry} with pdfjs polyfill`)
          }
        }
      },
    },
    {
      name: 'strip-pdfjs-sourcemap',
      writeBundle() {
        const assetsDir = resolve(__dirname, '../backend/src/main/resources/static/assets')
        if (!existsSync(assetsDir)) return
        for (const entry of readdirSync(assetsDir)) {
          if (!entry.startsWith('pdf.worker') || !entry.endsWith('.mjs')) continue
          const filePath = resolve(assetsDir, entry)
          const content = readFileSync(filePath, 'utf-8')
          // 移除 sourceMappingURL 注释：生产构建不含 .map，避免浏览器请求 pdf.worker.mjs.map 造成 404 日志噪声
          const cleaned = content.replace(/^\s*\/\/#\s*sourceMappingURL=.*$/gm, '')
          if (cleaned !== content) {
            writeFileSync(filePath, cleaned, 'utf-8')
            console.log(`✓ stripped sourceMappingURL from ${entry}`)
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
