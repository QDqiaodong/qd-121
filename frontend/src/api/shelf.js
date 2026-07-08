import request from './request'

export function getShelfLayerList() {
  return request({
    url: '/shelf-layer/list',
    method: 'get'
  })
}

export function getShelfLayerGroup() {
  return request({
    url: '/shelf-layer/group',
    method: 'get'
  })
}

export function getShelfLayerById(id) {
  return request({
    url: `/shelf-layer/${id}`,
    method: 'get'
  })
}

export function getShelfLayerByCode(layerCode) {
  return request({
    url: `/shelf-layer/code/${layerCode}`,
    method: 'get'
  })
}

export function saveShelfLayer(data) {
  return request({
    url: '/shelf-layer',
    method: 'post',
    data
  })
}

export function updateShelfLayer(data) {
  return request({
    url: '/shelf-layer',
    method: 'put',
    data
  })
}

export function deleteShelfLayer(id) {
  return request({
    url: `/shelf-layer/${id}`,
    method: 'delete'
  })
}
