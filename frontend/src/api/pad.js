import request from './request'

export function getPadPage(params) {
  return request({
    url: '/pad/page',
    method: 'get',
    params
  })
}

export function getPadById(id) {
  return request({
    url: `/pad/${id}`,
    method: 'get'
  })
}

export function getPadsByLayer(layerCode) {
  return request({
    url: `/pad/layer/${layerCode}`,
    method: 'get'
  })
}

export function getPadsByShelf(shelfCode) {
  return request({
    url: `/pad/shelf/${shelfCode}`,
    method: 'get'
  })
}

export function createPad(data) {
  return request({
    url: '/pad',
    method: 'post',
    data
  })
}

export function updatePad(data) {
  return request({
    url: '/pad',
    method: 'put',
    data
  })
}

export function deletePad(id) {
  return request({
    url: `/pad/${id}`,
    method: 'delete'
  })
}

export function bindLayer(data) {
  return request({
    url: '/pad/bind',
    method: 'post',
    data
  })
}

export function unbindLayer(data) {
  return request({
    url: '/pad/unbind',
    method: 'post',
    data
  })
}

export function getPadRecords(id) {
  return request({
    url: `/pad/${id}/records`,
    method: 'get'
  })
}

export function getStatistics() {
  return request({
    url: '/pad/statistics',
    method: 'get'
  })
}

export function exportPadsByLayer(layerCode) {
  return request({
    url: `/pad/export/layer/${layerCode}`,
    method: 'get',
    responseType: 'blob'
  })
}

export function exportAllPads() {
  return request({
    url: '/pad/export/all',
    method: 'get',
    responseType: 'blob'
  })
}

export function previewImportPads(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/pad/import/preview',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function confirmImportPads(rows) {
  return request({
    url: '/pad/import/confirm',
    method: 'post',
    data: { rows }
  })
}

export function downloadImportTemplate() {
  return request({
    url: '/pad/import/template',
    method: 'get',
    responseType: 'blob'
  })
}
