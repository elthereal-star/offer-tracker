import assert from 'node:assert/strict'
import test from 'node:test'

import { fetchAllApplicationPages, normalizeApiError } from './api.js'

test('loads and combines every application page', async () => {
  const applications = Array.from({ length: 205 }, (_, index) => ({ id: index + 1 }))
  const requestedPages = []

  const result = await fetchAllApplicationPages((page, size) => {
    requestedPages.push(page)
    const start = (page - 1) * size
    return Promise.resolve({
      records: applications.slice(start, start + size),
      pages: Math.ceil(applications.length / size)
    })
  })

  assert.deepEqual(requestedPages, [1, 2, 3])
  assert.equal(result.length, 205)
  assert.equal(result[0].id, 1)
  assert.equal(result[204].id, 205)
})

test('rejects the whole board load when any page fails', async () => {
  await assert.rejects(
    fetchAllApplicationPages((page) => {
      if (page === 2) return Promise.reject(new Error('page failed'))
      return Promise.resolve({ records: [{ id: page }], pages: 3 })
    }),
    /page failed/
  )
})

test('prefers backend error messages', () => {
  const error = normalizeApiError({
    response: { status: 404, data: { code: 404, message: '投递记录不存在' } }
  })
  assert.equal(error.message, '投递记录不存在')
})

test('distinguishes timeout and network failures', () => {
  assert.equal(normalizeApiError({ code: 'ECONNABORTED' }).message, '请求超时，请稍后重试')
  assert.equal(normalizeApiError({ message: 'Network Error' }).message, '无法连接服务器，请检查网络或后端服务')
})
