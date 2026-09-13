import request from './request'

export function getInventoryPage(params) {
  return request({
    url: '/inventory/page',
    method: 'get',
    params
  })
}

export function getInventoryStatistics() {
  return request({
    url: '/inventory/statistics',
    method: 'get'
  })
}

export function getInventoryDetail(id) {
  return request({
    url: `/inventory/${id}`,
    method: 'get'
  })
}

export function createInventorySheet(data) {
  return request({
    url: '/inventory',
    method: 'post',
    data
  })
}

export function markInventoryItem(data) {
  return request({
    url: '/inventory/item/mark',
    method: 'post',
    data
  })
}

export function addInventoryExtraItem(sheetId, data) {
  return request({
    url: `/inventory/${sheetId}/extra`,
    method: 'post',
    data
  })
}

export function removeInventoryExtraItem(itemId) {
  return request({
    url: `/inventory/item/${itemId}`,
    method: 'delete'
  })
}

export function submitInventorySheet(data) {
  return request({
    url: '/inventory/submit',
    method: 'post',
    data
  })
}

export function closeInventorySheet(data) {
  return request({
    url: '/inventory/close',
    method: 'post',
    data
  })
}

export function cancelInventorySheet(id) {
  return request({
    url: `/inventory/${id}/cancel`,
    method: 'post'
  })
}
