import { expect, test } from '@playwright/test'

test('dragging a card persists its new status', async ({ page, request }) => {
  const companyResponse = await request.post('/api/companies', {
    data: { name: `E2E Company ${Date.now()}` }
  })
  expect(companyResponse.ok()).toBeTruthy()
  const company = (await companyResponse.json()).data

  const applicationResponse = await request.post('/api/applications', {
    data: { companyId: company.id, position: 'E2E Position' }
  })
  expect(applicationResponse.ok()).toBeTruthy()
  const application = (await applicationResponse.json()).data

  try {
    await page.goto('/')

    const sourceCard = page.locator(`[data-status="APPLIED"] [data-id="${application.id}"]`)
    const targetColumn = page.locator('[data-status="INTERVIEWING"]')
    await expect(sourceCard).toBeVisible()

    const [statusResponse] = await Promise.all([
      page.waitForResponse((response) =>
        response.url().endsWith(`/api/applications/${application.id}/status`) &&
        response.request().method() === 'PUT',
      { timeout: 10_000 }),
      sourceCard.dragTo(targetColumn)
    ])

    expect(statusResponse.ok()).toBeTruthy()
    await expect(targetColumn.locator(`[data-id="${application.id}"]`)).toBeVisible()

    const detailResponse = await request.get(`/api/applications/${application.id}`)
    expect(detailResponse.ok()).toBeTruthy()
    expect((await detailResponse.json()).data.status).toBe('INTERVIEWING')
  } finally {
    await request.delete(`/api/applications/${application.id}`)
  }
})

test('filters, opens by keyboard, and edits an application on mobile', async ({ page, request }) => {
  const suffix = Date.now()
  const companyResponse = await request.post('/api/companies', {
    data: { name: `Mobile Company ${suffix}` }
  })
  expect(companyResponse.ok()).toBeTruthy()
  const company = (await companyResponse.json()).data

  const applicationResponse = await request.post('/api/applications', {
    data: { companyId: company.id, position: `Original Position ${suffix}`, city: '杭州' }
  })
  expect(applicationResponse.ok()).toBeTruthy()
  const application = (await applicationResponse.json()).data

  try {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/')

    const search = page.getByLabel('搜索公司、岗位、城市或渠道')
    await search.fill(`Mobile Company ${suffix}`)
    await page.getByRole('button', { name: '筛选', exact: true }).click()
    await expect(page.getByText('1 条结果')).toBeVisible()

    const card = page.locator(`[data-id="${application.id}"]`)
    await card.focus()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('button', { name: '编辑投递' })).toBeVisible()
    await page.getByRole('button', { name: '编辑投递' }).click()

    const updatedPosition = `Updated Position ${suffix}`
    const editDialog = page.getByRole('dialog', { name: '编辑投递' })
    await expect(editDialog).toBeVisible()
    await editDialog.getByRole('textbox', { name: '岗位', exact: true }).fill(updatedPosition)
    const updateResponse = page.waitForResponse((response) =>
      response.url().endsWith(`/api/applications/${application.id}`) &&
      response.request().method() === 'PUT'
    )
    await editDialog.getByRole('button', { name: '保存修改' }).click()
    expect((await updateResponse).ok()).toBeTruthy()
    await expect(page.locator(`[data-id="${application.id}"]`)).toContainText(updatedPosition)

    const pageOverflows = await page.evaluate(() =>
      document.documentElement.scrollWidth > document.documentElement.clientWidth
    )
    expect(pageOverflows).toBeFalsy()
  } finally {
    await request.delete(`/api/applications/${application.id}`)
  }
})

test('saves a company before applying and keeps the optional position empty', async ({ page, request }) => {
  const suffix = Date.now()
  let applicationId

  try {
    await page.goto('/')
    await page.getByRole('button', { name: '收藏公司' }).click()
    const dialog = page.getByRole('dialog', { name: '收藏公司' })
    await dialog.getByRole('combobox', { name: '收藏公司' }).fill(`Saved Company ${suffix}`)
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: `Saved Company ${suffix}` }).click()

    const createdResponse = page.waitForResponse((response) =>
      response.url().endsWith('/api/applications') && response.request().method() === 'POST'
    )
    await dialog.getByRole('button', { name: '加入收藏', exact: true }).click()
    const response = await createdResponse
    expect(response.ok()).toBeTruthy()
    applicationId = (await response.json()).data.id

    const savedColumn = page.locator('[data-status="SAVED"]')
    const card = savedColumn.locator(`[data-id="${applicationId}"]`)
    await expect(card).toContainText('岗位待确定')
  } finally {
    if (applicationId) await request.delete(`/api/applications/${applicationId}`)
  }
})

