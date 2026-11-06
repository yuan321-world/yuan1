import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { mockIdentity } from './identity-fixtures'
test('administrator validates a file and retries draft import using the same request ID', async ({ page }) => {
  await mockIdentity(page, false, true)
  await page.route('**/api/v1/admin/articles/categories', route => route.fulfill({ json: [] }))
  await page.route('**/api/v1/admin/articles?*', route => route.fulfill({ json: { items: [], page: 1, pageSize: 20, total: 0 } }))
  await page.route('**/api/v1/admin/articles/import/validate', route => { expect(route.request().postDataJSON().articles).toHaveLength(10); return route.fulfill({ json: { valid: true, fieldErrors: {}, itemCount: 10 } }) })
  const ids: string[] = []
  await page.route('**/api/v1/admin/articles/import/confirm?*', route => {
    ids.push(new URL(route.request().url()).searchParams.get('clientImportId')!)
    return route.fulfill(ids.length === 1 ? { status: 503, json: { code: 'SERVICE_UNAVAILABLE', message: '暂时无法导入，请重试' } } : { status: 201, json: { batchId: '1', articleIds: ['10', '11'] } })
  })
  await page.goto('/admin/articles')
  await page.getByText('批量导入草稿', { exact: true }).click()
  const file = page.getByLabel('文章JSON文件', { exact: true })
  await file.setInputFiles({ name: 'bad.json', mimeType: 'application/json', buffer: Buffer.from('{') })
  await expect(page.getByText('无法解析JSON文件，请检查格式后重新选择')).toBeVisible()
  await file.setInputFiles({ name: 'large.json', mimeType: 'application/json', buffer: Buffer.alloc(1048577, ' ') })
  await expect(page.getByText('文件须不超过1MiB，请拆分后导入')).toBeVisible()
  await file.setInputFiles({ name: 'original-b1-b2.v1.json', mimeType: 'application/json', buffer: readFileSync('../content/reading/original-b1-b2.v1.json') })
  await page.getByRole('button', { name: '校验文件', exact: true }).click()
  const confirm = page.getByRole('button', { name: '确认导入 10 篇草稿', exact: true })
  await confirm.click(); await expect(page.getByText('暂时无法导入，请重试', { exact: true })).toBeVisible()
  await confirm.click(); await expect(page.getByText('已创建草稿，请逐篇预览、校对后发布。')).toBeVisible()
  expect(ids).toHaveLength(2); expect(ids[0]).toBe(ids[1])
  await expect(page.getByRole('link', { name: '编辑草稿 #10', exact: true })).toHaveAttribute('href', '/admin/articles/10/edit')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
