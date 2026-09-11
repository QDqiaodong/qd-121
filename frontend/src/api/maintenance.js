import request from './request'

export function getMaintenancePage(params) {
  return request({
    url: '/maintenance/page',
    method: 'get',
    params
  })
}

export function getMaintenanceById(id) {
  return request({
    url: `/maintenance/${id}`,
    method: 'get'
  })
}

export function getMaintenanceByPadId(padId) {
  return request({
    url: `/maintenance/pad/${padId}`,
    method: 'get'
  })
}

export function getMaintenanceDetail(padId) {
  return request({
    url: `/maintenance/pad/${padId}/detail`,
    method: 'get'
  })
}

export function getMaintenanceStatistics() {
  return request({
    url: '/maintenance/statistics',
    method: 'get'
  })
}

export function registerMaintenance(data) {
  return request({
    url: '/maintenance',
    method: 'post',
    data
  })
}
