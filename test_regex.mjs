import fs from 'fs';

let text = fs.readFileSync('tg.html', 'utf8');
const messageRegex = /<div class="tgme_widget_message_bubble[^>]*>([\s\S]*?)<div class="tgme_widget_message_info/g;

let match;
while ((match = messageRegex.exec(text)) !== null) {
  let content = match[1];
  if (content.includes(".plugin")) {
     let titleMatch = content.match(/<div class="tgme_widget_message_document_title[^>]*>([^<]+)<\/div>/);
     let title = titleMatch ? titleMatch[1] : "Unknown";

     let descMatch = content.match(/Описание:<\/b>\s*([\s\S]*?)(?:<br|<\/blockquote>)/);
     let desc = descMatch ? descMatch[1].replace(/<[^>]*>/g, "").trim() : "No description";

     let authorMatch = content.match(/<a href="https:\/\/t\.me\/([^"]+)"\s*target="_blank">@[^<]+<\/a>/);
     let author = authorMatch ? "@" + authorMatch[1] : "@exteraPluginsSup";

     let nameMatch = content.match(/Название:<\/b>\s*([^<]+)/);
     let name = nameMatch ? nameMatch[1].trim() : title.replace(".plugin", "");

     console.log(`---\nID: ${title}\nName: ${name}\nAuthor: ${author}\nDescription: ${desc}`);
  }
}
