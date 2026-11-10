const pet = document.querySelector('#pet')
const petArt = document.querySelector('#pet-art')
const panel = document.querySelector('#assistant')
const panelBody = document.querySelector('#assistant-body')
const scene = document.querySelector('#scene')
const announce = document.querySelector('#announce')
const restore = document.querySelector('#restore')
const visibility = document.querySelector('#visibility')
const poseList = document.querySelector('#poses')

// Each view box frames one pose on the approved artwork.
const poses = {
  idle: ['眨眼', '36 95 358 420'],
  wave: ['挥手', '429 87 324 429'],
  read: ['看书', '796 107 342 408'],
  think: ['思考', '1184 76 322 439'],
  celebrate: ['庆祝', '37 526 344 461'],
  comfort: ['安慰', '426 526 332 461'],
  sleep: ['睡觉', '764 620 400 362'],
  stretch: ['伸懒腰', '1166 525 348 456'],
}

const passages = {
  library: {
    title: 'A Library of Small Things',
    first: 'Not every trip to the library begins with a book. At one neighborhood library, visitors can borrow a sewing machine, a set of tools, or even a small telescope.',
    sentence: 'Instead of buying things they might use only once, people can share them with their neighbors.',
    explanation: 'Instead of 后接动名词 buying，意思是「不必购买」。they might use only once 修饰 things。整句意思是，人们可以和邻居共享那些可能只用一次的东西。',
    question: 'Why do people borrow tools from the library?',
    choices: ['To buy more tools.', 'To share items they rarely need.', 'To learn how to build telescopes.'],
    feedback: '原文用 “use only once” 说明这些物品不常用，共享可以避免为偶尔使用而购买。',
  },
  campus: {
    title: 'A Quieter Campus',
    first: 'Last spring, a group of students began meeting in a small garden between classes. They left their phones in their bags and spent ten minutes listening to the sounds around them.',
    sentence: 'By making a little time for silence, the students found it easier to focus later in the day.',
    explanation: 'By 后接动名词 making，表示「通过留出一点安静的时间」。found it easier to focus 中，it 是形式宾语，to focus 才是实际内容。',
    question: 'What changed after the students spent time in the garden?',
    choices: ['They started using their phones more.', 'They could concentrate more easily later.', 'They moved their classes into the garden.'],
    feedback: '原文 “easier to focus later in the day” 直接说明，学生当天稍后更容易集中注意力。',
  },
}
const scenes = {
  read: ['外刊精读', '读一段短文，试试让 Yuss 解释其中一句。'],
  words: ['背单词', '看看当前单词，再试试请 Yuss 安排今天的学习。'],
  quiz: ['专项练习', '先选答案，再看看 Yuss 如何给你反馈。'],
}
let activeScene = 'read'
let activeMode = 'ask'
let passage = passages.library
let goal = 20
let returnFocus

function setPose(key) {
  const [label, box] = poses[key]
  pet.dataset.pose = key
  petArt.setAttribute('viewBox', box)
  const crop = petArt.querySelector('rect')
  box.split(' ').forEach((value, index) => crop.setAttribute(['x', 'y', 'width', 'height'][index], value))
  for (const button of poseList.children) button.setAttribute('aria-pressed', String(button.dataset.pose === key))
  announce.textContent = 'Yuss 正在' + label
}

function setVisible(visible) {
  pet.hidden = !visible
  restore.hidden = visible
  visibility.textContent = visible ? '隐藏 Yuss' : '显示 Yuss'
  if (!visible) closePanel(false)
}

function openPanel(mode, opener) {
  if (panel.hidden) returnFocus = opener
  setVisible(true)
  panel.hidden = false
  pet.setAttribute('aria-expanded', 'true')
  pet.setAttribute('aria-label', '收起 Yuss')
  setMode(mode)
  document.querySelector('#close').focus()
}

function closePanel(restoreFocus = true) {
  panel.hidden = true
  pet.setAttribute('aria-expanded', 'false')
  pet.setAttribute('aria-label', '打开 Yuss')
  if (restoreFocus && returnFocus?.isConnected && !returnFocus.hidden) returnFocus.focus()
}

