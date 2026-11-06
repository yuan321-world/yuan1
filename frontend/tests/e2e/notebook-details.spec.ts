import {test,expect} from '@playwright/test'
import {mockIdentity} from './identity-fixtures'

test('word notebook reuses dictionary details in a centered modal',async({page})=>{
  await mockIdentity(page)
  const word={id:'w1',entryId:'e1',lemma:'banana',phonetic:'bəˈnɑːnə',senses:[{partOfSpeech:'n.',definitionZh:'香蕉',definitionEn:''}],sourceNote:'本地词典',active:true,learning:null}
  await page.route('**/api/v1/vocabulary/words?**',r=>r.fulfill({json:{items:[word],total:1,page:1,pageSize:20}}))
  await page.route('**/api/v1/vocabulary/words/w1?**',r=>r.fulfill({json:{word,sources:{items:[],total:0,page:1,pageSize:10},origins:[{id:'o1',kind:'DICTIONARY',title:'查词收藏'}]}}))
  await page.route('**/api/v1/dictionary/lookup?**',r=>r.fulfill({json:{found:true,candidates:[{...word,collection:{wordId:'w1',active:true},examples:[]}]}}))
  await page.goto('/vocabulary?tab=words')
  await page.getByRole('button',{name:'banana',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'生词详情',exact:true})
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('button',{name:'英式朗读',exact:true})).toContainText('UK')
  await expect(dialog.getByRole('button',{name:'美式朗读',exact:true})).toContainText('US')
  await expect(dialog.getByRole('button',{name:'英式朗读',exact:true})).toContainText('/bəˈnɑːnə/')
  await expect(dialog.locator('.vl-example').first()).toBeVisible()
  await expect(dialog).not.toContainText('固定例句待补充')
  await expect(dialog.getByRole('button',{name:'加入学习',exact:true})).toBeVisible()
  const box=(await dialog.boundingBox())!,layoutWidth=await page.evaluate(()=>document.documentElement.clientWidth)
  // Native dialog centers within the root layout area, which reserves a scrollbar gutter.
  expect(Math.abs(box.x+box.width/2-layoutWidth/2)).toBeLessThan(10)
  expect(box.width).toBeLessThanOrEqual(520)
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(page.getByRole('button',{name:'banana',exact:true})).toBeFocused()
})

test('phrase notebook shows examples and retains editable personal notes',async({page})=>{
  await mockIdentity(page)
  let phrase={id:'p1',text:'play a role',meaningZh:'发挥作用；扮演角色',note:'我自己的记忆方法',active:true,enrolled:false,exempt:false,independentPasses:0,sources:[]}
  let expectedMeaning=phrase.meaningZh
  await page.route('**/api/v1/vocabulary/phrases?**',r=>r.fulfill({json:{items:[phrase],total:1,page:1,pageSize:20,stats:{collected:1,enrolled:0,independentPasses:0,available:0}}}))
  await page.route('**/api/v1/vocabulary/phrases/practice',r=>r.fulfill({json:null}))
  await page.route('**/api/v1/vocabulary/phrases/p1',r=>{
    expect(r.request().method()).toBe('PATCH')
    const input=r.request().postDataJSON();expect(input).toEqual({meaningZh:expectedMeaning,note:'新的记忆方法'})
    phrase={...phrase,...input};return r.fulfill({json:phrase})
  })
  await page.goto('/vocabulary?tab=phrases')
  const opener=page.getByRole('button',{name:'详情 / 编辑',exact:true})
  await expect(opener).toBeVisible()
  await expect(page.getByRole('button',{name:'手动收藏词组',exact:true})).toHaveCount(0)
  await opener.click()
  const dialog=page.getByRole('dialog',{name:'词组详情',exact:true})
  await expect(dialog.getByRole('heading',{name:'play a role',exact:true})).toBeVisible()
  await expect(dialog.locator('.ws-phrase-example')).toHaveCount(2)
  await expect(dialog.getByLabel('个人备注',{exact:true})).toHaveValue('我自己的记忆方法')
  const box=(await dialog.boundingBox())!,layoutWidth=await page.evaluate(()=>document.documentElement.clientWidth)
  // Native dialog centers within the root layout area, which reserves a scrollbar gutter.
  expect(Math.abs(box.x+box.width/2-layoutWidth/2)).toBeLessThan(10)
  expect(box.width).toBeLessThanOrEqual(520)
  await dialog.getByLabel('个人备注',{exact:true}).fill('新的记忆方法')
  await dialog.getByRole('button',{name:'保存',exact:true}).click()
  await expect(dialog).not.toBeVisible();await opener.click()
  await expect(dialog.getByLabel('个人备注',{exact:true})).toHaveValue('新的记忆方法')
  await dialog.getByRole('button',{name:'编辑释义',exact:true}).click()
  expectedMeaning='参与；发挥作用'
  await dialog.getByLabel('个人中文释义',{exact:true}).fill(expectedMeaning)
  await dialog.getByRole('button',{name:'保存',exact:true}).click()
  await expect(dialog).not.toBeVisible();await opener.click()
  await expect(dialog).toContainText(expectedMeaning)
  await page.keyboard.press('Escape');await expect(dialog).not.toBeVisible()
  await expect(opener).toBeFocused()
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})
