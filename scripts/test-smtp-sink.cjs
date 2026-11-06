// Local test utility only. Bind loopback; never route real mail through this collector.
const net = require('node:net');
const http = require('node:http');
const codes = new Map();
const smtp = net.createServer(socket => {
  socket.setEncoding('utf8');
  socket.write('220 local-test ESMTP\r\n');
  let buffer = '', data = false, lines = [], recipients = [];
  socket.on('data', chunk => {
    buffer += chunk;
    let end;
    while ((end = buffer.indexOf('\r\n')) >= 0) {
      const line = buffer.slice(0, end); buffer = buffer.slice(end+2);
      if (data) {
        if (line !== '.') { lines.push(line.replace(/^\.\./, '.')); continue; }
        const raw = lines.join('\r\n');
        const split = raw.indexOf('\r\n\r\n');
        const headers = raw.slice(0,split), body=raw.slice(split+4);
        const decoded = /Content-Transfer-Encoding:\s*base64/i.test(headers)
          ? Buffer.from(body.replace(/\s/g,''),'base64').toString('utf8')
          : /Content-Transfer-Encoding:\s*quoted-printable/i.test(headers)
          ? Buffer.from(body.replace(/=\r\n/g,'').replace(/=([0-9a-f]{2})/gi,(_,hex)=>String.fromCharCode(parseInt(hex,16))), 'latin1').toString('utf8') : body;
        const match=decoded.match(/\b\d{6}\b/);
        if(match) for(const email of recipients) codes.set(email,{code:match[0],at:Date.now()});
        data=false; lines=[]; recipients=[]; socket.write('250 accepted\r\n'); continue;
      }
      if (/^EHLO|^HELO/i.test(line)) socket.write('250-local-test\r\n250 8BITMIME\r\n');
      else if (/^MAIL FROM:/i.test(line)) { recipients=[]; socket.write('250 sender\r\n'); }
      else if (/^RCPT TO:/i.test(line)) { const m=line.match(/<([^>]+)>/); if(m)recipients.push(m[1].toLowerCase()); socket.write('250 recipient\r\n'); }
      else if (/^DATA$/i.test(line)) { data=true; lines=[]; socket.write('354 send data\r\n'); }
      else if (/^QUIT$/i.test(line)) socket.end('221 bye\r\n');
      else socket.write('250 ok\r\n');
    }
  });
  socket.on('error',()=>{});
});
smtp.listen(2525,'127.0.0.1');
const api=http.createServer((req,res)=>{
  const u=new URL(req.url,'http://127.0.0.1');
  const entry=codes.get((u.searchParams.get('email')||'').toLowerCase());
  res.setHeader('Cache-Control','no-store');res.setHeader('Content-Type','application/json');
  if(u.pathname!='/code'||!entry||Date.now()-entry.at>600000){res.writeHead(404);res.end('{}');return;}
  res.end(JSON.stringify({code:entry.code}));
});
api.listen(2526,'127.0.0.1',()=>process.stdout.write('Loopback SMTP test collector ready\n'));
