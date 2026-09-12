import request from './request'

export function getScrapPage(params) {
  return request({
    url: '/scrap/page',
    method: 'get',
    params
  })
}

export function getScrapById(id) {
  return request({
    url: `/scrap/${id}`,
    method: 'get'
  })
}

export function getScrapsByPadId(padId) {
  return request({
    url: `/scrap/pad/${padId}`,
    method: 'get'
  })
}

export function getOutboundCandidates() {
  return request({
    url: '/scrap/outbound-candidates',
    method: 'get'
  })
}

export function getScrapStatistics() {
  return request({
    url: '/scrap/statistics',
    method: 'get'
  })
}

export function outboundScrap(data) {
  return request({
    url: '/scrap/outbound',
    method: 'post',
    data
  })
}
