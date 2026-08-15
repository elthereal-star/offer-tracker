// 与后端 ApplicationStatus 枚举一一对应，顺序即求职流程顺序
export const STATUSES = [
  { key: 'SAVED', label: '已收藏', color: '#f59e0b' },
  { key: 'APPLIED', label: '已投递', color: '#3b82f6' },
  { key: 'WRITTEN_TEST', label: '笔试', color: '#f59e0b' },
  { key: 'INTERVIEWING', label: '面试中', color: '#8b5cf6' },
  { key: 'OFFER', label: 'Offer', color: '#10b981' },
  { key: 'REJECTED', label: '未通过', color: '#ef4444' },
  { key: 'WITHDRAWN', label: '已放弃', color: '#94a3b8' }
]

// WRITTEN_TEST 仅用于兼容历史数据；笔试现在作为面试记录展示在“面试中”。
export const BOARD_STATUSES = STATUSES.filter((status) => status.key !== 'WRITTEN_TEST')
export const EDITABLE_STATUSES = BOARD_STATUSES

export const INTERVIEW_TYPES = [
  { key: 'WRITTEN', label: '笔试' },
  { key: 'PHONE', label: '电话面试' },
  { key: 'VIDEO', label: '视频面试' },
  { key: 'ONSITE', label: '现场面试' },
  { key: 'HR', label: 'HR 面试' }
]

export const INTERVIEW_RESULTS = [
  { key: 'PENDING', label: '待结果', tag: 'info', color: '#94a3b8' },
  { key: 'PASS', label: '通过', tag: 'success', color: '#10b981' },
  { key: 'FAIL', label: '未通过', tag: 'danger', color: '#ef4444' }
]

export const SOURCES = ['BOSS直聘', '官网', '猎聘', '内推', '脉脉', '校园招聘', '实习僧', '牛客', '前程无忧', '智联招聘', 'LinkedIn', '其他']

export const CITIES = ['北京', '上海', '深圳', '杭州', '广州', '成都', '武汉', '南京', '苏州', '西安', '远程']