test('adding an interview moves the card and the record survives reopening and refresh', async ({ page, request }) => {
  const suffix = Date.now()
  const companyResponse = await request.post('/api/companies', { data: { name: `Interview Company ${suffix}` } })
  const company = (await companyResponse.json()).data
  const applicationResponse = await request.post('/api/applications', {
    data: { companyId: company.id, position: `Interview Position ${suffix}` }
  })
  const application = (await applicationResponse.json()).data

  try {
    await page.goto('/')
    await page.locator(`[data-id="${application.id}"]`).click()
    const drawer = page.locator('.el-drawer')
    await drawer.locator('.add-round-form .el-select').click()
    await page.getByRole('option', { name: '视频面试', exact: true }).click()
    await expect(drawer.getByRole('button', { name: /添加第 1 轮/ })).toBeEnabled()
    await drawer.getByPlaceholder('备注（考了点啥、感受如何）').fill('持久化回归记录')

    const interviewResponse = page.waitForResponse((response) =>
      response.url().endsWith(`/api/applications/${application.id}/interviews`) &&
      response.request().method() === 'POST'
    )
    await drawer.getByRole('button', { name: /添加第 1 轮/ }).click()
    expect((await interviewResponse).ok()).toBeTruthy()
    await expect(page.locator(`[data-status="INTERVIEWING"] [data-id="${application.id}"]`)).toBeVisible()
    await expect(drawer).toContainText('持久化回归记录')

    await page.keyboard.press('Escape')
    await expect(drawer).toBeHidden()
    await page.reload()
    await page.locator(`[data-status="INTERVIEWING"] [data-id="${application.id}"]`).click()
    await expect(page.locator('.el-drawer')).toContainText('持久化回归记录')

    const failedResponse = page.waitForResponse((response) =>
      response.url().includes('/api/applications/interviews/') &&
      response.url().endsWith('/result') &&
      response.request().method() === 'PUT'
    )
    await page.locator('.el-drawer').getByRole('button', { name: '未通过', exact: true }).click()
    expect((await failedResponse).ok()).toBeTruthy()
    await expect(page.locator(`[data-status="REJECTED"] [data-id="${application.id}"]`)).toBeVisible()
  } finally {
    await request.delete(`/api/applications/${application.id}`).catch(() => {})
  }
})

test('filter controls do not overlap and the board has no page-level horizontal overflow', async ({ page }) => {
  for (const width of [1440, 1024, 390]) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/')

    const dateTrigger = page.getByRole('button', { name: '选择日期' })
    const dateBox = await dateTrigger.boundingBox()
    const filterBox = await page.getByRole('button', { name: '筛选', exact: true }).boundingBox()
    expect(dateBox).not.toBeNull()
    expect(filterBox).not.toBeNull()
    const overlaps = !(
      dateBox.x + dateBox.width <= filterBox.x ||
      filterBox.x + filterBox.width <= dateBox.x ||
      dateBox.y + dateBox.height <= filterBox.y ||
      filterBox.y + filterBox.height <= dateBox.y
    )
    expect(overlaps).toBeFalsy()

    await dateTrigger.click()
    await expect(page.getByLabel('筛选投递日期').first()).toBeVisible()
    await page.keyboard.press('Escape')

    const pageOverflows = await page.evaluate(() =>
      document.documentElement.scrollWidth > document.documentElement.clientWidth
    )
    expect(pageOverflows).toBeFalsy()
  }
})

