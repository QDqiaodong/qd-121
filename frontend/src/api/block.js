import request from './request'

export function getLayerBlockPage(params) {
  return request({
    url: '/layer-block/page',
    method: 'get',
    params
  })
}

export function getActiveLayerBlocks() {
  return request({
    url: '/layer-block/active',
    method: 'get'
  })
}

export function getLayerBlockStatistics() {
  return request({
    url: '/layer-block/statistics',
    method: 'get'
  })
}

export function blockLayer(data) {
  return request({
    url: '/layer-block',
    method: 'post',
    data
  })
}

export function releaseLayerBlock(data) {
  return request({
    url: '/layer-block/release',
    method: 'post',
    data
  })
}
