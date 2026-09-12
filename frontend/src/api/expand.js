import request from './request'

export function getLayerExpandPage(params) {
  return request({
    url: '/layer-capacity-expand/page',
    method: 'get',
    params
  })
}

export function getEffectiveLayerExpands() {
  return request({
    url: '/layer-capacity-expand/active',
    method: 'get'
  })
}

export function getLayerExpandStatistics() {
  return request({
    url: '/layer-capacity-expand/statistics',
    method: 'get'
  })
}

export function registerLayerExpand(data) {
  return request({
    url: '/layer-capacity-expand',
    method: 'post',
    data
  })
}

export function finishLayerExpand(data) {
  return request({
    url: '/layer-capacity-expand/finish',
    method: 'post',
    data
  })
}
