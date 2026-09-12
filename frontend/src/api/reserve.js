import request from './request'

export function getPadReservePage(params) {
  return request({
    url: '/pad-reserve/page',
    method: 'get',
    params
  })
}

export function getPadReserveById(id) {
  return request({
    url: `/pad-reserve/${id}`,
    method: 'get'
  })
}

export function getPadReserveStatistics() {
  return request({
    url: '/pad-reserve/statistics',
    method: 'get'
  })
}

export function registerPadReserve(data) {
  return request({
    url: '/pad-reserve',
    method: 'post',
    data
  })
}

export function releasePadReserve(data) {
  return request({
    url: '/pad-reserve/release',
    method: 'post',
    data
  })
}
