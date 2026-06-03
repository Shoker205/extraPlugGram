import https from 'https';

https.get('https://api.github.com/search/repositories?q=tdlib+android', { headers: { 'User-Agent': 'node.js' } }, (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    let j = JSON.parse(data);
    if(j.items) j.items.slice(0, 5).forEach(i => console.log(i.full_name));
  });
});
