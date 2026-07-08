import request from './request'

export function getRecordPage(params) {
  return request({
    url: '/adjust-record/page',
    method: 'get',
    params
  })
}

export function getRecordById(id) {
  return request({
    url: `/adjust-record/${id}`,
    method: 'get'
  })
}

export function getRecordsByPadId(padId) {
  return request({
    url: `/adjust-record/pad/${padId}`,
    method: 'get'
  })
}

export function exportRecords(params) {
  return request({
    url: '/adjust-record/export',
    method: 'get',
    params,
    responseType: 'blob'
  })
}
