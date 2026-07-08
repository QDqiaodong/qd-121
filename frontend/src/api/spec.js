import request from './request'

export function getAllSpecs() {
  return request({
    url: '/spec/list',
    method: 'get'
  })
}

export function getSpecKeys() {
  return request({
    url: '/spec/keys',
    method: 'get'
  })
}

export function getSpecByKey(key) {
  return request({
    url: `/spec/${key}`,
    method: 'get'
  })
}

export function addSpec(key, value) {
  return request({
    url: '/spec',
    method: 'post',
    params: { key, value }
  })
}

export function deleteSpec(key) {
  return request({
    url: `/spec/${key}`,
    method: 'delete'
  })
}
