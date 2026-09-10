import request from './request'

export function getBorrowPage(params) {
  return request({
    url: '/borrow/page',
    method: 'get',
    params
  })
}

export function getBorrowById(id) {
  return request({
    url: `/borrow/${id}`,
    method: 'get'
  })
}

export function getBorrowsByPadId(padId) {
  return request({
    url: `/borrow/pad/${padId}`,
    method: 'get'
  })
}

export function getAvailableReturnLayers() {
  return request({
    url: '/borrow/available-layers',
    method: 'get'
  })
}

export function getBorrowStatistics() {
  return request({
    url: '/borrow/statistics',
    method: 'get'
  })
}

export function checkoutPad(data) {
  return request({
    url: '/borrow/checkout',
    method: 'post',
    data
  })
}

export function returnPad(data) {
  return request({
    url: '/borrow/return',
    method: 'post',
    data
  })
}

export function exportBorrowRecords(params) {
  return request({
    url: '/borrow/export',
    method: 'get',
    params,
    responseType: 'blob'
  })
}
