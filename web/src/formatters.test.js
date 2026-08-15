import assert from 'node:assert/strict'
import test from 'node:test'
import { formatSalary } from './formatters.js'

test('adds the yuan symbol to numeric salary values', () => {
  assert.equal(formatSalary('300-400/天'), '¥300-400/天')
  assert.equal(formatSalary('20K*14薪'), '¥20K*14薪')
})

test('keeps existing currency symbols and non-numeric labels', () => {
  assert.equal(formatSalary('¥25K*16薪'), '¥25K*16薪')
  assert.equal(formatSalary('面议'), '面议')
  assert.equal(formatSalary('', '待沟通'), '待沟通')
})
