import https from 'https';
import fs from 'fs';

https.get('https://t.me/s/exteraPluginsSup', (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    fs.writeFileSync('tg.html', data);
    
    let text = fs.readFileSync('tg.html', 'utf8');
    const messageRegex = /<div class="tgme_widget_message_bubble">([\s\S]*?)<div class="tgme_widget_message_info">/g;

    let match;
    while ((match = messageRegex.exec(text)) !== null) {
      let content = match[1];
      if (content.includes(".plugin")) {
         let fileUrlMatch = content.match(/href="(https:\/\/t\.me\/[^"]+?single)"/);
         let titleMatch = content.match(/<div class="tgme_widget_message_document_title"[^>]*><span dir="auto">([^<]+)<\/span>/) || content.match(/<div class="tgme_widget_message_document_title"[^>]*>([^<]+)<\/div>/);
         
         let authorMatch = content.match(/<a href="https:\/\/t\.me\/([^"]+)"\s*target="_blank">@[^<]+<\/a>/);
         let nameMatch = content.match(/Название:<\/b>\s*([^<]+)/);
         
         if (fileUrlMatch && titleMatch) {
            console.log("Found:", titleMatch[1]);
         }
      }
    }
  });
});
