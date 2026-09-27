import axios from 'axios'

// 统一解包后端 ApiResponse：code !== 0 时按失败处理
const http = axios.create({ baseURL: '/api', timeout: 10000 })

export function normalizeApiError(error) {
  const responseMessage = error?.response?.data?.message
  if (responseMessage) return new Error(responseMessage)
  if (error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT') {
    return new Error('请求超时，请稍后重试')
  }
  if (!error?.response) return new Error('无法连接服务器，请检查网络或后端服务')
  return new Error(error?.message || '请求失败')
}

http.interceptors.response.use(
  (res) => {
    if (res.data && typeof res.data === 'object' && 'code' in res.data) {
      if (res.data.code !== 0) {
        return Promise.reject(new Error(res.data.message || '请求失败'))
      }
      return res.data.data
    }
    return res.data
  },
  (err) => Promise.reject(normalizeApiError(err))
)

export async function fetchAllApplicationPages(fetchPage, pageSize = 100) {
  const firstPage = await fetchPage(1, pageSize)
  const totalPages = Number(firstPage.pages ?? 1)
  if (totalPages <= 1) return firstPage.records ?? firstPage

  const remainingPages = await Promise.all(
    Array.from({ length: totalPages - 1 }, (_, index) => fetchPage(index + 2, pageSize))
  )
  return [firstPage, ...remainingPages].flatMap((page) => page.records ?? page)
}

export default {
  listCompanies: () => http.get('/companies'),
  createCompany: (data) => http.post('/companies', data),
  updateCompany: (id, data) => http.put(`/companies/${id}`, data),
  deleteCompany: (id) => http.delete(`/companies/${id}`),
  async listApplications(filters = {}) {
    return fetchAllApplicationPages((page, size) =>
      http.get('/applications', { params: { page, size, ...filters } })
    )
  },
  createApplication: (data) => http.post('/applications', data),
  updateApplication: (id, data) => http.put(`/applications/${id}`, data),
  updateStatus: (id, status) => http.put(`/applications/${id}/status`, { status }),
  deleteApplication: (id) => http.delete(`/applications/${id}`),
  listInterviews: (applicationId) => http.get(`/applications/${applicationId}/interviews`),
  addInterview: (applicationId, data) => http.post(`/applications/${applicationId}/interviews`, data),
  updateInterviewResult: (roundId, result) =>
    http.put(`/applications/interviews/${roundId}/result`, { result }),
  listResumes: (applicationId) => http.get('/resumes', { params: applicationId ? { applicationId } : {} }),
  uploadResume: (file, applicationId) => {
    const form = new FormData()
    form.append('file', file)
    if (applicationId) form.append('applicationId', applicationId)
    return http.post('/resumes', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  deleteResume: (id) => http.delete(`/resumes/${id}`),
  validateImport: (data) => http.post('/data/import/validate', data),
  importBackup: (data, replaceExisting = false) =>
    http.post('/data/import', data, { params: { replaceExisting } }),
  stats: () => http.get('/stats/overview')
}
