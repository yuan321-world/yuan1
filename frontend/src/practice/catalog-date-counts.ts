type DateFacet={value:string;count:number}[]
type DateCatalog={year?:DateFacet;years?:DateFacet}

export async function catalogDateCounts<T extends DateCatalog>(query:URLSearchParams,load:(query:URLSearchParams)=>Promise<T>,field:'year'|'years'):Promise<T>{
  if(!query.has('month'))return load(query)
  // Choosing a year clears the previous month; its count must describe that result.
  const yearQuery=new URLSearchParams(query)
  yearQuery.delete('month')
  yearQuery.set('page','1')
  yearQuery.set('pageSize','1')
  const [selected,years]=await Promise.all([load(query),load(yearQuery)])
  return {...selected,[field]:years[field]}
}
