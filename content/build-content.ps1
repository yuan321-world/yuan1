$ErrorActionPreference = 'Stop'
$contentRoot = $PSScriptRoot
$dictionaryPath = Join-Path $contentRoot 'dictionary/entries.v1.json'
$existing = [IO.File]::ReadAllText($dictionaryPath) | ConvertFrom-Json
$initialLemmas = @('study','progress','learn','step','small','matter','try','again','change',"can't",'well-being','learner')
$entries = [Collections.Generic.List[object]]::new()
foreach ($entry in $existing.entries) { if ($entry.lemma -in $initialLemmas) { $entries.Add($entry) } }
foreach ($line in [IO.File]::ReadAllLines((Join-Path $contentRoot 'dictionary/editorial-supplement.v1.tsv'))) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $fields = $line.Split('|')
    if ($fields.Count -ne 5) { throw 'Dictionary authoring row must have five fields' }
    $forms = @(); if ($fields[4]) { $forms = @($fields[4].Split(',')) }
    $entries.Add([ordered]@{ lemma=$fields[0]; phonetic=$null; forms=$forms; senses=@([ordered]@{partOfSpeech=$fields[1];definitionZh=$fields[2];definitionEn=$fields[3]}) })
}
if (@($entries | ForEach-Object { $_.lemma } | Group-Object | Where-Object Count -gt 1).Count) { throw 'Duplicate dictionary lemma' }
$contextual = [IO.File]::ReadAllText((Join-Path $contentRoot 'dictionary/contextual-senses.v1.json')) | ConvertFrom-Json -AsHashtable
foreach ($entry in $entries) { if ($contextual.ContainsKey($entry.lemma)) { $entry.senses = @($entry.senses) + @($contextual[$entry.lemma]) } }
$dictionary = [ordered]@{schemaVersion=1;dataVersion='1.1';sourceNote=$existing.sourceNote;entries=@($entries.ToArray())}
[IO.File]::WriteAllText($dictionaryPath,($dictionary | ConvertTo-Json -Depth 12)+"`n")
$source = [IO.File]::ReadAllText((Join-Path $contentRoot 'reading/original-b1-b2.v1.source.md')).Replace("`r`n","`n")
$articles = [Collections.Generic.List[object]]::new(); $metadata = [Collections.Generic.List[object]]::new()
foreach ($part in [regex]::Split($source,'(?m)^@@')) {
    if ([string]::IsNullOrWhiteSpace($part)) { continue }
    $newline = $part.IndexOf("`n"); $header = $part.Substring(0,$newline).Split('|'); $body = $part.Substring($newline+1).Trim()
    if ($header.Count -ne 6) { throw 'Article authoring header must have six fields' }
    $articles.Add([ordered]@{categoryId=$header[0];title=$header[2];titleZh=$header[3];summary=$header[4];difficulty=$header[1];body=$body;author='Lingua Loop Editorial Team';sourceType='ORIGINAL';sourceUrl=$null;usageNote='Lingua Loop原创英语学习练习；情境为虚构示例。B1/B2为编辑难度标注，非官方评级、真题或媒体转载。'})
    $keywords = @($header[5].Split(',') | ForEach-Object { [ordered]@{surface=$_;lemma=$_} })
    $metadata.Add([ordered]@{title=$header[2];keywords=$keywords})
    $count = [regex]::Matches($body,"[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*").Count
    Write-Output "$($header[2]): $count words"
}
$payload = [ordered]@{schemaVersion=1;articles=@($articles.ToArray())}
$manifest = [ordered]@{schemaVersion=1;dataVersion='1.1';editorialNotes=@('All ten articles are original fictional learning scenarios.','Difficulty labels are editorial estimates, not official CEFR certification.','Keyword forms and selected dictionary senses were checked against each article.','Independent human language and visual acceptance are still pending.');articles=@($metadata.ToArray())}
[IO.File]::WriteAllText((Join-Path $contentRoot 'reading/original-b1-b2.v1.json'),($payload | ConvertTo-Json -Depth 12)+"`n")
[IO.File]::WriteAllText((Join-Path $contentRoot 'reading/original-b1-b2.v1.manifest.json'),($manifest | ConvertTo-Json -Depth 12)+"`n")
Write-Output "Dictionary: $($entries.Count) base entries; articles: $($articles.Count)."