function renderScene(key) {
  activeScene = key
  const [title, subtitle] = scenes[key]
  document.querySelector('#scene-title').textContent = title
  document.querySelector('#scene-subtitle').textContent = subtitle
  for (const button of document.querySelectorAll('[data-scene]')) {
    button.setAttribute('aria-pressed', String(button.dataset.scene === key))
  }
  if (key === 'read') {
    scene.innerHTML = '<p class="kicker">阅读示例</p><h2 lang="en"></h2><div class="reading-copy" lang="en"><p></p><p></p></div><div class="actions"><button type="button" data-open="ask">请 Yuss 解释末句</button></div>'
    scene.querySelector('h2').textContent = passage.title
    const lines = scene.querySelectorAll('.reading-copy p')
    lines[0].textContent = passage.first
    lines[1].textContent = passage.sentence
  } else if (key === 'words') {
    scene.innerHTML = '<p class="kicker">今日新词 · 示例</p><h2 lang="en">consistent</h2><p>形容词 · 一贯的，始终如一的</p><p class="reading-copy" lang="en">Consistent practice matters more than a perfect first attempt.</p><div class="actions"><button type="button" data-open="ask">请 Yuss 讲讲这个词</button><button type="button" data-open="plan">预览学习计划</button></div>'
  } else {
    scene.innerHTML = '<p class="kicker">阅读理解 · 示例</p><h2 lang="en"></h2><form id="quiz"><fieldset><legend lang="en"></legend><div id="options"></div></fieldset><div class="actions"><button type="submit" class="primary">检查答案</button></div><p id="quiz-feedback" role="status"></p></form>'
    scene.querySelector('h2').textContent = passage.title
    scene.querySelector('legend').textContent = passage.question
    passage.choices.forEach((choice, index) => {
      const label = document.createElement('label')
      label.className = 'option'
      const input = document.createElement('input')
      input.type = 'radio'
      input.name = 'answer'
      input.value = String(index)
      input.required = true
      label.append(input, choice)
      scene.querySelector('#options').append(label)
    })
    scene.querySelector('#quiz').addEventListener('submit', event => {
      event.preventDefault()
      const correct = new FormData(event.currentTarget).get('answer') === '1'
      setPose(correct ? 'celebrate' : 'comfort')
      scene.querySelector('#quiz-feedback').textContent = correct ? '答对了！' + passage.feedback : '再试一次。想想大家为什么更愿意借用这些物品。'
      if (!correct && passage === passages.campus) scene.querySelector('#quiz-feedback').textContent = '再试一次。找找原文里表示「更容易集中注意力」的短语。'
    })
  }
  if (!panel.hidden) setMode(activeMode)
  setPose(key === 'read' ? 'read' : 'idle')
}

function addMessage(thread, message, fromUser = false) {
  const bubble = document.createElement('p')
  bubble.className = fromUser ? 'bubble message-user' : 'bubble'
  bubble.textContent = message
  thread.append(bubble)
}

function setMode(mode) {
  activeMode = mode
  for (const button of document.querySelectorAll('[data-mode]')) {
    button.setAttribute('aria-pressed', String(button.dataset.mode === mode))
  }
  if (mode === 'ask') renderChat()
  if (mode === 'generate') renderGenerator()
  if (mode === 'plan') renderPlan()
}

function renderChat() {
  const source = activeScene === 'read' ? '当前文章末句' : activeScene === 'words' ? '当前单词' : '当前练习题'
  panelBody.innerHTML = '<div class="thread" role="log"><p class="bubble">嗨，我是 Yuss。可以带上当前学习内容，试一次答疑。</p></div><form><label>你想问什么？<input name="question" maxlength="240" required placeholder="输入一个问题"></label><label class="check"><input type="checkbox" name="context" checked><span></span></label><button class="primary" type="submit">发送</button></form><p class="subtle">示例回复根据学习场景切换。</p>'
  panelBody.querySelector('.check span').textContent = '带上' + source
  const thread = panelBody.querySelector('.thread')
  const form = panelBody.querySelector('form')
  form.addEventListener('submit', event => {
    event.preventDefault()
    const input = form.elements.question
    const question = input.value.trim()
    if (!question) { input.focus(); return }
    addMessage(thread, question, true)
    const reply = !form.elements.context.checked ? '已收到问题。勾选「带上当前内容」可以体验上下文答疑。'
      : activeScene === 'read' ? passage.explanation
      : activeScene === 'words' ? 'consistent 指做事或表现保持稳定。例句里的 consistent practice 是「持续练习」。'
      : '先看看题干在问原因还是结果，再回原文找能直接支持选项的句子。'
    addMessage(thread, reply)
    setPose('think')
    input.value = ''
    input.focus()
    panelBody.scrollTop = panelBody.scrollHeight
  })
}

