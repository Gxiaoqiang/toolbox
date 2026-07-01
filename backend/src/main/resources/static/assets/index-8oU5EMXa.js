import{d as b,o as h,a as g,b as e,e as v,v as w,t as y,f as k,i,j as M,_}from"./index-BVBxzMV-.js";import{g as c}from"./marked.esm-DiRRsLPS.js";import{u as H}from"./useClipboard-LtY0Bc24.js";const L={class:"flex gap-4 h-full"},T={class:"flex-1 flex flex-col min-w-0"},C={class:"flex-1 flex flex-col min-w-0"},O={class:"flex items-center justify-between mb-2"},U={class:"flex gap-2"},j=["innerHTML"],B=b({inheritAttrs:!1,__name:"index",setup(F,{expose:m}){m({meta:{id:"md-to-html",name:"Markdown → HTML",description:"将 Markdown 文本实时转换为 HTML，支持 GFM 语法",icon:"file-code",category:"document"}}),c.setOptions({gfm:!0,breaks:!1});const s=i(`# 欢迎使用 Markdown 转换器

## 基本语法

**粗体**、*斜体*、~~删除线~~、\`行内代码\`

### 代码块

\`\`\`python
def hello():
    print("Hello, World!")
\`\`\`

### 表格

| 姓名 | 年龄 | 城市 |
|------|------|------|
| 张三 | 28 | 北京 |
| 李四 | 32 | 上海 |

### 列表

- 无序列表项 1
- 无序列表项 2
  - 嵌套项

1. 有序列表项 1
2. 有序列表项 2

### 引用与链接

> 这是一段引用文字

[OpenAI](https://openai.com)

### 任务列表

- [x] 已完成项
- [ ] 待办项
`),o=i(""),{copied:p,copy:u}=H(),{success:a}=M();function d(){try{o.value=c.parse(s.value)}catch{o.value='<p style="color:red">Markdown 解析错误</p>'}}function f(){u(o.value),a("HTML 已复制到剪贴板")}function x(){const r=`<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Markdown Output</title>
<style>
  body {
    max-width: 900px;
    margin: 0 auto;
    padding: 2rem;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
    line-height: 1.6;
    color: #1a1a2e;
  }
  h1 { border-bottom: 2px solid #eee; padding-bottom: 0.3em; }
  h2 { border-bottom: 1px solid #eee; padding-bottom: 0.3em; }
  pre { background: #1e1e2e; color: #cdd6f4; padding: 1rem; border-radius: 8px; overflow-x: auto; }
  code { background: #f0f0f0; padding: 0.2em 0.4em; border-radius: 4px; font-size: 0.9em; }
  pre code { background: none; padding: 0; }
  table { border-collapse: collapse; width: 100%; }
  th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
  th { background: #f5f5f5; }
  blockquote { border-left: 4px solid #0366d6; padding-left: 1rem; color: #555; margin-left: 0; }
  img { max-width: 100%; }
</style>
</head>
<body>
${o.value}
</body>
</html>`,t=new Blob([r],{type:"text/html;charset=utf-8"}),n=URL.createObjectURL(t),l=document.createElement("a");l.href=n,l.download="output.html",document.body.appendChild(l),l.click(),document.body.removeChild(l),URL.revokeObjectURL(n),a("HTML 文件已下载")}return d(),(r,t)=>(h(),g("div",L,[e("div",T,[t[1]||(t[1]=e("div",{class:"flex items-center justify-between mb-2"},[e("label",{class:"text-xs font-semibold text-slate-500"},"Markdown 输入"),e("span",{class:"text-[10px] text-slate-400"},"支持 GFM 语法（表格/代码/任务列表等）")],-1)),v(e("textarea",{"onUpdate:modelValue":t[0]||(t[0]=n=>s.value=n),class:"flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-indigo-400",placeholder:"# 在这里输入 Markdown...",onInput:d},null,544),[[w,s.value]])]),e("div",C,[e("div",O,[t[2]||(t[2]=e("label",{class:"text-xs font-semibold text-slate-500"},"HTML 预览",-1)),e("div",U,[e("button",{onClick:f,class:"px-3 py-1 text-xs rounded-md bg-slate-100 hover:bg-slate-200 text-slate-600 transition-colors"},y(k(p)?"✓ 已复制":"复制 HTML"),1),e("button",{onClick:x,class:"px-3 py-1 text-xs rounded-md bg-indigo-500 hover:bg-indigo-600 text-white transition-colors"}," 下载 .html ")])]),e("div",{class:"flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white markdown-body",innerHTML:o.value},null,8,j)])]))}}),D=_(B,[["__scopeId","data-v-5fb637fa"]]);export{D as default};
