(() => {
  'use strict';
  const KEY = 'lingua-vocabulary-prototype-20261102-v1';
  const $ = (s) => document.querySelector(s);
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const words = Object.fromEntries(window.demoWords.map(w => [w.id, w]));
  const books = {
    cet4: {name:'四级 · 示例词书', ids:window.demoWords.slice(0,8).map(w=>w.id)},
    personal: {name:'我的生词 · 示例', ids:['study','library','step','look','progress']}
  };
  const presets = [['悠闲',5,30],['轻松',10,60],['标准',20,100],['进阶',35,150],['冲刺',50,200]];
  const intervals = [1,3,7,15,30,60,90];
  const day = (time=Date.now()) => new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(time);
  const seed = () => ({version:1,book:'cet4',scope:'all',settings:{newGoal:20,reviewGoal:100,autoNext:true},mastery:{study:{stage:1,due:Date.now()-86400000},library:{stage:1,due:Date.now()-86400000}},events:[],round:null});
  let state;
  try { state = JSON.parse(localStorage.getItem(KEY)); } catch { /* Start fresh on invalid storage. */ }
  if (!state || state.version !== 1 || !books[state.book]) state = seed();
  state.notebook ??= [...books.personal.ids];
  state.exempt ??= {};
  books.personal.ids=state.notebook;
  if(state.round) state.round.skipped ??= [];
  const isCovered = id => !!state.mastery[id] || !!state.exempt[id];
  let view = 'home', chosenKind = 'NEW', timer, toastTimer, speechGeneration=0, composing=false;
  function toast(message) { $('#toast').textContent=message; $('#toast').style.display='block'; clearTimeout(toastTimer); toastTimer=setTimeout(()=>$('#toast').style.display='none',3200); }
  function save() { try { localStorage.setItem(KEY,JSON.stringify(state)); } catch { toast('浏览器未允许保存，关闭页面后演示进度可能丢失。'); } }
  function stopTimer() { clearTimeout(timer); timer=null; }
  function stopVoice() { speechGeneration++; window.speechSynthesis?.cancel(); }
  function todayEvents(kind) { return state.events.filter(e=>e.kind===kind && day(e.time)===day()); }
  function dueIds() { return Object.keys(state.mastery).filter(id=>words[id] && !state.exempt[id] && state.mastery[id].due!=null && state.mastery[id].due<=Date.now() && (state.scope==='all' || books[state.book].ids.includes(id))); }
  function openBooks() {
    $('#book-options').innerHTML=Object.entries(books).map(([id,b])=>{
      const covered=b.ids.filter(isCovered).length,selected=id===state.book;
      return `<button class="book-option ${selected?'is-current':''}" data-book="${id}" aria-pressed="${selected}"><span class="book-cover ${id==='personal'?'personal':''}"><small>${id==='cet4'?'CET 4':'MY WORDS'}</small><b>${id==='cet4'?'Aa':'abc'}</b></span><span class="book-option-body"><b>${b.name}</b><small>${id==='cet4'?'按词书稳步学习':'把遇见的词，慢慢记牢'}</small><span class="book-option-stats">${b.ids.length} 个示例词 · 已学 ${covered} 个</span><progress aria-label="${b.name}覆盖" max="${b.ids.length}" value="${covered}"></progress></span><span class="book-choice">${selected?'✓ 当前':'选择 →'}</span></button>`;
    }).join('');
    $('#book-options').querySelectorAll('[data-book]').forEach(button=>button.onclick=()=>{state.book=button.dataset.book;save();$('#books').close();home();$('#book').focus({preventScroll:true});});
    $('#books').showModal();
  }
  function home() {
    stopTimer(); stopVoice(); view='home';library.ensureState();
    if(state.libraryTab!=='learn'){library.render();return;}
    const b=books[state.book], covered=b.ids.filter(isCovered).length, newDone=todayEvents('NEW').length, reviewDone=todayEvents('REVIEW').length, due=dueIds();
    const newAvailable=b.ids.filter(id=>!isCovered(id)&&(state.book!=='personal'||library.enrolled(id))).length;
    const active=state.round && state.round.phase!=='COMPLETED';
    const preset=presets.find(p=>p[1]===state.settings.newGoal && p[2]===state.settings.reviewGoal)?.[0] || '自定义';
    $('#app').innerHTML=library.frame()+`
      <section class="book-strip"><div class="book-select"><span class="book-icon">Aa</span><div><span class="tiny">当前词书</span><button class="book-trigger" id="book" aria-haspopup="dialog"><strong>${b.name}</strong><span>切换词书 <span aria-hidden="true">⌄</span></span></button><div class="book-meta">示例词汇 ${b.ids.length} 个 · 掌握记录跨词书共用</div></div></div><div><div class="progress-label"><span>本书学习覆盖</span><b>${covered} / ${b.ids.length}</b></div><progress aria-label="本书学习覆盖" max="${b.ids.length}" value="${covered}"></progress><p class="tiny muted">覆盖含已学与手动熟练；手动熟练的词不自动复习。</p></div></section>
      ${active?`<div class="pending">有一轮${state.round.kind==='NEW'?'新学':'复习'}已保存，继续时会回到原来的词和输入位置。<button class="secondary" id="resume">继续这一轮 →</button></div>`:''}
      <div class="task-grid"><section class="task"><div class="task-top"><h3>今日新学</h3><span class="tag">NEW</span></div><div class="task-number">${newDone}<small> / ${state.settings.newGoal} 词</small></div><p class="muted">给新词留下第一印象</p><footer><span class="tiny">本书可新学 ${newAvailable} 词</span><button class="primary" id="start-new" ${active || newDone>=state.settings.newGoal || !newAvailable?'disabled':''}>开始新学 →</button></footer></section>
      <section class="task review"><div class="task-top"><h3>到期复习</h3><select class="scope" id="scope" aria-label="复习范围"><option value="all" ${state.scope==='all'?'selected':''}>全部来源</option><option value="current" ${state.scope==='current'?'selected':''}>当前词书</option></select></div><div class="task-number">${due.length}<small> 词待复习</small></div><p class="muted">今天已复习 ${reviewDone} / 参考目标 ${state.settings.reviewGoal}</p><footer><span class="tiny">到期优先 · 目标不封顶</span><button class="primary" id="start-review" ${active || !due.length?'disabled':''}>开始复习 →</button></footer></section></div>
      <section class="plan-strip"><span>◷ <b>${preset}计划</b>　每天新学 ${state.settings.newGoal} 词 / 复习参考 ${state.settings.reviewGoal} 词</span><button id="plan-open">调整计划 ↗</button><button id="exempt-open">熟练免复习 · ${Object.keys(state.exempt).length} 词 ↗</button></section>
      <section class="bottom-grid"><div><div class="section-title"><h3>最近七天</h3><span class="tiny muted">仅统计本原型中的完成记录</span></div><div class="bars">${Array.from({length:7},(_,i)=>{const t=Date.now()-(6-i)*86400000; const count=state.events.filter(e=>day(e.time)===day(t)).length;return `<div class="bar-day"><span>${count}</span><div class="bar" style="height:${Math.max(3,Math.min(80,count*12))}px"></div><small>${i===6?'今天':day(t).slice(5).replace('-','/')}</small></div>`;}).join('')}</div></div><div class="notes"><h3>把记忆交给节奏。</h3><p class="muted">新词跟打帮助熟悉拼写。复习时再遮住英文，检验能否独立想起。</p><div class="stats"><span><b>${state.events.length}</b> 词次完成</span><span><b>${state.events.filter(e=>e.independent).length}</b> 次独立回忆</span></div></div></section>
      <details class="prototype-info"><summary>演示说明与词书范围</summary><p>本原型使用现有词典中的10个词。初始的 study、library 是两条到期复习示例，不计入今日完成。每轮最多3词，刷新可恢复；重置只清除此原型的记录。</p><p>原型中的加入生词本与手动熟练只保存在本浏览器；正式站点的收藏、词条管理和文章来源保持原样。词组本为独立本地演示，其他正式词书将在数据准备后扩展。朗读使用浏览器语音，未调用翻译或 AI 服务。</p></details>`;
    library.bindTabs();$('#book').onclick=openBooks;
    $('#scope').onchange=e=>{state.scope=e.target.value;save();home();};
    $('#plan-open').onclick=openPlan;$('#exempt-open').onclick=openExempt;
    $('#history-open').onclick=openHistory;
    $('#resume')?.addEventListener('click',()=>{state.libraryTab='learn';state.book=state.round.book || state.book;save();view='study';renderStudy();});
    $('#start-new').onclick=()=>openMode('NEW'); $('#start-review').onclick=()=>openMode('REVIEW');
  }
  function openPlan() {
    $('#new-goal').value=state.settings.newGoal; $('#review-goal').value=state.settings.reviewGoal; $('#auto-next').checked=state.settings.autoNext;
    const update=()=>{const n=Number($('#new-goal').value),r=Number($('#review-goal').value);$('#new-output').value=`${n} 词`;$('#review-output').value=`${r} 词`;$('#presets').innerHTML=presets.map(([name,a,b])=>`<button data-preset="${a},${b}" class="${n===a && r===b?'selected':''}">${name}<small>${a} / ${b}</small></button>`).join('');$('#presets').querySelectorAll('button').forEach(el=>el.onclick=()=>{const [a,b]=el.dataset.preset.split(',');$('#new-goal').value=a;$('#review-goal').value=b;update();});};
    $('#new-goal').oninput=update; $('#review-goal').oninput=update;update();$('#plan').showModal();
    $('#save-plan').onclick=()=>{state.settings={newGoal:Number($('#new-goal').value),reviewGoal:Number($('#review-goal').value),autoNext:$('#auto-next').checked};save();$('#plan').close();home();toast('学习计划已保存');};
  }
  function openMode(kind) {
    chosenKind=kind; $('#mode-title').textContent=kind==='NEW'?'这次，怎样认识新词？':'这次，怎样检验记忆？';
    $('#mode-note').textContent='本轮最多3个示例词。随时可暂停，继续原来的进度。';
    $('#recognition-note').textContent=kind==='NEW'?'看英文与释义，建立第一印象。':'先看英文，回想含义，再查看详情。';
    $('#spelling-title').textContent=kind==='NEW'?'跟打模式':'默写模式';
    $('#spelling-note').textContent=kind==='NEW'?'照着单词虚影输入，不计为独立回忆。':'只看中文输入英文，提示后需要再练一次。';
    $('#mode').showModal();
  }
  function start(mode) {
    $('#mode').close(); const ids=(chosenKind==='NEW'?books[state.book].ids.filter(id=>!isCovered(id) && (state.book!=='personal'||library.enrolled(id))).slice(0,Math.max(0,state.settings.newGoal-todayEvents('NEW').length)):dueIds()).slice(0,3);
    if(!ids.length){home();toast('当前没有可学习的示例词。');return;}
    state.round={book:state.book,kind:chosenKind,mode,queue:ids,originalTotal:ids.length,passed:[],failed:[],prefix:'',attemptHint:false,attemptError:false,phase:'QUESTION',lastResult:null};save();view='study';renderStudy();
  }
  function wordActions(id) {
    const saved=state.notebook.includes(id),exempt=!!state.exempt[id];
    return `<div class="word-actions"><button data-save-word="${id}" aria-pressed="${saved}" ${saved?'disabled':''}>${saved?'✓ 已加入生词本':'＋ 加入生词本'}</button><button data-exempt-word="${id}" ${exempt?'disabled':''}>${exempt?'✓ 已标熟练 · 免复习':'✓ 标记熟练'}</button></div>`;
  }
  function bindWordActions() {
    document.querySelectorAll('[data-save-word]').forEach(button=>button.onclick=()=>{
      const id=button.dataset.saveWord;stopTimer();
      if(!state.notebook.includes(id))state.notebook.push(id);
      state.wordSources ??= {};state.wordSources[id] ??= [{label:'学习过程中收藏 · 本地演示',sentence:'',translation:''}];
      books.personal.ids=state.notebook;save();button.textContent='✓ 已加入生词本';button.setAttribute('aria-pressed','true');button.disabled=true;
      toast('已加入生词本，复习安排保持不变。');$('#answer')?.focus({preventScroll:true});
    });
    document.querySelectorAll('[data-exempt-word]').forEach(button=>button.onclick=()=>{
      const id=button.dataset.exemptWord;if(state.exempt[id])return;stopTimer();stopVoice();state.exempt[id]={since:Date.now()};
      const r=state.round;
      if(view==='study' && r && r.phase!=='COMPLETED' && r.queue[0]===id){
        r.skipped ??= [];if(!r.passed.includes(id) && !r.skipped.includes(id))r.skipped.push(id);
        r.queue.shift();r.prefix='';r.attemptHint=false;r.attemptError=false;r.lastResult=null;r.phase=r.queue.length?'QUESTION':'COMPLETED';
      }
      save();renderStudy();toast('已标记熟练，不再自动复习。首页可恢复复习。');
    });
  }
  function openExempt() {
    $('#exempt-items').innerHTML=Object.keys(state.exempt).length?Object.keys(state.exempt).map(id=>`<div class="exempt-row"><div><b>${esc(words[id].word)}</b><small>手动熟练 · 不安排自动复习</small></div><button class="secondary" data-restore-word="${id}">恢复复习</button></div>`).join(''):'<p class="empty">还没有手动标记的熟练词。</p>';
    $('#exempt-items').querySelectorAll('[data-restore-word]').forEach(button=>button.onclick=()=>{const id=button.dataset.restoreWord;delete state.exempt[id];save();home();openExempt();toast(state.mastery[id]?'已恢复原复习计划，不改变已有历史。':'已取消熟练标记，可重新加入新学。');});
    if(!$('#exempt').open)$('#exempt').showModal();
  }
  function voices() { return '<span class="speech-controls"><button id="voice" aria-label="英式朗读">◖ 英</button><button id="voice-us" aria-label="美式朗读">◖ 美</button></span>'; }
  function details(w) {
    const example=w.examples.map(e=>`<p>${esc(e.text)}<br><span class="muted">${esc(e.translationZh)}</span></p>`).join('');
    return `<div class="meaning">${w.senses.map(s=>`<p><span class="muted">${esc(s.partOfSpeech)}</span> ${esc(s.definitionZh)}</p>`).join('')}</div><details open><summary>固定例句</summary>${example || '<p>该词的固定例句待补充。</p>'}</details><details><summary>短语与近义词</summary>${w.phrases.map(p=>`<p><b>${esc(p.text)}</b> ${esc(p.translationZh)}</p>`).join('')}${w.synonyms.map(s=>`<p>${esc(s.partOfSpeech)} ${s.words.map(esc).join(' / ')}<br><span class="muted">${esc(s.meaningEn)}</span></p>`).join('')}<p class="tiny muted">现有本地词典资料；近义词按词义区分，不代表任意替换。</p></details>`;
  }
  function renderStudy() {
    stopTimer();const r=state.round;if(!r){home();return;}
    if(r.phase==='COMPLETED'){renderComplete();return;}
    if(r.phase==='QUESTION' && state.exempt[r.queue[0]]){const id=r.queue.shift();r.skipped ??= [];if(!r.skipped.includes(id))r.skipped.push(id);r.prefix='';r.attemptHint=false;r.attemptError=false;r.phase=r.queue.length?'QUESTION':'COMPLETED';save();renderStudy();return;}
    const w=words[r.queue[0]], review=r.kind==='REVIEW', typing=r.mode==='SPELLING', definition=r.phase==='DEFINITION', copy=typing&&!review;
    if(copy && definition){next();return;}
    $('#app').innerHTML=`<div class="study-top"><div><p class="eyebrow">${review?'REVIEW / 到期复习':'NEW / 认识新词'}</p><h2>${typing?(review?'默写模式':'跟打模式'):'看词模式'}</h2></div><div class="study-heading-actions"><button class="secondary" id="manage-notebook">生词本 ↗</button><button class="secondary" id="pause">暂停并返回首页</button></div></div><div class="study-layout"><section class="study-card"><div class="study-label"><span>${books[r.book || state.book].name}</span><span>已处理 ${r.passed.length+(r.skipped?.length||0)} / ${r.originalTotal}</span></div>
      ${wordActions(w.id)}<p class="word-action-note">加入生词本方便回看；标记熟练会跳过本词，不再自动复习。</p>
      ${definition?`<div class="feedback ${r.lastResult==='PASS'?'':'assisted'}">${r.lastResult==='PASS'?(review?'本词复习完成':'本词学习完成'):'先记一记，稍后再试一次'}${r.failed.includes(w.id)?' · 保留本轮辅助记录':''}</div><h1 class="word">${esc(w.word)}</h1><div class="phonetic">/${esc(w.ipa)}/ ${voices()}</div>${details(w)}<div class="next-row"><span>${r.lastResult==='PASS'?'按计划安排下次复习':'本词将回到队尾，本次不增加完成数'}</span><button class="primary" id="next">${r.lastResult==='PASS'?'下一词 →':'回队再练 →'}</button></div>`:
      typing?`${review?`<p class="meaning" style="margin-top:44px">${esc(w.pos)} ${esc(w.meaning)}</p>`:'<p class="copy-heading">照着单词输入，完成后继续下一个</p>'}<div class="phonetic">${review?'':`/${esc(w.ipa)}/`}${voices()}</div><p class="muted tiny">${review?'请回想英文，输入后自动校验。':'照着虚影跟打，熟悉单词的拼写。'}</p><div class="typing-shell" id="typing-shell"><div class="typing-word" aria-hidden="true"><span class="typed" id="typed"></span><span class="ghost" id="ghost"></span></div><input id="answer" type="text" aria-label="输入英文单词" autocomplete="off" autocapitalize="off" spellcheck="false" inputmode="text"></div><p class="typing-tip" id="typing-tip" aria-live="polite">${r.attemptHint || r.attemptError?'本次已使用辅助，完成后会回队再练。':'输入正确字母，继续向前。'}</p>${review?'<div class="typing-tools"><button class="secondary" id="hint">提示一个字母</button><button class="secondary" id="unknown">想不起来</button></div>':'<p class="copy-note">整轮完成后，一起查看释义与例句。</p>'}<p class="tiny muted">${review?'默写不显示目标英文，输入错误会保留正确前缀。':'跟打算新词学习完成，不算独立回忆。'}</p>`:
      `<h1 class="word">${esc(w.word)}</h1><div class="phonetic">/${esc(w.ipa)}/ ${voices()}</div>${review?'<p class="meaning muted">先回想它的含义，再选择。</p>':`<p class="meaning">${esc(w.pos)} ${esc(w.meaning)}</p>`}<div class="choice-row"><button class="secondary" id="unknown">${review?'不认识':'还没记住'}</button><button class="primary" id="know">${review?'认识，查看释义':'学会了'}</button></div><p class="tiny muted">${review?'看词自评记录不计作独立默写。':'认识新词后，按间隔安排复习。'}</p>`}
      </section><aside class="queue"><h3>本轮词序</h3><p class="tiny muted">${r.originalTotal} 个词 · 错词回队</p><ol>${r.queue.map((id,i)=>`<li><span>${i===0?'●':i+1}</span><span>${review && (i>0 || !definition)?'待回忆':esc(words[id].word)}</span></li>`).join('')}</ol><p class="tiny muted">已完成 ${r.passed.length} 个<br>可暂停、刷新后继续</p>${typing&&review?`<label class="check"><input id="study-auto" type="checkbox" ${state.settings.autoNext?'checked':''}> 默写通过后自动下一词</label>`:`<p class="tiny muted">${copy?'跟打连续进行，释义在整轮结束后展示。':'查看释义后，点击下一词继续。'}</p>`}</aside></div>`;
    $('#manage-notebook').onclick=()=>{state.libraryTab='words';save();home();};
    bindWordActions();
    $('#pause').onclick=()=>{state.libraryTab='learn';save();home();toast('已暂停，进度保存在这个浏览器中。');};
    if($('#study-auto')) $('#study-auto').onchange=e=>{state.settings.autoNext=e.target.checked;save();stopTimer();};
    $('#voice')?.addEventListener('click',()=>speak(w.word,'en-GB'));$('#voice-us')?.addEventListener('click',()=>speak(w.word,'en-US'));
    $('#next')?.addEventListener('click',next);
    $('#unknown')?.addEventListener('click',()=>finish('UNKNOWN'));
    $('#know')?.addEventListener('click',()=>finish('PASS'));
    if(typing && !definition){const input=$('#answer');input.value=r.prefix;updateTyping(); input.oncompositionstart=()=>composing=true;input.oncompositionend=()=>{composing=false;typeInput(input.value);};input.oninput=()=>{if(!composing)typeInput(input.value);};input.onpaste=()=>{r.attemptHint=true;markFailed();save();}; input.onkeydown=e=>{if(e.repeat)e.preventDefault();};if(review) $('#hint').onclick=()=>{r.attemptHint=true;markFailed();r.prefix=w.word.slice(0,r.prefix.length+1);save();updateTyping();if(r.prefix===w.word)finish('ASSISTED');else input.focus({preventScroll:true});};input.focus({preventScroll:true});}
    if(definition && r.lastResult==='PASS' && state.settings.autoNext && typing && review && !document.hidden){timer=setTimeout(()=>{if(view==='study' && state.round===r && r.phase==='DEFINITION' && !document.hidden)next();},1800);}
  }
  function markFailed(){const r=state.round;if(!r.failed.includes(r.queue[0]))r.failed.push(r.queue[0]);}
  function updateTyping(){const r=state.round,w=words[r.queue[0]],review=r.kind==='REVIEW';$('#typed').textContent=r.prefix;$('#ghost').textContent=review?'·'.repeat(w.word.length-r.prefix.length):w.word.slice(r.prefix.length);$('#answer').value=r.prefix;}
  function typeInput(raw){const r=state.round;if(view!=='study'||r.phase!=='QUESTION')return;const target=words[r.queue[0]].word;const value=raw.toLowerCase().replace(/[’‘]/g,"'");let n=0;while(n<value.length && value[n]===target[n])n++;if(n<value.length){r.attemptError=true;markFailed();$('#typing-shell').classList.add('invalid');$('#typing-tip').textContent='这个字母不对，正确前缀已保留。完成后回队再练。';}else{$('#typing-shell').classList.remove('invalid');}r.prefix=value.slice(0,n);save();updateTyping();if(r.prefix===target)finish(r.attemptHint||r.attemptError?'ASSISTED':'PASS');}
  function finish(result){stopTimer();const r=state.round;if(r.phase!=='QUESTION')return;if(result!=='PASS')markFailed();r.lastResult=result;r.phase='DEFINITION';if(result==='PASS'){const id=r.queue[0],old=state.mastery[id],stage=r.kind==='NEW'||r.failed.includes(id)?1:Math.min(7,(old?.stage||0)+1);state.mastery[id]={stage,due:Date.now()+intervals[stage-1]*86400000};r.passed.push(id);state.events.push({id,kind:r.kind,mode:r.mode,time:Date.now(),assisted:r.failed.includes(id),independent:r.kind==='REVIEW' && r.mode==='SPELLING' && !r.failed.includes(id),stage});}save();stopVoice();if(r.kind==='NEW' && r.mode==='SPELLING'){next();return;}renderStudy();}
  function next(){stopTimer();const r=state.round;if(view!=='study'||r?.phase!=='DEFINITION')return;const id=r.queue.shift();if(r.lastResult!=='PASS')r.queue.push(id);r.prefix='';r.attemptHint=false;r.attemptError=false;r.lastResult=null;r.phase=r.queue.length?'QUESTION':'COMPLETED';save();stopVoice();renderStudy();}
  function renderComplete(){const r=state.round;$('#app').innerHTML=`<section class="study-card completed"><div class="seal">✓</div><p class="eyebrow">ROUND COMPLETE</p><h1>这一轮，完成了。</h1><p class="muted">${r.kind==='NEW'?'新学':'复习'} ${r.passed.length} 词 · ${r.failed.length} 词有辅助或回队记录 · ${r.skipped?.length||0} 词手动熟练跳过</p><p>${r.kind==='NEW'?'学习完成的词按计划复习；手动熟练的词不再自动复习。':'本轮用过辅助的词按较短间隔复习，最后拼对不会抹掉本轮记录。'}</p><button class="primary" id="done">返回学习首页 →</button></section>${r.kind==='NEW'&&r.mode==='SPELLING'?`<section class="round-details"><div class="section-title"><h2>这一轮的词，回头看一看</h2><span class="tiny muted">${r.passed.length} 个词</span></div>${r.passed.map(id=>{const w=words[id];return `<article class="round-word"><h2>${esc(w.word)} <small>/${esc(w.ipa)}/</small></h2>${wordActions(id)}${details(w)}</article>`;}).join('')}</section>`:''}`;$('#done').onclick=()=>{state.libraryTab='learn';save();home();};bindWordActions();}
  function openHistory(){$('#history-content').innerHTML=state.events.length?state.events.slice().reverse().map(e=>`<div class="history-row"><div><b>${esc(words[e.id].word)}</b><small>${e.kind==='NEW'?'新学':'复习'} · ${e.mode==='SPELLING'?(e.kind==='NEW'?'跟打':'默写'):'看词'} · ${e.independent?'独立回忆':e.assisted?'本轮有辅助':'学习完成'}<br>${new Date(e.time).toLocaleString('zh-CN')} · 下次间隔 ${intervals[e.stage-1]} 天</small></div><span>✓</span></div>`).join(''):'<p class="empty">还没有完成记录。先试一轮学习吧。</p>';$('#history').showModal();}
  function speak(word,lang){if(!window.speechSynthesis){toast('浏览器未提供朗读服务。');return;}stopVoice();const generation=speechGeneration,u=new SpeechSynthesisUtterance(word);u.lang=lang;u.onerror=e=>{if(generation===speechGeneration && !['canceled','interrupted'].includes(e.error))toast('朗读遇到问题，可以再试一次。');};window.speechSynthesis.speak(u);}
  $('#nav-menu').onclick=()=>{const open=$('#prototype-nav').classList.toggle('expanded');$('#nav-menu').setAttribute('aria-expanded',String(open));$('#nav-menu').setAttribute('aria-label',open?'收起导航':'展开导航');};
  $('#recognition').onclick=()=>start('RECOGNITION');$('#spelling').onclick=()=>start('SPELLING');
  document.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>b.closest('dialog').close());
  $('[data-home]').onclick=e=>{e.preventDefault();save();home();};
  $('#reset').onclick=()=>{stopTimer();stopVoice();document.querySelectorAll('dialog[open]').forEach(d=>d.close());state=seed();state.notebook=['study','library','step','look','progress'];state.exempt={};books.personal.ids=state.notebook;save();home();toast('演示已重置，正式学习数据未受影响。');};
  document.addEventListener('visibilitychange',()=>{if(document.hidden){stopTimer();stopVoice();save();}});
  window.addEventListener('pagehide',()=>{stopTimer();stopVoice();save();});
  function resumeRound(){state.libraryTab='learn';state.book=state.round.book||state.book;save();view='study';renderStudy();}
  const library=window.createVocabularyLibrary({getState:()=>state,words,esc,details,save,toast,home,resume:resumeRound,stopTimer});
  document.addEventListener('prototype-history',openHistory);
  home();
})();