function renderGenerator() {
  panelBody.innerHTML = '<p>选一个主题，预览一段短文和一道阅读题。</p><form><label>主题<select name="topic"><option value="library">社区图书馆</option><option value="campus">校园生活</option></select></label><button type="submit" class="primary">预览示例练习</button></form><div id="generated" class="bubble" hidden></div>'
  const form = panelBody.querySelector('form')
  form.addEventListener('submit', event => {
    event.preventDefault()
    const example = passages[form.elements.topic.value]
    const result = panelBody.querySelector('#generated')
    const title = document.createElement('h3')
    title.lang = 'en'
    title.textContent = example.title
    const text = document.createElement('p')
    text.lang = 'en'
    text.textContent = example.first + ' ' + example.sentence
    const start = document.createElement('button')
    start.type = 'button'
    start.textContent = '开始练习'
    start.addEventListener('click', () => {
      passage = example
      closePanel(false)
      renderScene('quiz')
      scene.querySelector('input').focus()
    })
    result.replaceChildren(title, text, start)
    result.hidden = false
    setPose('read')
    panelBody.scrollTop = panelBody.scrollHeight
  })
}

function renderPlan() {
  panelBody.innerHTML = '<p>先安排待复习的词，再按可用时间建议新词目标。</p><form><label>今天可用时间<select name="minutes"><option value="20">20 分钟</option><option value="40" selected>40 分钟</option><option value="60">60 分钟</option></select></label><button type="submit" class="primary">预览学习计划</button></form><div id="proposal" class="bubble" hidden></div>'
  const form = panelBody.querySelector('form')
  form.addEventListener('submit', event => {
    event.preventDefault()
    const nextGoal = { 20: 8, 40: 15, 60: 25 }[form.elements.minutes.value]
    const result = panelBody.querySelector('#proposal')
    const copy = document.createElement('p')
    copy.textContent = '先复习 35 词，再学 ' + nextGoal + ' 个新词。每日新词目标将从 ' + goal + ' 改为 ' + nextGoal + '。'
    const apply = document.createElement('button')
    apply.type = 'button'
    apply.textContent = '应用到演示目标'
    apply.addEventListener('click', () => {
      goal = nextGoal
      document.querySelector('#goal').textContent = String(goal)
      copy.textContent = '已将演示目标设为每日 ' + goal + ' 个新词。'
      apply.remove()
      form.querySelector('button').focus()
      announce.textContent = copy.textContent
      setPose('celebrate')
    })
    result.replaceChildren(copy, apply)
    result.hidden = false
    setPose('think')
  })
}

for (const [key, [label]] of Object.entries(poses)) {
  const button = document.createElement('button')
  button.type = 'button'
  button.dataset.pose = key
  button.textContent = label
  button.addEventListener('click', () => {
    setVisible(true)
    setPose(key)
  })
  poseList.append(button)
}

document.addEventListener('click', event => {
  const button = event.target.closest('button')
  if (!button) return
  if (button.dataset.scene) renderScene(button.dataset.scene)
  if (button.dataset.open) openPanel(button.dataset.open, button)
  if (button.dataset.mode) setMode(button.dataset.mode)
})
pet.addEventListener('click', () => {
  if (panel.hidden) { openPanel('ask', pet); setPose('wave') }
  else closePanel()
})
document.querySelector('#close').addEventListener('click', () => closePanel())
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && !panel.hidden) closePanel()
})
visibility.addEventListener('click', () => setVisible(pet.hidden))
restore.addEventListener('click', () => { setVisible(true); pet.focus() })
renderScene('read')
