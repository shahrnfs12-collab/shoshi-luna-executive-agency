from http.server import BaseHTTPRequestHandler


PAGE = r'''<!doctype html><html lang="he" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>הורדת Shoshi Luna</title><style>body{margin:0;background:#09090b;color:#eee;font-family:system-ui;display:grid;place-items:center;min-height:100vh}.box{max-width:520px;padding:28px;text-align:center;background:#15151a;border-radius:24px}button{border:0;border-radius:14px;padding:16px 24px;background:#dfbd63;font-size:18px;font-weight:700}progress{width:100%;margin:20px 0}</style><div class="box"><h1>SHOSHI • LUNA 2.5.3</h1><p>הורדת APK מלא ומאומת</p><button id="go">הורד עכשיו</button><progress id="p" value="0" max="1"></progress><div id="s"></div></div><script>
const go=document.querySelector('#go'),p=document.querySelector('#p'),s=document.querySelector('#s');
go.onclick=async()=>{go.disabled=true;try{s.textContent='מתחיל הורדה…';const m=await fetch('/api/apk_chunk?meta=1').then(r=>{if(!r.ok)throw Error('שגיאת שרת');return r.json()});p.max=m.chunks;const parts=[];for(let i=0;i<m.chunks;i++){s.textContent=`מוריד חלק ${i+1} מתוך ${m.chunks}`;const j=await fetch('/api/apk_chunk?index='+i).then(r=>{if(!r.ok)throw Error('שגיאה בחלק '+(i+1));return r.json()});const bin=atob(j.data),a=new Uint8Array(bin.length);for(let k=0;k<bin.length;k++)a[k]=bin.charCodeAt(k);parts.push(a);p.value=i+1}const blob=new Blob(parts,{type:'application/vnd.android.package-archive'}),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='ShoshiLuna-2.5.3-full-runtime.apk';a.click();s.textContent='ההורדה הושלמה';}catch(e){s.textContent='ההורדה נכשלה: '+e.message;go.disabled=false}};
</script></html>'''


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = PAGE.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)
