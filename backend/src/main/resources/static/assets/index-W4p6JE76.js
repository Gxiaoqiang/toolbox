import{d as g,w as h,o as m,a as f,b as t,n as _,h as k,t as y,e as C,v as M,i,j as T,_ as D}from"./index-CmF29rl0.js";import{g as p}from"./marked.esm-DiRRsLPS.js";const O={class:"flex gap-4 h-full"},j={class:"flex-1 flex flex-col min-w-0"},L={class:"flex items-center justify-between mb-2"},H=["disabled"],U={key:0,class:"inline-block animate-spin text-xs"},B={class:"flex-1 flex flex-col min-w-0"},E=["innerHTML"],R=g({inheritAttrs:!1,__name:"index",setup(P,{expose:x}){x({meta:{id:"md-to-doc",name:"Markdown → DOCX",description:"将 Markdown 转换为 Word 文档（.docx）下载",icon:"file-text",category:"document",requiresBackend:!0}}),p.setOptions({gfm:!0,breaks:!1});const o=i(`# 示例文档

## 第一节

这是一段**粗体**和*斜体*文字。支持 ~~删除线~~ 和 \`行内代码\`。

### 代码示例

\`\`\`javascript
function greet(name) {
  return "Hello, " + name;
}
\`\`\`

### 表格

| 项目 | 状态 | 负责人 |
|------|------|--------|
| 需求评审 | 已完成 | 张三 |
| 开发实现 | 进行中 | 李四 |

### 列表

1. 第一步
2. 第二步
3. 第三步

> 重要提示：转换结果请人工复核。

[查看详情](https://example.com)
`),l=i(""),s=i(!1),{success:v,error:c}=T();function b(){try{l.value=p.parse(o.value)}catch{l.value='<p style="color:red">Markdown 解析错误</p>'}}h(o,b,{immediate:!0});async function w(){if(!o.value.trim()){c("请先输入 Markdown 内容");return}s.value=!0;try{const a=new FormData;a.append("content",o.value),a.append("filename","converted");const e=await fetch("/api/convert/md-to-docx",{method:"POST",body:a});if(!e.ok){const d=await e.json().catch(()=>null);throw new Error((d==null?void 0:d.message)||"HTTP "+e.status)}const r=await e.blob(),u=URL.createObjectURL(r),n=document.createElement("a");n.href=u,n.download="output.docx",document.body.appendChild(n),n.click(),document.body.removeChild(n),URL.revokeObjectURL(u),v("DOCX 文件已下载")}catch(a){c("转换失败: "+a.message)}finally{s.value=!1}}return(a,e)=>(m(),f("div",O,[t("div",j,[t("div",L,[e[1]||(e[1]=t("label",{class:"text-xs font-semibold text-slate-500"},"Markdown 输入",-1)),t("button",{onClick:w,disabled:s.value,class:_(["px-4 py-1.5 text-sm rounded-md transition-colors flex items-center gap-1.5",s.value?"bg-slate-200 text-slate-400 cursor-not-allowed":"bg-indigo-500 hover:bg-indigo-600 text-white"])},[s.value?(m(),f("span",U,"⟳")):k("",!0),t("span",null,y(s.value?"转换中...":"转为 DOCX 并下载"),1)],10,H)]),C(t("textarea",{"onUpdate:modelValue":e[0]||(e[0]=r=>o.value=r),class:"flex-1 p-4 border border-slate-200 rounded-lg resize-none font-mono text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-indigo-400",placeholder:"# 在这里输入 Markdown..."},null,512),[[M,o.value]])]),t("div",B,[e[2]||(e[2]=t("label",{class:"text-xs font-semibold text-slate-500 mb-2"},"预览",-1)),t("div",{class:"flex-1 p-4 border border-slate-200 rounded-lg overflow-auto bg-white markdown-body",innerHTML:l.value},null,8,E)])]))}}),N=D(R,[["__scopeId","data-v-d0a6f60d"]]);export{N as default};
