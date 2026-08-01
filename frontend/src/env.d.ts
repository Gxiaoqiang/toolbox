/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// pdfjs-dist v6 兼容：Uint8Array.prototype.toHex polyfill
interface Uint8Array {
  toHex?: () => string
}
