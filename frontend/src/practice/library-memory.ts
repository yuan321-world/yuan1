// Remember navigation filters for this browser tab; no answers or account data.
export function libraryDestination(path: '/practice/cet' | '/practice/postgraduate', source?: 'PAST' | 'ORIGINAL') {
  try {
    const saved = sessionStorage.getItem('practice-library:' + path)
    const target=saved?.startsWith(path+'?')?saved:path
    if(!source)return target
    const query=new URLSearchParams(target.split('?')[1]||'')
    query.set('source',source);query.set('collection',source);query.set('page','1')
    if(source==='ORIGINAL')for(const key of ['year','month','paperNo'])query.delete(key)
    return path+'?'+query.toString()
  } catch { return source?path+'?source='+source+'&collection='+source:path }
}

export function rememberLibrary(path: '/practice/cet' | '/practice/postgraduate', query: string) {
  try { sessionStorage.setItem('practice-library:' + path, path + '?' + query) } catch { /* Navigation still works without storage. */ }
}
