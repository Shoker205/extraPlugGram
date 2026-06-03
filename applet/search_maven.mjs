import https from 'https';

https.get('https://search.maven.org/solrsearch/select?q=tdlib&rows=20&wt=json', (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    console.log(JSON.parse(data).response.docs.map(d => d.id).join('\n'));
  });
});
