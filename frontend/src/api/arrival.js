import request from './request'

export function getArrivalPage(params) {
  return request({
    url: '/arrival/page',
    method: 'get',
    params
  })
}

export function getArrivalById(id) {
  return request({
    url: `/arrival/${id}`,
    method: 'get'
  })
}

export function getArrivalStatistics() {
  return request({
    url: '/arrival/statistics',
    method: 'get'
  })
}

export function registerArrival(data) {
  return request({
    url: '/arrival',
    method: 'post',
    data
  })
}

export function passArrival(data) {
  return request({
    url: '/arrival/pass',
    method: 'post',
    data
  })
}

export function rejectArrival(data) {
  return request({
    url: '/arrival/reject',
    method: 'post',
    data
  })
}