test('new application dialog is compact on desktop', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 720 })
  await page.goto('/')
  await page.getByRole('button', { name: '记一笔投递' }).click()

  const dialog = page.getByRole('dialog', { name: '记一笔投递' })
  await expect(dialog).toBeVisible()
  await page.waitForTimeout(350)
  const box = await dialog.boundingBox()
  expect(box.y).toBeGreaterThanOrEqual(0)
  expect(box.y + box.height).toBeLessThanOrEqual(720)
  const bodyScrolls = await dialog.locator('.el-dialog__body').evaluate((element) =>
    element.scrollHeight > element.clientHeight
  )
  expect(bodyScrolls).toBeFalsy()
  await expect(dialog.getByPlaceholder('如：300/天 或 20K*14薪')).toBeVisible()
})

test('desktop board keeps all six status columns in one viewport', async ({ page, request }) => {
  const companyResponse = await request.post('/api/companies', {
    data: { name: `Layout Company ${Date.now()}` }
  })
  const company = (await companyResponse.json()).data
  const applicationIds = []

  try {
    for (let index = 0; index < 12; index += 1) {
      const response = await request.post('/api/applications', {
        data: { companyId: company.id, position: `Layout Position ${index}` }
      })
      applicationIds.push((await response.json()).data.id)
    }

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')
    const columns = page.locator('.board .column')
    await expect(columns).toHaveCount(6)

    const boxes = await Promise.all(Array.from({ length: 6 }, (_, index) => columns.nth(index).boundingBox()))
    boxes.slice(1).forEach((box) => expect(box.y).toBe(boxes[0].y))
    expect(boxes[5].x + boxes[5].width).toBeLessThanOrEqual(1440)
    expect(boxes[0].height).toBe(boxes[5].height)

    const appliedBody = page.locator('[data-status="APPLIED"]')
    const scrollable = await appliedBody.evaluate((element) => element.scrollHeight > element.clientHeight)
    expect(scrollable).toBeTruthy()
  } finally {
    await Promise.all(applicationIds.map((id) => request.delete(`/api/applications/${id}`)))
  }
})

test('switches between real-data views and persists dark mode', async ({ page, request }) => {
  const companyResponse = await request.post('/api/companies', { data: { name: `Views Company ${Date.now()}` } })
  const company = (await companyResponse.json()).data
  const applicationResponse = await request.post('/api/applications', {
    data: { companyId: company.id, position: 'Views Position', city: '上海', source: '内推', salaryRange: '20K*14薪' }
  })
  const application = (await applicationResponse.json()).data
  await request.post(`/api/applications/${application.id}/interviews`, {
    data: { type: 'VIDEO', scheduledAt: `${new Date().toISOString().slice(0, 10)}T14:30:00`, feedback: '视图测试' }
  })

  try {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')
    await expect(page.locator(`[data-id="${application.id}"]`)).toContainText('¥20K*14薪')
    await page.getByText('分析', { exact: true }).click()
    await expect(page.getByRole('heading', { name: '投递分析' })).toBeVisible()
    await expect(page.getByLabel('投递分析').getByText('内推', { exact: true })).toBeVisible()
    await expect(page.getByRole('img', { name: '求职流程各阶段数量' })).toBeVisible()
    await expect(page.getByRole('img', { name: '投递渠道占比' })).toBeVisible()
    await expect(page.getByRole('img', { name: '目标城市投递数量' })).toBeVisible()
    await expect(page.getByRole('img', { name: '近十四天投递趋势折线图' })).toBeVisible()

    const calendarRequests = page.waitForResponse((response) =>
      response.url().endsWith(`/api/applications/${application.id}/interviews`) && response.request().method() === 'GET'
    )
    await page.getByText('日历', { exact: true }).click()
    expect((await calendarRequests).ok()).toBeTruthy()
    await expect(page.getByRole('heading', { name: '面试日历' })).toBeVisible()
    await expect(page.getByText('Views Company', { exact: false })).toBeVisible()

    await page.getByText('表格', { exact: true }).click()
    await expect(page.getByRole('heading', { name: '投递明细' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Views Company' })).toBeVisible()

    await page.getByRole('button', { name: '切换为深色模式' }).click()
    await expect(page.locator('.app')).toHaveClass(/dark-mode/)
    await page.reload()
    await expect(page.locator('.app')).toHaveClass(/dark-mode/)
  } finally {
    await request.delete(`/api/applications/${application.id}`).catch(() => {})
  }
})
